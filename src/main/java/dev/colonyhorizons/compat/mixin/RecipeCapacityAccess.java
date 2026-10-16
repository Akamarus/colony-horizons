package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=AbstractCraftingBuildingModule.class,remap=false)
public interface RecipeCapacityAccess {
 @Invoker("getMaxRecipes") int colonyHorizons$capacity();
 @Invoker("getActiveRecipes") int colonyHorizons$used();
}
