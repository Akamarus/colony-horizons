package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.mayor.MayorProposals;
import dev.colonyhorizons.remote.RemoteModels.ItemKey;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
import static dev.colonyhorizons.qa.outpost.SupplyTests.*;
@PrefixGameTestTemplate(false)
public final class MayorProposalTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
 public static void approvals(GameTestHelper h)throws Exception {
  var rows=new ArrayList<ProbeResult>();var owner=UUID.randomUUID();var settlement=UUID.randomUUID();var building=UUID.randomUUID();var item=new ItemKey(new ItemStack(Items.OAK_PLANKS));var ledger=new MayorProposals();
  var proposal=ledger.propose(settlement,owner,building,1,"builder2.blueprint","a".repeat(64),Map.of(item,20));
  run(rows,"proposal_no_implicit_approval",()->check(proposal.state()==MayorProposals.State.PROPOSED&&proposal.target()==2));
  run(rows,"proposal_stable_identity",()->check(ledger.propose(settlement,owner,building,1,"builder2.blueprint","a".repeat(64),Map.of(item,20)).equals(proposal)&&ledger.entries().size()==1));
  run(rows,"proposal_changed_review_rejected",()->reject(()->ledger.propose(settlement,owner,building,1,"builder2.blueprint","b".repeat(64),Map.of(item,20))));
  run(rows,"proposal_owner_and_revision",()->{reject(()->ledger.decide(UUID.randomUUID(),proposal.id(),0,true));reject(()->ledger.decide(owner,proposal.id(),99,true));check(ledger.entries().get(proposal.id()).state()==MayorProposals.State.PROPOSED);});
  run(rows,"proposal_explicit_approval",()->{ledger.decide(owner,proposal.id(),0,true);check(ledger.entries().get(proposal.id()).state()==MayorProposals.State.APPROVED);reject(()->ledger.decide(owner,proposal.id(),0,true));});
  run(rows,"proposal_denial_persists",()->{var b=UUID.randomUUID();var p=ledger.propose(settlement,owner,b,1,"home2.blueprint","a".repeat(64),Map.of(item,4));ledger.decide(owner,p.id(),0,false);check(ledger.propose(settlement,owner,b,1,"home2.blueprint","a".repeat(64),Map.of(item,4)).state()==MayorProposals.State.REJECTED);});
  run(rows,"proposal_reload",()->check(MayorProposals.load(ledger.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess()).entries().equals(ledger.entries())));
  run(rows,"proposal_invalid_bounds",()->{reject(()->ledger.propose(settlement,owner,UUID.randomUUID(),5,"builder6.blueprint","a".repeat(64),Map.of(item,1)));reject(()->ledger.propose(settlement,owner,UUID.randomUUID(),1,"builder2.blueprint","a".repeat(64),Map.of(item,-1)));});
  run(rows,"proposal_corrupt_reload",()->{
   var saved=ledger.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
   for(var field:List.of("cost","revision","id")){var bad=saved.copy();bad.getList("proposals",10).getCompound(0).remove(field);reject(()->MayorProposals.load(bad,h.getLevel().registryAccess()));}
   var wrongId=saved.copy();wrongId.getList("proposals",10).getCompound(0).putUUID("id",UUID.randomUUID());reject(()->MayorProposals.load(wrongId,h.getLevel().registryAccess()));
   var bad=saved.copy();bad.getList("proposals",10).getCompound(0).putLong("revision",0);var invalid=bad;reject(()->MayorProposals.load(invalid,h.getLevel().registryAccess()));
  });
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"MayorProposals",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
