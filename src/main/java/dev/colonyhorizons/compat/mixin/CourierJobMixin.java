package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import dev.colonyhorizons.supply.NativeCourierGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value=JobDeliveryman.class,remap=false)
public abstract class CourierJobMixin implements dev.colonyhorizons.supply.CourierDeliveryState {
    @org.spongepowered.asm.mixin.Shadow
    private com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore getDataStore(){throw new AssertionError();}
    @Override public boolean colonyHorizons$hasOngoingDelivery() {
        return getDataStore().getQueue().stream().anyMatch(getDataStore().getOngoingDeliveries()::contains);
    }
    @Redirect(method="getCurrentTask",at=@At(value="INVOKE",target="Lcom/minecolonies/core/colony/buildings/modules/WarehouseRequestQueueModule;getMutableRequestList()Ljava/util/List;"))
    private List<IToken<?>> colonyHorizons$localQueue(WarehouseRequestQueueModule module) {
        return NativeCourierGuard.selectionQueue((JobDeliveryman)(Object)this,module);
    }
    @Inject(method="getTaskListWithSameDestination",at=@At("RETURN"),cancellable=true)
    private void colonyHorizons$batch(IRequest<? extends Delivery> request,CallbackInfoReturnable<List<IRequest<? extends Delivery>>> cir) {
        cir.setReturnValue(cir.getReturnValue().stream().filter(r->NativeCourierGuard.checkRequest((JobDeliveryman)(Object)this,r).allowed()).toList());
    }
}
