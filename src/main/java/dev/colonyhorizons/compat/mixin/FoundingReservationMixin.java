package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.api.colony.ICitizenData;
import dev.colonyhorizons.remote.FoundingReservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=WorkerBuildingModule.class,remap=false)
public abstract class FoundingReservationMixin {
 @Inject(method="assignCitizen",at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$reserveFounders(ICitizenData citizen,CallbackInfoReturnable<Boolean> result){if(!FoundingReservation.permits(citizen,(WorkerBuildingModule)(Object)this))result.setReturnValue(false);}
}
