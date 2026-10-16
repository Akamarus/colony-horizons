// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.client.supply;
import dev.colonyhorizons.surveyor.SurveyorMenuView;
import net.minecraft.resources.ResourceLocation;
public final class WindowSurveyorTab extends com.minecolonies.core.client.gui.AbstractModuleWindow<SurveyorMenuView> {
 public WindowSurveyorTab(SurveyorMenuView view){super(view,ResourceLocation.fromNamespaceAndPath("colonyhorizons","gui/supply/surveyor-tab.xml"));registerButton("surveys",()->SupplyMenuClient.openSurvey(view.getColony().getID(),view.getBuildingView().getID(),this));}
}
