// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Assigns one original unhoused founder to a completed, approved native home per review. */
public final class MayorHousing {
 private MayorHousing(){}
 public static StepResult reconcile(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var record=data.find(id).orElseThrow();
  var owner=level.getServer().getPlayerList().getPlayer(record.owner());
  if(owner==null||owner.isRemoved()||owner instanceof net.neoforged.neoforge.common.util.FakePlayer||owner.serverLevel()!=level||!RemoteTickets.snapshot(level).containsKey(id)||!MayorService.available(level,id))return idle();
  var colony=IColonyManager.getInstance().getColonyByDimension(record.draft().request().colonyId(),level.dimension());
  for(int citizenId:FoundingRoster.citizenIds(data,record.id())){
   var citizen=colony.getCitizenManager().getCivilian(citizenId);
   if(citizen==null||!citizen.getUUID().equals(data.approvedParty(id).get(citizenId))||citizen.getHomeBuilding()!=null)continue;
   for(var plan:RemoteDevelopmentData.get(level).buildings(record.id())){
    if(!plan.role().equals("home")||!FoundingPlacement.isPaidAnchor(level,plan))continue;
    var building=colony.getServerBuildingManager().getBuilding(plan.anchor());
    if(building==null||!building.isBuilt()||building.getBuildingLevel()<1)continue;
    var home=building.getFirstModuleOccurance(LivingBuildingModule.class);
    if(home==null||home.isFull())continue;
    if(home.assignCitizen(citizen)){
     NativeFoundingCheckpoint.save(level,colony);
     return new StepResult("Founder assigned a local home",List.of(),true);
    }
   }
  }
  return idle();
 }
 private static StepResult idle(){return new StepResult("Housing reviewed",List.of(),false);}
}
