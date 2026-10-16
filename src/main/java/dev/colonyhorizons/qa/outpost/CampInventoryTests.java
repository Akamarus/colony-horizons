// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class CampInventoryTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void custody(GameTestHelper h){var rows=new ArrayList<ProbeResult>();
  SupplyTests.run(rows,"unload_preserves_equipment",()->{var a=new ItemStackHandler(4);var b=new ItemStackHandler(2);a.setStackInSlot(0,new ItemStack(Items.STONE_SHOVEL));a.setStackInSlot(1,new ItemStack(Items.BREAD,8));a.setStackInSlot(2,new ItemStack(Items.DIRT,32));a.setStackInSlot(3,new ItemStack(Items.COBBLESTONE,64));var order=new ArrayList<String>();SupplyTests.check(unload(a,b,true,()->order.add("intent"),()->order.add("save"),()->order.add("receipt")));SupplyTests.check(a.getStackInSlot(3).isEmpty()&&a.getStackInSlot(2).getCount()==32&&a.getStackInSlot(1).getCount()==8&&a.getStackInSlot(0).is(Items.STONE_SHOVEL)&&b.getStackInSlot(0).getCount()==64&&order.equals(List.of("intent","save","receipt")));});
  SupplyTests.run(rows,"excess_dirt_unloaded",()->{var a=new ItemStackHandler(2);var b=new ItemStackHandler(1);a.setStackInSlot(0,new ItemStack(Items.DIRT,64));a.setStackInSlot(1,new ItemStack(Items.DIRT,64));SupplyTests.check(unload(a,b,true,()->{},()->{},()->{}));SupplyTests.check(a.getStackInSlot(0).getCount()+a.getStackInSlot(1).getCount()==64&&b.getStackInSlot(0).getCount()==64);});
  SupplyTests.run(rows,"full_camp_no_mutation",()->{var a=new ItemStackHandler(1);var b=new ItemStackHandler(1);a.setStackInSlot(0,new ItemStack(Items.COBBLESTONE,64));b.setStackInSlot(0,new ItemStack(Items.DIRT,64));SupplyTests.check(!unload(a,b,false,()->{throw new AssertionError("No custody intent for full camp");},()->{},()->{}));SupplyTests.check(a.getStackInSlot(0).getCount()==64&&b.getStackInSlot(0).getCount()==64);});
  SupplyTests.run(rows,"partial_space_conserves_items",()->{var a=new ItemStackHandler(1);var b=new ItemStackHandler(1);a.setStackInSlot(0,new ItemStack(Items.COBBLESTONE,64));b.setStackInSlot(0,new ItemStack(Items.COBBLESTONE,60));SupplyTests.check(unload(a,b,false,()->{},()->{},()->{}));SupplyTests.check(a.getStackInSlot(0).getCount()==60&&b.getStackInSlot(0).getCount()==64);});
  SupplyTests.run(rows,"failed_intent_keeps_inventory",()->{var a=new ItemStackHandler(1);var b=new ItemStackHandler(1);a.setStackInSlot(0,new ItemStack(Items.OAK_LOG,12));reject(()->unload(a,b,false,()->{throw new IllegalStateException("disk");},()->{},()->{}));SupplyTests.check(a.getStackInSlot(0).getCount()==12&&b.getStackInSlot(0).isEmpty());});
  SupplyTests.run(rows,"failed_flush_no_receipt",()->{var a=new ItemStackHandler(1);var b=new ItemStackHandler(1);a.setStackInSlot(0,new ItemStack(Items.OAK_LOG,12));boolean[] pending={false};reject(()->unload(a,b,false,()->pending[0]=true,()->{throw new IllegalStateException("native save");},()->pending[0]=false));SupplyTests.check(pending[0]&&a.getStackInSlot(0).isEmpty()&&b.getStackInSlot(0).getCount()==12);});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"CampInventory",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 private static void reject(Runnable r){try{r.run();throw new AssertionError("Expected interruption");}catch(IllegalStateException expected){}}
 private static boolean unload(IItemHandlerModifiable a,IItemHandlerModifiable b,boolean dirt,Runnable intent,Runnable flush,Runnable receipt){try{return (boolean)Class.forName("dev.colonyhorizons.remote.CampInventoryTransfer").getMethod("unload",IItemHandlerModifiable.class,IItemHandlerModifiable.class,boolean.class,Runnable.class,Runnable.class,Runnable.class).invoke(null,a,b,dirt,intent,flush,receipt);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Camp unloading missing",e);}}
}
