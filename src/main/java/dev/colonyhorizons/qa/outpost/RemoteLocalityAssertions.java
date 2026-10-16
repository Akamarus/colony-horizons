package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.*;
final class RemoteLocalityAssertions {
 static void run(GameTestHelper h,RemoteTestEnvironment env,List<ProbeResult> rows)throws Exception{
  var colony=env.home.colony();var citizen=colony.getCitizenManager().getCivilian(env.draft.request().mayorId());var entity=citizen.getEntity().orElseThrow();var nav=(MinecoloniesAdvancedPathNavigate)entity.getNavigation();nav.stop();var before=entity.blockPosition();
  EntityNavigationUtils.walkToPos(entity,env.home.center(),1,true,1.0);
  h.assertTrue(nav.getPathResult()==null&&entity.blockPosition().equals(before),"Routine Home request cannot start a cross-gap path or teleport Home");pass(rows,"no_home_service_path");
  EntityNavigationUtils.walkToPos(entity,env.draft.request().center().offset(4,0,8),1,true,1.0);
  h.assertTrue(nav.getPathResult()!=null,"Ordinary local travel remains native");nav.stop();pass(rows,"local_path_allowed");
  var eating=new com.minecolonies.core.entity.ai.minimal.EntityAIEatTask((com.minecolonies.core.entity.citizen.EntityCitizen)entity);
  var go=eating.getClass().getDeclaredMethod("goToHut");go.setAccessible(true);
  h.assertTrue(go.invoke(eating)==com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.EAT,"Arrived food can be eaten without completing a now-unnecessary hut path");pass(rows,"eat_delivered_food_without_hut_path");
  var home=colony.getServerBuildingManager().getBuilding(env.home.center());var local=colony.getServerBuildingManager().getBuilding(env.draft.buildings().getFirst().anchor());
  var order=WorkOrderBuilding.create(WorkOrderType.BUILD,home);var own=WorkOrderBuilding.create(WorkOrderType.BUILD,local);
  h.assertTrue(!order.canBuildIgnoringDistance(local,local.getPosition(),5)&&!own.canBuildIgnoringDistance(home,home.getPosition(),5),"Manual assignment cannot bypass settlement work boundaries");
  h.assertTrue(own.canBuildIgnoringDistance(local,local.getPosition(),0)&&order.canBuildIgnoringDistance(home,home.getPosition(),1),"Own hut and unmanaged Home work remain allowed");pass(rows,"local_work_orders_only");
  var plan=env.draft.buildings().stream().filter(b->b.role().equals("home")).findFirst().orElseThrow();var pos=env.home.center().offset(12,0,0);var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint(plan.pack(),plan.blueprint(),h.getLevel().registryAccess());
  com.minecolonies.api.blocks.ModBlocks.blockHutHome.setup(env.owner,h.getLevel(),pos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,plan.pack(),plan.blueprint());
  var housing=colony.getServerBuildingManager().getBuilding(pos).getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.LivingBuildingModule.class);
  h.assertTrue(!housing.assignCitizen(citizen),"Remote founder must not be assigned a Home house");var ordinary=env.home.createCitizen();ordinary.setPaused(true);h.assertTrue(housing.assignCitizen(ordinary),"Unmanaged Home citizen retains native housing");pass(rows,"local_housing_only");
 }
 private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native housing, work-order and navigation boundary checks"));}
}
