package dev.colonyhorizons.qa.outpost;
import com.ldtteam.structurize.blueprints.v1.*;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
/** Physically adjoining modules and a six-block upper storey; native citizen walk commands. */
@PrefixGameTestTemplate(false)
public final class ArcologyConnectedLayoutTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=18000)
 public static void connected_access(GameTestHelper h){
  for(int x=29;x<=35;x++)for(int z=-2;z<=3;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new Run(h).start());
 }
 static final class Run {
  final GameTestHelper h;final Path root=Path.of(System.getProperty("colonyhorizons.arcologyPath")),output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final String[] cases={"connected_timber_modules","connected_stone_modules","stacked_modules_external_stairs"};
  final List<ProbeResult> rows=new ArrayList<>();ICitizenData citizen;List<BlockPos> route;int index,leg,age;boolean arrived,crossedGate;Vec3 previous;
  Run(GameTestHelper h){this.h=h;}
  void start(){try{
   var l=h.getLevel();l.setDayTime(6000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   var fixture=OutpostProbeFixture.create(l,new BlockPos(480,16,0),UUID.randomUUID());citizen=fixture.createCitizen();check(citizen!=null&&citizen.getEntity().isPresent(),"Native citizen spawn");citizen.setPaused(true);citizen.setSaturation(20);
   var entity=(com.minecolonies.core.entity.citizen.EntityCitizen)citizen.getEntity().orElseThrow();((MinecoloniesAdvancedPathNavigate)entity.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   entity.getCitizenAI().addTransition(new com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget<com.minecolonies.api.entity.ai.statemachine.states.IState>(() -> {
    if(route!=null&&leg<route.size()&&!arrived)arrived=EntityNavigationUtils.walkToPos(entity,route.get(leg),1,true);
    return entity.getCitizenAI().getState();
   }){public boolean shouldRemove(){return index>=cases.length;}});next();
  }catch(Throwable e){fail(e);}}
  void paste(String path,BlockPos origin)throws Exception{
   var l=h.getLevel();var b=BlueprintUtil.readBlueprintFromNBT(NbtIo.readCompressed(root.resolve(path),NbtAccounter.create(64000000)),l.registryAccess());
   for(var info:b.getBlockInfoAsList()){var p=origin.offset(info.getPos());l.setBlock(p,b.getBlockState(info.getPos()),18);var tile=l.getBlockEntity(p);if(tile!=null&&info.getTileEntityData()!=null)tile.loadWithComponents(info.getTileEntityData().copy(),l.registryAccess());}
  }
  void next()throws Exception{
   if(index==cases.length){ProbeReport.write(output,"ArcologyConnectedLayout",rows);h.succeed();return;}
   var l=h.getLevel();var e=citizen.getEntity().orElseThrow();e.getNavigation().stop();
   for(int x=500;x<=540;x++)for(int z=-10;z<=35;z++){l.setBlock(new BlockPos(x,15,z),Blocks.DIRT.defaultBlockState(),18);for(int y=16;y<=29;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);}
   int gate=index==0?1:3,stable=index==0?1:5;
   paste("military/gatehouse"+gate+".blueprint",new BlockPos(512,15,0));paste("military/stable"+stable+".blueprint",new BlockPos(512,15,10));
   var start=new BlockPos(519,16,-5);var target=new BlockPos(519,16,25);
   if(index==2){
    paste("military/gatehouse3.blueprint",new BlockPos(512,21,0));paste("military/stable5.blueprint",new BlockPos(512,21,10));
    // Disclosed fixture staircase: tests six-block floor spacing and shared
    // entrances, not automatic connection/stair generation by the pack.
    for(int x=518;x<=520;x++)for(int step=0;step<6;step++){
     int z=-7+step,y=16+step;for(int yy=16;yy<y;yy++)l.setBlock(new BlockPos(x,yy,z),Blocks.OAK_PLANKS.defaultBlockState(),18);
     l.setBlock(new BlockPos(x,y,z),Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH),18);
    }
    for(int x=518;x<=520;x++)l.setBlock(new BlockPos(x,21,-1),Blocks.OAK_PLANKS.defaultBlockState(),18);
    start=new BlockPos(516,16,-8);target=new BlockPos(519,22,25);
   }
   e.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);previous=e.position();route=List.of(target,start);leg=0;age=0;arrived=false;crossedGate=false;schedule();
  }
  void schedule(){h.runAfterDelay(10,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   age+=10;var e=citizen.getEntity().orElseThrow();check(e.isAlive()&&previous.distanceTo(e.position())<=8,"Death or recovery teleport");previous=e.position();
   var position=e.blockPosition();crossedGate|=position.getX()>=513&&position.getX()<=525&&position.getZ()>=2&&position.getZ()<=7&&position.getY()==(index==2?22:16);
   if(arrived){check(crossedGate,"Route bypassed the connecting gatehouse");crossedGate=false;check(e.blockPosition().distSqr(route.get(leg))<=4,"Reported arrival outside target range");arrived=false;e.getNavigation().stop();
    if(++leg==route.size()){
     rows.add(new ProbeResult(cases[index],ProbeResult.Outcome.PASS,"Same native citizen walked exterior -> through adjoining Gatehouse -> Stable central aisle -> exterior, exact one-block route targets, recovery disabled; ticks="+age+(index==2?". Upper modules six blocks above lower modules; disclosed three-wide external fixture stair. Does not certify automatic stair generation, all rotations or mounted cavalry.":". Blueprint envelopes touch without wall edits or bridge blocks. Not job production or cavalry testing.")));
     ProbeReport.write(output,"ArcologyConnectedLayout",rows);index++;next();return;
    }
   }
   check(age<5000,"Connected route timeout case="+cases[index]+" leg="+leg+" actual="+e.position()+" target="+route.get(leg));schedule();
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("connected_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyConnectedLayout",rows);}catch(Exception ignored){}h.fail(e.toString());}
 }
}
