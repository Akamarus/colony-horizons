// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ProductionWorkerTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1600)
 public static void configure(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var env=new RemoteTestEnvironment(h.getLevel(),1024,new RemoteModels.Budget(Map.of(),Map.of()),0);var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=env.home;setup.owner=env.owner;
  com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)env.home.colony(),true,new BlockPos(1024,-59,0),4,h.getLevel(),false);
  var wood=(BuildingLumberjack)setup.place("fundamentals/lumberjack1.blueprint",new BlockPos(1000,-59,0),true);
  var flower=(BuildingFlorist)setup.place("agriculture/horticulture/florist1.blueprint",new BlockPos(1024,-59,0),true);
  var plant=(BuildingPlantation)setup.place("agriculture/horticulture/plantation1.blueprint",new BlockPos(1048,-59,0),true);
  SupplyTests.run(rows,"exact_sapling_filter",()->{configure(wood,"birch_log");var module=wood.getModuleMatching(ItemListModule.class,m->m.getId().equals(com.minecolonies.core.entity.ai.workers.production.EntityAIWorkLumberjack.SAPLINGS_LIST));SupplyTests.check(module.isItemInList(new ItemStorage(new ItemStack(Items.OAK_SAPLING)))&&!module.isItemInList(new ItemStorage(new ItemStack(Items.BIRCH_SAPLING))));});
  SupplyTests.run(rows,"exact_flower_filter",()->{configure(flower,"poppy");for(int i=0;i<32;i++)SupplyTests.check(flower.getFlowerToGrow().is(Items.POPPY));});
  SupplyTests.run(rows,"native_flower_level_gate",()->SupplyTests.reject(()->configure(flower,"blue_orchid")));
  SupplyTests.run(rows,"wrong_native_hut_rejected",()->SupplyTests.reject(()->configure(wood,"poppy")));
  SupplyTests.run(rows,"plantation_job_identity",()->{var profile=ProductionProfile.forTarget(ResourceLocation.withDefaultNamespace("kelp"));try{SupplyTests.check(profile.getClass().getMethod("jobId").invoke(profile).equals(plant.getModule(BuildingModules.PLANTATION_WORK).getJobEntry().getKey()));}catch(Exception e){throw new AssertionError("Native planter job mismatch",e);}});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ProductionWorkers",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }catch(Exception e){h.fail(e.toString());}});}
 static void configure(IBuilding building,String target){try{Class.forName("dev.colonyhorizons.remote.ProductionWorker").getMethod("configure",IBuilding.class,ProductionProfile.class).invoke(null,building,ProductionProfile.forTarget(ResourceLocation.withDefaultNamespace(target)));}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof IllegalArgumentException x)throw x;throw new AssertionError(e.getCause());}catch(Exception e){throw new AssertionError("Native producer configuration missing",e);}}
}
