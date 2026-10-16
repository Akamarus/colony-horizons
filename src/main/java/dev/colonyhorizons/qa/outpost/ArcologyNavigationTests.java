package dev.colonyhorizons.qa.outpost;

import com.google.gson.JsonParser;
import com.ldtteam.structurize.blueprints.v1.*;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Real native navigation through physically placed blueprints; not construction/production evidence. */
@PrefixGameTestTemplate(false)
public final class ArcologyNavigationTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=400000)
 public static void all_hut_levels_access(GameTestHelper h)throws Exception { new Run(h).start(); }
 static final class Run {
  final GameTestHelper h;final Path root=Path.of(System.getProperty("colonyhorizons.arcologyPath"));
  final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ProbeResult> rows=new ArrayList<>();final List<String> paths=new ArrayList<>();
  BlockPos origin;final BlockPos entry=new BlockPos(29,-40,5);
  OutpostProbeFixture colony;ICitizenData citizen;int index,age,leg;List<BlockPos> route;BlockPos buildingCenter;boolean arrived;net.minecraft.world.phys.Vec3 previous;
  final String reportName;final int expectedCount;boolean transformed,interior,controlDone;int legAge;String recallEvidence="";
  Run(GameTestHelper h){this.h=h;reportName="ArcologyNavigation";expectedCount=238;}
  Run(GameTestHelper h,List<String> selected,String reportName){this.h=h;this.reportName=reportName;paths.addAll(selected);expectedCount=selected.size();}
  Run(GameTestHelper h,List<String> selected,String reportName,boolean transformed){this(h,selected.stream().flatMap(path->java.util.stream.IntStream.range(0,8).mapToObj(i->path)).toList(),reportName);this.transformed=transformed;}
  Run(GameTestHelper h,List<String> selected,String reportName,boolean transformed,boolean interior){this(h,selected,reportName,transformed);this.interior=interior;}
  boolean blockedControl(){return interior && index==0 && !controlDone;}
  void start()throws Exception {
   var level=h.getLevel();level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
   level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=-1;x<=4;x++)for(int z=-1;z<=2;z++)level.setChunkForced(x,z,true);
   if(paths.isEmpty())try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/qa/arcology-building-anchors.json")))){
    paths.addAll(JsonParser.parseReader(reader).getAsJsonObject().keySet());
   }
   if(!interior)paths.sort(Comparator.comparingInt((String s)->s.contains("miner")?0:s.contains("netherworker")?1:s.contains("plantation")?2:3).thenComparing(s->s));
   colony=OutpostProbeFixture.create(level,new BlockPos(0,-59,0),UUID.randomUUID());citizen=colony.createCitizen();
   if(citizen==null || citizen.getEntity().isEmpty())throw new IllegalStateException("Native citizen spawn failed");
   citizen.setPaused(true);citizen.setSaturation(20);
   ((MinecoloniesAdvancedPathNavigate)citizen.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   // Same one-time AI event mechanism as MineColonies' native citizen walk command.
   // Without it, an unemployed citizen's wandering AI replaces the requested paths.
   var entity=(com.minecolonies.core.entity.citizen.EntityCitizen)citizen.getEntity().orElseThrow();
   entity.getCitizenAI().addTransition(new com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget<com.minecolonies.api.entity.ai.statemachine.states.IState>(() -> {
    if(route!=null && leg<route.size()){
     var p=route.get(leg);
     if(!arrived)arrived=!interior && !reportName.equals("ArcologyBuilderRecall") && leg==0
      ? EntityNavigationUtils.walkCloseToXNearY(entity,p,buildingCenter,EntityNavigationUtils.BUILDING_REACH_DIST,true)
      : EntityNavigationUtils.walkToPos(entity,p,1,true);
    }
    return entity.getCitizenAI().getState();
   }){ @Override public boolean shouldRemove(){return index>=paths.size();} });
   next();
  }
  void next(){try{
   if(index==paths.size()){finish();return;}
   var level=h.getLevel();var entity=citizen.getEntity().orElseThrow();entity.getNavigation().stop();
   // Initial placement for each independent case only; no teleports during a measured route.
   entity.setPos(entry.getX()+0.5,entry.getY(),entry.getZ()+0.5);
   var bp=BlueprintUtil.readBlueprintFromNBT(NbtIo.readCompressed(root.resolve(paths.get(index)),NbtAccounter.create(64000000)),level.registryAccess());
   if(transformed)bp.setRotationMirror(com.ldtteam.structurize.api.RotationMirror.values()[index%8],level);
   // Build-tool placement is referenced to the hut, not the blueprint minimum Y.
   // Mine and several animal huts include below-floor space. Preserve that space
   // underground instead of accidentally raising their entrances two blocks.
   origin=new BlockPos(32,entry.getY()-bp.getPrimaryBlockOffset().getY(),0);
   for(int x=28;x<(transformed?74:66);x++)for(int z=-4;z<(transformed?42:32);z++){
    for(int y=-63;y<entry.getY()-1;y++)level.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),18);
    level.setBlock(new BlockPos(x,entry.getY()-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),18);
    for(int y=entry.getY();y<entry.getY()+16;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);
   }
   for(var info:bp.getBlockInfoAsList()){
    var state=bp.getBlockState(info.getPos());var id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    if(id.equals("structurize:blocktagsubstitution") || id.equals("structurize:blocksubstitution"))state=Blocks.AIR.defaultBlockState();
    else if(id.equals("structurize:blocksolidsubstitution"))state=Blocks.STONE.defaultBlockState();
    else if(id.equals("structurize:blockfluidsubstitution"))state=Blocks.WATER.defaultBlockState();
    var pos=origin.offset(info.getPos());level.setBlock(pos,state,18);
    var tile=level.getBlockEntity(pos);
    if(tile!=null && info.getTileEntityData()!=null)tile.loadWithComponents(info.getTileEntityData().copy(),level.registryAccess());
   }
   var target=origin.offset(bp.getPrimaryBlockOffset());
   buildingCenter=new BlockPos(origin.getX()+bp.getSizeX()/2,target.getY(),origin.getZ()+bp.getSizeZ()/2);
   if(interior){
    var destinations=new TreeSet<BlockPos>(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY));
    boolean stable=paths.get(index).contains("stable");int buildingLevel=Character.digit(paths.get(index).charAt(paths.get(index).length()-11),10);
    for(var info:bp.getBlockInfoAsList())if(info.getTileEntityData()!=null){
     for(var raw:info.getTileEntityData().getCompound("blueprintDataProvider").getList("posTagMap",Tag.TAG_COMPOUND)){
      var marker=(CompoundTag)raw;boolean selected=false;
      for(var name:marker.getList("tagNameList",Tag.TAG_COMPOUND)){
       var label=((CompoundTag)name).getString("tagName");selected|=stable?label.equals("stall"):label.equals("knight")||label.equals("archer");
      }
      if(selected){var offset=marker.getCompound("tagPos");destinations.add(origin.offset(info.getPos()).offset(offset.getInt("x"),offset.getInt("y"),offset.getInt("z")));}
     }
    }
    h.assertTrue(destinations.size()==(stable?2*buildingLevel:4),"Missing/duplicate interior destinations "+paths.get(index)+": "+destinations);
    for(var p:destinations)h.assertTrue(level.getBlockState(p).getCollisionShape(level,p).isEmpty()&&level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty()&&level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP),"Interior marker obstructed/unsupported: "+p);
    var stops=new ArrayList<BlockPos>(destinations);
    if(blockedControl()){
     var sealed=stops.getFirst();
     for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)==2||Math.abs(dz)==2)for(int dy=0;dy<4;dy++)level.setBlock(sealed.offset(dx,dy,dz),Blocks.STONE.defaultBlockState(),18);
     stops=new ArrayList<>(List.of(sealed));
    }
    stops.add(entry);route=List.copyOf(stops);
   }else if(reportName.equals("ArcologyBuilderRecall")){
    // Elevated tower: surrounding terrain must not give a trapped worker a shortcut.
    for(int x=28;x<74;x++)for(int z=-4;z<42;z++){
     if(x>=origin.getX()&&x<origin.getX()+bp.getSizeX()&&z>=origin.getZ()&&z<origin.getZ()+bp.getSizeZ())continue;
     for(int y=-63;y<entry.getY();y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);
    }
    // One connecting module corridor on the west side, as in an elevated Arcology.
    for(int x=entry.getX();x<=origin.getX();x++)for(int z=1;z<=5;z++)level.setBlock(new BlockPos(x,entry.getY()-1,z),Blocks.OAK_PLANKS.defaultBlockState(),18);
    h.assertTrue(com.minecolonies.core.util.TeleportHelper.teleportCitizen(entity,level,target),"Recall failed");
    recallEvidence="Native Recall start="+entity.position()+" elevated module -> corridor; ";
    route=List.of(entry);
   }else route=List.of(target,entry);
   leg=0;age=0;legAge=0;arrived=false;previous=entity.position();schedule();
  }catch(Throwable e){record(false,e.toString());next();}}
  void schedule(){h.runAfterDelay(10,()->{try{tick();}catch(Throwable e){record(false,e.toString());next();}});}
  void tick()throws Exception {
   age+=10;legAge+=10;var entity=citizen.getEntity().orElseThrow();
   if(!entity.isAlive() || previous.distanceTo(entity.position())>8)throw new IllegalStateException("Death or recovery teleport during measured route");
   previous=entity.position();var target=route.get(leg);
   if(arrived){
    if(blockedControl())throw new IllegalStateException("Sealed interior incorrectly reported reachable");
    if(interior && !entity.blockPosition().equals(target))throw new IllegalStateException("Interior arrival outside exact destination: "+entity.position()+" target="+target);
    legAge=0;
    arrived=false;entity.getNavigation().stop();
    if(++leg==route.size()){record(true,reportName.equals("ArcologyBuilderRecall")?"Native Recall followed by walking to the elevated exterior corridor; no recovery teleport/block breaking; ticks="+age:interior?"Native citizen reached every interior marker ("+(route.size()-1)+") then returned outside; all rotations/mirrors use exact destinations, recovery disabled; walking only, not mounted/production; ticks="+age:"Native citizen walked outside -> native building interaction range (4 blocks, center-biased approach) -> outside; hut aligned to exterior ground, native walk-command AI event, recovery teleport/block breaking disabled; ticks="+age);next();return;}
   }
   if((interior?legAge:age)>=(reportName.equals("ArcologyBuilderRecall")?600:2400)){if(blockedControl()){record(true,"Sealed stall unreachable for 2400 ticks; same citizen alive, no recovery teleport; negative control only");next();return;}record(false,"Could not reach leg="+leg+" target="+target+" actual="+entity.position()+"; native path job="+((MinecoloniesAdvancedPathNavigate)entity.getNavigation()).getPathResult()+"");next();return;}
   schedule();
  }
  void record(boolean pass,String evidence){rows.add(new ProbeResult(blockedControl()?"sealed_stall_control":"navigation:"+paths.get(index)+(transformed?":"+com.ldtteam.structurize.api.RotationMirror.values()[index%8].name():""),pass?ProbeResult.Outcome.PASS:ProbeResult.Outcome.FAIL,recallEvidence+evidence));if(blockedControl())controlDone=true;else index++;try{ProbeReport.write(output,reportName,rows);}catch(Exception e){throw new RuntimeException(e);}}
  void finish()throws Exception {ProbeReport.write(output,reportName,rows);h.assertTrue(rows.size()==expectedCount+(interior?1:0) && rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Navigation failures; see ArcologyNavigation.json");h.succeed();}
 }
}
