// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class SurveyorPreparationWorkTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void work(GameTestHelper h){
  var rows=new ArrayList<ProbeResult>();var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,8,5));
  var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TerrainWorkQA"));
  Runnable reset=()->{l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.AIR.defaultBlockState(),2);};
  SupplyTests.run(rows,"paid_fill_once",()->{reset.run();var inv=new ItemStackHandler(3);inv.setStackInSlot(0,new ItemStack(Items.DIRT,2));var op=fill(p);var attempt=prepare(l,actor,op,inv);var steps=new ArrayList<String>();commit(attempt,()->steps.add("intent"),()->steps.add("native"),()->steps.add("receipt"));SupplyTests.check(l.getBlockState(p).is(Blocks.DIRT)&&inv.getStackInSlot(0).getCount()==1&&steps.equals(List.of("intent","native","receipt")));reject(()->commit(attempt,()->{},()->{},()->{}));});
  SupplyTests.run(rows,"missing_dirt_preserves_world",()->{reset.run();reject(()->prepare(l,actor,fill(p),new ItemStackHandler(3)));SupplyTests.check(l.isEmptyBlock(p));});
  SupplyTests.run(rows,"tool_and_real_drop",()->{reset.run();l.setBlock(p,Blocks.STONE.defaultBlockState(),2);var inv=new ItemStackHandler(3);inv.setStackInSlot(0,new ItemStack(Items.STONE_PICKAXE));commit(prepare(l,actor,clear(p,Blocks.STONE),inv),()->{},()->{},()->{});SupplyTests.check(l.isEmptyBlock(p)&&inv.getStackInSlot(0).getDamageValue()==1&&inv.getStackInSlot(1).is(Items.COBBLESTONE)&&inv.getStackInSlot(1).getCount()==1);});
  SupplyTests.run(rows,"missing_tool_preserves_world",()->{reset.run();l.setBlock(p,Blocks.STONE.defaultBlockState(),2);reject(()->prepare(l,actor,clear(p,Blocks.STONE),new ItemStackHandler(3)));SupplyTests.check(l.getBlockState(p).is(Blocks.STONE));});
  SupplyTests.run(rows,"full_inventory_preserves_world",()->{reset.run();l.setBlock(p,Blocks.STONE.defaultBlockState(),2);var inv=new ItemStackHandler(1);inv.setStackInSlot(0,new ItemStack(Items.STONE_PICKAXE));reject(()->prepare(l,actor,clear(p,Blocks.STONE),inv));SupplyTests.check(l.getBlockState(p).is(Blocks.STONE)&&inv.getStackInSlot(0).getDamageValue()==0);});
  SupplyTests.run(rows,"changed_container_preserved",()->{reset.run();var inv=new ItemStackHandler(3);inv.setStackInSlot(0,new ItemStack(Items.DIRT));var attempt=prepare(l,actor,fill(p),inv);l.setBlock(p,Blocks.CHEST.defaultBlockState(),2);reject(()->commit(attempt,()->{},()->{},()->{}));SupplyTests.check(l.getBlockState(p).is(Blocks.CHEST)&&inv.getStackInSlot(0).getCount()==1);});
  SupplyTests.run(rows,"failed_intent_changes_nothing",()->{reset.run();var inv=new ItemStackHandler(3);inv.setStackInSlot(0,new ItemStack(Items.DIRT));var attempt=prepare(l,actor,fill(p),inv);reject(()->commit(attempt,()->{throw new IllegalStateException("disk failure");},()->{},()->{}));SupplyTests.check(l.isEmptyBlock(p)&&inv.getStackInSlot(0).getCount()==1);});
  SupplyTests.run(rows,"failed_native_save_no_receipt_or_replay",()->{reset.run();var inv=new ItemStackHandler(3);inv.setStackInSlot(0,new ItemStack(Items.DIRT,2));var attempt=prepare(l,actor,fill(p),inv);boolean[] receipt={false};reject(()->commit(attempt,()->{},()->{throw new IllegalStateException("native save failed");},()->receipt[0]=true));SupplyTests.check(!receipt[0]&&inv.getStackInSlot(0).getCount()==1&&l.getBlockState(p).is(Blocks.DIRT));reject(()->commit(attempt,()->{},()->{},()->{}));});
  SupplyTests.run(rows,"occupied_fill_rejected",()->{reset.run();var stand=new net.minecraft.world.entity.decoration.ArmorStand(l,p.getX()+0.5,p.getY(),p.getZ()+0.5);l.addFreshEntity(stand);try{var inv=new ItemStackHandler(3);inv.setStackInSlot(0,new ItemStack(Items.DIRT));reject(()->prepare(l,actor,fill(p),inv));SupplyTests.check(l.isEmptyBlock(p)&&inv.getStackInSlot(0).getCount()==1);}finally{stand.discard();}});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorPreparationWork",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 private static SitePreparationPlan.Operation fill(BlockPos p){return new SitePreparationPlan.Operation(p,Blocks.AIR.defaultBlockState(),Blocks.DIRT.defaultBlockState(),SitePreparationPlan.Kind.FILL);}
 private static SitePreparationPlan.Operation clear(BlockPos p,net.minecraft.world.level.block.Block b){return new SitePreparationPlan.Operation(p,b.defaultBlockState(),Blocks.AIR.defaultBlockState(),SitePreparationPlan.Kind.CLEAR);}
 private static Object prepare(net.minecraft.server.level.ServerLevel l,net.minecraft.server.level.ServerPlayer a,SitePreparationPlan.Operation op,IItemHandlerModifiable inv){try{return Class.forName("dev.colonyhorizons.remote.SitePreparationWork").getMethod("prepare",net.minecraft.server.level.ServerLevel.class,net.minecraft.server.level.ServerPlayer.class,SitePreparationPlan.Operation.class,IItemHandlerModifiable.class).invoke(null,l,a,op,inv);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Paid terrain operation is missing",e);}}
 private static void commit(Object attempt,Runnable intent,Runnable nativeSave,Runnable receipt){try{attempt.getClass().getMethod("commit",Runnable.class,Runnable.class,Runnable.class).invoke(attempt,intent,nativeSave,receipt);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 private static void reject(Runnable r){try{r.run();throw new AssertionError("Unsafe terrain operation accepted");}catch(IllegalStateException|IllegalArgumentException expected){}}
}
