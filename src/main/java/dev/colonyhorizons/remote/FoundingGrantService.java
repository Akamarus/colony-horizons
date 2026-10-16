// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.api.colony.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.server.level.*;
import java.util.*;

/** Server-authoritative, one-time founding entitlement. No creation occurs during review. */
public final class FoundingGrantService {
 private FoundingGrantService(){}
 public static StepResult review(ServerLevel level,UUID outpost){
  return review(level,RemoteDevelopmentData.get(level).find(outpost).orElseThrow());
 }
 public static StepResult review(ServerLevel level,Development r){
  var data=RemoteDevelopmentData.get(level);var outpost=r.id();var q=r.draft().request();var colony=colony(level,r);
  if(!FoundingRoster.free(q))return blocked("This outpost uses its original selected citizens");
  for(var other:data.all())if(!other.id().equals(outpost)&&other.draft().request().colonyId()==q.colonyId()&&data.grant(other.id()).filter(FoundingGrantJournal::consumed).isPresent()&&!Collections.disjoint(r.draft().chunks(),other.draft().chunks()))return blocked("This site already received its founding settlers; recover the original outpost");
  var grant=data.grant(outpost);if(grant.filter(g->g.state()==FoundingGrantJournal.State.RECOVERY_REQUIRED||g.intent().isPresent()).isPresent())return blocked("Settler creation needs recovery; no replacement settlers granted");
  int needed=4-grant.map(g->g.receipts().size()).orElse(0);int otherReserved=0;
  for(var other:data.all())if(!other.id().equals(outpost)&&other.draft().request().colonyId()==q.colonyId()&&other.phase()!=Phase.CANCELLED)otherReserved+=data.grant(other.id()).map(g->4-g.receipts().size()).orElse(0);
  if((long)colony.getCitizenManager().getCurrentCitizenCount()+needed+otherReserved>colony.getCitizenManager().maxCitizensFromResearch())return blocked("Needs room for "+needed+" settlers within the colony population limit");
  return new StepResult("Four free settlers available",List.of(),false);
 }
 public static StepResult reserve(ServerPlayer actor,UUID outpost,long revision){
  var level=actor.serverLevel();var data=RemoteDevelopmentData.get(level);var r=data.find(outpost).orElseThrow();
  if(!actor.getUUID().equals(r.owner()))throw new SecurityException("Only the owner may reserve settlers");
  if(r.phase()!=Phase.PROPOSED||r.revision()!=revision)return blocked("Founding review changed");
  var result=review(level,outpost);if(!result.blockers().isEmpty())return result;
  if(data.grant(outpost).isEmpty()){var originals=new HashSet<UUID>();colony(level,r).getCitizenManager().getCitizens().forEach(c->originals.add(c.getUUID()));data.putGrant(outpost,FoundingGrantJournal.reserved(r.draft().request(),originals));}
  data.enableFreeFounding(outpost);data.checkpoint(level);return new StepResult("Four founding places reserved",List.of(),true);
 }
 public static StepResult advance(ServerLevel level,UUID outpost){
  var data=RemoteDevelopmentData.get(level);var r=data.find(outpost).orElseThrow();var grant=data.grant(outpost).orElseThrow();var colony=colony(level,r);
  if(r.phase()!=Phase.STAGING||!data.foundingManaged(outpost)||!RemoteTickets.snapshot(level).containsKey(outpost)||level.getServer().getPlayerList().getPlayer(r.owner())==null)return blocked("Waiting for owner and active outpost");
  if(grant.state()==FoundingGrantJournal.State.RECOVERY_REQUIRED||grant.intent().isPresent())return blocked("Settler creation requires recovery");
  for(var m:grant.receipts()){var c=colony.getCitizenManager().getCivilian(m.citizenId());if(c==null||!m.entityId().equals(c.getUUID()))return recover(level,data,r,grant,"Original settler missing; no replacement granted");}
  if(grant.receipts().size()==4){data.putFounderRoster(outpost,grant.receipts());return new StepResult("Four settlers registered",List.of(),false);}
  var check=review(level,outpost);if(!check.blockers().isEmpty())return check;
  FoundingCargo.load(data.foundingCargo(outpost).orElseThrow(()->new IllegalStateException("Founding supplies not secured")),level.registryAccess());
  if(r.draft().preparation().isPresent()?!PreparedFounding.readyForArrival(level,outpost):(data.anchorReceipts(outpost).size()!=r.draft().buildings().size()||data.anchorReceipts(outpost).values().stream().anyMatch(a->!a.complete())))return blocked("Founding huts must be placed before settlers arrive");
  for(int i=0;i<4;i++)if(!FoundingTravel.safe(level,colony.getID(),PreparedFounding.arrival(r.draft(),i)))return blocked("Founding arrival needs dry supported space");
  try{
   // Do not yield between capacity check and all four registrations. Each native side effect has a durable intent.
   while(grant.receipts().size()<4){
    grant=grant.begin();data.putGrant(outpost,grant);data.checkpoint(level);
    var citizen=NativeFounderFactory.register(colony);var member=new FoundingRoster.Member(grant.intent().orElseThrow(),citizen.getId(),citizen.getUUID());
    grant=grant.receipt(member);data.putGrant(outpost,grant);data.checkpoint(level);NativeFoundingCheckpoint.save(level,colony);
   }
   data.putFounderRoster(outpost,grant.receipts());data.checkpoint(level);return new StepResult("Four free settlers registered",List.of(),true);
  }catch(RuntimeException failure){return recover(level,data,data.find(outpost).orElseThrow(),data.grant(outpost).orElseThrow(),"Settler registration interrupted; recovery required");}
 }
 public static void resumeReturns(ServerLevel level,UUID outpost){
  var data=RemoteDevelopmentData.get(level);var r=data.find(outpost).orElseThrow();
  if(r.phase()!=Phase.RECOVERY_NEEDED||data.grant(outpost).filter(g->!g.returnTargets().isEmpty()).isEmpty())return;
  var owner=level.getServer().getPlayerList().getPlayer(r.owner());if(owner!=null&&owner.serverLevel()==level)cancel(owner,outpost,r.revision());
 }
 public static StepResult cancel(ServerPlayer actor,UUID outpost,long revision){
  var level=actor.serverLevel();var data=RemoteDevelopmentData.get(level);var r=data.find(outpost).orElseThrow();var colony=colony(level,r);
  if(!r.owner().equals(actor.getUUID()))throw new SecurityException("Only the owner can recall founders");
  if(r.revision()!=revision)return blocked("Outpost changed; refresh before cancelling");
  var grant=data.grant(outpost).orElseThrow();
  if(!grant.consumed()||grant.intent().isPresent()||grant.state()==FoundingGrantJournal.State.RECOVERY_REQUIRED)return blocked("Ambiguous founding needs recovery before recall");
  if(grant.returnTargets().isEmpty()){
   var targets=new ArrayList<net.minecraft.core.BlockPos>();var center=r.draft().request().homeSupply();
   for(int dy=0;dy<=4&&targets.size()<grant.receipts().size();dy++)for(int x=-12;x<=12&&targets.size()<grant.receipts().size();x+=3)for(int z=-12;z<=12&&targets.size()<grant.receipts().size();z+=3){var pos=center.offset(x,dy,z);if(FoundingTravel.safe(level,colony.getID(),pos))targets.add(pos);}
   if(targets.size()!=grant.receipts().size())return blocked("Make dry supported space near the Home warehouse for returning settlers");
   grant=grant.returning(targets);data.putGrant(outpost,grant);if(r.phase()!=Phase.RECOVERY_NEEDED)data.transition(outpost,r.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(level);
  }
  while(grant.returned()<grant.receipts().size()){
   int index=grant.returned();var member=grant.receipts().get(index);var target=grant.returnTargets().get(index);
   if(!FoundingTravel.safe(level,colony.getID(),target))return blocked("Home return space is blocked; settlers and supplies retained");
   var citizen=colony.getCitizenManager().getCivilian(member.citizenId());
   if(citizen!=null&&!member.entityId().equals(citizen.getUUID()))return blocked("Original settler identity changed; recovery required");
   if(citizen!=null){
    if(citizen.getJob()!=null&&citizen.getJob().getWorkModule()!=null)citizen.getJob().getWorkModule().removeCitizen(citizen);
    citizen.setJob(null);if(citizen.getHomeBuilding()!=null)citizen.getHomeBuilding().getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.LivingBuildingModule.class).removeCitizen(citizen);citizen.setHomeBuilding(null);
    FoundingTravel.returnGranted(level,citizen,target);NativeFoundingCheckpoint.save(level,colony);
   }
   grant=grant.returnedOne();data.putGrant(outpost,grant);data.checkpoint(level);
  }
  var current=data.find(outpost).orElseThrow();if(current.phase()!=Phase.CANCELLED)data.transition(outpost,current.revision(),Phase.CANCELLED);data.checkpoint(level);RemoteActivityService.tick(level);
  return new StepResult("Settlers returned Home; site supplies retained",List.of(),true);
 }
 private static StepResult recover(ServerLevel level,RemoteDevelopmentData data,Development r,FoundingGrantJournal grant,String message){data.putGrant(r.id(),grant.recovery());if(r.phase()!=Phase.RECOVERY_NEEDED)data.transition(r.id(),r.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(level);return blocked(message);}
 private static IColony colony(ServerLevel level,Development r){var c=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());if(c==null||!r.owner().equals(c.getPermissions().getOwner()))throw new SecurityException("Founding colony ownership changed");return c;}
 private static StepResult blocked(String reason){return new StepResult("Founding settlers waiting",List.of(reason),false);}
}
