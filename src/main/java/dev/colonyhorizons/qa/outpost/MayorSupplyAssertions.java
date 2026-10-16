package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.mayor.*;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;
final class MayorSupplyAssertions {
 static void run(GameTestHelper h,RemoteTestEnvironment env,ServerPlayer owner,List<ProbeResult> rows)throws Exception{
  var l=h.getLevel();var id=env.draft.id();var key=new ItemKey(new ItemStack(Items.BREAD));var op=UUID.randomUUID();
  h.assertTrue(!MayorReservations.reserve(l,id,op,Map.of(key,8)),"Vacant Mayor cannot reserve supplies");pass(rows,"reservation_needs_mayor");MayorService.appoint(owner,id,env.draft.request().mayorId());
  h.assertTrue(MayorReservations.reserve(l,id,op,Map.of(key,8)),"Current Mayor can reserve8 above Home reserve");h.assertTrue(MayorReservations.reserve(l,id,op,Map.of(key,8))&&MayorReservations.get(l).entries().size()==1,"Retry is inert");pass(rows,"native_reservation_once");
  h.assertTrue(!MayorReservations.reserve(l,id,UUID.randomUUID(),Map.of(key,17)),"Founding16 plus reservation8 leaves16 of40 allowance");h.assertTrue(MayorReservations.reserve(l,id,UUID.randomUUID(),Map.of(key,16)),"Remaining16 can be reserved");pass(rows,"native_reservation_budget");
  var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(env.home.center());int food=0;for(int i=0;i<hut.getInventory().getSlots();i++)if(hut.getInventory().getStackInSlot(i).is(Items.BREAD))food+=hut.getInventory().getStackInSlot(i).getCount();h.assertTrue(food==40,"Reservation does not withdraw supplies");pass(rows,"reservation_keeps_native_stock");
  var homeBoundary=dev.colonyhorizons.supply.NativeCourierDistricts.resolve(l,env.home.colony().getID(),env.home.center());var remoteBoundary=dev.colonyhorizons.supply.NativeCourierDistricts.resolve(l,env.home.colony().getID(),env.draft.buildings().getFirst().anchor());
  h.assertTrue(homeBoundary.status()==dev.colonyhorizons.supply.CourierDistrictPolicy.Status.ACTIVE&&remoteBoundary.status()==dev.colonyhorizons.supply.CourierDistrictPolicy.Status.ACTIVE&&!dev.colonyhorizons.supply.CourierDistrictPolicy.check(homeBoundary,homeBoundary,homeBoundary,remoteBoundary).allowed(),"Native courier cannot treat remote huts as Home");pass(rows,"remote_courier_boundary");
  var service=dev.colonyhorizons.supply.NativeSupplyService.get(l);
  var result=MayorSupplyController.ensure(l,id,op,env.draft.buildings().getFirst().anchor(),key.stack(),8);h.assertTrue(result.blockers().isEmpty(),"Mayor coordinates reserved route: "+result.blockers());var route=op;service.tick(100);h.assertTrue(service.route(route).cargo().getCount()==8,"Reserved supplies enter protected custody");var remaining=service.route(route).remainingTicks();pass(rows,"managed_supply_dispatch");
  RemoteActivityTests.connect(owner,false);service.tick(1000);h.assertTrue(service.route(route).remainingTicks()==remaining&&service.route(route).cargo().getCount()==8,"Owner offline freezes cargo clock");RemoteActivityTests.connect(owner,true);pass(rows,"managed_supply_offline");
  if(System.getProperty("colonyhorizons.foundationSuite").endsWith("Restart"))return;
  dev.colonyhorizons.supply.NativeSupplyService.stop();service=dev.colonyhorizons.supply.NativeSupplyService.get(l);h.assertTrue(service.problem().isEmpty(),"Managed binding reload");for(int i=0;i<4;i++)service.tick(1200);h.assertTrue(service.route(route).cargo().isEmpty()&&service.route(route).delivered()==8,"Reserved cargo arrives once after reload");pass(rows,"managed_supply_reload");
  RemoteActivityTests.connect(owner,false);h.assertTrue(!MayorReservations.reserve(l,id,UUID.randomUUID(),Map.of(key,1)),"Offline Mayor reservation paused");RemoteActivityTests.connect(owner,true);pass(rows,"reservation_offline_pause");
 }
 private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Approved native Mayor and finite Home supply reservation"));}
}


