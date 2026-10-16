// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.server.level.*;
import java.util.*;

/** Owner-approved native Mayor appointments. Construction and policy dispatch are separate services. */
public final class MayorService {
 private MayorService(){}
 private static Optional<Development> station(IBuilding building){
  if(building==null||!(building.getColony().getWorld() instanceof ServerLevel level))return Optional.empty();var data=RemoteDevelopmentData.existing(level);if(data.isEmpty())return Optional.empty();
  return data.orElseThrow().all().stream().filter(r->r.draft().request().colonyId()==building.getColony().getID()&&data.orElseThrow().foundingManaged(r.id())&&r.phase()!=Phase.PROPOSED&&r.phase()!=Phase.CANCELLED&&r.draft().buildings().stream().anyMatch(p->p.role().equals("builder")&&p.anchor().equals(building.getPosition())&&data.orElseThrow().anchorReceipts(r.id()).containsKey(p.operationId())&&data.orElseThrow().anchorReceipts(r.id()).get(p.operationId()).complete())).findFirst();
 }
 public static boolean isStation(IBuilding building){return station(building).isPresent();}
 public static boolean mayAssign(IBuilding building,ICitizenData citizen){
  if(citizen==null||citizen.isChild()||building==null||citizen.getColony()!=building.getColony())return false;var r=station(building);if(r.isEmpty())return false;
  var data=RemoteDevelopmentData.get((ServerLevel)building.getColony().getWorld());var a=data.mayor(r.orElseThrow().id());
  return a.isPresent()&&a.orElseThrow().citizenId()==citizen.getId()&&a.orElseThrow().entityId().equals(citizen.getUUID())&&a.orElseThrow().station().equals(building.getPosition());
 }
 public static StepResult appoint(ServerPlayer actor,UUID development,int citizenId){
  var level=actor.serverLevel();var data=RemoteDevelopmentData.get(level);var r=owned(actor,development);
  if(citizenId!=FoundingRoster.mayorId(data,r.id()))throw new IllegalArgumentException("Select the Mayor approved for this founding party");
  if(r.phase()!=Phase.STAGING&&r.phase()!=Phase.BUILDING&&r.phase()!=Phase.OPERATING)throw new IllegalStateException("Settlement is not ready for an appointment");
  if(data.journey(development).orElseThrow().state()!=FoundingTravel.State.ARRIVED||data.starterDelivery(development).filter(FoundingSupplies.Delivery::complete).isEmpty())throw new IllegalStateException("Founders and starter supplies must arrive first");
  var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());var citizen=colony.getCitizenManager().getCivilian(citizenId);
  if(citizen==null||citizen.isChild()||!citizen.getUUID().equals(data.approvedParty(development).get(citizenId))||citizen.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isEmpty())throw new IllegalStateException("Original approved Mayor is unavailable");
  var plan=r.draft().buildings().stream().filter(b->b.role().equals("builder")).findFirst().orElseThrow();var building=colony.getServerBuildingManager().getBuilding(plan.anchor());
  if(building==null||!FoundingPlacement.isPaidAnchor(level,plan)||!isStation(building))throw new IllegalStateException("Approved Mayor station is unavailable");
  var module=building.getFirstModuleOccurance(MayorTerminalModule.class);
  if(available(level,development))return new StepResult("Mayor appointed",List.of(),false);
  if(citizen.getJob()!=null&&!(citizen.getJob() instanceof JobMayor))throw new IllegalArgumentException("Mayor is already employed elsewhere");
  data.putMayor(development,new MayorAppointment(citizenId,citizen.getUUID(),plan.anchor()));data.checkpoint(level);
  if(!module.assignCitizen(citizen))throw new IllegalStateException("Native Mayor assignment failed");
  NativeFoundingCheckpoint.save(level,colony);data.checkpoint(level);return new StepResult("Mayor appointed",List.of(),true);
 }
 public static boolean available(ServerLevel level,UUID development){
  var data=RemoteDevelopmentData.existing(level);if(data.isEmpty())return false;var r=data.orElseThrow().find(development);var a=data.orElseThrow().mayor(development);if(r.isEmpty()||a.isEmpty()||!Set.of(Phase.STAGING,Phase.BUILDING,Phase.OPERATING).contains(r.orElseThrow().phase()))return false;
  var colony=IColonyManager.getInstance().getColonyByDimension(r.orElseThrow().draft().request().colonyId(),level.dimension());if(colony==null||!r.orElseThrow().owner().equals(colony.getPermissions().getOwner()))return false;
  var plan=r.orElseThrow().draft().buildings().stream().filter(p->p.role().equals("builder")&&p.anchor().equals(a.orElseThrow().station())).findFirst();if(plan.isEmpty()||!FoundingPlacement.isPaidAnchor(level,plan.orElseThrow()))return false;
  var citizen=colony.getCitizenManager().getCivilian(a.orElseThrow().citizenId());return citizen!=null&&citizen.getUUID().equals(a.orElseThrow().entityId())&&citizen.getJob() instanceof JobMayor&&a.orElseThrow().station().equals(citizen.getJob().getBuildingPos())&&citizen.getJob().getWorkModule() instanceof MayorTerminalModule module&&module.hasAssignedCitizen(citizen)&&citizen.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isPresent();
 }
 public static StepResult vacate(ServerPlayer actor,UUID development){
  var r=owned(actor,development);var data=RemoteDevelopmentData.get(actor.serverLevel());var a=data.mayor(development);if(a.isEmpty())return new StepResult("Needs Mayor",List.of(),false);
  var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),actor.level().dimension());var c=colony.getCitizenManager().getCivilian(a.orElseThrow().citizenId());
  data.removeMayor(development);data.checkpoint(actor.serverLevel());if(c!=null&&c.getUUID().equals(a.orElseThrow().entityId())&&c.getJob() instanceof JobMayor){var module=c.getJob().getWorkModule();if(module!=null)module.removeCitizen(c);c.setJob(null);}
  NativeFoundingCheckpoint.save(actor.serverLevel(),colony);return new StepResult("Needs Mayor",List.of(),true);
 }
 public static void tick(ICitizenData citizen){
  if(!(citizen.getColony().getWorld() instanceof ServerLevel level)||citizen.isPaused())return;var data=RemoteDevelopmentData.existing(level);if(data.isEmpty())return;
  for(var r:data.orElseThrow().all())if(data.orElseThrow().mayor(r.id()).filter(a->a.citizenId()==citizen.getId()&&a.entityId().equals(citizen.getUUID())).isPresent()&&available(level,r.id()))MayorPlanner.tick(level,r.id());
 }
 private static Development owned(ServerPlayer actor,UUID id){var r=RemoteDevelopmentData.get(actor.serverLevel()).find(id).orElseThrow();var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),actor.level().dimension());if(colony==null||!actor.getUUID().equals(r.owner())||!r.owner().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only the current owner may appoint or release a Mayor");return r;}
}
