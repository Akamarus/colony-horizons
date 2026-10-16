package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.ldtteam.blockui.views.BOWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=CraftingModuleView.class,remap=false)
public abstract class CraftingCatalogWindowMixin {
 @Inject(method="getWindow",at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$catalog(CallbackInfoReturnable<BOWindow> cir){
  if(dev.colonyhorizons.recipes.RecipeCatalogConfig.ENABLED.get())cir.setReturnValue(new dev.colonyhorizons.client.recipes.CatalogRecipeList((CraftingModuleView)(Object)this));
 }
}
