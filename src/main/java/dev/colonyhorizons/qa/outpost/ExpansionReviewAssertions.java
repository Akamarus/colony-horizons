package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import java.util.*;

/** Refusal must not consume land, grant authority, or move an unapproved Home worker. */
final class ExpansionReviewAssertions {
 static ExpansionProposal check(GameTestHelper h,RemoteTestEnvironment env,ServerPlayer owner,UUID id,String role)throws Exception {
  var l=h.getLevel();var d=RemoteDevelopmentData.get(l);var failures=new ArrayList<String>();
  var proposed=RemoteExpansion.review(owner,id,role);var original=proposed;
  for(int i=0;i<4;i++){
   RemoteExpansion.decide(owner,id,proposed.plan().operationId(),proposed.revision(),false);
   try{proposed=RemoteExpansion.review(owner,id,role);}catch(IllegalArgumentException exhausted){failures.add("Rejected reviews exhausted unused plots");break;}
   if(!proposed.plan().anchor().equals(original.plan().anchor()))failures.add("Rejection retained spatial reservation");
  }
  if(proposed.state()==ExpansionProposal.State.PROPOSED&&d.expansions(id).get(proposed.plan().operationId()).state()==ExpansionProposal.State.PROPOSED){
   var before=d.save(new CompoundTag(),l.registryAccess());boolean refused=false;
   try{RemoteExpansion.decide(owner,id,original.plan().operationId(),original.revision(),true);}catch(IllegalArgumentException expected){refused=true;}
   if(!refused||!before.equals(d.save(new CompoundTag(),l.registryAccess())))failures.add("Old approval authorized replacement review");
   var field=RemoteLimits.class.getDeclaredField("DEVELOPING");field.setAccessible(true);var cap=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)field.get(null);int previous=cap.get();
   before=d.save(new CompoundTag(),l.registryAccess());refused=false;
   try{cap.set(0);try{RemoteExpansion.decide(owner,id,proposed.plan().operationId(),proposed.revision(),true);}catch(IllegalArgumentException expected){refused=true;}if(!refused||!before.equals(d.save(new CompoundTag(),l.registryAccess())))failures.add("Failed phase approval retained executable authority");}finally{cap.set(previous);}
   RemoteExpansion.tick(l,id);if(!before.equals(d.save(new CompoundTag(),l.registryAccess()))||env.home.colony().getServerBuildingManager().getBuilding(proposed.plan().anchor())!=null)failures.add("Refused expansion tick changed authority or placed hut");
  }
  var outsider=env.home.createCitizen();outsider.setPaused(true);
  var remote=d.find(id).orElseThrow().draft().buildings().stream().filter(p->p.role().equals("lumberjack")).findFirst().orElseThrow();var hut=env.home.colony().getServerBuildingManager().getBuilding(remote.anchor());
  var module=hut.getModule(BuildingModules.FORESTER_WORK);
  if(FoundingReservation.permits(outsider,module))failures.add("Remote hut accepts unapproved Home citizen");
  h.assertTrue(failures.isEmpty(),"Expansion review safety: "+failures);
  return proposed;
 }
}
