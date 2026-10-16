package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class FreeFounderPolicyTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void policy(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();var env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(),Map.of()),0);var id=env.draft.id();var manager=env.home.colony().getCitizenManager();
  SupplyTests.run(rows,"housing_not_required",()->{manager.setMaxCitizens(1);SupplyTests.check(FoundingGrantService.review(l,id).blockers().isEmpty());});
  SupplyTests.run(rows,"server_cap",()->{var cap=com.minecolonies.core.MineColonies.getConfig().getServer().maxCitizenPerColony;int old=cap.get();try{cap.set(3);SupplyTests.check(!FoundingGrantService.review(l,id).blockers().isEmpty()&&manager.getCurrentCitizenCount()==0);}finally{cap.set(old);}});
  SupplyTests.run(rows,"reservation_without_creation",()->{SupplyTests.check(FoundingGrantService.reserve(env.owner,id,0).blockers().isEmpty()&&manager.getCurrentCitizenCount()==0);});
  SupplyTests.run(rows,"duplicate_reservation",()->{FoundingGrantService.reserve(env.owner,id,0);SupplyTests.check(manager.getCurrentCitizenCount()==0&&RemoteDevelopmentData.get(l).grant(id).orElseThrow().receipts().isEmpty());});
  SupplyTests.run(rows,"two_pending_reservations",()->{var data=RemoteDevelopmentData.get(l);var d=env.draft;var second=UUID.randomUUID();data.create(new Development(second,env.owner.getUUID(),new SiteDraft(second,d.request(),d.chunks(),d.buildings(),d.fingerprint(),d.blockers()),Mode.APPROVAL,Phase.PROPOSED,0));var cap=com.minecolonies.core.MineColonies.getConfig().getServer().maxCitizenPerColony;int old=cap.get();try{cap.set(7);SupplyTests.check(!FoundingGrantService.review(l,second).blockers().isEmpty()&&manager.getCurrentCitizenCount()==0);}finally{cap.set(old);data.discardUnapproved(second);}});
  SupplyTests.run(rows,"concurrent_immigration",()->{var cap=com.minecolonies.core.MineColonies.getConfig().getServer().maxCitizenPerColony;int old=cap.get();try{cap.set(4);manager.createAndRegisterCivilianData();SupplyTests.check(!FoundingGrantService.review(l,id).blockers().isEmpty()&&manager.getCurrentCitizenCount()==1);}finally{cap.set(old);}});
  SupplyTests.run(rows,"research_cap",()->{int maximum=(int)manager.maxCitizensFromResearch();for(int i=manager.getCurrentCitizenCount();i<maximum-3;i++)manager.createAndRegisterCivilianData();SupplyTests.check(!FoundingGrantService.review(l,id).blockers().isEmpty());});
  SupplyTests.run(rows,"unknown_receipt_id_rejected",()->{var j=new FoundingGrantJournal(FoundingGrantJournal.State.RESERVED,List.of(),Optional.empty(),false,Set.of()).begin();var t=j.save();var members=new ListTag();var m=new CompoundTag();m.putString("role","MAYOR");m.putInt("citizen",0);m.putUUID("entity",UUID.randomUUID());members.add(m);t.put("receipts",members);SupplyTests.reject(()->FoundingGrantJournal.load(t));});
  SupplyTests.run(rows,"interrupted_creation_quarantined",()->{var data=RemoteDevelopmentData.get(l);var t=data.save(new CompoundTag(),l.registryAccess());var dev=t.getList("developments",10).getCompound(0);dev.putString("phase","STAGING");dev.put("founderGrant",data.grant(id).orElseThrow().begin().save());var restored=RemoteDevelopmentData.load(t,l.registryAccess(),l.dimension());SupplyTests.check(restored.find(id).orElseThrow().phase()==Phase.RECOVERY_NEEDED&&restored.grant(id).orElseThrow().state()==FoundingGrantJournal.State.RECOVERY_REQUIRED&&restored.grant(id).orElseThrow().consumed());});
  SupplyTests.run(rows,"return_journal_strict_types",()->{var t=new FoundingGrantJournal(FoundingGrantJournal.State.RESERVED,List.of(),Optional.empty(),false,Set.of()).save();t.putString("returnTargets","invalid");SupplyTests.reject(()->FoundingGrantJournal.load(t));});
  SupplyTests.run(rows,"return_cursor_requires_targets",()->{var t=new FoundingGrantJournal(FoundingGrantJournal.State.RESERVED,List.of(),Optional.empty(),false,Set.of()).save();t.putInt("returned",0);SupplyTests.reject(()->FoundingGrantJournal.load(t));});
  SupplyTests.run(rows,"ambiguous_grant_does_not_block_home",()->{var data=RemoteDevelopmentData.get(l);try{FreeFounderRestartTests.put(data,id,data.grant(id).orElseThrow().begin());}catch(Exception e){throw new RuntimeException(e);}var immigrant=manager.createAndRegisterCivilianData();SupplyTests.check(!FoundingTravel.blocksSpawn(immigrant));});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FreeFounderPolicy",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }catch(Throwable e){h.fail(e.toString());}});}
}
