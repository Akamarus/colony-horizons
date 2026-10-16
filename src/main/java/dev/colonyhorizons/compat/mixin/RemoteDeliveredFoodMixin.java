package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Hungry managed residents use their local workplace or warehouse, including during a hut walk. */
@Mixin(value=EntityAIEatTask.class,remap=false)
public abstract class RemoteDeliveredFoodMixin {
 @Shadow @Final private EntityCitizen citizen;
 @Shadow private com.minecolonies.api.colony.buildings.IBuilding restaurant;
 @Inject(method={"goToHut","searchRestaurant","goToRestaurant","getFoodYourself"},at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$localFood(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir){var state=dev.colonyhorizons.remote.RemoteCare.food(citizen);if(state!=null){restaurant=null;cir.setReturnValue(state);}}
}
