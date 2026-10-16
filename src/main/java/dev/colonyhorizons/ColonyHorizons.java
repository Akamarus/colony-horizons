package dev.colonyhorizons;

import dev.colonyhorizons.integration.MineColoniesBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

@Mod(ColonyHorizons.ID)
public final class ColonyHorizons {
    public static final String ID = "colonyhorizons";
    public ColonyHorizons(IEventBus bus, net.neoforged.fml.ModContainer container) {
        dev.colonyhorizons.remote.RemoteRuntime.initialize(container, bus);
        if (Boolean.getBoolean("colonyhorizons.foundation")) bus.addListener(dev.colonyhorizons.qa.outpost.FoundationGameTests::register);
        if (Boolean.getBoolean("colonyhorizons.audit")) bus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) -> event.register(dev.colonyhorizons.qa.ResourceAudit.class));
        if (Boolean.getBoolean("colonyhorizons.outpostProbe")) bus.addListener((net.neoforged.neoforge.event.RegisterGameTestsEvent event) -> event.register("Restart".equals(System.getProperty("colonyhorizons.outpostSuite")) ? dev.colonyhorizons.qa.outpost.OutpostRestartProbe.class : "Transfers".equals(System.getProperty("colonyhorizons.outpostSuite")) ? dev.colonyhorizons.qa.outpost.OutpostTransferTests.class : dev.colonyhorizons.qa.outpost.OutpostIntegrationTests.class));
        bus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(MineColoniesBridge::verifyLoaded));
    }
}
