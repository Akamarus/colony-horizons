// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.requestable.*;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.NativeSupplyService;
import net.minecraft.server.level.ServerLevel;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native food/tool requests retain native suitability checks and finite shared Home allowances. */
public final class MayorOperatingSupplies {
 private MayorOperatingSupplies(){}
 public static StepResult reconcile(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();
  var owner=level.getServer().getPlayerList().getPlayer(r.owner());
  if(owner==null||owner.isRemoved()||owner instanceof net.neoforged.neoforge.common.util.FakePlayer||owner.serverLevel()!=level||!RemoteTickets.snapshot(level).containsKey(id)||!MayorService.available(level,id))return idle();
  var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
  StepResult waiting=null;
  for(int citizenId:FoundingRoster.citizenIds(data,id)){
   var citizen=colony.getCitizenManager().getCivilian(citizenId);
   if(citizen==null||!citizen.getUUID().equals(data.approvedParty(id).get(citizenId)))continue;
   var building=citizen.getWorkBuilding();
   if(building==null||RemoteDevelopmentData.get(level).buildings(r.id()).stream().noneMatch(p->p.anchor().equals(building.getPosition())&&FoundingPlacement.isPaidAnchor(level,p))||!(level.getBlockEntity(building.getPosition()) instanceof TileEntityColonyBuilding hut))continue;
   colony.getRequestManager().onColonyUpdate(request -> request.getRequester().getLocation().getInDimensionLocation().equals(building.getPosition())
    && (request.getRequest() instanceof Food||request.getRequest() instanceof Tool)
    && count(hut.getInventory(),(IDeliverable)request.getRequest())>=((IDeliverable)request.getRequest()).getMinimumCount());
   var requests=new ArrayList<>(building.getOpenRequests(citizenId));
   requests.sort(Comparator.comparing(request->request.getId().getIdentifier().toString()));
   for(var request:requests){
    if(!(request.getRequest() instanceof Food||request.getRequest() instanceof Tool))continue;
    var need=(IDeliverable)request.getRequest();
    if(count(hut.getInventory(),need)+count(citizen.getInventory(),need)>=need.getCount())continue;
    var operation=UUID.nameUUIDFromBytes((id+":operating:"+citizen.getUUID()+":"+request.getId().getIdentifier()).getBytes(StandardCharsets.UTF_8));
    var reserved=MayorReservations.get(level).entries().get(operation);
    var item=reserved==null?r.draft().request().budget().allowances().keySet().stream().filter(k->need.matches(k.stack())).sorted(Comparator.comparing(k->k.stack().save(level.registryAccess()).toString())).findFirst():reserved.cost().keySet().stream().findFirst();
    if(item.isEmpty()){waiting=blocked("No approved food or suitable tool for "+citizen.getName());continue;}
    int amount=Math.min(64,need.getCount()-count(hut.getInventory(),need)-count(citizen.getInventory(),need));
    if(reserved!=null)amount=reserved.cost().get(item.orElseThrow());
    var result=MayorSupplyController.ensure(level,id,operation,building.getPosition(),item.orElseThrow().stack(),amount);
    if(result.progressed())return result;
    if(waiting==null||!result.blockers().isEmpty())waiting=result;
   }
   // Native eating can collect food from the workplace. Request a small buffer before it runs out.
   int food=0;for(var inventory:List.of(hut.getInventory(),citizen.getInventory()))for(int slot=0;slot<inventory.getSlots();slot++){
    var stack=inventory.getStackInSlot(slot);if(!stack.isEmpty()&&com.minecolonies.api.util.FoodUtils.canEat(stack,citizen.getHomeBuilding(),building))food+=stack.getCount();
   }
   boolean foodPending=requests.stream().anyMatch(q->q.getRequest() instanceof Food)||building.getCompletedRequestsOfCitizenOrBuilding(citizen).stream().anyMatch(q->q.getRequest() instanceof Food);
   if(food<2&&!foodPending){citizen.createRequestAsync(new Food(8,0));NativeFoundingCheckpoint.save(level,colony);return new StepResult("Food requested for local worker",List.of(),true);}
  }
  return waiting==null?idle():waiting;
 }
 private static int count(net.neoforged.neoforge.items.IItemHandler inventory,IDeliverable need){int n=0;for(int i=0;i<inventory.getSlots();i++){var s=inventory.getStackInSlot(i);if(!s.isEmpty()&&need.matches(s))n+=s.getCount();}return n;}
 private static StepResult idle(){return new StepResult("Food and tools reviewed",List.of(),false);}
 private static StepResult blocked(String message){return new StepResult("Waiting for food or tools",List.of(message),false);}
}
