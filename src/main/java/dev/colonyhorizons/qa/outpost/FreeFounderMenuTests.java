package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.menu.RemoteMenuService;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class FreeFounderMenuTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void menus(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();var env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(),Map.of()),0);
  var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","craftsmanship/storage/warehouse1.blueprint",l.registryAccess());com.minecolonies.api.blocks.ModBlocks.blockHutWareHouse.setup(env.owner,l,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","craftsmanship/storage/warehouse1.blueprint");
  var form=new CompoundTag();form.putString("name","Free Settlers");form.putLong("center",env.draft.request().center().asLong());form.putLong("home",env.home.center().asLong());form.putInt("cap",1);form.put("budget",new ListTag());int colony=env.home.colony().getID();
  SupplyTests.run(rows,"no_picker_required",()->{var draft=RemoteMenuService.review(env.owner,colony,form);SupplyTests.check(draft.request().citizenIds().isEmpty()&&FoundingRoster.free(draft.request())&&draft.buildings().size()==8&&draft.preparation().isPresent());});
  SupplyTests.run(rows,"free_four_copy",()->SupplyTests.check(RemoteMenuService.reviewView(env.owner,env.draft).getString("founders").equals("4 free settlers: Mayor, Builder, Courier, Forester.")));
  SupplyTests.run(rows,"planned_vs_arrived",()->{var summary=RemoteMenuService.mayor(env.owner,colony,env.draft.id());SupplyTests.check(summary.getInt("plannedPeople")==4&&summary.getInt("arrivedPeople")==0);});
  SupplyTests.run(rows,"cap_blocker_readable",()->{var cap=com.minecolonies.core.MineColonies.getConfig().getServer().maxCitizenPerColony;int old=cap.get();try{cap.set(3);var draft=RemoteMenuService.review(env.owner,colony,form);SupplyTests.check(draft.blockers().stream().anyMatch(s->s.contains("population")));}finally{cap.set(old);}});
  SupplyTests.run(rows,"stale_approval_inert",()->{var data=RemoteDevelopmentData.get(l);SupplyTests.check(!RemoteFoundingService.approve(env.owner,env.draft.id(),99).blockers().isEmpty()&&data.grant(env.draft.id()).isEmpty()&&data.find(env.draft.id()).orElseThrow().revision()==0);});
  SupplyTests.run(rows,"foreign_owner_rejected",()->{var stranger=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Visitor"));SupplyTests.reject(()->RemoteMenuService.review(stranger,colony,form));});
  SupplyTests.run(rows,"forged_roster_rejected",()->{var forged=form.copy();forged.putIntArray("citizens",new int[]{1,2,3,4});SupplyTests.reject(()->RemoteMenuService.review(env.owner,colony,forged));var invalid=form.copy();invalid.putInt("mayor",1);SupplyTests.reject(()->RemoteMenuService.review(env.owner,colony,invalid));});
  SupplyTests.run(rows,"legacy_proposal_still_readable",()->{var data=RemoteDevelopmentData.get(l);var legacy=RemoteRecordTests.sample(env.owner.getUUID(),2048);var q=legacy.draft().request();var corrected=new SiteRequest(colony,q.name(),q.center(),q.allowedArea(),q.homeSupply(),q.citizenIds(),q.mayorId(),q.maxBuildingLevel(),q.budget());var d=legacy.draft();data.create(new Development(d.id(),env.owner.getUUID(),new SiteDraft(d.id(),corrected,d.chunks(),d.buildings(),d.fingerprint(),d.blockers()),Mode.APPROVAL,Phase.PROPOSED,0));SupplyTests.check(RemoteMenuService.mayor(env.owner,colony,d.id()).getInt("plannedPeople")==4);});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FreeFounderMenu",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }catch(Throwable e){h.fail(e.toString());}});}
}
