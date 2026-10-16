// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import com.minecolonies.api.blocks.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationCourierTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=18000)
 public static void courier(GameTestHelper h){var center=h.absolutePos(new BlockPos(5,2,5));var chunk=new net.minecraft.world.level.ChunkPos(center);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.getLevel().setChunkForced(chunk.x+x,chunk.z+z,true);h.runAfterDelay(40,()->{try{run(h,center);}catch(Exception e){throw new RuntimeException(e);}});}
 static IBuilding place(ExplorationFixture f,BlockPos p,AbstractBlockHut<?> block,String path){var l=f.owner().serverLevel();var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,l.registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);SupplyTests.check(block.setup(f.owner(),l,p,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons",path));var b=f.colony().colony().getServerBuildingManager().getBuilding(p);b.setBuildingLevel(1);return b;}
 static void run(GameTestHelper h,BlockPos center)throws Exception{
  var l=h.getLevel();var f=ExplorationFixture.create(l,center);l.setDayTime(6000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
  for(int x=-8;x<=24;x++)for(int z=-8;z<=18;z++){l.setBlockAndUpdate(center.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());for(int y=0;y<3;y++)if(!center.offset(x,y,z).equals(f.hut().getPosition()))l.setBlockAndUpdate(center.offset(x,y,z),Blocks.AIR.defaultBlockState());}
  var warehouse=place(f,center.offset(16,0,4),ModBlocks.blockHutWareHouse,"craftsmanship/storage/warehouse1.blueprint");var courierHut=place(f,center.offset(16,0,-4),ModBlocks.blockHutDeliveryman,"craftsmanship/storage/deliveryman1.blueprint");var courier=f.colony().createCitizen();SupplyTests.check(courierHut.getModule(BuildingModules.COURIER_WORK).assignCitizen(courier));SupplyTests.check(warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS).assignCitizen(courier));courier.setSaturation(20);courier.getEntity().orElseThrow().setPos(center.getX()+16.5,center.getY(),center.getZ());var original=courier.getUUID();var previous=new net.minecraft.world.phys.Vec3[]{courier.getEntity().orElseThrow().position()};var walked=new double[1];
  f.colony().colony().getPackageManager().addImportantColonyPlayer(f.owner());f.colony().colony().getPackageManager().addCloseSubscriber(f.owner());
  var inv=f.hut().getTileEntity().getInventory();inv.setStackInSlot(0,new ItemStack(Items.BREAD,64));inv.setStackInSlot(1,new ItemStack(Items.SHEARS));f.hut().getModule(BuildingModules.MIN_STOCK).addMinimumStock(new ItemStack(Items.BREAD),1);f.hut().getModule(BuildingModules.MIN_STOCK).addMinimumStock(new ItemStack(Items.SHEARS),1);
  var id=f.start(false);var d=GatheringSavedData.get(l);var r=d.find(id).orElseThrow();while(r.remaining()>0){var n=r.tick();d.replace(r,n);r=n;}d.writeCargo(id,GatheringCargo.load(d.cargo(id),l.registryAccess()).earn(r.offer()).save(l.registryAccess()));d.checkpoint(l);
  l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack(),"tick sprint 18000");
  h.succeedWhen(()->{var entity=courier.getEntity().orElseThrow();double step=previous[0].distanceTo(entity.position());h.assertTrue(step<2,"Courier jumped");walked[0]+=step;previous[0]=entity.position();h.assertTrue(ExplorationRestartTests.count(warehouse.getItemHandlerCap(),Items.KELP)==16,"Waiting for native pickup/delivery; state="+((JobDeliveryman)courier.getJob()).getWorkerAI().getState());var rows=new ArrayList<ProbeResult>();SupplyTests.run(rows,"native_exploration_pickup",()->SupplyTests.check(d.find(id).orElseThrow().state()==GatheringRecord.State.COMPLETE&&walked[0]>3&&ExplorationRestartTests.count(inv,Items.KELP)==0));SupplyTests.run(rows,"minimum_stock_preserved",()->SupplyTests.check(ExplorationRestartTests.count(inv,Items.BREAD)==62&&ExplorationRestartTests.count(inv,Items.SHEARS)==1));SupplyTests.run(rows,"native_jobs_unchanged",()->SupplyTests.check(courier.getUUID().equals(original)&&courier.getWorkBuilding()==courierHut&&f.worker().getWorkBuilding()==f.hut()));try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationCourier",rows);RemoteActivityTests.connect(f.owner(),false);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(x->x.outcome()==ProbeResult.Outcome.PASS),rows.toString());});
 }
}
