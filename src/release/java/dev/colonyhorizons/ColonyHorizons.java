// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Akamarus and contributors
package dev.colonyhorizons;
import dev.colonyhorizons.integration.MineColoniesBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
/** Production entry point: no QA registration or test runtime references. */
@Mod(ColonyHorizons.ID)
public final class ColonyHorizons {
    public static final String ID = "colonyhorizons";
    public ColonyHorizons(IEventBus bus, net.neoforged.fml.ModContainer container) {
        dev.colonyhorizons.remote.RemoteRuntime.initialize(container, bus);
        bus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(MineColoniesBridge::verifyLoaded));
    }
}
