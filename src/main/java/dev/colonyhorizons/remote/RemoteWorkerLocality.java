// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.api.colony.ICitizenData;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
/** Routine activity boundaries for the original, arrived remote residents. */
public final class RemoteWorkerLocality {
 private RemoteWorkerLocality(){}
 public static Optional<Development> settlement(ICitizenData citizen){
  if(citizen==null||!(citizen.getColony().getWorld() instanceof ServerLevel level))return Optional.empty();
  var existing=RemoteDevelopmentData.existing(level);if(existing.isEmpty())return Optional.empty();var data=existing.orElseThrow();
  return data.all().stream().filter(r->data.foundingManaged(r.id())&&r.phase()!=Phase.PROPOSED&&r.phase()!=Phase.CANCELLED
   &&r.draft().request().colonyId()==citizen.getColony().getID()&&citizen.getUUID().equals(data.approvedParty(r.id()).get(citizen.getId()))
   &&data.journey(r.id()).filter(j->j.state()==FoundingTravel.State.ARRIVED).isPresent()).findFirst();
 }
 public static boolean permits(ICitizenData citizen,BlockPos target){return settlement(citizen).map(r->target!=null&&r.draft().chunks().contains(new ChunkPos(target))).orElse(true);}
 public static boolean permitsWork(com.minecolonies.api.colony.buildings.IBuilding building,BlockPos target){
  if(building==null||!(building.getColony().getWorld() instanceof ServerLevel level))return true;
  var existing=RemoteDevelopmentData.existing(level);if(existing.isEmpty())return true;var data=existing.orElseThrow();
  UUID origin=null,destination=null;
  for(var r:data.all())if(data.foundingManaged(r.id())&&r.phase()!=Phase.PROPOSED&&(r.phase()!=Phase.CANCELLED||(data.grant(r.id()).filter(FoundingGrantJournal::consumed).isPresent()||!data.anchorReceipts(r.id()).isEmpty()))&&r.draft().request().colonyId()==building.getColony().getID()){
   if(r.draft().chunks().contains(new ChunkPos(building.getPosition())))origin=r.id();
   if(target!=null&&r.draft().chunks().contains(new ChunkPos(target)))destination=r.id();
  }
  return Objects.equals(origin,destination);
 } public static boolean permitsHome(ICitizenData citizen,com.minecolonies.api.colony.buildings.IBuilding building){
  if(citizen==null||building==null||!(building.getColony().getWorld() instanceof ServerLevel level))return true;
  var local=settlement(citizen);
  if(local.isPresent())return citizen.getColony()==building.getColony()&&RemoteDevelopmentData.get(level).buildings(local.orElseThrow().id()).stream().anyMatch(p->p.role().equals("home")&&p.anchor().equals(building.getPosition()));
  var existing=RemoteDevelopmentData.existing(level);if(existing.isEmpty())return true;var data=existing.orElseThrow();
  return data.all().stream().noneMatch(r->data.foundingManaged(r.id())&&r.phase()!=Phase.PROPOSED&&(r.phase()!=Phase.CANCELLED||(data.grant(r.id()).filter(FoundingGrantJournal::consumed).isPresent()||!data.anchorReceipts(r.id()).isEmpty()))&&r.draft().request().colonyId()==building.getColony().getID()&&r.draft().chunks().contains(new ChunkPos(building.getPosition())));
 }}
