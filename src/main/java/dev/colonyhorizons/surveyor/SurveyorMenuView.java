// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.ldtteam.blockui.views.BOWindow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
public final class SurveyorMenuView extends AbstractBuildingModuleView {
 @Override public void deserialize(RegistryFriendlyByteBuf buf){}
 @Override public Component getDesc(){return Component.literal("Exploration");}
 @Override public ResourceLocation getIconResourceLocation(){return ResourceLocation.fromNamespaceAndPath("minecolonies","textures/gui/modules/main.png");}
 @Override public BOWindow getWindow(){return new dev.colonyhorizons.client.supply.WindowSurveyorTab(this);}
}
