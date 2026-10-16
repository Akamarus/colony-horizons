package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.mayor.*;
import dev.colonyhorizons.remote.*;
import com.minecolonies.api.colony.requestsystem.requestable.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;
final class MayorNeedsAssertions {
 static void run(GameTestHelper h,RemoteTestEnvironment env,ServerPlayer owner,List<ProbeResult> rows){
  var l=h.getLevel();var id=env.draft.id();MayorService.appoint(owner,id,env.draft.request().mayorId());RemoteConstruction.queue(l,id,env.draft.buildings().getFirst().operationId());
  var citizen=env.home.colony().getCitizenManager().getCivilian(env.draft.request().citizenIds().get(1));
  var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(env.draft.buildings().getFirst().anchor());
  // Model consumption, not a new source of supplies; replacement must leave finite Home stock.
  for(int i=0;i<citizen.getInventory().getSlots();i++)if(citizen.getInventory().getStackInSlot(i).is(Items.BREAD)||citizen.getInventory().getStackInSlot(i).is(Items.STONE_AXE))citizen.getInventory().setStackInSlot(i,ItemStack.EMPTY);
  citizen.createRequestAsync(new Food(8,0));
  var result=MayorOperatingSupplies.reconcile(l,id);h.assertTrue(result.progressed()&&result.blockers().isEmpty(),"Native food shortage reserved: "+result);pass(rows,"native_food_request");
  var service=dev.colonyhorizons.supply.NativeSupplyService.get(l);var route=service.routes(owner.getUUID()).getFirst().id();MayorOperatingSupplies.reconcile(l,id);h.assertTrue(service.routes(owner.getUUID()).size()==1,"Same native request cannot reserve twice");
  for(int i=0;i<4;i++)service.tick(1200);MayorOperatingSupplies.reconcile(l,id);h.assertTrue(service.route(route).delivered()==8&&count(hut,Items.BREAD)==8,"Paid food arrives in local hut");pass(rows,"native_food_arrival");
  citizen.createRequestAsync(new Tool(com.minecolonies.api.equipment.ModEquipmentTypes.axe.get(),0,citizen.getWorkBuilding().getMaxEquipmentLevel()));
  result=MayorOperatingSupplies.reconcile(l,id);h.assertTrue(result.progressed()&&service.routes(owner.getUUID()).size()==2,"Native tool shortage uses suitable approved item: "+result);pass(rows,"native_tool_request");
  for(int i=0;i<4;i++)service.tick(1200);MayorOperatingSupplies.reconcile(l,id);h.assertTrue(count(hut,Items.STONE_AXE)==1,"One actual suitable tool delivered");pass(rows,"native_tool_arrival");
  for(int i=0;i<4;i++)MayorOperatingSupplies.reconcile(l,id);h.assertTrue(service.routes(owner.getUUID()).size()==2&&MayorReservations.get(l).entries().size()==2,"Repeat reviews preserve original requests and allowances");pass(rows,"operating_request_idempotent");
 }
 static int count(com.minecolonies.core.tileentities.TileEntityColonyBuilding h,Item item){int n=0;for(int i=0;i<h.getInventory().getSlots();i++)if(h.getInventory().getStackInSlot(i).is(item))n+=h.getInventory().getStackInSlot(i).getCount();return n;}
 static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native request, suitable finite supplies and stable reservation identity"));}
}
