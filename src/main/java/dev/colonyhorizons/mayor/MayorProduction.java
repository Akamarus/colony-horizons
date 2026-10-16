// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.*;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLumberjack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import java.util.*;
/** Completes the approved forestry setup and returns surplus through the two warehouses. */
public final class MayorProduction {
 private MayorProduction(){}
 public static StepResult reconcile(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();var owner=level.getServer().getPlayerList().getPlayer(r.owner());
  if(!FoundingConstructionSupplies.warehouseFirst(r)||owner==null||owner.isRemoved()||owner.serverLevel()!=level||!RemoteTickets.snapshot(level).containsKey(id)||!MayorService.available(level,id)||!MayorLogistics.ready(level,id)||!Set.of(Phase.BUILDING,Phase.OPERATING).contains(r.phase()))return idle();
  if(RemoteConstruction.next(level,id).isPresent()||data.construction(id).size()<r.draft().buildings().size()||data.construction(id).values().stream().anyMatch(e->e.state()!=ConstructionJournal.State.COMPLETE))return idle();
  var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());var plans=data.buildings(id).stream().filter(p->p.role().equals("lumberjack")).toList();
  for(int i=0;i<plans.size();i++){
   var plan=plans.get(i);if(!FoundingPlacement.isPaidAnchor(level,plan)||!(colony.getServerBuildingManager().getBuilding(plan.anchor()) instanceof BuildingLumberjack hut))return new StepResult("Forester hut unavailable",List.of("Restore the approved forester hut"),false);
   var module=hut.getModule(BuildingModules.FORESTER_WORK);
   if(module.getAssignedCitizen().isEmpty()){
    for(int personId:FoundingRoster.citizenIds(data,r.id())){var person=colony.getCitizenManager().getCivilian(personId);if(person!=null&&person.getUUID().equals(data.approvedParty(id).get(personId))&&person.getJob()==null&&module.assignCitizen(person)){NativeFoundingCheckpoint.save(level,colony);return new StepResult("Local forester assigned",List.of(),true);}}
   }
   if(!hut.shouldRestrict()){
    int minX=r.draft().chunks().stream().mapToInt(c->c.getMinBlockX()).min().orElseThrow(),maxX=r.draft().chunks().stream().mapToInt(c->c.getMaxBlockX()).max().orElseThrow();int minZ=r.draft().chunks().stream().mapToInt(c->c.getMinBlockZ()).min().orElseThrow(),maxZ=r.draft().chunks().stream().mapToInt(c->c.getMaxBlockZ()).max().orElseThrow();
    hut.setRestrictedArea(new BlockPos(i==1?r.draft().request().center().getX():minX,level.getMinBuildHeight(),minZ),new BlockPos(i==0?r.draft().request().center().getX()-1:maxX,level.getMaxBuildHeight()-1,maxZ));NativeFoundingCheckpoint.save(level,colony);return new StepResult("Forester work area assigned",List.of(),true);
   }
  }
  if(r.phase()!=Phase.OPERATING){data.transition(id,r.revision(),Phase.OPERATING);data.checkpoint(level);return new StepResult("Forestry outpost operating",List.of(),true);}
  var warehouse=MayorLogistics.warehouse(level,id).orElseThrow();final WarehouseInventory.View view;try{view=WarehouseInventory.resolve(level,warehouse.getTileEntity());}catch(WarehouseInventory.Unavailable e){return new StepResult("Waiting for warehouse",List.of(e.getMessage()),false);}
  var service=NativeSupplyService.get(level);if(!service.problem().isBlank())return new StepResult("Supply recovery needed",List.of(service.problem()),false);
  var goods=new LinkedHashMap<ItemKey,Integer>();for(int slot=0;slot<view.inventory().getSlots();slot++){var stack=view.inventory().getStackInSlot(slot);if(stack.is(ItemTags.LOGS))goods.merge(new ItemKey(stack),stack.getCount(),Integer::sum);}
  for(var e:goods.entrySet())if(e.getValue()>16){
   if(service.endpointAt(r.owner(),warehouse.getPosition()).isEmpty())service.bind(r.owner(),warehouse.getPosition(),"Outpost "+id.toString().substring(0,8),true);
   if(service.endpointAt(r.owner(),r.draft().request().homeSupply()).isEmpty())service.bind(r.owner(),r.draft().request().homeSupply(),"Home "+r.draft().request().colonyId(),false);
   var source=service.endpointAt(r.owner(),warehouse.getPosition()).orElseThrow();var target=service.endpointAt(r.owner(),r.draft().request().homeSupply()).orElseThrow();
   if(service.routes(r.owner()).stream().anyMatch(route->route.source().equals(source.id())&&route.destination().equals(target.id())&&net.minecraft.world.item.ItemStack.isSameItemSameComponents(service.settings(route.id()).item(),e.getKey().stack())))continue;
   service.link(r.owner(),source.name(),target.name(),e.getKey().stack(),64,16,8);return new StepResult("Returning surplus logs Home",List.of(),true);
  }
  return idle();
 }
 private static StepResult idle(){return new StepResult("Foresters working; couriers collect locally",List.of(),false);}
}
