package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.supply.*;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.tileentities.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class WarehouseInventoryTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1600)
 public static void native_rack_inventory(GameTestHelper h){h.runAfterDelay(40,()->{
  var rows=new ArrayList<ProbeResult>();try{
   var l=h.getLevel();var f=OutpostProbeFixture.create(l,new BlockPos(0,-59,0),UUID.randomUUID());
   var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(f.ownerId(),"WarehouseOwner"));
   var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","craftsmanship/storage/warehouse1.blueprint",l.registryAccess());
   ModBlocks.blockHutWareHouse.setup(actor,l,f.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","craftsmanship/storage/warehouse1.blueprint");
   var hut=(TileEntityColonyBuilding)l.getBlockEntity(f.center());var building=f.colony().getServerBuildingManager().getBuilding(f.center());
   var pos=f.center().offset(2,0,0);l.setBlockAndUpdate(pos,ModBlocks.blockRack.defaultBlockState());building.registerBlockPosition(l.getBlockState(pos),pos,l);
   var rack=(TileEntityRack)l.getBlockEntity(pos);var named=new ItemStack(Items.BREAD,23);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Finite warehouse stock"));rack.getInventory().setStackInSlot(0,named);
   var view=WarehouseInventory.resolve(l,hut);h.assertTrue(view.entities().contains(rack),"Native registered rack included");
   var batch=new SupplyInventoryBatch(Map.of(f.ownerId(),view.inventory()));var taken=batch.extract(f.ownerId(),named,7);h.assertTrue(taken.getCount()==7,"Rack extraction amount");batch.apply();
   h.assertTrue(rack.getInventory().getStackInSlot(0).getCount()==16&&ItemStack.isSameItemSameComponents(taken,named),"Finite component preservation");
   view.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);NativeSupplyCheckpoint.flushAndVerify(l,view.entities());
   l.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());boolean rejected=false;try{view.validate(l);}catch(IllegalStateException expected){rejected=true;}h.assertTrue(rejected,"Replaced rack invalidates staged inventory");
   var service=NativeSupplyService.get(l);service.bind(f.ownerId(),hut.getBlockPos(),"Warehouse",false);
   var unloaded=new BlockPos(24000,-59,24000);h.assertTrue(!l.hasChunkAt(unloaded),"Rack fixture starts unloaded");building.addContainerPosition(unloaded);
   service.tick(20);h.assertTrue(service.problem().isEmpty(),"Unloaded rack is retryable, not a custody failure");
   building.removeContainerPosition(unloaded);service.tick(20);h.assertTrue(service.problem().isEmpty(),"Endpoint resumes after unavailable rack removed");
   rows.add(new ProbeResult("warehouse_rack_custody",ProbeResult.Outcome.PASS,"Native rack stock, components, checkpoint and replacement guard"));
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"WarehouseInventory",rows);h.succeed();
  }catch(Throwable e){try{rows.add(new ProbeResult("warehouse_rack_custody",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"WarehouseInventory",rows);}catch(Exception ignored){}h.fail(e.toString());}
 });}
}
