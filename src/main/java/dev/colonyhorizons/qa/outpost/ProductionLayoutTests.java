// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.integration.ArcologyCatalog;
import com.ldtteam.structurize.storage.StructurePacks;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ProductionLayoutTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void layouts(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();
  for(String target:List.of("birch_log","poppy","wheat_seeds","vine","kelp","seagrass"))SupplyTests.run(rows,target+"_layout",()->{
   var profile=ProductionProfile.forTarget(ResourceLocation.withDefaultNamespace(target));var tag=FreeFounderRecordTests.seed(h,true,4);var t=FreeFounderRecordTests.entry(tag);t.putString("foundingPolicy","RESOURCE_FOUR_V2");var origin=FreeFounderRecordTests.binding();origin.put("profile",profile.save());t.put("surveyOrigin",origin);var r=RemoteDevelopmentData.load(tag,h.getLevel().registryAccess(),Level.OVERWORLD).all().getFirst();
   var pack=StructurePacks.getStructurePack("Arcology Horizons");var catalog=inspect(pack.getPath(),h.getLevel().registryAccess(),profile);var plans=ArcologySettlementLayout.plan(r.id(),r.draft().request(),catalog);
   SupplyTests.check(plans.size()==8&&plans.stream().filter(p->p.role().equals(profile.hutRole())).count()==1&&plans.stream().filter(p->p.role().equals("home")).count()==4);
   SupplyTests.check(plans.subList(0,3).stream().map(RemoteModels.BuildingPlan::role).toList().equals(List.of("builder","warehouse","courier")));
   var producer=plans.get(3);SupplyTests.check(producer.blueprint().equals(profile.blueprintPrefix()+"1.blueprint"));
   for(int i=0;i<plans.size();i++)for(int j=i+1;j<plans.size();j++)SupplyTests.check(!plans.get(i).upgradeVolume().intersects(plans.get(j).upgradeVolume()));
  });
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ProductionLayouts",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 });}
 static ArcologyCatalog.Inspection inspect(Path path,net.minecraft.core.HolderLookup.Provider registries,ProductionProfile profile){try{return (ArcologyCatalog.Inspection)ArcologyCatalog.class.getMethod("inspect",Path.class,net.minecraft.core.HolderLookup.Provider.class,ProductionProfile.class).invoke(null,path,registries,profile);}catch(Exception e){throw new AssertionError("Profile catalog unavailable",e);}}
}
