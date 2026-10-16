package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.mayor.MayorService;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
final class RemoteConstructionAssertions {
 static void run(GameTestHelper h,RemoteTestEnvironment env,ServerPlayer owner,List<ProbeResult> rows){
  var l=h.getLevel();var id=env.draft.id();var plan=env.draft.buildings().getFirst();var colony=env.home.colony();
  h.assertTrue(!RemoteConstruction.queue(l,id,plan.operationId()).blockers().isEmpty()&&colony.getWorkManager().getWorkOrders().isEmpty(),"Mayor required before construction");pass(rows,"requires_mayor");
  MayorService.appoint(owner,id,env.draft.request().mayorId());
  h.assertTrue(!RemoteConstruction.queue(l,id,env.draft.buildings().get(1).operationId()).blockers().isEmpty(),"Builder shelter precedes workplaces");pass(rows,"construction_dependencies");
  var pos=plan.anchor().offset(2,0,0);l.setBlockAndUpdate(pos,Blocks.COBBLESTONE.defaultBlockState());
  h.assertTrue(!RemoteConstruction.queue(l,id,plan.operationId()).blockers().isEmpty()&&colony.getWorkManager().getWorkOrders().isEmpty(),"Player obstruction is preserved");l.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());pass(rows,"construction_obstruction");
  boolean planner=System.getProperty("colonyhorizons.foundationSuite").equals("MayorConstruction");
  var result=planner?dev.colonyhorizons.mayor.MayorPlanner.tick(l,id):RemoteConstruction.queue(l,id,plan.operationId());h.assertTrue(result.blockers().isEmpty(),"Native queue: "+result.blockers());var orders=colony.getWorkManager().getWorkOrders();h.assertTrue(orders.size()==1,"Exactly one actual work order");var order=orders.values().iterator().next();h.assertTrue(order.getLocation().equals(plan.anchor())&&order.getClaimedBy().equals(plan.anchor())&&order.getTargetLevel()==1,"Local native builder and level");
  h.assertTrue(colony.getServerBuildingManager().getBuilding(plan.anchor()).getBuildingLevel()==0,"Queue must not complete a building");pass(rows,"native_build_order");
  if(planner){var repeat=dev.colonyhorizons.mayor.MayorPlanner.tick(l,id);h.assertTrue(!repeat.progressed(),"Mayor cadence cannot perform a second mutation");}else RemoteConstruction.queue(l,id,plan.operationId());h.assertTrue(orders.size()==1&&orders.containsKey(order.getID()),"Repeated queue preserves native order");pass(rows,"construction_idempotent");
  var saved=RemoteDevelopmentData.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());var loaded=RemoteDevelopmentData.load(saved,l.registryAccess(),l.dimension());h.assertTrue(loaded.construction(id).size()==1,"Construction intent persists");pass(rows,"construction_journal");
  var authority=RemoteDevelopmentData.get(l);var beforePause=authority.find(id).orElseThrow();authority.transition(id,beforePause.revision(),Phase.PAUSED);
  h.assertTrue(!RemoteConstruction.reconcile(l,id).blockers().isEmpty()&&orders.containsKey(order.getID()),"Paused settlement cannot reconcile native work");authority.transition(id,authority.find(id).orElseThrow().revision(),Phase.BUILDING);pass(rows,"construction_pause_guard");
  MayorService.vacate(owner,id);var vacant=RemoteDevelopmentData.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());h.assertTrue(RemoteDevelopmentData.load(vacant,l.registryAccess(),l.dimension()).construction(id).size()==1,"Mayor vacancy retains readable construction custody");MayorService.appoint(owner,id,env.draft.request().mayorId());pass(rows,"construction_mayor_vacancy");
  var uncertain=saved.copy();var entry=uncertain.getList("developments",10).getCompound(0).getList("construction",10).getCompound(0);entry.putString("state","INTENT");entry.putInt("order",0);h.assertTrue(RemoteDevelopmentData.load(uncertain,l.registryAccess(),l.dimension()).find(id).orElseThrow().phase()==Phase.RECOVERY_NEEDED,"Uncertain native order must not replay");pass(rows,"construction_interruption");
  if(planner){var builder=(com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder)colony.getServerBuildingManager().getBuilding(plan.anchor());builder.addNeededResource(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND),1);l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+100);h.assertTrue(!dev.colonyhorizons.mayor.MayorPlanner.tick(l,id).blockers().isEmpty(),"Native material requests cannot exceed approved supplies");pass(rows,"mayor_material_budget");}
  if(System.getProperty("colonyhorizons.foundationSuite").endsWith("Restart"))return;
  colony.getWorkManager().removeWorkOrder(order);h.assertTrue(!RemoteConstruction.reconcile(l,id).blockers().isEmpty()&&colony.getWorkManager().getWorkOrders().isEmpty(),"Cancelled native order cannot be recreated silently");pass(rows,"construction_cancel_preserved");
 }
 private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Real native build order, durable intent and no forced completion"));}
}
