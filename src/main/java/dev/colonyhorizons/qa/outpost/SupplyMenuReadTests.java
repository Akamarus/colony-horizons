package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.supply.*;
import dev.colonyhorizons.menu.*;
import java.util.*;
import java.nio.file.Path;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.core.BlockPos;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class SupplyMenuReadTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=400)
 public static void menu_read(GameTestHelper h)throws Exception {
  var rows=new ArrayList<ProbeResult>();var f=new SupplyTests.Fixture();var id=f.route(f.home,f.outpost,Items.BREAD,64,3,8,10);
  SupplyTests.run(rows,"empty_route_settings",()->{var s=f.n.settings(id);SupplyTests.check(s.item().is(Items.BREAD)&&f.n.route(id).cargo().isEmpty());SupplyTests.eq(s.target(),64);SupplyTests.eq(s.reserve(),3);SupplyTests.eq(s.batch(),8);});
  SupplyTests.run(rows,"settings_copy",()->{f.n.settings(id).item().setCount(55);SupplyTests.eq(f.n.settings(id).item().getCount(),1);});
  SupplyTests.run(rows,"settings_reload",()->{f.n.pause(f.owner,id,true);var n=SupplyNetwork.load(f.n.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());SupplyTests.check(n.settings(id).paused());n.cancel(f.owner,id);n=SupplyNetwork.load(n.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());SupplyTests.check(n.settings(id).disabled()&&!n.settings(id).paused());});
  var fixture=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
  var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MenuStranger"));
  SupplyTests.run(rows,"owner_only_snapshot",()->SupplyTests.reject(()->SupplyMenuService.snapshot(stranger,fixture.colony().getID(),0,0,0)));
  SupplyTests.run(rows,"paged_snapshot",()->{var p=SupplyMenuModels.page(java.util.stream.IntStream.range(0,17).boxed().toList(),0);SupplyTests.eq(p.rows().size(),16);SupplyTests.check(p.hasNext());SupplyTests.eq(SupplyMenuModels.page(java.util.stream.IntStream.range(0,17).boxed().toList(),1).rows().getFirst(),16);SupplyTests.reject(()->SupplyMenuModels.page(List.of(),-1));});
  SupplyTests.run(rows,"unloaded_snapshot",()->{var pos=new BlockPos(1000000,64,1000000);SupplyTests.check(!h.getLevel().hasChunkAt(pos));SupplyTests.check(!SupplyMenuService.loaded(h.getLevel(),pos));SupplyTests.check(!h.getLevel().hasChunkAt(pos));});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"MenuRead",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
