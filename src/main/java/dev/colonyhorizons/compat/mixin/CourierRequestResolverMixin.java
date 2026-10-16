package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.requestsystem.resolvers.DeliverymenRequestResolver;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import dev.colonyhorizons.supply.NativeCourierGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

@Mixin(value=DeliverymenRequestResolver.class,remap=false)
public abstract class CourierRequestResolverMixin {
    @Inject(method="canResolveRequest",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$eligibility(IRequestManager manager,IRequest<?> request,CallbackInfoReturnable<Boolean> cir) {
        if(!NativeCourierGuard.eligible(manager,((DeliverymenRequestResolver<?>)(Object)this).getLocation().getInDimensionLocation(),request))cir.setReturnValue(false);
    }
    @Inject(method="attemptResolveRequest",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$attempt(IRequestManager manager,IRequest<?> request,CallbackInfoReturnable<List<IToken<?>>> cir) {
        if(!NativeCourierGuard.eligible(manager,((DeliverymenRequestResolver<?>)(Object)this).getLocation().getInDimensionLocation(),request))cir.setReturnValue(null);
    }
    @Inject(method="resolveRequest",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$enqueue(IRequestManager manager,IRequest<?> request,CallbackInfo ci) {
        if(!NativeCourierGuard.eligible(manager,((DeliverymenRequestResolver<?>)(Object)this).getLocation().getInDimensionLocation(),request))ci.cancel();
    }
}
