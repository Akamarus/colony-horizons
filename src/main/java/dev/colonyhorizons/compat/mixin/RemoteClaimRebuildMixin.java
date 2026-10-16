package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.BackUpHelper;
import dev.colonyhorizons.remote.RemoteClaimService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BackUpHelper.class,remap=false)
public abstract class RemoteClaimRebuildMixin {
    @Inject(method="reclaimChunks",at=@At("TAIL"))
    private static void colonyHorizons$restoreRemote(Colony colony,CallbackInfo ci){RemoteClaimService.reconcileColony(colony);}
}
