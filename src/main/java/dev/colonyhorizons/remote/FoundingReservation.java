// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.api.colony.ICitizenData;
import net.minecraft.server.level.ServerLevel;
import dev.colonyhorizons.remote.RemoteModels.Phase;

/** Reserves original founders and confines their native job assignments to approved local roles. */
public final class FoundingReservation {
 private FoundingReservation() {}
 public static boolean permits(ICitizenData citizen,com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule module) {
  if(citizen==null||!(citizen.getColony().getWorld() instanceof ServerLevel level))return true;
  var existing=RemoteDevelopmentData.existing(level);if(existing.isEmpty())return true;var data=existing.orElseThrow();
  // Check the destination too: a vacant remote hut must not recruit an unrelated Home adult.
  if(module.getBuilding()!=null)for(var r:data.all())if(data.foundingManaged(r.id())&&r.phase()!=Phase.PROPOSED&&(r.phase()!=Phase.CANCELLED||(data.grant(r.id()).filter(FoundingGrantJournal::consumed).isPresent()||!data.anchorReceipts(r.id()).isEmpty()))&&r.draft().request().colonyId()==citizen.getColony().getID()&&(data.buildings(r.id()).stream().anyMatch(p->p.anchor().equals(module.getBuilding().getPosition()))||r.draft().preparation().filter(p->p.camp().equals(module.getBuilding().getPosition())).isPresent())) {
   if(r.phase()==Phase.CANCELLED||data.grant(r.id()).filter(g->!g.returnTargets().isEmpty()).isPresent())return false;
   if(!citizen.getUUID().equals(data.approvedParty(r.id()).get(citizen.getId())))return false;
  }
  for(var r:data.all())if(data.foundingManaged(r.id())&&r.phase()!=Phase.PROPOSED&&r.phase()!=Phase.CANCELLED&&r.draft().request().colonyId()==citizen.getColony().getID()&&FoundingRoster.citizenIds(data,r.id()).contains(citizen.getId())) {
   if(!citizen.getUUID().equals(data.approvedParty(r.id()).get(citizen.getId())))continue;
   var journey=data.journey(r.id());if(journey.isEmpty()||journey.orElseThrow().state()!=FoundingTravel.State.ARRIVED)return false;
   if(module.getBuilding()==null||module.getBuilding().getColony()!=citizen.getColony())return false;
   if(r.draft().preparation().filter(p->p.camp().equals(module.getBuilding().getPosition())).isPresent())return data.preparationProgress(r.id()).filter(p->p.stage()==PreparationProgress.Stage.CAMP_READY).isPresent()&&FoundingRoster.member(data,r.id(),FoundingRoster.Role.BUILDER).filter(m->m.citizenId()==citizen.getId()&&m.entityId().equals(citizen.getUUID())).isPresent()&&module.getJobEntry().getKey().toString().equals("minecolonies:builder");
   var plan=RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(b->b.anchor().equals(module.getBuilding().getPosition())).findFirst();if(plan.isEmpty())return false;
   if(citizen.getId()==FoundingRoster.mayorId(data,r.id()))return module instanceof dev.colonyhorizons.mayor.MayorTerminalModule;
   var assigned=FoundingRoster.resolve(data,r.id()).stream().filter(m->m.citizenId()==citizen.getId()&&m.entityId().equals(citizen.getUUID())).findFirst();if(assigned.isEmpty())return false;
   String role=switch(assigned.orElseThrow().role()){case BUILDER->"builder";case COURIER->"courier";case FORESTER->"lumberjack";case PRODUCER->r.draft().request().surveyOrigin().orElseThrow().profile().hutRole();default->"mayor";};
   var job=assigned.orElseThrow().role()==FoundingRoster.Role.PRODUCER?r.draft().request().surveyOrigin().orElseThrow().profile().jobId():net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecolonies",role.equals("courier")?"deliveryman":role);
   return plan.orElseThrow().role().equals(role)&&module.getJobEntry().getKey().equals(job);
  }
  return true;
 }
}
