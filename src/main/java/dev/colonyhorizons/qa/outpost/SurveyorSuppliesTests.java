// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorSuppliesTests {
 private static SurveyResource target(){return SurveyResourceCatalog.find(ResourceLocation.withDefaultNamespace("seagrass")).orElseThrow();}
 private static ItemStackHandler stock(){var inv=new ItemStackHandler(3);inv.setStackInSlot(0,new ItemStack(Items.BREAD,10));var shears=new ItemStack(Items.SHEARS);shears.setDamageValue(7);inv.setStackInSlot(1,shears);return inv;}
 private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException|IllegalStateException e){return;}throw new AssertionError("Invalid withdrawal accepted");}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void supplies(GameTestHelper h){var rows=new ArrayList<ProbeResult>();var registries=h.getLevel().registryAccess();
  SupplyTests.run(rows,"finite_rations_and_tool",()->{var inv=stock();var plan=SurveySupplies.prepare(inv,target());SupplyTests.check(inv.getStackInSlot(0).getCount()==10&&!inv.getStackInSlot(1).isEmpty());var saved=new CompoundTag[1];var cargo=plan.commit(registries,t->saved[0]=t,()->{});SupplyTests.check(inv.getStackInSlot(0).getCount()==2&&inv.getStackInSlot(1).isEmpty()&&cargo.paid()&&cargo.tool().getDamageValue()==7&&!saved[0].contains("pending"));reject(()->plan.commit(registries,t->{},()->{}));});
  SupplyTests.run(rows,"shortage_no_mutation",()->{var inv=stock();inv.setStackInSlot(0,new ItemStack(Items.BREAD,7));reject(()->SurveySupplies.prepare(inv,target()));SupplyTests.check(inv.getStackInSlot(0).getCount()==7&&!inv.getStackInSlot(1).isEmpty());});
  SupplyTests.run(rows,"missing_tool_no_mutation",()->{var inv=stock();inv.setStackInSlot(1,ItemStack.EMPTY);reject(()->SurveySupplies.prepare(inv,target()));SupplyTests.check(inv.getStackInSlot(0).getCount()==10);});
  SupplyTests.run(rows,"failed_save_no_withdrawal",()->{var inv=stock();var p=SurveySupplies.prepare(inv,target());reject(()->p.commit(registries,t->{throw new IllegalStateException("save failed");},()->{}));SupplyTests.check(inv.getStackInSlot(0).getCount()==10&&!inv.getStackInSlot(1).isEmpty());});
  SupplyTests.run(rows,"interrupted_flush_quarantined",()->{var inv=stock();var p=SurveySupplies.prepare(inv,target());var saved=new CompoundTag[1];reject(()->p.commit(registries,t->saved[0]=t,()->{throw new IllegalStateException("flush failed");}));SupplyTests.check(saved[0].contains("pending")&&inv.getStackInSlot(0).getCount()==2);reject(()->SurveyCargo.load(saved[0],registries));});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorSupplies",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
