package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.workorders.WorkOrderPlantationField;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Actual native field work orders; no target geometry or registration injected. */
@PrefixGameTestTemplate(false)
public final class ArcologyCropBuildTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=520000)
 public static void crop_construction(GameTestHelper h){
  for(int x=29;x<=40;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new Run(h).start());
 }
 static final class Run {
  final GameTestHelper h;final ArcologyUpgradeTests.Run support;final List<ProbeResult> rows=new ArrayList<>();
  final String[] names={"cocoa","vine","kelp","seagrass","seapickle","glowberry","weepingvine","twistingvine","crimson","warped"};
  int index,ticks,settle;Blueprint blueprint;BlockPos anchor,origin;WorkOrderPlantationField order;UUID identity;net.minecraft.world.phys.Vec3 previous;
  Run(GameTestHelper h){this.h=h;support=new ArcologyUpgradeTests.Run(h);}
  void start(){try{
   var l=h.getLevel();l.setDayTime(6000);l.setWeatherParameters(1000000,0,false,false);
   l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,l.getServer());
   for(int x=468;x<=635;x++)for(int z=-18;z<=58;z++)for(int y=12;y<16;y++)l.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"FieldBuilderProbe");l.getServer().getProfileCache().add(profile);
   support.fixture=OutpostProbeFixture.create(l,new BlockPos(480,16,0),profile.getId());
   var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,profile);support.owner=new ServerPlayer(l.getServer(),l,profile,ClientInformation.createDefault());support.owner.connection=fake.connection;support.owner.setPos(544,16,16);
   support.fixture.colony().getPackageManager().addImportantColonyPlayer(support.owner);support.fixture.colony().getPackageManager().addCloseSubscriber(support.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)support.fixture.colony(),true,new BlockPos(544,16,16),9,l,false);
   support.builder=(BuildingBuilder)support.place("fundamentals/builder5.blueprint",new BlockPos(480,16,0),true);
   support.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,32),true);
   support.worker=support.fixture.createCitizen();check(support.worker!=null&&support.worker.getEntity().isPresent(),"Builder spawn");
   check(support.builder.getModule(BuildingModules.BUILDER_WORK).assignCitizen(support.worker),"Builder assignment");
   var e=support.worker.getEntity().orElseThrow();e.setPos(482.5,16,-4.5);identity=e.getUUID();previous=e.position();
   ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   int slot=0;for(var item:List.of(Items.DIAMOND_AXE,Items.DIAMOND_PICKAXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_HOE,Items.SHEARS))support.worker.getInventory().setStackInSlot(slot++,new ItemStack(item));
   support.worker.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));next();schedule();
  }catch(Throwable e){fail(e);}}
  void next()throws Exception{
   ticks=0;settle=0;String path="infrastructure/plantationfields/"+names[index]+"1.blueprint";blueprint=com.ldtteam.structurize.storage.StructurePacks.getBlueprintFuture("Arcology Horizons",path,h.getLevel().registryAccess()).get();
   check(blueprint!=null&&"Arcology Horizons".equals(blueprint.getPackName()),"Native pack discovery failed");
   origin=new BlockPos(504+(index%5)*24,16-blueprint.getPrimaryBlockOffset().getY(),-2+(index/5)*24);anchor=origin.offset(blueprint.getPrimaryBlockOffset());
   check(support.fixture.colony().getServerBuildingManager().getBuildingExtensions(f->f.getPosition().equals(anchor)).isEmpty(),"Target already registered");
   order=WorkOrderPlantationField.create(WorkOrderType.BUILD,"Arcology Horizons",path,names[index],anchor,RotationMirror.NONE,0);
   order.setBlueprint(blueprint,h.getLevel());order.setClaimedBy(support.builder.getPosition());support.fixture.colony().getWorkManager().addWorkOrder(order,false);
   check(support.fixture.colony().getWorkManager().getWorkOrders().containsValue(order),"Native field order rejected");
  }
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=support.worker.getEntity().orElseThrow();support.worker.setSaturation(20);
   check(e.isAlive()&&e.getUUID().equals(identity)&&support.worker.getWorkBuilding()==support.builder,"Builder identity/assignment lost");check(previous.distanceTo(e.position())<=12,"Builder recovery teleport");previous=e.position();
   support.supply();support.ticks=ticks;support.index=index;if(ticks%400==0)support.diagnostic();
   check(ticks<50000,"Field construction timeout "+names[index]);
   if(support.fixture.colony().getWorkManager().getWorkOrders().isEmpty()){
    settle+=20;if(settle<240){schedule();return;}
    int checked=0;for(var info:blueprint.getBlockInfoAsList()){
     var expected=blueprint.getBlockState(info.getPos());if(expected.isAir())continue;
     var actual=h.getLevel().getBlockState(origin.offset(info.getPos()));
     check(actual.getBlock()==expected.getBlock(),"Completed field mismatch "+names[index]+" at "+info.getPos()+": "+actual+" expected "+expected);
     if(expected.is(Blocks.WATER))check(h.getLevel().getFluidState(origin.offset(info.getPos())).isSource(),"Built pond water must settle as a source");checked++;
    }
    var fields=support.fixture.colony().getServerBuildingManager().getBuildingExtensions(f->f.getPosition().equals(anchor));
    check(fields.size()==1&&fields.getFirst() instanceof PlantationField,"Built field did not register natively");var field=(PlantationField)fields.getFirst();
    int[] requiredPositions={8,10,6,12,8,4,4,4,9,9};
    check(field.getWorkingPositions().size()==requiredPositions[index]&&field.isValidPlacement(support.fixture.colony()),"Built field lost expected work positions");
    rows.add(new ProbeResult(names[index]+"_construction",ProbeResult.Outcome.PASS,"Native WorkOrderPlantationField completed from bare terrain using the same real builder, recovery disabled; "+checked+" non-air blueprint blocks match after240 settling ticks and exactly one valid native field registered. Support L5 builder, research, tools and requested supplies provided; no target paste or registration call. ticks="+ticks));
    ProbeReport.write(support.output,"ArcologyCropBuild",rows);if(++index==names.length){support.worker.setPaused(true);h.succeed();return;}next();
   }schedule();
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("crop_build_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(support.output,"ArcologyCropBuild",rows);}catch(Exception ignored){}h.fail(e.toString());}
 }
}
