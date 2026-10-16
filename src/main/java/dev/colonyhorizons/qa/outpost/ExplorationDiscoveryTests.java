// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationDiscoveryTests {
 static ResourceLocation id(String s){return ResourceLocation.withDefaultNamespace(s);}
 static void reject(Runnable r){try{r.run();throw new AssertionError("Invalid evidence accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void discovery(GameTestHelper h){var rows=new ArrayList<ProbeResult>();var data=new SurveySavedData();var r=SurveyRecord.create(UUID.randomUUID(),1,UUID.randomUUID(),BlockPos.ZERO,7,UUID.randomUUID(),id("kelp"));data.add(r);var searching=r.next(SurveyRecord.State.SEARCHING);data.replace(r,searching);var source=new BlockPos(100,62,200);var evidence=new DiscoveryEvidence(r.id(),1,r.owner(),id("overworld"),id("ocean"),r.target(),source);
 SupplyTests.run(rows,"no_sample_no_unlock",()->{reject(()->data.recordEvidence(evidence));SupplyTests.check(data.unlocked(r.owner(),1,r.target()).isEmpty());});
 var op=UUID.randomUUID();data.putSamples(r.id(),data.samples(r.id()).prepare(op,source));data.recordEvidence(evidence);
 SupplyTests.run(rows,"pending_return_no_unlock",()->{SupplyTests.check(data.unlocked(r.owner(),1,r.target()).isEmpty());reject(()->data.recordEvidence(evidence));});
 data.putSamples(r.id(),data.samples(r.id()).collected(op,1));var returning=searching.next(SurveyRecord.State.RETURNING);data.replace(searching,returning);data.writeCargo(r.id(),new SurveyCargo(true,ItemStack.EMPTY,List.of(new ItemStack(Items.KELP))).save(h.getLevel().registryAccess()));
 SupplyTests.run(rows,"returning_locked",()->SupplyTests.check(data.unlocked(r.owner(),1,r.target()).isEmpty()));
 data.replace(returning,returning.next(SurveyRecord.State.COMPLETE));
 SupplyTests.run(rows,"undelivered_cargo_locked",()->SupplyTests.check(data.unlocked(r.owner(),1,r.target()).isEmpty()));
 data.writeCargo(r.id(),new SurveyCargo(true,ItemStack.EMPTY,List.of()).save(h.getLevel().registryAccess()));
 SupplyTests.run(rows,"completed_return_unlocks",()->SupplyTests.check(data.unlocked(r.owner(),1,r.target()).orElseThrow().equals(evidence)));
 SupplyTests.run(rows,"foreign_owner_colony_locked",()->SupplyTests.check(data.unlocked(UUID.randomUUID(),1,r.target()).isEmpty()&&data.unlocked(r.owner(),2,r.target()).isEmpty()&&data.unlocked(r.owner(),1,id("vine")).isEmpty()));
 SupplyTests.run(rows,"evidence_round_trip",()->{var restored=SurveySavedData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());SupplyTests.check(restored.unlocked(r.owner(),1,r.target()).orElseThrow().equals(evidence));});
 SupplyTests.run(rows,"legacy_report_locked",()->{var tag=data.save(new CompoundTag(),h.getLevel().registryAccess());tag.getList("surveys",10).getCompound(0).remove("discovery");SupplyTests.check(SurveySavedData.load(tag,h.getLevel().registryAccess()).unlocked(r.owner(),1,r.target()).isEmpty());});
 SupplyTests.run(rows,"forged_evidence_rejected",()->{var tag=data.save(new CompoundTag(),h.getLevel().registryAccess());tag.getList("surveys",10).getCompound(0).getCompound("discovery").putString("target","minecraft:vine");reject(()->SurveySavedData.load(tag,h.getLevel().registryAccess()));});
 SupplyTests.run(rows,"malformed_receipt_locked",()->{var receipt=data.cargo(r.id());var bad=receipt.copy();var strings=new net.minecraft.nbt.ListTag();strings.add(net.minecraft.nbt.StringTag.valueOf("still holding cargo"));bad.put("samples",strings);data.writeCargo(r.id(),bad);SupplyTests.check(data.unlocked(r.owner(),1,r.target()).isEmpty());data.writeCargo(r.id(),receipt);});
 SupplyTests.run(rows,"missing_source_rejected",()->{var t=evidence.save();t.remove("source");reject(()->DiscoveryEvidence.load(t));});
 try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationDiscovery",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(v->v.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
