package dev.colonyhorizons.qa;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-only bridge: the separate QA jar has its own mod id. */
@EventBusSubscriber(modid="colonyhorizons_qa", value=Dist.CLIENT)
public final class PackagedMenuPreview {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if(Boolean.getBoolean("colonyhorizons.copyFounding")){var mc=net.minecraft.client.Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());if(CopyFoundingProbe.done){mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen());mc.stop();}return;}
        MenuPreviewClient.tick(event);
    }
}
