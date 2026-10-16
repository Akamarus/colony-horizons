package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.*;
import java.nio.file.*;
import java.util.*;

/** Disposable QA only. Native AI performs all construction and forestry after initial fixture setup. */
final class ArcologyOperationsFixture {
 static final BlockPos HOME=new BlockPos(0,-59,0),REMOTE=new BlockPos(512,-59,0);
 final GameTestHelper h;final ServerLevel level;final Path output;
 final List<ProbeResult> rows=new ArrayList<>();
 final List<IBuilding> buildings=new ArrayList<>();final List<Blueprint> blueprints=new ArrayList<>();
 final List<BoundingBox> volumes=new ArrayList<>();final List<ICitizenData> citizens=new ArrayList<>();
 final List<BlockPos> anchors=List.of(REMOTE.offset(-24,0,0),REMOTE.offset(-8,0,0),REMOTE.offset(8,0,0),REMOTE.offset(-24,0,24),REMOTE.offset(-8,0,24),REMOTE.offset(8,0,24));
 final Map<String,Integer> supplied=new TreeMap<>();
 final Map<Integer,UUID> identities=new HashMap<>();final Map<Integer,net.minecraft.world.phys.Vec3> previous=new HashMap<>();
 OutpostProbeFixture fixture;net.minecraft.server.level.ServerPlayer owner;BuildingBuilder builder;
 int elapsed,target,phase,accessIndex,accessWorker;boolean requested;double maxDistance;
 ArcologyOperationsFixture(GameTestHelper h){this.h=h;level=h.getLevel();output=Path.of(System.getProperty("colonyhorizons.probeOutput"));}
 void start(){try{
  level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
  level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
  level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
  level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
  var ownerId=UUID.randomUUID();level.getServer().getProfileCache().add(new GameProfile(ownerId,"OperationsProbe"));
  fixture=OutpostProbeFixture.create(level,HOME,ownerId);
  var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(ownerId,"OperationsProbe"));
  owner=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new GameProfile(ownerId,"OperationsProbe"),net.minecraft.server.level.ClientInformation.createDefault());
  owner.connection=fake.connection; // QA headless subscriber: native colonies intentionally reject FakePlayer subscribers.
  owner.setPos(REMOTE.getX(),REMOTE.getY(),REMOTE.getZ());fixture.colony().getPackageManager().addImportantColonyPlayer(owner);fixture.colony().getPackageManager().addCloseSubscriber(owner);
  ChunkDataHelper.staticClaimInRange((Colony)fixture.colony(),true,REMOTE,3,level,false);
  for(int x=464;x<=575;x++)for(int z=-32;z<=63;z++){
   // Superflat test ground, grounded in the world's bedrock layer; no floating test platforms.
   for(int y=-63;y<=-61;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState());
   level.setBlockAndUpdate(new BlockPos(x,-60,z),Blocks.GRASS_BLOCK.defaultBlockState());
   for(int y=-59;y<=-45;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
  }
  String packName=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
  var paths=List.of("fundamentals/builder1.blueprint","fundamentals/lumberjack1.blueprint","fundamentals/lumberjack1.blueprint","fundamentals/citizen1.blueprint","fundamentals/citizen1.blueprint","fundamentals/citizen1.blueprint");
  for(int i=0;i<paths.size();i++){
   var path=paths.get(i);var bp=StructurePacks.getBlueprint(packName,path,level.registryAccess());
   check(bp!=null,"Missing actual Arcology blueprint: "+path);blueprints.add(bp);
   var anchor=anchors.get(i);var origin=anchor.subtract(bp.getPrimaryBlockOffset());
   var box=new BoundingBox(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+bp.getSizeX()-1,origin.getY()+bp.getSizeY()-1,origin.getZ()+bp.getSizeZ()-1);
   for(var existing:volumes)check(!existing.intersects(box),"Layout collision");volumes.add(box);
   for(int upgrade=2;upgrade<=5;upgrade++){
    var higher=StructurePacks.getBlueprint(packName,path.replace("1.blueprint",upgrade+".blueprint"),level.registryAccess());
    check(higher!=null && higher.getSizeX()==bp.getSizeX() && higher.getSizeY()==bp.getSizeY() && higher.getSizeZ()==bp.getSizeZ() && higher.getPrimaryBlockOffset().equals(bp.getPrimaryBlockOffset()),"Unreserved upgrade extent");
   }
   for(int x=box.minX();x<=box.maxX();x++)for(int z=box.minZ();z<=box.maxZ();z++)check(level.getBlockState(new BlockPos(x,box.minY()-1,z)).isSolid(),"Unsupported footprint");
   var hut=(AbstractBlockHut<?>)bp.getBlockState(bp.getPrimaryBlockOffset()).getBlock();
   level.setBlockAndUpdate(anchor,bp.getBlockState(bp.getPrimaryBlockOffset()));
   hut.onBlockPlacedByBuildTool(level,anchor,level.getBlockState(anchor),owner,new ItemStack(hut),com.ldtteam.structurize.api.RotationMirror.NONE,packName,path);
   var building=fixture.colony().getServerBuildingManager().getBuilding(anchor);check(building.getBuildingLevel()==0,"Must start unbuilt");buildings.add(building);
  }
  builder=(BuildingBuilder)buildings.getFirst();
  check(builder.getModule(BuildingModules.BUILDER_WORK).assignCitizen(spawnWorker(0)),"Builder assignment");
  for(var tool:List.of(Items.STONE_AXE,Items.STONE_PICKAXE,Items.STONE_SHOVEL))for(int n=0;n<4;n++)check(ItemHandlerHelper.insertItemStacked(builder.getItemHandlerCap((Direction)null),new ItemStack(tool),false).isEmpty(),"Local spare tool capacity");
  pass("supported_layout","Six nonoverlapping level-1 through level-5 envelopes on grounded superflat terrain; ground-level gaps provide access, no inferred vertical connectors");
  schedule();
 }catch(Throwable e){fail(e);}}
 void assignHome(ICitizenData c,int index){
  var home=buildings.get(index);
  if(c.getHomeBuilding()==home)return;
  if(c.getHomeBuilding()!=null)c.getHomeBuilding().getModule(BuildingModules.LIVING).removeCitizen(c);
  check(home.getModule(BuildingModules.LIVING).assignCitizen(c),"Native local housing capacity/assignment");
 }
 ICitizenData spawnWorker(int index){
  var c=fixture.createCitizen();check(c!=null && c.getEntity().isPresent(),"Citizen spawn");
  c.setSaturation(20);c.getEntity().orElseThrow().setPos(anchors.get(index).getX()+2.5,-59,-4.5);
  if(index==0)c.getInventory().setStackInSlot(0,new ItemStack(Items.STONE_AXE));
  if(index==0)c.getInventory().setStackInSlot(1,new ItemStack(Items.STONE_PICKAXE));
  if(index==0)c.getInventory().setStackInSlot(2,new ItemStack(Items.STONE_SHOVEL));
  c.getInventory().setStackInSlot(3,new ItemStack(Items.BREAD,64));
  citizens.add(c);identities.put(c.getId(),c.getEntity().orElseThrow().getUUID());previous.put(c.getId(),c.getEntity().orElseThrow().position());return c;
 }
 void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
 void tick()throws Exception{
  elapsed+=20;
  for(var c:citizens){
   var e=c.getEntity().orElseThrow();check(e.isAlive(),"Worker died");
   check(fixture.colony().getCitizenManager().getCivilian(c.getId())==c && e.getUUID().equals(identities.get(c.getId())) && c.getColony().getID()==fixture.colony().getID(),"Worker identity changed");
   if(phase>0){int index=citizens.indexOf(c);check(c.getWorkBuilding()==buildings.get(index) && c.getHomeBuilding()==buildings.get(index+3),"Native local assignment changed");}
   var last=previous.put(c.getId(),e.position());check(last==null || last.distanceTo(e.position())<=12,"Native navigation jumped/teleported: "+c.getId()+" from "+last+" to "+e.position());
   double distance=Math.sqrt(e.distanceToSqr(REMOTE.getX(),REMOTE.getY(),REMOTE.getZ()));maxDistance=Math.max(maxDistance,distance);
   check(distance<100,"Worker left local district toward home: "+e.blockPosition());
  }
  check(fixture.colony().getCitizenManager().getCurrentCitizenCount()==citizens.size(),"Unexpected native population change");
  if(elapsed%200==0)diagnostic();
  check(elapsed<95000,"Operation timed out; see diagnostics.txt");
  if(phase==0){
   if(target<buildings.size()){
    var b=buildings.get(target);
    if(!requested){b.requestUpgrade(owner,builder.getPosition());requested=true;check(!fixture.colony().getWorkManager().getWorkOrders().isEmpty(),"Native construction request rejected for "+target);}
    supplyBuilder();
    if(b.getBuildingLevel()==1){
     check(countPlaced(target)>10,"Completion without actual structure blocks");
     Files.writeString(output.resolve("diagnostics.txt"),"COMPLETED target="+target+" tick="+elapsed+" blocks="+countPlaced(target)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
     target++;requested=false;
    }
   }else{
    check(fixture.colony().getWorkManager().getWorkOrders().isEmpty(),"Unfinished native orders");
    pass("native_builder_completion","Native builder constructed six actual Arcology buildings from level zero; supplies="+supplied);
    assignHome(citizens.getFirst(),3);
    for(int i=1;i<3;i++){var c=spawnWorker(i);assignHome(c,i+3);check(buildings.get(i).getModule(BuildingModules.FORESTER_WORK).assignCitizen(c),"Forester assignment");c.setPaused(true);}
    for(var c:citizens)((com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler.createStuckHandler()); // QA access must work without recovery teleport/block breaking.
    phase=1;
   }
  }else if(phase==1){
   if(accessWorker<citizens.size()){
    var c=citizens.get(accessWorker);c.setPaused(true);var entity=c.getEntity().orElseThrow();
    var route=new ArrayList<BlockPos>();
    route.add(standNear(anchors.get(accessWorker)));
    route.add(standNear(homeBed(accessWorker+3)));
    route.add(standNear(anchors.get(accessWorker)));
    if(accessWorker>0){route.add(trees.get(accessWorker-1).offset(-4,0,0));route.add(standNear(anchors.get(accessWorker)));}
    if(accessIndex==route.size()){accessWorker++;accessIndex=0;}
    else{
     var dest=route.get(accessIndex);
     if(entity.distanceToSqr(dest.getX()+0.5,dest.getY(),dest.getZ()+0.5)<1.5){
      Files.writeString(output.resolve("diagnostics.txt"),"ACCESS citizen="+c.getId()+" step="+accessIndex+" destination="+dest+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);accessIndex++;
     }else entity.getNavigation().moveTo(dest.getX()+0.5,dest.getY(),dest.getZ()+0.5,1.0);
    }
   }else{
    pass("worker_access","Each worker navigated workplace-to-own-bed-to-workplace; both foresters also reached their forestry approach and returned. Adjacent open positions required; native recovery teleports/block breaking disabled for access and forestry; identity and sampled movement checks active");
    beginForestry();phase=2;
   }  }else if(forestryComplete()){
   pass("two_foresters_harvest_replant","Both native foresters harvested distinct trees, collected real logs, used tools and replanted within restricted areas");
   pass("local_supply_boundary","Workers stayed within 100 blocks of satellite; max observed="+maxDistance+". Tools collected from local huts; sapling pickups observed="+java.util.Arrays.toString(saplingsPickedUp)+"; one starter sapling and bread preloaded; autonomous sapling/food restocking is not established. No courier or home route exercised");
   writeReport();h.succeed();return;
  }
  schedule();
 }
 BlockPos homeBed(int index){
  var b=volumes.get(index);
  for(var p:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()))if(level.getBlockState(p).getBlock() instanceof BedBlock)return p.immutable();
  throw new IllegalStateException("No actual bed in completed home "+index);
 }
 BlockPos standNear(BlockPos target){
  for(var direction:Direction.Plane.HORIZONTAL){
   var p=target.relative(direction);
   if(level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir()&&!level.getBlockState(p.below()).getCollisionShape(level,p.below()).isEmpty())return p;
  }
  throw new IllegalStateException("No usable stand position near "+target);
 }
 int countPlaced(int i){var box=volumes.get(i);int n=0;for(var p:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()))if(!level.getBlockState(p).isAir())n++;return n;}
 void supplyBuilder(){
  // Test provisioning uses native calculated requirements, including item components; not guessed palettes.
  for(var r:new ArrayList<>(builder.getNeededResources().values())){
   var stack=r.getItemStack();int have=count(builder.getItemHandlerCap((Direction)null),stack)+count(citizens.getFirst().getInventory(),stack);
   int missing=Math.min(64,r.getAmount()-have);if(missing<=0)continue;
   var incoming=stack.copyWithCount(missing);var rest=ItemHandlerHelper.insertItemStacked(builder.getItemHandlerCap((Direction)null),incoming,false);
   supplied.merge(stack.toString(),missing-rest.getCount(),Integer::sum);
  }
 }
 static int count(IItemHandler inv,ItemStack stack){int n=0;for(int i=0;i<inv.getSlots();i++){var s=inv.getStackInSlot(i);if(ItemStack.isSameItemSameComponents(s,stack))n+=s.getCount();}return n;}
 final List<BlockPos> trees=List.of(new BlockPos(546,-59,-14),new BlockPos(546,-59,18));
 final boolean[] harvested={false,false},saplingsPickedUp={false,false};
 void beginForestry(){

  for(int i=0;i<2;i++){
   var hut=(BuildingLumberjack)buildings.get(i+1);var c=citizens.get(i+1);var tree=trees.get(i);

   hut.setRestrictedArea(tree.offset(-7,-1,-7),tree.offset(7,10,7));
   check(hut.shouldRestrict() && hut.shouldReplant(),"Native restrict/replant settings");

   c.setPaused(false);c.getInventory().setStackInSlot(0,ItemStack.EMPTY);
   c.getInventory().setStackInSlot(4,new ItemStack(Items.OAK_SAPLING)); // Native first-tree timeout requires a starter sapling before a hut round trip.
   check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.STONE_AXE),false).isEmpty(),"Forester local tool capacity");
   check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.STONE_HOE),false).isEmpty(),"Forester leaf-clearing tool capacity");
   check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.OAK_SAPLING,16),false).isEmpty(),"Forester sapling capacity");
   ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.BREAD,32),false);
   for(int y=0;y<5;y++)level.setBlockAndUpdate(tree.above(y),Blocks.OAK_LOG.defaultBlockState());
   for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=3;y<=5;y++)if(x!=0||z!=0||y==5)level.setBlockAndUpdate(tree.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState());
  }
  var control=new BlockPos(562,-59,0);
  for(int y=0;y<5;y++)level.setBlockAndUpdate(control.above(y),Blocks.OAK_LOG.defaultBlockState());
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=3;y<=5;y++)if(x!=0||z!=0||y==5)level.setBlockAndUpdate(control.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState());
 }
 boolean forestryComplete(){
  boolean done=true;
  for(int i=0;i<2;i++){
   var tree=trees.get(i);var c=citizens.get(i+1);var inv=c.getInventory();var hut=buildings.get(i+1);
   harvested[i]|=!level.getBlockState(tree.above()).is(Blocks.OAK_LOG);
   saplingsPickedUp[i]|=count(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.OAK_SAPLING))<16;
   boolean logs=count(inv,new ItemStack(Items.OAK_LOG))+count(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.OAK_LOG))>=5;
   boolean used=false;for(int slot=0;slot<inv.getSlots();slot++){var s=inv.getStackInSlot(slot);used|=s.is(Items.STONE_AXE)&&s.getDamageValue()>0;}
   done&=harvested[i]&&logs&&used&&level.getBlockState(tree).is(Blocks.OAK_SAPLING);
  }
  for(int y=0;y<5;y++)check(level.getBlockState(new BlockPos(562,-59+y,0)).is(Blocks.OAK_LOG),"Outside-area control tree damaged at height "+y);return done;
 }
 void diagnostic()throws Exception{
  var s=new StringBuilder("tick="+elapsed+" phase="+phase+" target="+target+" orders="+fixture.colony().getWorkManager().getWorkOrders().size()+" active="+fixture.colony().isActive()+" resources="+builder.getNeededResources().size()+"\n");
  for(var c:citizens)s.append(" citizen=").append(c.getId()).append(" pos=").append(c.getEntity().orElseThrow().blockPosition()).append(" status=").append(c.getStatus()==null?"none":c.getStatus().getTranslationKey()).append(" job=").append(c.getJobStatus()).append(" ai=").append(c.getJob()==null?"none":c.getJob().getWorkerAI().getState()).append("\n");
  for(var r:builder.getNeededResources().values())s.append(" need=").append(r.getItemStack()).append(" quantity=").append(r.getAmount()).append(" hut=").append(count(builder.getItemHandlerCap((Direction)null),r.getItemStack())).append(" worker=").append(count(citizens.getFirst().getInventory(),r.getItemStack())).append("\n");
  for(var c:citizens)if(c.getWorkBuilding()!=null)for(var request:((com.minecolonies.core.colony.buildings.AbstractBuilding)c.getWorkBuilding()).getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(" request citizen=").append(c.getId()).append(" ").append(request.getLongDisplayString().getString()).append(" state=").append(request.getState()).append("\n");
  if(phase==2)for(int i=0;i<2;i++){var c=citizens.get(i+1);s.append(" forestry=").append(i).append(" base=").append(level.getBlockState(trees.get(i))).append(" above=").append(level.getBlockState(trees.get(i).above())).append(" logsWorker=").append(count(c.getInventory(),new ItemStack(Items.OAK_LOG))).append(" logsHut=").append(count(buildings.get(i+1).getItemHandlerCap((Direction)null),new ItemStack(Items.OAK_LOG))).append(" saplingsHut=").append(count(buildings.get(i+1).getItemHandlerCap((Direction)null),new ItemStack(Items.OAK_SAPLING))).append(" inventory=");for(int slot=0;slot<c.getInventory().getSlots();slot++){var stack=c.getInventory().getStackInSlot(slot);if(!stack.isEmpty())s.append(stack).append(" damage=").append(stack.getDamageValue()).append(";");}s.append("\n");}
  if(phase==2)for(int i=0;i<2;i++){var job=(com.minecolonies.core.colony.jobs.JobLumberjack)citizens.get(i+1).getJob();var tree=job.getTree();if(tree!=null)s.append(" TREE ").append(i).append(" location=").append(tree.getLocation()).append(" stumps=").append(tree.getStumpLocations()).append(" sapling=").append(tree.getSapling()).append(" soil=").append(level.getBlockState(tree.getLocation().below())).append(" survives=").append(Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level,tree.getLocation())).append("\n");}
  Files.writeString(output.resolve("diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
 }
 void pass(String id,String evidence){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence));writeReport();}
 void writeReport(){try{ProbeReport.write(output,"Operations",rows);}catch(Exception e){throw new RuntimeException(e);}}
 void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
 void fail(Throwable e){rows.add(new ProbeResult("operations_failure",ProbeResult.Outcome.FAIL,e.toString()));writeReport();h.fail(e.toString());}
}
