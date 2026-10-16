package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class WarehouseBootstrapTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void finite_bootstrap_bill(GameTestHelper h){h.runAfterDelay(40,()->{
  var rows=new ArrayList<ProbeResult>();try{
   var env=new RemoteTestEnvironment(h.getLevel(),1024,new Budget(Map.of(),Map.of()),5);
   h.assertTrue(FoundingTravel.review(env.owner,env.draft.id()).size()==5,"Five original founders accepted");
   var bill=RemoteFoundingService.bootstrapBill(h.getLevel(),env.draft.id());
   h.assertTrue(bill.getOrDefault(new ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD)),0)>=84,"Founders plus finite bootstrap food reserve supplied");
   h.assertTrue(bill.getOrDefault(new ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPRUCE_PLANKS)),0)>64,"Paid construction kit includes logistics buildings");
   h.assertTrue(bill.getOrDefault(new ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_HOE)),0)==2,"Native foresters require a hoe as well as an axe");
   h.assertTrue(bill.keySet().stream().anyMatch(k->k.stack().is(com.minecolonies.api.blocks.ModBlocks.blockRack.asItem())),"Warehouse rack materials included before approval");
   h.assertTrue(RemoteConstruction.next(h.getLevel(),env.draft.id()).orElseThrow().role().equals("builder"),"Native builder dependency first");
   var saved=RemoteDevelopmentData.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());var restored=RemoteDevelopmentData.load(saved,h.getLevel().registryAccess(),h.getLevel().dimension());h.assertTrue(restored.find(env.draft.id()).orElseThrow().draft().buildings().equals(env.draft.buildings()),"Warehouse plan survives reload");
   rows.add(new ProbeResult("warehouse_bootstrap_bill",ProbeResult.Outcome.PASS,"Five originals and finite logistics material bill"));
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"WarehouseBootstrap",rows);h.succeed();
  }catch(Throwable e){try{rows.add(new ProbeResult("warehouse_bootstrap_bill",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"WarehouseBootstrap",rows);}catch(Exception ignored){}h.fail(e.toString());}
 });}
}
