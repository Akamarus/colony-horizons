// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.*;

public final class MayorJobRegistration {
 private MayorJobRegistration(){}
 private static final DeferredRegister<JobEntry> JOBS=DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS,"colonyhorizons");
 public static final DeferredHolder<JobEntry,JobEntry> MAYOR=JOBS.register("mayor",()->new JobEntry.Builder().setJobProducer(JobMayor::new).setJobViewProducer(()->DefaultJobView::new).setRegistryName(ResourceLocation.fromNamespaceAndPath("colonyhorizons","mayor")).createJobEntry());
 private static final BuildingEntry.ModuleProducer TERMINAL=new BuildingEntry.ModuleProducer("colonyhorizons:mayor_terminal",MayorTerminalModule::new,null);
 public static void initialize(IEventBus bus){JOBS.register(bus);bus.addListener((FMLCommonSetupEvent event)->event.enqueueWork(()->ModBuildings.builder.get().getModuleProducers().add(TERMINAL)));}
}
