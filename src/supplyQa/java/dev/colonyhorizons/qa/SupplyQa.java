package dev.colonyhorizons.qa;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
/** Separate, disposable test harness. Never included in the addon artifact. */
@Mod("colonyhorizons_qa")
public final class SupplyQa {
    public SupplyQa(IEventBus bus){bus.addListener(dev.colonyhorizons.qa.outpost.FoundationGameTests::register);}
}
