// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/** Shared initialization for both development and public entry points. */
public final class RemoteRuntime {
    private RemoteRuntime() {}
    public static void initialize(ModContainer container,IEventBus bus) {
        dev.colonyhorizons.surveyor.SurveyorRegistration.initialize(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post event)->{
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level){dev.colonyhorizons.surveyor.SurveyService.tick(level);dev.colonyhorizons.surveyor.GatheringService.tick(level);}
        });
        dev.colonyhorizons.mayor.MayorJobRegistration.initialize(bus);
        container.registerConfig(ModConfig.Type.SERVER,RemoteLimits.SPEC);
        bus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event)->event.register(RemoteTickets.CONTROLLER));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RemoteRuntime::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event)->{
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level && event.getEntity() instanceof com.minecolonies.api.entity.citizen.AbstractEntityCitizen && (FoundingTravel.blocksEntity(level,event.getEntity().getUUID())||dev.colonyhorizons.surveyor.SurveyService.blocksEntity(level,event.getEntity().getUUID())))event.setCanceled(true);
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event)->{
            if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level){RemoteTickets.release(level);dev.colonyhorizons.surveyor.GatheringService.unload(level);dev.colonyhorizons.surveyor.SurveyService.unload(level);}
        });
    }
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final java.util.Map<net.minecraft.server.level.ServerLevel,String> FAILURES=new java.util.WeakHashMap<>();
    private static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)||level.getGameTime()%20!=0)return;
        try {RemoteActivityService.tick(level);RemoteDevelopmentData.existing(level).ifPresent(data->{for(var record:data.all()){FoundingGrantService.resumeReturns(level,record.id());FoundingTravel.reconcile(level,record.id());if(level.getGameTime()%100==0 && data.foundingManaged(record.id())){RemoteFoundingService.advance(level,record.id());PreparedFounding.cleanup(level,record.id());RemoteCare.tick(level,record.id());}}});FAILURES.remove(level);}
        catch(RuntimeException e){RemoteTickets.release(level);if(!e.toString().equals(FAILURES.put(level,e.toString())))LOGGER.error("Remote settlements paused: {}",e.toString());}
    }
}
