package dev.colonyhorizons.compat.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingDeliveryman;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman;
import dev.colonyhorizons.supply.NativeCourierGuard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

@Mixin(value=EntityAIWorkDeliveryman.class,remap=false)
public abstract class CourierWorkMixin extends AbstractEntityAIInteract<JobDeliveryman,BuildingDeliveryman> {
    protected CourierWorkMixin(JobDeliveryman job){super(job);}
    @org.spongepowered.asm.mixin.Unique private boolean colonyHorizons$ready() {
        var decision=NativeCourierGuard.checkWork(worker);
        if(!decision.allowed()) {
            worker.getNavigation().stop();
            worker.getCitizenData().setVisibleStatus(new com.minecolonies.api.entity.citizen.VisibleCitizenStatus(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecolonies","textures/icons/work/delivery.png"),
                "Delivery waiting: "+decision.reason()));
        }
        return decision.allowed();
    }
    @Inject(method={"pickup","prepareDelivery","deliver","dump","decide"},at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$work(CallbackInfoReturnable<IAIState> cir) {
        if(!colonyHorizons$ready()) {
            worker.getNavigation().stop();setDelay(100);cir.setReturnValue(getState());
        }
    }
    @Inject(method="decide",at=@At("RETURN"),cancellable=true)
    private void colonyHorizons$resume(CallbackInfoReturnable<IAIState> cir) {
        if(cir.getReturnValue()!=com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DUMPING
            || !(job instanceof dev.colonyhorizons.supply.CourierDeliveryState state) || !state.colonyHorizons$hasOngoingDelivery()
            || !NativeCourierGuard.checkWork(worker).allowed())return;
        var level=(net.minecraft.server.level.ServerLevel)worker.level();
        var endpoint=dev.colonyhorizons.supply.NativeCourierDistricts.resolve(level,job.getColony().getID(),building.getPosition());
        if(endpoint.status()==dev.colonyhorizons.supply.CourierDistrictPolicy.Status.ACTIVE)
            cir.setReturnValue(com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.PREPARE_DELIVERY);
    }
    @Inject(method="checkIfExecute",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$execute(CallbackInfoReturnable<Boolean> cir) {
        if(!colonyHorizons$ready()) {worker.getNavigation().stop();cir.setReturnValue(false);}
    }
    @Inject(method="gatherIfInTileEntity",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$gather(BlockEntity tile,ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        if(!colonyHorizons$ready())cir.setReturnValue(false);
    }
    @Inject(method="pickupFromBuilding",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$pickup(IBuilding source,CallbackInfoReturnable<Boolean> cir) {
        if(!colonyHorizons$ready())cir.setReturnValue(false);
    }
}
