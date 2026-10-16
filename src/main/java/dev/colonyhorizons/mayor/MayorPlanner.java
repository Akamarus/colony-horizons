// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Advances only buildings already included in the owner's accepted founding layout. */
public final class MayorPlanner {
 private MayorPlanner(){}
 private static final Map<ServerLevel,Map<UUID,Long>> NEXT=new WeakHashMap<>();
 public static StepResult tick(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var record=data.find(id).orElseThrow();
  if(!data.foundingManaged(id)||!Set.of(Phase.STAGING,Phase.BUILDING,Phase.OPERATING).contains(record.phase())||!MayorService.available(level,id))return blocked("Needs an active settlement and Mayor");
  var owner=level.getServer().getPlayerList().getPlayer(record.owner());
  if(owner==null||owner.isRemoved()||owner instanceof net.neoforged.neoforge.common.util.FakePlayer||owner.serverLevel()!=level||!RemoteTickets.snapshot(level).containsKey(id))return blocked("Waiting for owner and approved terrain");
  var due=NEXT.computeIfAbsent(level,k->new HashMap<>());long now=level.getGameTime();
  if(now<due.getOrDefault(id,Long.MIN_VALUE))return new StepResult("Mayor reviewing",List.of(),false);due.put(id,now+100);
  var logistics=MayorLogistics.reconcile(level,id);if(logistics.progressed()||!logistics.blockers().isEmpty())return logistics;
  if(FoundingConstructionSupplies.warehouseFirst(record)){var stores=FoundingConstructionSupplies.finish(level,id);if(stores.progressed()||!stores.blockers().isEmpty())return stores;}
  var housing=MayorHousing.reconcile(level,id);if(housing.progressed())return housing;
  var production=MayorProduction.reconcile(level,id);if(production.progressed()||!production.blockers().isEmpty())return production;
  var supplies=MayorOperatingSupplies.reconcile(level,id);if(supplies.progressed()||!supplies.blockers().isEmpty())return supplies;
  var expansion=RemoteExpansion.tick(level,id);if(expansion.progressed()||!expansion.blockers().isEmpty())return expansion;
  if(record.phase()==Phase.OPERATING)return MayorUpgrades.tick(level,id);
  if(data.construction(id).values().stream().anyMatch(e->e.state()!=ConstructionJournal.State.COMPLETE)){
   var construction=RemoteConstruction.reconcile(level,id);if(construction.progressed()||!construction.blockers().isEmpty())return construction;
   return MayorSupplyController.reconcile(level,id);
  }
  var next=RemoteConstruction.next(level,id);if(next.isEmpty())return new StepResult("Approved buildings completed",List.of(),false);
  var plan=next.orElseThrow();
  // Founding approval already covers these exact anchors, hashes and levels in either mode.
  // Any later expansion/upgrade needs a separate proposal; never infer approval from proximity.
  var candidate=new MayorPolicy.Candidate(plan.operationId(),MayorPolicy.Priority.CONSTRUCTION,true,true,Map.of());
  var decision=MayorPolicy.choose(record.mode(),List.of(candidate),Map.of());
  if(decision.action()!=MayorPolicy.Action.EXECUTE)return blocked(decision.reason());
  return RemoteConstruction.queue(level,id,plan.operationId());
 }
 private static StepResult blocked(String message){return new StepResult("Mayor waiting",List.of(message),false);}
}
