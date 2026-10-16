package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.mayor.MayorReservations;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
import static dev.colonyhorizons.qa.outpost.SupplyTests.*;
@PrefixGameTestTemplate(false)
public final class MayorReservationTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
 public static void reservations(GameTestHelper h)throws Exception{
  var rows=new ArrayList<ProbeResult>();var key=new ItemKey(new ItemStack(Items.BREAD));var owner=UUID.randomUUID();var first=UUID.randomUUID();var second=UUID.randomUUID();var budget=new Budget(Map.of(key,24),Map.of(key,16));var home=BlockPos.ZERO;
  run(rows,"shared_home_reserve",()->{var n=new MayorReservations();check(n.reserve(owner,first,home,UUID.randomUUID(),budget,Map.of(),Map.of(key,40),Map.of(key,8)));check(!n.reserve(owner,second,home,UUID.randomUUID(),budget,Map.of(),Map.of(key,40),Map.of(key,17)));check(n.reserve(owner,second,home,UUID.randomUUID(),budget,Map.of(),Map.of(key,40),Map.of(key,16)));});
  run(rows,"stable_reservation",()->{var n=new MayorReservations();var op=UUID.randomUUID();check(n.reserve(owner,first,home,op,budget,Map.of(),Map.of(key,40),Map.of(key,8)));check(n.reserve(owner,first,home,op,budget,Map.of(),Map.of(key,0),Map.of(key,8)));eq(n.entries().size(),1);reject(()->n.reserve(owner,second,home,op,budget,Map.of(),Map.of(key,40),Map.of(key,8)));});
  run(rows,"spent_not_double_counted",()->{var n=new MayorReservations();var op=UUID.randomUUID();check(n.reserve(owner,first,home,op,budget,Map.of(),Map.of(key,40),Map.of(key,8)));n.recordDispatched(op,Map.of(key,8));check(n.reserve(owner,second,home,UUID.randomUUID(),budget,Map.of(),Map.of(key,32),Map.of(key,16)));});
  run(rows,"cancel_releases_only_unspent",()->{var n=new MayorReservations();var op=UUID.randomUUID();check(n.reserve(owner,first,home,op,budget,Map.of(),Map.of(key,40),Map.of(key,20)));n.recordDispatched(op,Map.of(key,8));n.close(op);check(n.reserve(owner,first,home,UUID.randomUUID(),budget,Map.of(),Map.of(key,32),Map.of(key,16)));check(!n.reserve(owner,first,home,UUID.randomUUID(),budget,Map.of(),Map.of(key,64),Map.of(key,1)));reject(()->n.recordDispatched(op,Map.of(key,9)));});
  run(rows,"founding_already_charged",()->{var n=new MayorReservations();check(!n.reserve(owner,first,home,UUID.randomUUID(),budget,Map.of(key,16),Map.of(key,64),Map.of(key,9)));check(n.reserve(owner,first,home,UUID.randomUUID(),budget,Map.of(key,16),Map.of(key,64),Map.of(key,8)));});
  run(rows,"reservation_components",()->{var named=new ItemStack(Items.BREAD);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Named"));var k=new ItemKey(named);var n=new MayorReservations();check(!n.reserve(owner,first,home,UUID.randomUUID(),new Budget(Map.of(k,8),Map.of()),Map.of(),Map.of(key,64),Map.of(k,8)));});
  run(rows,"reservation_reload",()->{var n=new MayorReservations();var op=UUID.randomUUID();check(n.reserve(owner,first,home,op,budget,Map.of(),Map.of(key,40),Map.of(key,20)));n.recordDispatched(op,Map.of(key,8));var restored=MayorReservations.load(n.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());eq(restored.entries().get(op).dispatched().get(key),8);check(!restored.reserve(owner,second,home,UUID.randomUUID(),budget,Map.of(),Map.of(key,32),Map.of(key,5)));});
  run(rows,"reservation_corrupt_rejected",()->{var n=new MayorReservations();var op=UUID.randomUUID();check(n.reserve(owner,first,home,op,budget,Map.of(),Map.of(key,40),Map.of(key,8)));reject(()->n.recordDispatched(op,Map.of(key,9)));n.recordDispatched(op,Map.of(key,4));reject(()->n.recordDispatched(op,Map.of(key,3)));var tag=n.save(h.getLevel().registryAccess());tag.getList("entries",10).getCompound(0).remove("cost");reject(()->MayorReservations.load(tag,h.getLevel().registryAccess()));});
  run(rows,"reservation_server_checkpoint",()->{try{var n=MayorReservations.get(h.getLevel());var op=UUID.randomUUID();check(n.reserve(owner,first,home,op,budget,Map.of(),Map.of(key,40),Map.of(key,8)));n.checkpoint(h.getLevel());var file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(h.getLevel().dimension(),h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/colonyhorizons_mayor_reservations.dat");var tag=net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data");check(MayorReservations.load(tag,h.getLevel().registryAccess()).entries().get(op).cost().equals(Map.of(key,8)));}catch(Exception e){throw new RuntimeException(e);}});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"MayorReservations",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Reservations: "+rows);h.succeed();
 }
}
