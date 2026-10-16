// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Original local courier and warehouse; never assigns a cross-settlement courier. */
public final class MayorLogistics {
 private MayorLogistics(){}
 public static Optional<BuildingWareHouse> warehouse(ServerLevel level,UUID id){
  var r=RemoteDevelopmentData.get(level).find(id).orElseThrow();var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
  if(colony==null)return Optional.empty();
  return r.draft().buildings().stream().filter(p->p.role().equals("warehouse")&&FoundingPlacement.isPaidAnchor(level,p)).map(p->colony.getServerBuildingManager().getBuilding(p.anchor())).filter(b->b instanceof BuildingWareHouse&&b.isBuilt()&&b.getBuildingLevel()>0).map(b->(BuildingWareHouse)b).findFirst();
 }
 public static boolean ready(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();var warehouse=warehouse(level,id);if(warehouse.isEmpty()||!MayorService.available(level,id))return false;
  var citizen=warehouse.orElseThrow().getColony().getCitizenManager().getCivilian(FoundingRoster.member(data,id,FoundingRoster.Role.COURIER).map(FoundingRoster.Member::citizenId).orElse(0));
  return citizen!=null&&!citizen.isPaused()&&citizen.getUUID().equals(data.approvedParty(id).get(citizen.getId()))&&citizen.getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman&&citizen.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isPresent()&&citizen.getWorkBuilding()!=null&&citizen.getWorkBuilding().isBuilt()&&r.draft().buildings().stream().anyMatch(p->p.role().equals("courier")&&p.anchor().equals(citizen.getWorkBuilding().getPosition())&&FoundingPlacement.isPaidAnchor(level,p))&&warehouse.orElseThrow().getModule(BuildingModules.WAREHOUSE_COURIERS).hasAssignedCitizen(citizen);
 }
 public static StepResult reconcile(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();if(!FoundingConstructionSupplies.warehouseFirst(r)||!MayorService.available(level,id)||!RemoteTickets.snapshot(level).containsKey(id))return idle();
  var warehouse=warehouse(level,id);if(warehouse.isEmpty())return idle();var colony=warehouse.orElseThrow().getColony();
  var plan=r.draft().buildings().stream().filter(p->p.role().equals("courier")).findFirst().orElseThrow();var hut=colony.getServerBuildingManager().getBuilding(plan.anchor());
  if(hut==null||!hut.isBuilt()||hut.getBuildingLevel()<1||!FoundingPlacement.isPaidAnchor(level,plan))return idle();
  var citizen=colony.getCitizenManager().getCivilian(FoundingRoster.member(data,id,FoundingRoster.Role.COURIER).map(FoundingRoster.Member::citizenId).orElse(0));
  if(citizen==null||!citizen.getUUID().equals(data.approvedParty(id).get(citizen.getId())))return new StepResult("Needs original courier",List.of("Approved courier unavailable"),false);
  var worker=hut.getModule(BuildingModules.COURIER_WORK);var store=warehouse.orElseThrow().getModule(BuildingModules.WAREHOUSE_COURIERS);boolean changed=false;
  if(!worker.hasAssignedCitizen(citizen)){if(citizen.getJob()!=null||!worker.assignCitizen(citizen))return new StepResult("Needs courier",List.of("Original courier is assigned elsewhere"),false);changed=true;}
  if(!store.hasAssignedCitizen(citizen)){if(!store.assignCitizen(citizen))return new StepResult("Needs courier",List.of("Warehouse courier assignment unavailable"),false);changed=true;}
  if(changed)NativeFoundingCheckpoint.save(level,colony);return new StepResult("Local courier assigned",List.of(),changed);
 }
 private static StepResult idle(){return new StepResult("Logistics reviewed",List.of(),false);}
}
