// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.workorders.*;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class SurveyorRegistration {
    private SurveyorRegistration() {}
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "colonyhorizons");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "colonyhorizons");
    private static final DeferredRegister<BuildingEntry> BUILDINGS = DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, "colonyhorizons");
    private static final DeferredRegister<JobEntry> JOBS = DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, "colonyhorizons");
    public static final DeferredHolder<Block, BlockHutSurveyor> HUT = BLOCKS.register("blockhutsurveyor", BlockHutSurveyor::new);
    public static final DeferredHolder<Item, ItemBlockHut> ITEM = ITEMS.register("blockhutsurveyor", () -> new ItemBlockHut(HUT.get(), new Item.Properties()));
    public static final DeferredHolder<JobEntry, JobEntry> JOB = JOBS.register("surveyor", () -> new JobEntry.Builder()
        .setJobProducer(JobSurveyor::new).setJobViewProducer(() -> DefaultJobView::new)
        .setRegistryName(id()).createJobEntry());
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> WORK =
        new BuildingEntry.ModuleProducer<>("colonyhorizons:surveyor_work",
            () -> new WorkerBuildingModule(JOB.get(), Skill.Intelligence, Skill.Adaptability, false, b -> 1),
            () -> WorkerBuildingModuleView::new);
    public static final BuildingEntry.ModuleProducer<SurveyorMenuModule,SurveyorMenuView> MENU=new BuildingEntry.ModuleProducer<>("colonyhorizons:surveyor_menu",SurveyorMenuModule::new,()->SurveyorMenuView::new);
    public static final DeferredHolder<BuildingEntry, BuildingEntry> SURVEYOR = BUILDINGS.register("surveyor", () -> new BuildingEntry.Builder()
        .setBuildingBlock(HUT.get()).setBuildingProducer(BuildingSurveyor::new).setBuildingViewProducer(() -> EmptyView::new)
        .setRegistryName(id()).addBuildingModuleProducer(WORK).addBuildingModuleProducer(MENU)
        .addBuildingModuleProducer(BuildingModules.MIN_STOCK).addBuildingModuleProducer(BuildingModules.STATS_MODULE).createBuildingEntry());
    private static ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("colonyhorizons", "surveyor"); }
    public static void initialize(IEventBus bus) {
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENTS.putIfAbsent("surveyor", com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENTS.get("unemployed"))));
        BLOCKS.register(bus); ITEMS.register(bus); JOBS.register(bus); BUILDINGS.register(bus);
        bus.addListener((net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent event) ->
            event.modify(com.minecolonies.api.tileentities.MinecoloniesTileEntities.BUILDING.get(), HUT.get()));
    }
}
