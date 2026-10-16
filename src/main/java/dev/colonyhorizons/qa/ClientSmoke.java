package dev.colonyhorizons.qa;

import dev.colonyhorizons.ColonyHorizons;
import dev.colonyhorizons.integration.MineColoniesBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in, isolated title-screen smoke check. Never opens a world. */
@EventBusSubscriber(modid=ColonyHorizons.ID, value=Dist.CLIENT)
public final class ClientSmoke {
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("colonyhorizons.smoke")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)
            mc.setScreen(new TitleScreen());
        if (!(mc.screen instanceof TitleScreen)) {
            if (++ticks > 600) throw new IllegalStateException("Startup did not reach title: " + mc.screen);
            return;
        }
        if (!MineColoniesBridge.ready()) throw new IllegalStateException("Addon API integration failed");
        if (++ticks == 20) {
            System.out.println("COLONY_HORIZONS_SMOKE PASSED: real MineColonies loaded; API initialized; title screen reached");
            mc.stop();
        }
    }
}
