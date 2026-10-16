package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.CitizenData;
import com.minecolonies.api.colony.ICitizenData;
import dev.colonyhorizons.remote.FoundingTravel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=CitizenData.class,remap=false)
public abstract class FoundingTravelGuardMixin {
    @Inject(method="updateEntityIfNecessary",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$guardFoundingArrival(CallbackInfo ci){if(FoundingTravel.blocksSpawn((ICitizenData)(Object)this)||dev.colonyhorizons.surveyor.SurveyService.blocksSpawn((ICitizenData)(Object)this))ci.cancel();}
}
