package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.mayor.MayorPolicy;
import dev.colonyhorizons.remote.RemoteModels.*;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.colonyhorizons.qa.outpost.SupplyTests.*;
@PrefixGameTestTemplate(false)
public final class MayorPolicyTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
 public static void decisions(GameTestHelper h)throws Exception{
  var rows=new ArrayList<ProbeResult>();var bread=new ItemKey(new ItemStack(Items.BREAD));
  run(rows,"reserve_math",()->{eq(MayorPolicy.spendable(40,16,0),24);eq(MayorPolicy.spendable(40,16,8),16);eq(MayorPolicy.spendable(8,16,8),0);});
  run(rows,"incoming_demand",()->{eq(MayorPolicy.unmet(64,32,16),16);eq(MayorPolicy.unmet(8,32,16),0);reject(()->MayorPolicy.unmet(64,-1,0));});
  run(rows,"approval_proposes_once_identity",()->{var c=candidate(MayorPolicy.Priority.CONSTRUCTION,false,true,bread,8);var a=MayorPolicy.choose(Mode.APPROVAL,List.of(c),Map.of(bread,16));check(a.action()==MayorPolicy.Action.PROPOSE&&a.operation().equals(c.operation()));check(a.equals(MayorPolicy.choose(Mode.APPROVAL,List.of(c),Map.of(bread,16))));});
  run(rows,"automatic_scope_and_budget",()->{var c=candidate(MayorPolicy.Priority.CONSTRUCTION,false,true,bread,8);check(MayorPolicy.choose(Mode.AUTOMATIC,List.of(c),Map.of(bread,8)).action()==MayorPolicy.Action.EXECUTE);check(MayorPolicy.choose(Mode.AUTOMATIC,List.of(c),Map.of(bread,7)).action()==MayorPolicy.Action.BLOCKED);var outside=candidate(MayorPolicy.Priority.UPGRADE,true,false,bread,1);check(MayorPolicy.choose(Mode.AUTOMATIC,List.of(outside),Map.of(bread,8)).action()==MayorPolicy.Action.BLOCKED);});
  run(rows,"priority_operating_before_expansion",()->{var candidates=new ArrayList<MayorPolicy.Candidate>();for(var priority:MayorPolicy.Priority.values())candidates.add(candidate(priority,true,true,bread,8));Collections.reverse(candidates);check(MayorPolicy.choose(Mode.APPROVAL,candidates,Map.of(bread,8)).operation().equals(candidates.getLast().operation()));check(MayorPolicy.choose(Mode.APPROVAL,candidates,Map.of(bread,0)).operation().equals(candidates.getLast().operation()));});
  run(rows,"components_and_accepted_construction",()->{var named=new ItemStack(Items.BREAD);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Special"));var c=candidate(MayorPolicy.Priority.CONSTRUCTION,true,true,new ItemKey(named),8);check(MayorPolicy.choose(Mode.APPROVAL,List.of(c),Map.of(bread,64)).action()==MayorPolicy.Action.BLOCKED);check(MayorPolicy.choose(Mode.APPROVAL,List.of(c),Map.of(new ItemKey(named),8)).action()==MayorPolicy.Action.EXECUTE);});
  run(rows,"empty_and_invalid_candidates",()->{check(MayorPolicy.choose(Mode.AUTOMATIC,List.of(),Map.of()).action()==MayorPolicy.Action.NONE);reject(()->candidate(MayorPolicy.Priority.CONSTRUCTION,true,true,bread,-1));var c=candidate(MayorPolicy.Priority.CONSTRUCTION,true,true,bread,1);reject(()->MayorPolicy.choose(Mode.AUTOMATIC,List.of(c,c),Map.of(bread,1)));});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"MayorPolicy",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Mayor policy: "+rows);h.succeed();
 }
 private static MayorPolicy.Candidate candidate(MayorPolicy.Priority p,boolean approved,boolean scope,ItemKey item,int count){return new MayorPolicy.Candidate(UUID.randomUUID(),p,approved,scope,Map.of(item,count));}
}
