// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorCargoTests {
 private static void rejected(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException e){return;}throw new AssertionError("Invalid cargo accepted");}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void cargo(GameTestHelper h){var rows=new ArrayList<ProbeResult>();var registries=h.getLevel().registryAccess();
  SupplyTests.run(rows,"cargo_round_trip",()->{var tool=new ItemStack(Items.SHEARS);tool.setDamageValue(4);var cargo=new SurveyCargo(true,tool,List.of(new ItemStack(Items.KELP,3)));var loaded=SurveyCargo.load(cargo.save(registries),registries);SupplyTests.check(loaded.paid()&&loaded.tool().getDamageValue()==4&&loaded.samples().getFirst().getCount()==3);});
  SupplyTests.run(rows,"defensive_item_copies",()->{var tool=new ItemStack(Items.SHEARS);var sample=new ItemStack(Items.KELP,3);var cargo=new SurveyCargo(true,tool,List.of(sample));sample.grow(20);tool.setDamageValue(100);cargo.samples().getFirst().grow(20);cargo.tool().setDamageValue(100);SupplyTests.check(cargo.count()==3&&cargo.tool().getDamageValue()==0);});
  SupplyTests.run(rows,"cargo_limits",()->{rejected(()->new SurveyCargo(true,ItemStack.EMPTY,List.of(new ItemStack(Items.KELP,17))));rejected(()->new SurveyCargo(false,ItemStack.EMPTY,List.of(new ItemStack(Items.KELP))));});
  SupplyTests.run(rows,"pending_custody_rejected",()->{var raw=new SurveyCargo(true,ItemStack.EMPTY,List.of()).save(registries);raw.put("pending",new CompoundTag());rejected(()->SurveyCargo.load(raw,registries));});
  SupplyTests.run(rows,"harvest_receipt_persistence",()->{var record=SurveyRecord.create(UUID.randomUUID(),2,UUID.randomUUID(),net.minecraft.core.BlockPos.ZERO,1,UUID.randomUUID(),net.minecraft.resources.ResourceLocation.withDefaultNamespace("kelp"));var data=new SurveySavedData();data.add(record);var op=UUID.randomUUID();data.putSamples(record.id(),SampleJournal.empty().prepare(op,net.minecraft.core.BlockPos.ZERO));data.writeCargo(record.id(),new SurveyCargo(true,ItemStack.EMPTY,List.of(new ItemStack(Items.KELP))).save(registries));var loaded=SurveySavedData.load(data.save(new CompoundTag(),registries),registries);SupplyTests.check(loaded.samples(record.id()).pending().orElseThrow().equals(op));SupplyTests.check(SurveyCargo.load(loaded.cargo(record.id()),registries).count()==1);});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorCargo",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
