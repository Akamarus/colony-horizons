package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.*;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.*;
import java.nio.file.*;
import java.util.*;

/** Real builder orders. Only the support builder starts prebuilt; target levels are never assigned by QA. */
@PrefixGameTestTemplate(false)
public final class ArcologyUpgradeTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=350000)
 public static void priority_huts_native_upgrades(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new Run(h).start());
 }
 static final class Run {
  java.util.function.Consumer<Run> afterSetup;
  final boolean behaviorOnly;final GameTestHelper h;final ServerLevel level;final Path root=Path.of(System.getProperty("colonyhorizons.arcologyPath"));
  final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ProbeResult> rows=new ArrayList<>();
  final List<String> paths;final String reportName;int expectedCases;
  final List<BlockPos> anchors;
  final List<IBuilding> targets=new ArrayList<>();
  OutpostProbeFixture fixture;ServerPlayer owner;BuildingBuilder builder;ICitizenData worker;
  ICitizenData stableWorker;UUID stableWorkerId;BuildingStable retainedStable;ItemStack stableSentinel;boolean stableDepositObserved;int lastStableWorkerCount=-1,lastStableHutCount=-1;
  int index,wanted=1,ticks,orderTicks;boolean requested;UUID workerId;net.minecraft.world.phys.Vec3 previous;
  Run(GameTestHelper h){this(h,false);}
  Run(GameTestHelper h,boolean behaviorOnly){this(h,behaviorOnly,List.of("fundamentals/miner1.blueprint","mystic/netherworker1.blueprint","agriculture/horticulture/plantation1.blueprint"),List.of(new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(512,16,32)),"ArcologyUpgrades",2);}
  Run(GameTestHelper h,List<String> paths,List<BlockPos> anchors,String reportName){this(h,false,paths,anchors,reportName,0);}
  private Run(GameTestHelper h,boolean behaviorOnly,List<String> paths,List<BlockPos> anchors,String reportName,int startIndex){
   this.h=h;level=h.getLevel();this.behaviorOnly=behaviorOnly;this.paths=List.copyOf(paths);this.anchors=List.copyOf(anchors);this.reportName=reportName;expectedCases=0;index=startIndex;
   check(!paths.isEmpty()&&paths.size()==anchors.size()&&startIndex>=0&&startIndex<paths.size(),"Invalid upgrade fixture configuration");
  }
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
   level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
   // Solid test terrain, including meaningful depth for later miner behavior checks.
   for(int x=468;x<=564;x++)for(int z=-16;z<=55;z++)for(int y=-63;y<16;y++)
    level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:y>12?Blocks.DIRT:Blocks.STONE).defaultBlockState(),18);
   var id=UUID.randomUUID();var profile=new GameProfile(id,"UpgradeProbe");level.getServer().getProfileCache().add(profile);
   fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),id);
   var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());owner.connection=fake.connection;
   owner.setPos(512,16,0);fixture.colony().getPackageManager().addImportantColonyPlayer(owner);fixture.colony().getPackageManager().addCloseSubscriber(owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(512,16,16),4,level,false);
   builder=(BuildingBuilder)place("fundamentals/builder5.blueprint",new BlockPos(480,16,0),true);
   for(int i=0;i<paths.size();i++)targets.add(place(behaviorOnly?paths.get(i).replace("1.blueprint","5.blueprint"):paths.get(i),anchors.get(i),behaviorOnly));
   expectedCases=targets.stream().mapToInt(IBuilding::getMaxBuildingLevel).sum();
   place("fundamentals/townhall5.blueprint",new BlockPos(480,16,32),true); // Native expedition respawn scheduler requires a Town Hall.
   worker=fixture.createCitizen();check(worker!=null && worker.getEntity().isPresent(),"Builder spawn");
   worker.setSaturation(20);worker.getEntity().orElseThrow().setPos(482.5,16,-4.5);
   workerId=worker.getEntity().orElseThrow().getUUID();previous=worker.getEntity().orElseThrow().position();
   check(builder.getModule(BuildingModules.BUILDER_WORK).assignCitizen(worker),"Builder assignment");
   int slot=0;for(var item:List.of(Items.DIAMOND_AXE,Items.DIAMOND_PICKAXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_HOE,Items.SHEARS))worker.getInventory().setStackInSlot(slot++,new ItemStack(item));
   worker.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
   for(var item:List.of(Items.DIAMOND_AXE,Items.DIAMOND_PICKAXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_HOE,Items.SHEARS))ItemHandlerHelper.insertItemStacked(builder.getItemHandlerCap((Direction)null),new ItemStack(item),false);
   ((com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)worker.getEntity().orElseThrow().getNavigation()).setStuckHandler(com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler.createStuckHandler());
   if(behaviorOnly){worker.setPaused(true);new ArcologyBehaviorTests.Run(h,fixture,targets).start();}
   else if(afterSetup!=null)afterSetup.accept(this);else schedule();
  }catch(Throwable e){fail(e);}}
  Blueprint blueprint(String path)throws Exception{return BlueprintUtil.readBlueprintFromNBT(NbtIo.readCompressed(root.resolve(path),NbtAccounter.create(64000000)),level.registryAccess());}
  IBuilding place(String path,BlockPos anchor,boolean support)throws Exception{
   var bp=blueprint(path);var origin=anchor.subtract(bp.getPrimaryBlockOffset());
   if(support)for(var info:bp.getBlockInfoAsList()){
    var state=bp.getBlockState(info.getPos());var pos=origin.offset(info.getPos());level.setBlock(pos,state,18);
    var tile=level.getBlockEntity(pos);if(tile!=null && info.getTileEntityData()!=null)tile.loadWithComponents(info.getTileEntityData().copy(),level.registryAccess());
   }
   var state=bp.getBlockState(bp.getPrimaryBlockOffset());var hut=(AbstractBlockHut<?>)state.getBlock();level.setBlockAndUpdate(anchor,state);
   hut.onBlockPlacedByBuildTool(level,anchor,state,owner,new ItemStack(hut),RotationMirror.NONE,"Arcology Horizons",path);
   var b=fixture.colony().getServerBuildingManager().getBuilding(anchor);check(b!=null,"Hut registration "+path);
   for(var module:b.getModulesByType(com.minecolonies.api.colony.buildings.modules.IAssignsCitizen.class))module.setHiringMode(com.minecolonies.api.colony.buildings.HiringMode.MANUAL);
   var research=fixture.colony().getResearchManager().getResearchEffectIdFrom(b.getBuildingType().getBuildingBlock());
   fixture.colony().getResearchManager().getResearchEffects().applyEffect(new com.minecolonies.core.research.GlobalResearchEffect(research,"QA prerequisite","",5,5));
   if(support){
    for(var info:bp.getBlockInfoAsList())b.registerBlockPosition(bp.getBlockState(info.getPos()),origin.offset(info.getPos()),level);
    int placedLevel=Character.digit(path.charAt(path.length()-".blueprint".length()-1),10);
    check(placedLevel>=1&&placedLevel<=b.getMaxBuildingLevel(),"Invalid support blueprint level: "+path);
    b.onUpgradeComplete(bp,placedLevel);check(b.getBuildingLevel()==placedLevel,"Support fixture level: "+path);
   }else check(b.getBuildingLevel()==0,"Target must start unbuilt");
   return b;
  }
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=worker.getEntity().orElseThrow();
   if(stableWorker!=null)checkStableRetention();
   check(e.isAlive() && e.getUUID().equals(workerId) && worker.getWorkBuilding()==builder,"Builder identity/assignment lost");
   check(previous.distanceTo(e.position())<=12,"Builder recovery teleport");previous=e.position();
   if(rows.size()==expectedCases){
    check(rows.size()==expectedCases,"Missing upgrade cases");check(fixture.colony().getWorkManager().getWorkOrders().isEmpty(),"Orders remain");
    ProbeReport.write(output,reportName,rows);
    if(System.getProperty("colonyhorizons.foundationSuite", "").equals("ArcologyLifecycle")){worker.setPaused(true);ArcologyLifecycleTests.afterUpgrades(h,fixture,targets,rows);}
    else h.succeed();return;
   }
   var b=targets.get(index);
   if(!requested){
    // Native level promotion can precede work-order cleanup. Wait for the old
    // order to leave before requesting the next one; duplicate requests are ignored.
    if(!fixture.colony().getWorkManager().getWorkOrders().isEmpty()){schedule();return;}
    orderTicks=ticks;b.requestUpgrade(owner,builder.getPosition());requested=true;
    check(fixture.colony().getWorkManager().getWorkOrders().values().stream().anyMatch(o->o.getTargetLevel()==wanted),"Native upgrade rejected "+paths.get(index)+" level "+wanted);
   }
   supply();
   if(ticks%200==0)diagnostic();
   check(ticks-orderTicks<50000,"Native upgrade timeout; see upgrade-diagnostics.txt");
   if(b.getBuildingLevel()==wanted && fixture.colony().getWorkManager().getWorkOrders().isEmpty()){
    String path=paths.get(index).replace("1.blueprint",wanted+".blueprint");var bp=blueprint(path);var origin=b.getPosition().subtract(bp.getPrimaryBlockOffset());
    int structural=0,matching=0;
    for(var info:bp.getBlockInfoAsList()){
     var state=bp.getBlockState(info.getPos());
     if(state.is(Blocks.OAK_PLANKS)||state.is(Blocks.SPRUCE_PLANKS)||state.is(Blocks.OBSIDIAN)||state.is(Blocks.COBBLESTONE)||state.is(Blocks.STONE)||state.is(Blocks.STONE_BRICKS)||state.is(Blocks.MOSSY_STONE_BRICKS)||state.is(Blocks.CRACKED_STONE_BRICKS)){
      structural++;if(level.getBlockState(origin.offset(info.getPos())).getBlock()==state.getBlock())matching++;
     }
    }
    check(structural>0 && structural==matching,"Native completion with missing structure "+matching+"/"+structural);
    check(b.getStructurePack().equals("Arcology Horizons"),"Upgrade changed style");
    if(b instanceof BuildingStable || b instanceof BuildingGateHouse)for(int lampX:new int[]{2,12}){
     var lamp=origin.offset(lampX,4,b instanceof BuildingStable?2:7);var state=level.getBlockState(lamp);
     check(state.is(Blocks.LANTERN)&&state.canSurvive(level,lamp),"Completed building has missing/unsupported hanging light: "+lamp);
    }
    if(b instanceof BuildingMiner m)check(m.getLadderLocation()!=null && m.getCobbleLocation()!=null,"Upgrade lost shaft markers");
    if(b instanceof BuildingNetherWorker n)check(n.getPortalLocation()!=null,"Upgrade lost portal marker");
    if(b instanceof BuildingPlantation p)check(!fixture.colony().getServerBuildingManager().getBuildingExtensions(f->f.getPosition().equals(p.getPosition())).isEmpty(),"Upgrade lost fields");
    if(b.hasModule(com.minecolonies.core.colony.buildings.modules.QuarryModule.class)){
     String shaft=paths.get(index).substring(paths.get(index).lastIndexOf('/')+1).replace("1.blueprint","shaft1.blueprint");
     check(b.getLocationsFromTag("shaft=infrastructure/mineshafts/"+shaft).equals(List.of(b.getPosition())),"Builder lost native quarry shaft selector");
     check(b.isInBuilding(b.getPosition()),"Completed quarry excludes its own hut");
    }
    if(b instanceof BuildingGateHouse gate){
     check(gate.getMaxBuildingLevel()==3,"Gatehouse must stop at native L3");
     for(var role:List.of("knight","archer")){
      Set<BlockPos> expected=role.equals("knight")?Set.of(origin.offset(6,1,1),origin.offset(8,1,1)):Set.of(origin.offset(2,1,2),origin.offset(12,1,2));
      check(gate.getLocationsFromTag(role).size()==2&&new HashSet<>(gate.getLocationsFromTag(role)).equals(expected),"Gatehouse upgrade lost exact "+role+" markers");
     }
     check(new HashSet<>(gate.getModule(BuildingModules.BED).getRegisteredBlocks()).equals(Set.of(origin.offset(1,1,8),origin.offset(12,1,8))),"Gatehouse upgrade lost beds");
     int terminals=0;for(var tag:fixture.colony().getConnectionManager().serializeNBT(level.registryAccess()).getList("gatehouses",Tag.TAG_COMPOUND))
      if(com.minecolonies.api.util.BlockPosUtil.read((CompoundTag)tag,"pos").equals(gate.getPosition()))terminals++;
     check(terminals==1,"Gatehouse upgrade duplicated/lost connection terminal");
    }
    if(b instanceof BuildingStable stable){
     Set<BlockPos> stalls=new HashSet<>();for(int pair=0;pair<wanted;pair++){stalls.add(origin.offset(2,1,5+3*pair));stalls.add(origin.offset(11,1,5+3*pair));}
     check(stable.stallPositions().size()==2*wanted&&new HashSet<>(stable.stallPositions()).equals(stalls),"Native stable upgrade lost exact stall tags L"+wanted+": "+stable.stallPositions());
     int[] bedX={1,2,3,4,10,11};Set<BlockPos> beds=new HashSet<>();
     for(int j=0;j<=wanted;j++){
      var head=origin.offset(bedX[j],1,1);beds.add(head);var hs=level.getBlockState(head);var fs=level.getBlockState(head.south());
      check(hs.getBlock() instanceof BedBlock&&hs.getValue(BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.HEAD&&hs.getValue(BedBlock.FACING)==Direction.NORTH&&fs.getBlock()==hs.getBlock()&&fs.getValue(BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.FOOT&&fs.getValue(BedBlock.FACING)==Direction.NORTH,"Native stable upgrade incomplete bed L"+wanted+" at "+head);
     }
     check(new HashSet<>(stable.getModule(BuildingModules.BED).getRegisteredBlocks()).equals(beds),"Native stable upgrade lost exact bed registrations L"+wanted);
     if(wanted==1){
      check(stableWorker==null,"Stable retention fixture already assigned");retainedStable=stable;stableWorker=fixture.createCitizen();check(stableWorker!=null&&stableWorker.getEntity().isPresent(),"Retention stablemaster spawn");
      stableWorkerId=stableWorker.getEntity().orElseThrow().getUUID();stableWorker.getEntity().orElseThrow().setPos(stable.getPosition().getX()-4.5,16,stable.getPosition().getZ()-4.5);stableWorker.setSaturation(20);
      check(stable.getModule(BuildingModules.STABLEMASTER_WORK).assignCitizen(stableWorker),"Retention stablemaster assignment");stableWorker.setPaused(true);stableSentinel=new ItemStack(Items.AMETHYST_SHARD,17);stableWorker.getInventory().setStackInSlot(7,stableSentinel.copy());
      ((com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)stableWorker.getEntity().orElseThrow().getNavigation()).setStuckHandler(com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler.createStuckHandler());
     }
     checkStableRetention();if(wanted>1)check(stableDepositObserved,"Native paused-worker deposit not observed before retained upgrade completion");
    }
    rows.add(new ProbeResult("upgrade:"+path,ProbeResult.Outcome.PASS,"Native builder completed "+(wanted==1?"construction":"upgrade from "+(wanted-1))+"; matching structural blocks="+matching+"; same hut/colony/worker; recovery disabled; ticks="+(ticks-orderTicks)+". Support builder level5 and required research/materials provided as fixtures."+(b instanceof BuildingStable?" Stablemaster assigned after L1 and paused through upgrades; same UUID and hut/module membership retained. Exactly 17 matching sentinel shards conserved across worker and own stable/racks; native pause may deposit them into storage. Native deposit observed="+stableDepositObserved+". This does not certify worker-carried inventory or stablemaster operation during construction.":"")));
    ProbeReport.write(output,reportName,rows);
    if(++wanted>b.getMaxBuildingLevel()){index=(index+1)%targets.size();wanted=1;}requested=false;
   }
   schedule();
  }
  void checkStableRetention()throws Exception{
   var e=stableWorker.getEntity().orElseThrow();
   check(e.isAlive()&&e.getUUID().equals(stableWorkerId)&&stableWorker.isPaused()&&stableWorker.getWorkBuilding()==retainedStable&&retainedStable.getModule(BuildingModules.STABLEMASTER_WORK).getAssignedCitizen().contains(stableWorker),"Paused stablemaster identity/assignment lost during upgrade");
   // Native building capability already aggregates its hut and racks: enumerate it once.
   IItemHandler[] inventories={stableWorker.getInventory(),retainedStable.getItemHandlerCap((Direction)null)};int[] counts=new int[2];boolean componentsMatch=true;
   var contents=new StringBuilder("tick="+ticks+" stablemaster="+stableWorkerId+" expected="+stableSentinel+" components="+stableSentinel.getComponents()+"\n");
   for(int owner=0;owner<inventories.length;owner++)for(int slot=0;slot<inventories[owner].getSlots();slot++){
    var stack=inventories[owner].getStackInSlot(slot);contents.append(owner==0?"worker":"own-stable-and-racks").append(" slot=").append(slot).append(" stack=").append(stack).append(" components=").append(stack.getComponents()).append('\n');
    if(stack.is(Items.AMETHYST_SHARD)){counts[owner]+=stack.getCount();componentsMatch&=ItemStack.isSameItemSameComponents(stack,stableSentinel);}
   }
   int ground=0;for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(468,0,-16,565,80,56),item->item.getItem().is(Items.AMETHYST_SHARD))){ground+=item.getItem().getCount();contents.append("ground=").append(item.getUUID()).append(" pos=").append(item.position()).append(" stack=").append(item.getItem()).append(" components=").append(item.getItem().getComponents()).append('\n');}
   if(counts[0]+counts[1]!=17||!componentsMatch||ground!=0){Files.writeString(output.resolve("stable-retention-inventory.txt"),contents,StandardOpenOption.CREATE,StandardOpenOption.APPEND);throw new IllegalStateException("Paused stablemaster sentinel conservation failed: worker="+counts[0]+" ownStable="+counts[1]+" ground="+ground+" expected=17 componentsMatch="+componentsMatch+"; see stable-retention-inventory.txt");}
   stableDepositObserved|=counts[1]==17;
   if(counts[0]!=lastStableWorkerCount||counts[1]!=lastStableHutCount){Files.writeString(output.resolve("stable-retention-inventory.txt"),contents.append("CONSERVATION PASS worker=").append(counts[0]).append(" ownStable=").append(counts[1]).append(" ground=").append(ground).append(" nativePauseDepositObserved=").append(stableDepositObserved).append('\n'),StandardOpenOption.CREATE,StandardOpenOption.APPEND);lastStableWorkerCount=counts[0];lastStableHutCount=counts[1];}
  }
  void supply(){
   for(var r:new ArrayList<>(builder.getNeededResources().values())){
    var stack=r.getItemStack();int have=ArcologyOperationsFixture.count(builder.getItemHandlerCap((Direction)null),stack)+ArcologyOperationsFixture.count(worker.getInventory(),stack);
    int amount=Math.min(64,r.getAmount()-have);if(amount>0)ItemHandlerHelper.insertItemStacked(builder.getItemHandlerCap((Direction)null),stack.copyWithCount(amount),false);
   }
  }
  void diagnostic()throws Exception{
   var s=new StringBuilder("tick="+ticks+" target="+index+" level="+wanted+" active="+fixture.colony().isActive()+" orders="+fixture.colony().getWorkManager().getWorkOrders().size()+" citizenAI="+((com.minecolonies.core.entity.citizen.EntityCitizen)worker.getEntity().orElseThrow()).getCitizenAI().getState()+" saturation="+worker.getSaturation()+" health="+worker.getEntity().orElseThrow().getHealth()+" position="+worker.getEntity().orElseThrow().position()+" AI="+worker.getJob().getWorkerAI().getState()+" resources="+builder.getNeededResources().size()+"\n");
   for(var r:builder.getNeededResources().values())s.append(r.getItemStack()).append(" amount=").append(r.getAmount()).append(" worker=").append(ArcologyOperationsFixture.count(worker.getInventory(),r.getItemStack())).append(" hut=").append(ArcologyOperationsFixture.count(builder.getItemHandlerCap((Direction)null),r.getItemStack())).append('\n');
   var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)worker.getEntity().orElseThrow().getNavigation();
   s.append("workOrder=").append(builder.getWorkOrder()).append(" paused=").append(worker.isPaused()).append(" jobStatus=").append(worker.getJobStatus()).append(" pathResult=").append(nav.getPathResult()==null?"none":nav.getPathResult().getStatus()).append("\n");
   s.append("destination=").append(nav.getSafeDestination()).append(" path=").append(nav.getPath()==null?"none":nav.getPath().getTarget()).append("\n");
   if(ticks-orderTicks>15000 && index==2 && ticks%2000==0){
    var p=worker.getEntity().orElseThrow().blockPosition();
    for(var q:BlockPos.betweenClosed(p.offset(-2,-1,-2),p.offset(2,2,2)))s.append(q.toShortString()).append(" ").append(level.getBlockState(q)).append("\n");
   }
   Files.writeString(output.resolve("upgrade-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("upgrade_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,behaviorOnly?"ArcologyBehavior":reportName,rows);}catch(Exception io){e.addSuppressed(io);}h.fail(e.toString());}
 }
}

