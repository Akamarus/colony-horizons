// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationRecordsTests {
 static ResourceLocation id(String s){return ResourceLocation.withDefaultNamespace(s);}
 static void reject(Runnable r){try{r.run();throw new AssertionError("Invalid gathering record accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
 static GatheringRecord fresh(){return GatheringRecord.create(UUID.randomUUID(),1,UUID.randomUUID(),BlockPos.ZERO,7,UUID.randomUUID(),UUID.randomUUID(),GatheringCatalog.find(id("kelp")).orElseThrow(),false);}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void records(GameTestHelper h){var rows=new ArrayList<ProbeResult>();
 SupplyTests.run(rows,"all_exact_offers",()->{SupplyTests.check(GatheringCatalog.all().size()==36);for(var o:GatheringCatalog.all()){SupplyTests.check(o.ticks()==6000&&o.bread()==2);var p=o.target().getPath();int count=p.endsWith("_log")||p.equals("kelp")?16:p.endsWith("_sapling")||p.equals("mangrove_propagule")?4:8;int wear=p.endsWith("_log")?16:p.equals("vine")||p.equals("seagrass")?8:0;SupplyTests.check(o.count()==count&&o.wear()==wear);}SupplyTests.check(GatheringCatalog.find(id("pumpkin")).isEmpty()&&GatheringCatalog.find(id("melon_slice")).isEmpty());});
 SupplyTests.run(rows,"timer_preserves_command_revision",()->{var r=fresh().transition(GatheringRecord.State.GATHERING,"Working");SupplyTests.check(r.tick().revision()==r.revision());});
 SupplyTests.run(rows,"completed_repeat_is_off",()->{var r=fresh();r=new GatheringRecord(r.id(),r.revision(),r.colony(),r.owner(),r.hut(),r.worker(),r.workerUuid(),r.discovery(),r.offer(),r.remaining(),true,r.state(),r.status());r=r.transition(GatheringRecord.State.GATHERING,"Working");for(int i=0;i<6000;i++)r=r.tick();SupplyTests.check(!r.transition(GatheringRecord.State.COMPLETE,"Done").repeat());});
 SupplyTests.run(rows,"waiting_return_is_idempotent",()->{var r=fresh().transition(GatheringRecord.State.GATHERING,"Working").returning("Full hut");SupplyTests.check(r.returning("Full hut")==r);});
 SupplyTests.run(rows,"wrong_list_type_rejected",()->{var t=new net.minecraft.nbt.CompoundTag();t.putInt("version",1);var list=new net.minecraft.nbt.ListTag();list.add(net.minecraft.nbt.StringTag.valueOf("bad"));t.put("expeditions",list);reject(()->GatheringSavedData.load(t,h.getLevel().registryAccess()));});
 SupplyTests.run(rows,"completed_history_does_not_block",()->{var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("version",1);var list=new net.minecraft.nbt.ListTag();UUID oldest=null;for(int i=0;i<4096;i++){var old=fresh();if(oldest==null)oldest=old.id();var done=old.transition(GatheringRecord.State.GATHERING,"Working").returning("Recalled").transition(GatheringRecord.State.COMPLETE,"Returned");var t=done.save();t.put("cargo",new GatheringCargo(true,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY,false,true).save(h.getLevel().registryAccess()));list.add(t);}tag.put("expeditions",list);var d=GatheringSavedData.load(tag,h.getLevel().registryAccess());var next=fresh();d.add(next);SupplyTests.check(d.records().size()==4096&&d.find(oldest).isEmpty()&&d.find(next.id()).isPresent());});
 SupplyTests.run(rows,"record_round_trip",()->{var r=fresh();SupplyTests.check(GatheringRecord.load(r.save()).equals(r));});
 SupplyTests.run(rows,"exact_timer_boundary",()->{var r=fresh().transition(GatheringRecord.State.GATHERING,"Working");for(int i=0;i<5999;i++)r=r.tick();SupplyTests.check(r.remaining()==1&&r.state()==GatheringRecord.State.GATHERING);r=r.tick();SupplyTests.check(r.remaining()==0&&r.state()==GatheringRecord.State.RETURNING);var done=r;reject(done::tick);});
 SupplyTests.run(rows,"invalid_record_bounds",()->{var tag=fresh().save();tag.putInt("remaining",6001);reject(()->GatheringRecord.load(tag));var bad=fresh().save();bad.putString("state","BROKEN");reject(()->GatheringRecord.load(bad));});
 SupplyTests.run(rows,"stale_and_duplicate_records",()->{var d=new GatheringSavedData();var r=fresh();d.add(r);reject(()->d.add(r));var next=r.transition(GatheringRecord.State.GATHERING,"Working");d.replace(r,next);reject(()->d.replace(r,next));});
 SupplyTests.run(rows,"one_active_per_colony",()->{var d=new GatheringSavedData();d.add(fresh());reject(()->d.add(fresh()));});
 SupplyTests.run(rows,"recall_stops_repeat",()->{var r=fresh().transition(GatheringRecord.State.GATHERING,"Working").returning("Recalled");SupplyTests.check(!r.repeat()&&r.remaining()==6000&&r.state()==GatheringRecord.State.RETURNING);});
 SupplyTests.run(rows,"shared_expedition_exclusion",()->{var l=h.getLevel();ExpeditionAccess.requireIdle(l,987);var r=SurveyRecord.create(UUID.randomUUID(),987,UUID.randomUUID(),BlockPos.ZERO,7,UUID.randomUUID(),id("kelp"));SurveySavedData.get(l).add(r);reject(()->ExpeditionAccess.requireIdle(l,987));SurveySavedData.get(l).replace(r,r.next(SurveyRecord.State.CANCELLED));ExpeditionAccess.requireIdle(l,987);var g=GatheringRecord.create(UUID.randomUUID(),987,r.owner(),BlockPos.ZERO,7,r.workerUuid(),UUID.randomUUID(),GatheringCatalog.find(id("kelp")).orElseThrow(),false);GatheringSavedData.get(l).add(g);reject(()->ExpeditionAccess.requireIdle(l,987));});
 try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationRecords",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
