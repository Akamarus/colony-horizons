// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;

@PrefixGameTestTemplate(false)
public final class SurveyorRecordTests {
 private static ResourceLocation item(String s){return ResourceLocation.withDefaultNamespace(s);}
 private static void rejected(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Accepted invalid operation");}
 private static SurveyRecord fresh(){return SurveyRecord.create(UUID.randomUUID(),1,UUID.randomUUID(),new BlockPos(1,64,1),7,UUID.randomUUID(),item("kelp"));}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void records(GameTestHelper h){
  var rows=new ArrayList<ProbeResult>();
  SupplyTests.run(rows,"round_trip",()->{var r=fresh();var data=new SurveySavedData();data.add(r);var restored=SurveySavedData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());SupplyTests.check(restored.find(r.id()).orElseThrow().equals(r));});
  SupplyTests.run(rows,"unknown_resource_state",()->{rejected(()->SurveyRecord.create(UUID.randomUUID(),1,UUID.randomUUID(),BlockPos.ZERO,1,UUID.randomUUID(),item("diamond")));var tag=fresh().save();tag.putString("state","MADE_UP");rejected(()->SurveyRecord.load(tag));});
  SupplyTests.run(rows,"worker_identity",()->{var r=fresh();SupplyTests.check(r.belongsToWorker(7,r.workerUuid()));SupplyTests.check(!r.belongsToWorker(7,UUID.randomUUID()));});
  SupplyTests.run(rows,"stale_mutation",()->{var data=new SurveySavedData();var r=fresh();data.add(r);data.replace(r,r.next(SurveyRecord.State.SEARCHING));rejected(()->data.replace(r,r.next(SurveyRecord.State.CANCELLED)));});
  SupplyTests.run(rows,"bounded_search",()->{var r=fresh().next(SurveyRecord.State.SEARCHING);for(int i=0;i<32;i++)r=r.advanceCandidate();var full=r;rejected(full::advanceCandidate);});
  SupplyTests.run(rows,"one_active_per_colony",()->{var data=new SurveySavedData();data.add(fresh());rejected(()->data.add(fresh()));});
  SupplyTests.run(rows,"exact_resource_identity",()->{var oak=SurveyResourceCatalog.find(item("oak_log")).orElseThrow();SupplyTests.check(oak.matches(Blocks.OAK_LOG.defaultBlockState())&&!oak.matches(Blocks.BIRCH_LOG.defaultBlockState()));var kelp=SurveyResourceCatalog.find(item("kelp")).orElseThrow();SupplyTests.check(kelp.matches(Blocks.KELP_PLANT.defaultBlockState())&&!kelp.matches(Blocks.SEAGRASS.defaultBlockState()));SupplyTests.check(SurveyResourceCatalog.find(item("wheat_seeds")).orElseThrow().matches(Blocks.SHORT_GRASS.defaultBlockState()));SupplyTests.check(SurveyResourceCatalog.find(item("blue_orchid")).orElseThrow().matches(Blocks.BLUE_ORCHID.defaultBlockState()));});
  SupplyTests.run(rows,"duplicate_sample_receipt",()->{var j=SampleJournal.empty();var op=UUID.randomUUID();j=j.prepare(op,new BlockPos(0,64,0));j=j.collected(op,3);var done=j;rejected(()->done.collected(op,3));rejected(()->done.prepare(op,BlockPos.ZERO));SupplyTests.check(SampleJournal.load(j.save()).equals(j));});
  SupplyTests.run(rows,"sample_limit",()->{var j=SampleJournal.empty().prepare(UUID.randomUUID(),BlockPos.ZERO);var pending=j;rejected(()->pending.collected(pending.pending().orElseThrow(),17));});
  SupplyTests.run(rows,"ambiguous_sample_recovery",()->{var j=SampleJournal.empty().prepare(UUID.randomUUID(),BlockPos.ZERO);var restored=SampleJournal.load(j.save());SupplyTests.check(restored.pending().isPresent());rejected(()->restored.prepare(UUID.randomUUID(),BlockPos.ZERO));});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorRecords",rows);}catch(Exception e){throw new RuntimeException(e);}
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
