// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ProductionProfileTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void profiles(GameTestHelper h){var rows=new ArrayList<ProbeResult>();
  SupplyTests.run(rows,"wood_exact_seed",()->{var p=profile("birch_log");SupplyTests.check(value(p,"hutRole").equals("lumberjack")&&value(p,"starterItem").equals(ResourceLocation.withDefaultNamespace("birch_sapling")));});
  SupplyTests.run(rows,"flowers_native_levels",()->{SupplyTests.check(value(profile("poppy"),"minimumLevel").equals(1)&&value(profile("blue_orchid"),"minimumLevel").equals(2)&&value(profile("rose_bush"),"minimumLevel").equals(3));});
  SupplyTests.run(rows,"wheat_native_farmer",()->SupplyTests.check(value(profile("wheat_seeds"),"hutRole").equals("farmer")));
  SupplyTests.run(rows,"aquatic_field_identity",()->{var p=profile("kelp");SupplyTests.check(value(p,"hutRole").equals("plantation")&&value(p,"fieldBlueprint").equals("infrastructure/plantationfields/kelp1.blueprint")&&value(p,"aquatic").equals(true)&&!value(p,"fieldBlueprint").equals(value(profile("seagrass"),"fieldBlueprint")));});
  SupplyTests.run(rows,"vine_field_identity",()->{var p=profile("vine");SupplyTests.check(value(p,"fieldBlueprint").equals("infrastructure/plantationfields/vine1.blueprint")&&value(p,"aquatic").equals(false));});
  SupplyTests.run(rows,"unsupported_target_rejected",()->reject(()->profile("diamond")));
  SupplyTests.run(rows,"unimplemented_seed_conversion_rejected",()->reject(()->profile("melon_slice")));
  SupplyTests.run(rows,"profile_round_trip",()->{var p=profile("seagrass");var tag=(CompoundTag)value(p,"save");SupplyTests.check(p.equals(load(tag)));});
  SupplyTests.run(rows,"forged_profile_rejected",()->{var tag=(CompoundTag)value(profile("kelp"),"save");tag.putString("kind","FORESTRY");reject(()->load(tag));});
  SupplyTests.run(rows,"profile_starter_stock",()->{var supplies=(Map<?,?>)value(profile("birch_log"),"starterSupplies");SupplyTests.check(supplies.containsKey(new dev.colonyhorizons.remote.RemoteModels.ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BIRCH_SAPLING)))&&!supplies.containsKey(new dev.colonyhorizons.remote.RemoteModels.ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_SAPLING))));});
  SupplyTests.run(rows,"aquatic_operating_supplies",()->{var supplies=(Map<?,?>)value(profile("seagrass"),"starterSupplies");for(var item:List.of(net.minecraft.world.item.Items.SHEARS,net.minecraft.world.item.Items.BONE_MEAL,net.minecraft.world.item.Items.SEAGRASS))SupplyTests.check(supplies.containsKey(new dev.colonyhorizons.remote.RemoteModels.ItemKey(new net.minecraft.world.item.ItemStack(item))));});
  SupplyTests.run(rows,"profile_grant_journal",()->{var j=new dev.colonyhorizons.remote.FoundingGrantJournal(dev.colonyhorizons.remote.FoundingGrantJournal.State.RESERVED,List.of(),Optional.empty(),false,Set.of(),List.of(),0,dev.colonyhorizons.remote.FoundingRoster.Role.PRODUCER);for(int i=1;i<=4;i++){j=j.begin();j=dev.colonyhorizons.remote.FoundingGrantJournal.load(j.save());j=j.receipt(new dev.colonyhorizons.remote.FoundingRoster.Member(j.intent().orElseThrow(),i,UUID.randomUUID()));}SupplyTests.check(j.receipts().get(3).role()==dev.colonyhorizons.remote.FoundingRoster.Role.PRODUCER&&dev.colonyhorizons.remote.FoundingGrantJournal.load(j.save()).equals(j));});
  SupplyTests.run(rows,"legacy_grant_preserved",()->{var j=new dev.colonyhorizons.remote.FoundingGrantJournal(dev.colonyhorizons.remote.FoundingGrantJournal.State.RESERVED,List.of(),Optional.empty(),false,Set.of());SupplyTests.check(!j.save().contains("productionRole")&&dev.colonyhorizons.remote.FoundingGrantJournal.load(j.save()).productionRole()==dev.colonyhorizons.remote.FoundingRoster.Role.FORESTER);});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ProductionProfiles",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 private static Class<?> type(){try{return Class.forName("dev.colonyhorizons.remote.ProductionProfile");}catch(Exception e){throw new AssertionError("Production profiles missing",e);}}
 private static Object invoke(java.lang.reflect.Method m,Object target,Object...args){try{return m.invoke(target,args);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof IllegalArgumentException x)throw x;throw new AssertionError(e.getCause());}catch(Exception e){throw new AssertionError(e);}}
 private static Object profile(String target){try{return invoke(type().getMethod("forTarget",ResourceLocation.class),null,ResourceLocation.withDefaultNamespace(target));}catch(NoSuchMethodException e){throw new AssertionError(e);}}
 private static Object value(Object p,String name){try{return invoke(type().getMethod(name),p);}catch(NoSuchMethodException e){throw new AssertionError(e);}}
 private static Object load(CompoundTag tag){try{return invoke(type().getMethod("load",CompoundTag.class),null,tag);}catch(NoSuchMethodException e){throw new AssertionError(e);}}
 private static void reject(Runnable r){try{r.run();throw new AssertionError("Unsupported profile accepted");}catch(IllegalArgumentException expected){}}
}
