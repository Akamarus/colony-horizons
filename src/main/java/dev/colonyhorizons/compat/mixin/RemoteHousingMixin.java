package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.api.colony.ICitizenData;
import dev.colonyhorizons.remote.RemoteWorkerLocality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=AbstractAssignedCitizenModule.class,remap=false)
public abstract class RemoteHousingMixin {
 @Inject(method="assignCitizen",at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$localHome(ICitizenData citizen,CallbackInfoReturnable<Boolean> result){
  if((Object)this instanceof LivingBuildingModule home&&!RemoteWorkerLocality.permitsHome(citizen,home.getBuilding()))result.setReturnValue(false);
 }
}
