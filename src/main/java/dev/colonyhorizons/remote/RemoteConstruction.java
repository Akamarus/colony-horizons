// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.mayor.MayorService;
import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Queues native work, never places the finished structure or changes a hut level. */
public final class RemoteConstruction {
 private RemoteConstruction(){}
 public static Optional<BuildingPlan> next(ServerLevel level,UUID id){
  var r=RemoteDevelopmentData.get(level).find(id).orElseThrow();var colony=colony(level,r);
  return RemoteDevelopmentData.get(level).buildings(r.id()).stream().sorted(Comparator.comparingInt(p->p.role().equals("builder")?0:p.role().equals("warehouse")?1:p.role().equals("courier")?2:p.role().equals("home")?3:4)).filter(p->{var b=colony.getServerBuildingManager().getBuilding(p.anchor());return b==null||b.getBuildingLevel()<p.targetLevel()||!b.isBuilt();}).findFirst();
 }
 public static StepResult queue(ServerLevel level,UUID id,UUID operation){
  return queue(level,id,operation,null);
 }
 public static StepResult queueUpgrade(ServerLevel level,UUID id,UUID proposal){var entry=dev.colonyhorizons.mayor.MayorProposals.get(level).entries().get(proposal);if(entry==null||!entry.settlement().equals(id)||entry.state()!=dev.colonyhorizons.mayor.MayorProposals.State.APPROVED)return blocked("Upgrade needs owner approval");return queue(level,id,entry.building(),entry);}
 private static StepResult queue(ServerLevel level,UUID id,UUID operation,dev.colonyhorizons.mayor.MayorProposals.Entry upgrade){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();
  try{
   if(!Set.of(Phase.STAGING,Phase.BUILDING).contains(r.phase())&&(upgrade==null||r.phase()!=Phase.OPERATING))return blocked("Settlement is not ready for construction");
   if(!MayorService.available(level,id))return blocked("Needs Mayor");
   var actor=level.getServer().getPlayerList().getPlayer(r.owner());var colony=colony(level,r);
   if(actor==null||actor.serverLevel()!=level||!RemoteTickets.snapshot(level).containsKey(id)||!r.owner().equals(colony.getPermissions().getOwner()))return blocked("Waiting for owner and approved terrain");
   var plan=RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(p->p.operationId().equals(operation)).findFirst().orElseThrow(()->new IllegalArgumentException("Unapproved building"));
   int target=upgrade==null?plan.targetLevel():upgrade.target();
   var intent=new ConstructionJournal.Entry(operation,target,ConstructionJournal.State.INTENT,0);var prior=data.construction(id).get(intent.operation());
   if(prior!=null)return reconcile(level,id);
   if(upgrade==null&&next(level,id).filter(p->p.operationId().equals(operation)).isEmpty())return blocked("Complete the earlier settlement buildings first");
   if(data.construction(id).values().stream().anyMatch(e->e.state()!=ConstructionJournal.State.COMPLETE))return blocked("An existing construction operation needs attention");
   FoundingPlacement.validatePack(level,r);
   if(!FoundingPlacement.isPaidAnchor(level,plan))return blocked("Original paid hut is unavailable");
   var building=colony.getServerBuildingManager().getBuilding(plan.anchor());var builderPlan=RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(p->p.role().equals("builder")).findFirst().orElseThrow();var builder=colony.getServerBuildingManager().getBuilding(builderPlan.anchor());
   if(building==null||builder==null||!FoundingPlacement.isPaidAnchor(level,builderPlan))return blocked("Needs original local builder hut");
   if(building.getBuildingLevel()!=target-1)return blocked("Building level changed; review construction");
   if(!plan.role().equals("builder")&&(!builder.isBuilt()||builder.getBuildingLevel()<target))return blocked("Builder hut must be completed first");
   for(var order:colony.getWorkManager().getWorkOrders().values())if(order.getLocation().equals(plan.anchor())||order.getClaimedBy().equals(builderPlan.anchor()))return blocked("Existing player work order needs attention");
   if(upgrade==null)validateEmptySite(level,r,plan);else dev.colonyhorizons.mayor.MayorUpgrades.validateApproved(level,r,upgrade);
   int builderId=FoundingRoster.member(data,id,FoundingRoster.Role.BUILDER).orElseThrow().citizenId();var citizen=colony.getCitizenManager().getCivilian(builderId);
   if(citizen==null||!citizen.getUUID().equals(data.approvedParty(id).get(builderId)))return blocked("Original builder unavailable");
   var worker=builder.getFirstModuleOccurance(WorkerBuildingModule.class);
   if(!worker.hasAssignedCitizen(citizen)&&(citizen.getJob()!=null||!worker.assignCitizen(citizen)))return blocked("Local builder is unavailable or assigned elsewhere");
   validateNativeRequirements(building);
   data.putConstruction(id,intent);data.checkpoint(level);
   try{
    building.requestUpgrade(actor,builderPlan.anchor());
    var orders=colony.getWorkManager().getWorkOrders().values().stream().filter(o->o.getLocation().equals(plan.anchor())).toList();
    if(orders.size()!=1||!(orders.getFirst() instanceof WorkOrderBuilding order)||order.getTargetLevel()!=target||!order.getClaimedBy().equals(builderPlan.anchor()))throw new IllegalStateException("Native construction requirements refused the order; review research and builder level");
    NativeFoundingCheckpoint.save(level,colony);data.putConstruction(id,new ConstructionJournal.Entry(operation,target,ConstructionJournal.State.QUEUED,order.getID()));
    if(r.phase()==Phase.STAGING||r.phase()==Phase.OPERATING)data.transition(id,r.revision(),Phase.BUILDING);data.checkpoint(level);return new StepResult("Construction queued",List.of(),true);
   }catch(RuntimeException failure){data.putConstruction(id,data.construction(id).get(intent.operation()).state(ConstructionJournal.State.RECOVERY));recover(level,id);throw failure;}
  }catch(IllegalArgumentException|IllegalStateException failure){return blocked(failure.getMessage());}
 }
 public static StepResult reconcile(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();var colony=colony(level,r);
  if(!Set.of(Phase.STAGING,Phase.BUILDING).contains(r.phase()))return blocked("Settlement is not ready for construction");
  var actor=level.getServer().getPlayerList().getPlayer(r.owner());
  if(actor==null||actor.serverLevel()!=level||!r.owner().equals(colony.getPermissions().getOwner())||!RemoteTickets.snapshot(level).containsKey(id))return blocked("Waiting for owner and approved terrain");
  if(!MayorService.available(level,id))return blocked("Needs Mayor");
  for(var entry:data.construction(id).values()){
   if(entry.state()==ConstructionJournal.State.COMPLETE)continue;
   if(entry.state()!=ConstructionJournal.State.QUEUED)return blocked("Interrupted construction requires review");
   var plan=RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(p->p.operationId().equals(entry.building())).findFirst().orElseThrow();var building=colony.getServerBuildingManager().getBuilding(plan.anchor());
   if(!level.hasChunkAt(plan.anchor()))return blocked("Waiting for local hut to load");
   var order=colony.getWorkManager().getWorkOrder(entry.nativeOrder());
   if(FoundingPlacement.isPaidAnchor(level,plan)&&building!=null&&building.isBuilt()&&building.getBuildingLevel()==entry.target()&&order==null){NativeFoundingCheckpoint.save(level,colony);data.putConstruction(id,entry.state(ConstructionJournal.State.COMPLETE));data.checkpoint(level);return new StepResult("Construction complete",List.of(),true);}
   var builder=RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(p->p.role().equals("builder")).findFirst().orElseThrow();
   if(!(order instanceof WorkOrderBuilding)||!order.getLocation().equals(plan.anchor())||order.getTargetLevel()!=entry.target()||!order.getClaimedBy().equals(builder.anchor())||!FoundingPlacement.isPaidAnchor(level,plan)){
    data.putConstruction(id,entry.state(ConstructionJournal.State.RECOVERY));recover(level,id);return blocked("Native work order changed or was cancelled; review before continuing");
   }
   return new StepResult("Builder working",List.of(),false);
  }
  return new StepResult("Ready for next building",List.of(),false);
 }
 /** Match native no-order prerequisites before creating a durable intent. */
 public static void validateNativeRequirements(com.minecolonies.api.colony.buildings.IBuilding building){
  var colony=building.getColony();var effect=colony.getResearchManager().getResearchEffectIdFrom(building.getBuildingType().getBuildingBlock());
  if(com.minecolonies.api.MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(effect)&&colony.getResearchManager().getResearchEffects().getEffectStrength(effect)<=building.getBuildingLevel())throw new IllegalArgumentException("Complete the native research for this building level first");
  var parent=colony.getServerBuildingManager().getBuilding(building.getParent());
  if(building.getBuildingLevel()>=building.getMaxBuildingLevel()||parent!=null&&!(building.getBuildingLevel()==0?parent.getBuildingLevel()>0:building.getBuildingLevel()<parent.getBuildingLevel()||parent.getBuildingLevel()>=parent.getMaxBuildingLevel()))throw new IllegalArgumentException("Native building or parent level does not permit this upgrade");
 }
 private static void validateEmptySite(ServerLevel level,Development r,BuildingPlan plan){
  if(plan.targetLevel()!=1)throw new IllegalArgumentException("Upgrade requires a separate approved review");var v=plan.upgradeVolume();var data=RemoteDevelopmentData.get(level);
  for(var pos:BlockPos.betweenClosed(v.minX(),v.minY(),v.minZ(),v.maxX(),v.maxY(),v.maxZ())){
   var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(pos));
   if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||claim==null||claim.getOwningColony()!=r.draft().request().colonyId()||!data.confirmedClaims(r.id()).contains(new ChunkPos(pos)))throw new IllegalArgumentException("Construction land is unavailable or ownership changed");
   if(pos.equals(plan.anchor()))continue;
   boolean support=pos.getX()==plan.anchor().getX()&&pos.getZ()==plan.anchor().getZ()&&pos.getY()<plan.anchor().getY()&&pos.getY()>=r.draft().request().center().getY()&&level.getBlockState(pos).is(Blocks.DIRT);
   if(!support&&(level.getBlockEntity(pos)!=null||!level.getFluidState(pos).isEmpty()||!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()))throw new IllegalArgumentException("Construction area changed; preserve the obstruction and review");
   if(pos.getY()==v.minY()&&!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))throw new IllegalArgumentException("Construction floor is unsupported");
  }
 }
 private static IColony colony(ServerLevel level,Development r){return Objects.requireNonNull(IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension()),"Colony unavailable");}
 private static void recover(ServerLevel level,UUID id){var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();if(r.phase()!=Phase.RECOVERY_NEEDED)data.transition(id,r.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(level);}
 private static StepResult blocked(String message){return new StepResult("Construction blocked",List.of(message==null?"Review construction":message),false);}
}
