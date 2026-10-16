package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobQuarrier;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native Mine-employed quarriers excavate two external quarry variants; early excavation and full-depth variants. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2000000)
 public static void quarry(GameTestHelper h){for(int x=28;x<=39;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final boolean deep=System.getProperty("colonyhorizons.foundationSuite","").equals("ArcologyQuarryDepth");
  final String reportName=deep?"ArcologyQuarryDepth":"ArcologyQuarry";
  final List<Map<BlockPos,BlockState>> completeShafts=new ArrayList<>();final boolean[] early=new boolean[2];final int[] lastProgressTick=new int[2],lastProgressSignature=new int[2];
  IBuilding warehouse,courierHut;ICitizenData courier;UUID courierId;Vec3 courierPrevious;boolean coalCarried;
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final String[] names={"simplequarry","mediumquarry"};final List<BuildingMiner> mines=new ArrayList<>();final List<IBuilding> quarries=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();
  final List<UUID> ids=new ArrayList<>();final List<Vec3> previous=new ArrayList<>();final List<Map<BlockPos,BlockState>> initial=new ArrayList<>();final List<Map<BlockPos,BlockState>> infrastructure=new ArrayList<>();
  final boolean[] orderSeen=new boolean[2],done=new boolean[2];final double[] minY={16,16};final int[] oreBaseline=new int[2],blockBaseline=new int[2];final List<ProbeResult> rows=new ArrayList<>();OutpostProbeFixture fixture;int ticks;java.util.function.Consumer<Run> afterSetup;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(2000000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=604;x++)for(int z=-16;z<=80;z++)for(int y=-63;y<16;y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"QuarryProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(544,16,20);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(536,16,24),7,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   for(int i=0;i<2;i++){
    int width=i==0?15:25,depth=i==0?32:64;var anchor=new BlockPos(i==0?512:576,16,40);var mine=(BuildingMiner)setup.place("fundamentals/miner5.blueprint",new BlockPos(anchor.getX(),16,0),true);mines.add(mine);
    String surface="fundamentals/"+names[i]+"1.blueprint",shaft="infrastructure/mineshafts/"+names[i]+"shaft1.blueprint";var bp=setup.blueprint(surface);var origin=anchor.subtract(bp.getPrimaryBlockOffset());
    var quarry=setup.place(surface,anchor,true);quarries.add(quarry);check(quarry.getBuildingLevel()==1&&quarry.getMaxBuildingLevel()==1&&quarry.getBuildingType().getRegistryName().getPath().equals(names[i]),"Native quarry identity "+names[i]);
    // Prebuilt fixture emulates substitution preservation: restore the disclosed dry terrain,
    // never place shaft cells or remove excavation material on the worker's behalf.
    for(var info:bp.getBlockInfoAsList())if(BuiltInRegistries.BLOCK.getKey(bp.getBlockState(info.getPos()).getBlock()).toString().equals("structurize:blocksubstitution")){var pos=origin.offset(info.getPos());level.setBlock(pos,(pos.getY()<16?Blocks.STONE:Blocks.AIR).defaultBlockState(),18);}
    check(quarry.getTileEntity().getPositionedTags().get(BlockPos.ZERO).equals(List.of("shaft="+shaft)),"Sole origin shaft path tag "+names[i]);
    var shaftBp=setup.blueprint(shaft);check(shaftBp.getSizeY()==depth&&anchor.getY()-depth>level.getMinBuildHeight(),"Shaft depth must fit above world minimum");
    Map<BlockPos,BlockState> sampled=new HashMap<>();initial.add(sampled);for(int y=14;y<=15;y++)for(int x=3;x<=width-4;x++)for(int z=5;z<=width-4;z++){
     var pos=new BlockPos(origin.getX()+x,y,origin.getZ()+z);if(x==3&&z==5)continue;
     check(level.getBlockState(pos).is(Blocks.STONE),"Fixture shaft excavation cell was pre-cleared or obstructed: "+pos+" "+level.getBlockState(pos));
     if(x>=4&&x<=7&&z>=6&&z<=9)level.setBlock(pos,Blocks.COAL_ORE.defaultBlockState(),18);sampled.put(pos,level.getBlockState(pos));
    }
    Map<BlockPos,BlockState> changed=new HashMap<>();infrastructure.add(changed);var shaftOrigin=anchor.below(2).subtract(shaftBp.getPrimaryBlockOffset());
    var expectedShaft=new HashMap<BlockPos,BlockState>();completeShafts.add(expectedShaft);
    for(var info:shaftBp.getBlockInfoAsList()){var state=shaftBp.getBlockState(info.getPos());if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("structurize:blocksubstitution"))expectedShaft.put(shaftOrigin.offset(info.getPos()),state);}
    if(deep)for(var access:List.of(Blocks.LADDER,Blocks.WALL_TORCH)){
     var cell=expectedShaft.entrySet().stream().filter(entry->entry.getValue().is(access)&&entry.getKey().getY()<13).max(java.util.Comparator.comparingInt(entry->entry.getKey().getY())).orElseThrow().getKey();
     check(level.getBlockState(cell).is(Blocks.STONE),"Access ore must replace untouched test terrain");level.setBlock(cell,Blocks.COAL_ORE.defaultBlockState(),18);
    }
    for(var info:shaftBp.getBlockInfoAsList()){var pos=shaftOrigin.offset(info.getPos());var state=shaftBp.getBlockState(info.getPos());if(pos.getY()>=13&&pos.getY()<=15&&(state.is(Blocks.COBBLESTONE)||state.is(Blocks.LADDER))&&!level.getBlockState(pos).equals(state))changed.put(pos,state);}
    var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Quarrier spawn");workers.add(c);var e=c.getEntity().orElseThrow();e.setPos(anchor.getX()+1.5,16,-4.5);ids.add(e.getUUID());previous.add(e.position());c.setSaturation(20);
    check(mine.getModule(BuildingModules.QUARRIER_WORK).assignCitizen(c)&&c.getJob() instanceof JobQuarrier,"Mine-employed native JobQuarrier");check(quarry.getFirstModuleOccurance(QuarryModule.class).assignCitizen(c)&&((JobQuarrier)c.getJob()).findQuarry()==quarry&&c.getWorkBuilding()==mine,"External native quarry assignment");
    int slot=0;for(var item:List.of(Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL))c.getInventory().setStackInSlot(slot++,new ItemStack(item));c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
    check(outputCount(i)==0&&stat(i)==0,"Coal output must not be supplied");oreBaseline[i]=colonyStat("ores_mined");blockBaseline[i]=colonyStat("blocks_mined");
    var module=quarry.getFirstModuleOccurance(QuarryModule.class);Files.writeString(output.resolve("quarry-diagnostics.txt"),names[i]+" corners="+quarry.getCorners()+" additionalCorners="+module.getAdditionalCorners()+" containsHut="+quarry.isInBuilding(anchor)+" containsShaftTop="+quarry.isInBuilding(anchor.offset(0,-1,6))+" bottomY="+(anchor.getY()-depth)+" minBuild="+level.getMinBuildHeight()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    pass(names[i]+"_assignment","Native level1 quarry and separate prebuilt level5 Mine; real Mine-employed JobQuarrier is assigned to the external QuarryModule. Sole origin shaft tag resolves "+shaft+". Disclosed dry stone/coal terrain and tools supplied; shaft bottom remains above world minimum. Native isInBuilding hut result="+quarry.isInBuilding(anchor)+"; footprint extension semantics are measured in diagnostics, not certified by this assignment check.");
   }
   if(deep){
    warehouse=setup.place("craftsmanship/storage/warehouse5.blueprint",new BlockPos(480,16,64),true);
    courierHut=setup.place("craftsmanship/storage/deliveryman5.blueprint",new BlockPos(544,16,0),true);
    courier=fixture.createCitizen();check(courier!=null&&courier.getEntity().isPresent(),"Quarry courier spawn");
    check(courierHut.getModule(BuildingModules.COURIER_WORK).assignCitizen(courier)&&warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS).assignCitizen(courier),"Native quarry warehouse courier assignment");
    var e=courier.getEntity().orElseThrow();e.setPos(546.5,16,-4.5);courierId=e.getUUID();courierPrevious=e.position();courier.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   }
   if(afterSetup!=null)afterSetup.accept(this);else schedule();
  }catch(Throwable e){fail(e);}}
  int colonyStat(String key){return fixture.colony().getStatisticsManager().getStatTotal(key);}
  int stat(int i){return mines.get(i).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("item_obtained;"+Items.COAL.getDescriptionId());}
  int outputCount(int i){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(Items.COAL))+ArcologyOperationsFixture.count(quarries.get(i).getItemHandlerCap((Direction)null),new ItemStack(Items.COAL));}
  int wear(int i){int damage=0;for(int s=0;s<workers.get(i).getInventory().getSlots();s++){var stack=workers.get(i).getInventory().getStackInSlot(s);if(stack.is(Items.DIAMOND_PICKAXE))damage+=stack.getDamageValue();}return damage;}
  BlockPos workingFrom(int i)throws Exception{var member=com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure.class.getDeclaredField("workFrom");member.setAccessible(true);return (BlockPos)member.get(workers.get(i).getJob().getWorkerAI());}
  void supply(int i){var mine=mines.get(i);for(var resource:new ArrayList<>(mine.getNeededResources().values())){var stack=resource.getItemStack();check(!stack.is(Items.COAL)&&!stack.is(Items.COAL_ORE),"Output cannot be supplied as material");int have=ArcologyOperationsFixture.count(mine.getItemHandlerCap((Direction)null),stack)+ArcologyOperationsFixture.count(workers.get(i).getInventory(),stack);int target=Math.min(64,resource.getAmount());if(have<target)ItemHandlerHelper.insertItemStacked(mine.getItemHandlerCap((Direction)null),stack.copyWithCount(target-have),false);}}
  void pass(String id,String detail)throws Exception{rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,detail));ProbeReport.write(output,reportName,rows);}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<2;i++){
    var c=workers.get(i);var e=c.getEntity().orElseThrow();var mine=mines.get(i);var quarry=quarries.get(i);
    check(e.isAlive()&&e.getUUID().equals(ids.get(i))&&c.getWorkBuilding()==mine&&c.getJob() instanceof JobQuarrier&&((JobQuarrier)c.getJob()).findQuarry()==quarry&&mine.getModule(BuildingModules.QUARRIER_WORK).getAssignedCitizen().contains(c)&&quarry.getFirstModuleOccurance(QuarryModule.class).hasAssignedCitizen(c),"Quarrier identity/dual assignment lost "+names[i]);
    check(previous.get(i).distanceTo(e.position())<=12,"Quarrier recovery teleport");previous.set(i,e.position());minY[i]=Math.min(minY[i],e.getY());c.setSaturation(20);if(done[i])continue;
    var order=mine.getWorkOrder();if(order!=null){check(order instanceof WorkOrderMiner,"Wrong quarry order type");var nativeOrder=(WorkOrderMiner)order;check(nativeOrder.getStructurePack().equals("Arcology Horizons")&&nativeOrder.getStructurePath().equals("infrastructure/mineshafts/"+names[i]+"shaft1.blueprint")&&nativeOrder.getLocation().equals(quarry.getPosition().below(2))&&nativeOrder.getClaimedBy().equals(mine.getPosition())&&nativeOrder.getMinerBuilding().equals(mine.getPosition()),"Native shaft workorder path/anchor/owner mismatch");orderSeen[i]=true;}
    supply(i);int removed=0,oreRemoved=0,lowerRemoved=0,built=0;for(var cell:initial.get(i).entrySet())if(level.getBlockState(cell.getKey()).isAir()){removed++;if(cell.getValue().is(Blocks.COAL_ORE))oreRemoved++;if(cell.getKey().getY()==14)lowerRemoved++;}for(var cell:infrastructure.get(i).entrySet())if(level.getBlockState(cell.getKey()).equals(cell.getValue()))built++;
    if(!early[i]&&orderSeen[i]&&removed>=12&&oreRemoved>0&&wear(i)>0&&stat(i)>0&&outputCount(i)>0&&colonyStat("ores_mined")>oreBaseline[i]&&colonyStat("blocks_mined")>blockBaseline[i]){
     early[i]=true;if(!deep){done[i]=true;c.setPaused(true);}pass(names[i]+"_excavation","Native Mine-owned shaft WorkOrderMiner verified; observed "+removed+" originally solid cells become air (ore="+oreRemoved+", second sampled layer="+lowerRemoved+"). Pick damage="+wear(i)+", Mine coal-obtained statistic="+stat(i)+", physically collected nonsupplied coal="+outputCount(i)+"; colony ore/block counters increased. Newly matched shaft liner/ladder cells="+built+", minimum workerY="+minY[i]+". Same worker/dual assignment, recovery disabled. Early excavation only: no full32/64-depth, completed layer, lower-half traversal, final floor, output deposit, bounds protection, restart or surface construction claim.");
    }
    if(deep){
     int signature=java.util.Objects.hash(wear(i),stat(i),minY[i],removed,built);if(signature!=lastProgressSignature[i]){lastProgressSignature[i]=signature;lastProgressTick[i]=ticks;}
     check(ticks-lastProgressTick[i]<40000,"Quarry stopped making progress "+names[i]+"; see diagnostics");
     for(var tool:List.of(Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL)){
      var probe=new ItemStack(tool);int available=ArcologyOperationsFixture.count(c.getInventory(),probe)+ArcologyOperationsFixture.count(mine.getItemHandlerCap((Direction)null),probe);
      if(available<2)ItemHandlerHelper.insertItemStacked(mine.getItemHandlerCap((Direction)null),probe,false);
     }
     if(ticks%400==0)quarry.createPickupRequest(64,true);
     if(quarry.getFirstModuleOccurance(QuarryModule.class).isFinished()){
      check(early[i],"Completed without observed excavation/output");check(stat(i)>=34,"Access-cell coal lost before ladder/torch placement: "+stat(i));int air=0,solid=0,ladder=0;int bottom=16-(i==0?32:64);
      for(var cell:completeShafts.get(i).entrySet()){
       var wanted=cell.getValue();var actual=level.getBlockState(cell.getKey());
       check(actual.getBlock()==wanted.getBlock(),"Finished shaft mismatch "+names[i]+" at "+cell.getKey()+": "+actual+" expected "+wanted);
       if(wanted.isAir())air++;else if(wanted.is(Blocks.LADDER)){check(actual.equals(wanted),"Ladder facing/water mismatch");ladder++;}else solid++;
      }
      check(ladder==(i==0?31:63)&&minY[i]<=bottom+10,"Full-depth ladder/traversal not observed");
      done[i]=true;c.setPaused(true);pass(names[i]+"_full_depth","Native finished flag with complete32/64-block-depth blueprint matched: air="+air+", solid="+solid+", ladders="+ladder+", minimum workerY="+minY[i]+" bottomY="+bottom+". Includes native mining of coal deliberately placed under a ladder and wall torch; at least34 coal obtained per shaft. Same native quarrier, no target clearing or shaft paste; requested materials, replacement tools and food supplied. Dry uniform test terrain; not fluids, hazards or restart. ticks="+ticks);
     }
    }
    if(ticks%400==0){var text=new StringBuilder("tick="+ticks+" variant="+names[i]+" pos="+e.position()+" minY="+minY[i]+" AI="+(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState())+" order="+(order==null?"none":order.getStructurePath())+" progress="+(mine.getProgress()==null?"none":mine.getProgress().getA()+"/"+mine.getProgress().getB())+" removed="+removed+" oreRemoved="+oreRemoved+" built="+built+" wear="+wear(i)+" coal="+outputCount(i)+" stat="+stat(i)+"\n");text.append("target=").append(((com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier)c.getJob().getWorkerAI()).getCurrentBuildingPosition()).append(" ");text.append("blueprintPack=").append(order==null||order.getBlueprint()==null?"none":order.getBlueprint().getPackName()).append(" blueprintName=").append(order==null||order.getBlueprint()==null?"none":order.getBlueprint().getName()).append(" ");text.append("workFrom=").append(workingFrom(i)).append(" navDone=").append(e.getNavigation().isDone()).append(" ");text.append("mineCobble=").append(ArcologyOperationsFixture.count(mine.getItemHandlerCap((Direction)null),new ItemStack(Items.COBBLESTONE))).append(" workerCobble=").append(ArcologyOperationsFixture.count(c.getInventory(),new ItemStack(Items.COBBLESTONE))).append("\n");for(var request:mine.getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))text.append(request.getLongDisplayString().getString()).append(' ').append(request.getState()).append('\n');Files.writeString(output.resolve("quarry-diagnostics.txt"),text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   }
   if(deep){
    var e=courier.getEntity().orElseThrow();courier.setSaturation(20);check(e.isAlive()&&e.getUUID().equals(courierId)&&courier.getWorkBuilding()==courierHut,"Quarry courier identity/assignment lost");check(courierPrevious.distanceTo(e.position())<=12,"Courier recovery teleport");courierPrevious=e.position();
    coalCarried|=ArcologyOperationsFixture.count(courier.getInventory(),new ItemStack(Items.COAL))>0;
   }
   if(done[0]&&done[1]){
    if(deep){int delivered=ArcologyOperationsFixture.count(warehouse.getItemHandlerCap((Direction)null),new ItemStack(Items.COAL));
     if(!coalCarried||delivered<68){check(ticks<1900000,"Quarry hauling timeout");schedule();return;}
     pass("quarry_courier_hauling","Native linked courier physically carried nonsupplied mined coal and deposited "+delivered+" into warehouse while quarries completed; native pickup requests, same courier, recovery disabled. Not exact throughput or separate source attribution.");
    }h.succeed();return;
   }check(ticks<(deep?1900000:120000),"Quarry timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("quarry_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,reportName,rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
