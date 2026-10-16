// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;

@PrefixGameTestTemplate(false)
public final class SurveyorHarvestTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void harvest(GameTestHelper h){
  var rows=new ArrayList<ProbeResult>();var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,3,3));
  var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SurveyHarvestQA"));
  var target=SurveyResourceCatalog.find(ResourceLocation.withDefaultNamespace("seagrass")).orElseThrow();
  SupplyTests.run(rows,"requires_shears",()->{level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,Blocks.SEAGRASS.defaultBlockState(),3);SupplyTests.check(SurveyHarvest.prepare(level,actor,pos,target,ItemStack.EMPTY,16).isEmpty());SupplyTests.check(level.getBlockState(pos).is(Blocks.SEAGRASS));});
  SupplyTests.run(rows,"real_sheared_drop",()->{var tool=new ItemStack(Items.SHEARS);var plan=SurveyHarvest.prepare(level,actor,pos,target,tool,16).orElseThrow();var events=new ArrayList<String>();var result=plan.commit(()->events.add("intent"),()->events.add("receipt"));SupplyTests.check(result.stream().filter(s->s.is(Items.SEAGRASS)).mapToInt(ItemStack::getCount).sum()==1);SupplyTests.check(level.getBlockState(pos).is(Blocks.WATER)&&tool.getDamageValue()==1&&events.equals(List.of("intent","receipt")));});
  SupplyTests.run(rows,"no_room_no_harvest",()->{level.setBlock(pos,Blocks.SEAGRASS.defaultBlockState(),3);SupplyTests.check(SurveyHarvest.prepare(level,actor,pos,target,new ItemStack(Items.SHEARS),0).isEmpty());SupplyTests.check(level.getBlockState(pos).is(Blocks.SEAGRASS));});
  SupplyTests.run(rows,"changed_block_rejected",()->{var plan=SurveyHarvest.prepare(level,actor,pos,target,new ItemStack(Items.SHEARS),16).orElseThrow();level.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);try{plan.commit(()->{},()->{});throw new AssertionError("Changed block harvested");}catch(IllegalStateException expected){}SupplyTests.check(level.getBlockState(pos).is(Blocks.CHEST));});
  SupplyTests.run(rows,"intent_failure_preserves_block",()->{level.setBlock(pos,Blocks.SEAGRASS.defaultBlockState(),3);var plan=SurveyHarvest.prepare(level,actor,pos,target,new ItemStack(Items.SHEARS),16).orElseThrow();try{plan.commit(()->{throw new IllegalStateException("disk failure");},()->{});throw new AssertionError("Ignored save failure");}catch(IllegalStateException expected){}SupplyTests.check(level.getBlockState(pos).is(Blocks.SEAGRASS));});
  SupplyTests.run(rows,"harvest_cannot_replay",()->{var plan=SurveyHarvest.prepare(level,actor,pos,target,new ItemStack(Items.SHEARS),16).orElseThrow();plan.commit(()->{},()->{});level.setBlock(pos,Blocks.SEAGRASS.defaultBlockState(),3);try{plan.commit(()->{},()->{});throw new AssertionError("Replayed harvest");}catch(IllegalStateException expected){}SupplyTests.check(level.getBlockState(pos).is(Blocks.SEAGRASS));});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorHarvest",rows);}catch(Exception e){throw new RuntimeException(e);}
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
