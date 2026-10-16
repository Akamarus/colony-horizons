package dev.colonyhorizons.client.recipes;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.client.gui.modules.building.WindowListRecipes;
import com.ldtteam.blockui.controls.Button;
import net.minecraft.network.chat.Component;
/** Retains native recipe removal, disabling and priority controls. */
public final class CatalogRecipeList extends WindowListRecipes {
 private final CraftingModuleView crafting;
 public CatalogRecipeList(CraftingModuleView module){super(module);crafting=module;findPaneOfTypeByID("crafting",Button.class).setText(Component.literal("Recipe catalog"));}
 @Override public void craftingClicked(){new RecipeCatalogWindow(crafting,this).open();}
}
