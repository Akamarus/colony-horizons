// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.*;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Imports finite approved resources through native hut buffers, never creates materials. */
public final class MayorSupplyController {
 private MayorSupplyController(){}
 public static StepResult ensure(ServerLevel level,UUID development,UUID operation,BlockPos destination,ItemStack item,int amount){
  try{
   if(amount<=0||amount>4096||item.isEmpty())return blocked("Material request is outside the supported limit");
   var r=RemoteDevelopmentData.get(level).find(development).orElseThrow();
   var requestedDestination=destination;
   if(!MayorService.available(level,development)||RemoteDevelopmentData.get(level).buildings(r.id()).stream().noneMatch(b->b.anchor().equals(requestedDestination)&&FoundingPlacement.isPaidAnchor(level,b)))return blocked("Needs original local hut and Mayor");
   boolean warehouseFirst=FoundingConstructionSupplies.warehouseFirst(r);
   if(warehouseFirst){
    if(!MayorLogistics.ready(level,development))return FoundingConstructionSupplies.supply(level,development,destination,item,amount);
    var warehouse=MayorLogistics.warehouse(level,development).orElseThrow();
    var inventory=WarehouseInventory.resolve(level,warehouse.getTileEntity()).inventory();
    if(count(inventory,item)>=amount){
     var requestedAt=destination;warehouse.getColony().getRequestManager().onColonyUpdate(q->q.getRequester().getLocation().getInDimensionLocation().equals(requestedAt)&&q.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable d&&d.matches(item));
     return new StepResult("Waiting for local courier",List.of(),false);
    }
    destination=warehouse.getPosition();
   }
   var service=NativeSupplyService.get(level);if(!service.problem().isEmpty())return blocked("Supply recovery needed");
   var existing=service.routes(r.owner()).stream().filter(route->route.id().equals(operation)).findFirst();
   if(existing.isPresent()&&warehouseFirst&&service.allowanceRemaining(operation)==0&&existing.orElseThrow().cargo().isEmpty())return new StepResult("Waiting for local courier",List.of(),false);
   if(existing.isPresent())return service.allowanceRemaining(operation)==0&&existing.orElseThrow().cargo().isEmpty()?blocked("Approved material allowance used; review remaining needs"):new StepResult("Supplies requested",List.of(),false);
   if(!MayorReservations.reserve(level,development,operation,Map.of(new ItemKey(item),amount)))return blocked("Needs "+amount+" "+item.getHoverName().getString()+" within allowance and Home reserve");
   var source=endpoint(service,r.owner(),r.draft().request().homeSupply(),false);var target=endpoint(service,r.owner(),destination,true);
   service.linkManaged(r.owner(),development,operation,source,target,item,amount,Math.min(64,amount));
   return new StepResult("Supplies requested",List.of(),true);
  }catch(IllegalArgumentException|IllegalStateException e){return blocked(e.getMessage()==null?"Supply needs review":e.getMessage());}
 }
 private static UUID endpoint(NativeSupplyService service,UUID owner,BlockPos pos,boolean outpost){
  var existing=service.endpointAt(owner,pos);if(existing.isPresent()){if(existing.orElseThrow().outpost()!=outpost)throw new IllegalArgumentException("Existing endpoint has a different role");return existing.orElseThrow().id();}
  return service.bind(owner,pos,(outpost?"Outpost ":"Home ")+Long.toUnsignedString(pos.asLong(),36),outpost);
 }
 public static StepResult reconcile(ServerLevel level,UUID development){
  var data=RemoteDevelopmentData.get(level);var r=data.find(development).orElseThrow();
  var active=data.construction(development).values().stream().filter(e->e.state()==ConstructionJournal.State.QUEUED).findFirst();
  if(active.isEmpty())return new StepResult("No construction supplies needed",List.of(),false);
  var plan=RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(p->p.role().equals("builder")).findFirst().orElseThrow();var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
  if(colony==null||!(colony.getServerBuildingManager().getBuilding(plan.anchor()) instanceof BuildingBuilder builder)||!(level.getBlockEntity(plan.anchor()) instanceof TileEntityColonyBuilding hut))return blocked("Local builder unavailable");
  var workers=builder.getFirstModuleOccurance(WorkerBuildingModule.class).getAssignedCitizen();
  // Native requests can fall back to the player while cargo is travelling. Ask native
  // resolvers to reconsider only this hut's deliverables now present in its inventory.
  // Repeating this after reload is safe: this neither accepts requests nor supplies items.
  colony.getRequestManager().onColonyUpdate(request ->
   request.getRequester().getLocation().getInDimensionLocation().equals(plan.anchor())
   && request.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable deliverable
   && available(hut.getInventory(),deliverable)>=deliverable.getMinimumCount());
  var resources=new ArrayList<>(builder.getNeededResources().values());resources.sort(Comparator.comparing(e->e.getItemStack().save(level.registryAccess()).toString()));
  StepResult waiting=null;
  for(var resource:resources){var item=resource.getItemStack();if(item.isEmpty())continue;int have=count(hut.getInventory(),item);for(var worker:workers)have+=count(worker.getInventory(),item);int need=resource.getAmount()-have;if(need<=0)continue;
   var identity=active.orElseThrow().operation()+":materials:"+item.copyWithCount(1).save(level.registryAccess());var op=UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var result=ensure(level,development,op,plan.anchor(),item,need);
   // One new reservation per review, but an in-flight material must not hold up other materials.
   if(result.progressed())return result;
   if(waiting==null||!result.blockers().isEmpty())waiting=result;
  }
  return waiting==null?new StepResult("Builder working",List.of(),false):waiting;
 }
 private static int count(net.neoforged.neoforge.items.IItemHandler inventory,ItemStack item){int n=0;for(int i=0;i<inventory.getSlots();i++){var s=inventory.getStackInSlot(i);if(ItemStack.isSameItemSameComponents(s,item))n+=s.getCount();}return n;}
 private static int available(net.neoforged.neoforge.items.IItemHandler inventory,com.minecolonies.api.colony.requestsystem.requestable.IDeliverable item){int n=0;for(int i=0;i<inventory.getSlots();i++){var s=inventory.getStackInSlot(i);if(!s.isEmpty()&&item.matches(s))n+=s.getCount();}return n;}
 private static StepResult blocked(String message){return new StepResult("Waiting for supplies",List.of(message.length()>500?message.substring(0,500):message),false);}
}
