// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorWaterTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void water(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,12,5));var rows=new ArrayList<ProbeResult>();
  Runnable reset=()->{for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-3;y<=2;y++)l.setBlock(p.offset(x,y,z),y<0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);for(int y=-2;y<0;y++)l.setBlock(p.offset(0,y,0),Blocks.WATER.defaultBlockState(),2);};
  var box=new BoundingBox(p);
  SupplyTests.run(rows,"contained_pool_costed",()->{reset.run();var plan=review(l,box,Set.of());SupplyTests.check(plan.approved()&&plan.dirtRequired()==2&&l.getBlockState(p.below()).is(Blocks.WATER)&&plan.operations().getFirst().pos().equals(p.below(2)));});
  SupplyTests.run(rows,"legacy_review_rejects_water",()->{reset.run();SupplyTests.check(!SitePreparationPlan.review(l,List.of(box)).approved());});
  SupplyTests.run(rows,"open_water_rejected",()->{reset.run();l.setBlock(p.below().east(),Blocks.WATER.defaultBlockState(),2);SupplyTests.check(!review(l,box,Set.of()).approved());});
  SupplyTests.run(rows,"broken_wall_rejected",()->{reset.run();l.setBlock(p.below().east(),Blocks.AIR.defaultBlockState(),2);SupplyTests.check(!review(l,box,Set.of()).approved());});
  SupplyTests.run(rows,"deep_water_rejected",()->{reset.run();l.setBlock(p.below(3),Blocks.WATER.defaultBlockState(),2);l.setBlock(p.below(4),Blocks.STONE.defaultBlockState(),2);SupplyTests.check(!review(l,box,Set.of()).approved());});
  SupplyTests.run(rows,"flowing_water_rejected",()->{reset.run();l.setBlock(p.below(),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),2);SupplyTests.check(!review(l,box,Set.of()).approved());});
  SupplyTests.run(rows,"aquatic_area_preserved",()->{reset.run();SupplyTests.check(!review(l,box,Set.of(p.below())).approved()&&l.getBlockState(p.below()).is(Blocks.WATER));});
  SupplyTests.run(rows,"water_plants_preserved",()->{reset.run();l.setBlock(p.below(),Blocks.SEAGRASS.defaultBlockState(),2);SupplyTests.check(!review(l,box,Set.of()).approved());});
  SupplyTests.run(rows,"changed_wall_detected",()->{reset.run();var plan=review(l,box,Set.of());SupplyTests.check(plan.approved());l.setBlock(p.below().east(),Blocks.AIR.defaultBlockState(),2);SupplyTests.check(!plan.matches(l));});
  SupplyTests.run(rows,"water_plan_round_trip",()->{reset.run();var plan=review(l,box,Set.of());var loaded=SitePreparationPlan.load(plan.save());SupplyTests.check(loaded.dirtRequired()==2&&loaded.matches(l)&&loaded.fingerprint().equals(plan.fingerprint()));});
  var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WaterQA"));
  SupplyTests.run(rows,"paid_water_fill_and_reload",()->{reset.run();var plan=review(l,box,Set.of());var inv=new net.neoforged.neoforge.items.ItemStackHandler(2);inv.setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT,2));var attempt=prepare(l,owner,plan,0,inv);attempt.commit(()->{},()->{},()->{});SupplyTests.check(inv.getStackInSlot(0).getCount()==1&&l.getBlockState(p.below(2)).is(Blocks.DIRT));var restored=SitePreparationPlan.load(plan.save());prepare(l,owner,restored,1,inv).commit(()->{},()->{},()->{});SupplyTests.check(inv.getStackInSlot(0).isEmpty()&&l.getBlockState(p.below()).is(Blocks.DIRT));});
  SupplyTests.run(rows,"breach_before_commit_preserves_dirt",()->{reset.run();var plan=review(l,box,Set.of());var inv=new net.neoforged.neoforge.items.ItemStackHandler(1);inv.setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT,2));var attempt=prepare(l,owner,plan,0,inv);l.setBlock(p.below().east(),Blocks.AIR.defaultBlockState(),2);reject(()->attempt.commit(()->{},()->{},()->{}));SupplyTests.check(inv.getStackInSlot(0).getCount()==2&&l.getBlockState(p.below(2)).is(Blocks.WATER));});
  SupplyTests.run(rows,"unplanned_water_fill_rejected",()->{reset.run();var plan=review(l,box,Set.of());var inv=new net.neoforged.neoforge.items.ItemStackHandler(1);inv.setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT,2));reject(()->SitePreparationWork.prepare(l,owner,plan.operations().getFirst(),inv));});
  SupplyTests.run(rows,"missing_boundary_rejected_on_load",()->{reset.run();var plan=review(l,box,Set.of());var tag=plan.save();var states=tag.getList("observed",net.minecraft.nbt.Tag.TAG_COMPOUND);for(int i=states.size()-1;i>=0;i--)if(states.getCompound(i).getLong("pos")==p.below().east().asLong())states.remove(i);reject(()->SitePreparationPlan.load(tag));});
  SupplyTests.run(rows,"placement_callback_cannot_bypass_containment",()->{reset.run();var plan=review(l,box,Set.of());var inv=new net.neoforged.neoforge.items.ItemStackHandler(1);inv.setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT,2));var attempt=prepare(l,owner,plan,0,inv);int[] calls={0};boolean[] receipt={false};java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> listener=event->{if(event.getPos().equals(p.below(2))&&++calls[0]==2)l.setBlock(p.below().east(),Blocks.AIR.defaultBlockState(),2);};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);try{reject(()->attempt.commit(()->{},()->{},()->receipt[0]=true));SupplyTests.check(!receipt[0]&&inv.getStackInSlot(0).getCount()==2&&l.getBlockState(p.below(2)).is(Blocks.WATER));}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorWater",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 private static SitePreparationWork.Attempt prepare(net.minecraft.server.level.ServerLevel l,net.minecraft.server.level.ServerPlayer owner,SitePreparationPlan plan,int cursor,net.neoforged.neoforge.items.IItemHandlerModifiable inv){try{return (SitePreparationWork.Attempt)SitePreparationWork.class.getMethod("prepareContained",net.minecraft.server.level.ServerLevel.class,net.minecraft.server.level.ServerPlayer.class,SitePreparationPlan.class,int.class,net.neoforged.neoforge.items.IItemHandlerModifiable.class).invoke(null,l,owner,plan,cursor,inv);}catch(ReflectiveOperationException e){throw new AssertionError("Contained-water work unavailable",e);}}
 private static void reject(Runnable r){try{r.run();throw new AssertionError("Unsafe water operation accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
 private static SitePreparationPlan review(net.minecraft.server.level.ServerLevel l,BoundingBox box,Set<BlockPos> preserved){try{return (SitePreparationPlan)SitePreparationPlan.class.getMethod("reviewContainedWater",net.minecraft.server.level.ServerLevel.class,List.class,Set.class).invoke(null,l,List.of(box),preserved);}catch(ReflectiveOperationException e){throw new AssertionError("Contained-water planning unavailable",e);}}
}
