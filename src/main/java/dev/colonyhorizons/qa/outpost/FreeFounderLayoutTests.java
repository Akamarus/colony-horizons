package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.integration.ArcologyCatalog;
import com.ldtteam.structurize.storage.StructurePacks;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class FreeFounderLayoutTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void layout(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var pack=StructurePacks.getStructurePack("Arcology Horizons");if(pack==null)pack=StructurePacks.getStructurePack("Arcology");var catalog=ArcologyCatalog.inspect(pack.getPath(),h.getLevel().registryAccess());
  var tag=FreeFounderRecordTests.seed(h,true,4);var data=RemoteDevelopmentData.load(tag,h.getLevel().registryAccess(),Level.OVERWORLD);var record=data.all().getFirst();
  SupplyTests.run(rows,"eight_huts",()->SupplyTests.check(ArcologySettlementLayout.plan(record.id(),record.draft().request(),catalog).size()==8));
  SupplyTests.run(rows,"four_homes_one_forester",()->{var p=ArcologySettlementLayout.plan(record.id(),record.draft().request(),catalog);SupplyTests.check(p.stream().filter(b->b.role().equals("home")).count()==4&&p.stream().filter(b->b.role().equals("lumberjack")).count()==1);});
  SupplyTests.run(rows,"builder_warehouse_courier_order",()->{var p=ArcologySettlementLayout.plan(record.id(),record.draft().request(),catalog);SupplyTests.check(p.subList(0,3).stream().map(BuildingPlan::role).toList().equals(List.of("builder","warehouse","courier")));});
  for(int n:List.of(4,5))SupplyTests.run(rows,n==4?"legacy_four_no_courier":"legacy_five_two_foresters",()->{var d=RemoteDevelopmentData.load(FreeFounderRecordTests.seed(h,false,n),h.getLevel().registryAccess(),Level.OVERWORLD).all().getFirst();var p=ArcologySettlementLayout.plan(d.id(),d.draft().request(),catalog);SupplyTests.check(p.stream().filter(b->b.role().equals("lumberjack")).count()==2&&p.stream().filter(b->b.role().equals("courier")).count()==(n==5?1:0));});
  SupplyTests.run(rows,"roster_order_independent",()->{var t=tag.copy();var list=FreeFounderRecordTests.roster();var courier=list.getCompound(2).getUUID("entity");var shuffled=new ListTag();shuffled.add(list.get(3));shuffled.add(list.get(2));shuffled.add(list.get(0));shuffled.add(list.get(1));FreeFounderRecordTests.entry(t).put("founderRoster",shuffled);var d=RemoteDevelopmentData.load(t,h.getLevel().registryAccess(),Level.OVERWORLD);SupplyTests.check(FoundingRoster.member(d,record.id(),FoundingRoster.Role.COURIER).orElseThrow().entityId().equals(courier));});
  SupplyTests.run(rows,"incomplete_roster_no_authority",()->{SupplyTests.check(FoundingRoster.resolve(data,record.id()).isEmpty()&&data.approvedParty(record.id()).isEmpty());var t=tag.copy();var list=FreeFounderRecordTests.roster();list.removeLast();FreeFounderRecordTests.entry(t).put("founderRoster",list);SupplyTests.reject(()->FreeFounderRecordTests.round(h,t));});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FreeFounderLayout",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }catch(Throwable e){h.fail(e.toString());}});}
}
