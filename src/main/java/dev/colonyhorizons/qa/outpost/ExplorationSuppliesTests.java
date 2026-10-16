// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationSuppliesTests {
 static GatheringOffer offer(){return GatheringCatalog.find(ResourceLocation.withDefaultNamespace("birch_log")).orElseThrow();}
 static ItemStackHandler stock(int durability){var inv=new ItemStackHandler(4);inv.setStackInSlot(0,new ItemStack(Items.BREAD,8));var axe=new ItemStack(Items.IRON_AXE);axe.setDamageValue(axe.getMaxDamage()-durability);axe.set(DataComponents.CUSTOM_NAME,Component.literal("Explorer axe"));inv.setStackInSlot(1,axe);return inv;}
 static void reject(Runnable r){try{r.run();throw new AssertionError("Invalid gathering payment accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void supplies(GameTestHelper h){var rows=new ArrayList<ProbeResult>();var regs=h.getLevel().registryAccess();
 SupplyTests.run(rows,"exact_payment_and_wear",()->{var inv=stock(32);inv.getStackInSlot(1).enchant(regs.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING),3);var before=inv.getStackInSlot(1).copy();before.setDamageValue(before.getDamageValue()+16);var p=GatheringSupplies.prepare(inv,offer());var saved=new CompoundTag[1];var c=p.commit(regs,t->saved[0]=t,()->{});SupplyTests.check(inv.getStackInSlot(0).getCount()==6&&inv.getStackInSlot(1).isEmpty()&&c.paid()&&c.tool().getMaxDamage()-c.tool().getDamageValue()==16&&c.tool().getHoverName().getString().equals("Explorer axe")&&c.reward().isEmpty()&&ItemStack.isSameItemSameComponents(c.tool(),before));reject(()->p.commit(regs,t->{},()->{}));});
 SupplyTests.run(rows,"insufficient_wear_no_payment",()->{var inv=stock(15);reject(()->GatheringSupplies.prepare(inv,offer()));SupplyTests.check(inv.getStackInSlot(0).getCount()==8&&!inv.getStackInSlot(1).isEmpty());});
 SupplyTests.run(rows,"last_trip_breaks_tool",()->{var inv=stock(16);var c=GatheringSupplies.prepare(inv,offer()).commit(regs,t->{},()->{});SupplyTests.check(c.tool().isEmpty()&&inv.getStackInSlot(0).getCount()==6);});
 SupplyTests.run(rows,"shortage_no_payment",()->{var inv=stock(32);inv.setStackInSlot(0,new ItemStack(Items.BREAD));reject(()->GatheringSupplies.prepare(inv,offer()));SupplyTests.check(inv.getStackInSlot(0).getCount()==1&&!inv.getStackInSlot(1).isEmpty());});
 SupplyTests.run(rows,"pending_write_failure",()->{var inv=stock(32);var p=GatheringSupplies.prepare(inv,offer());reject(()->p.commit(regs,t->{throw new IllegalStateException("save failed");},()->{}));SupplyTests.check(inv.getStackInSlot(0).getCount()==8&&!inv.getStackInSlot(1).isEmpty());});
 SupplyTests.run(rows,"native_flush_failure_quarantines",()->{var inv=stock(32);var p=GatheringSupplies.prepare(inv,offer());var saved=new CompoundTag[1];reject(()->p.commit(regs,t->saved[0]=t,()->{throw new IllegalStateException("flush failed");}));SupplyTests.check(saved[0].contains("pending")&&inv.getStackInSlot(0).getCount()==6);reject(()->GatheringCargo.load(saved[0],regs));});
 SupplyTests.run(rows,"stale_apply_quarantines",()->{var inv=stock(32);var p=GatheringSupplies.prepare(inv,offer());inv.setStackInSlot(0,new ItemStack(Items.BREAD,7));var saved=new CompoundTag[1];reject(()->p.commit(regs,t->saved[0]=t,()->{}));SupplyTests.check(saved[0].contains("pending")&&inv.getStackInSlot(0).getCount()==7&&!inv.getStackInSlot(1).isEmpty());reject(()->GatheringCargo.load(saved[0],regs));});
 SupplyTests.run(rows,"final_receipt_failure_quarantines",()->{var inv=stock(32);var p=GatheringSupplies.prepare(inv,offer());var saved=new CompoundTag[1];int[] writes={0};reject(()->p.commit(regs,t->{if(++writes[0]==2)throw new IllegalStateException("receipt failed");saved[0]=t;},()->{}));SupplyTests.check(saved[0].contains("pending")&&inv.getStackInSlot(0).getCount()==6&&inv.getStackInSlot(1).isEmpty());reject(()->GatheringCargo.load(saved[0],regs));reject(()->p.commit(regs,t->{},()->{}));});
 SupplyTests.run(rows,"missing_earned_reward_rejected",()->reject(()->new GatheringCargo(true,ItemStack.EMPTY,ItemStack.EMPTY,true,false)));
 SupplyTests.run(rows,"cargo_defensive_copies",()->{var tool=new ItemStack(Items.SHEARS);var c=new GatheringCargo(true,tool,ItemStack.EMPTY,false,false);tool.setDamageValue(50);c.tool().setDamageValue(20);SupplyTests.check(c.tool().getDamageValue()==0);var earned=c.earn(offer());SupplyTests.check(earned.reward().getCount()==16&&earned.earned());reject(()->earned.earn(offer()));var delivered=earned.deliveredCargo();SupplyTests.check(delivered.delivered()&&delivered.tool().isEmpty()&&delivered.reward().isEmpty());});
 try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationSupplies",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
