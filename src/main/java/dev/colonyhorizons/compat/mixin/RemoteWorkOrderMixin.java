package dev.colonyhorizons.compat.mixin;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.core.colony.workorders.*;
import dev.colonyhorizons.remote.RemoteWorkerLocality;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value={WorkOrderBuilding.class,WorkOrderDecoration.class},remap=false)
public abstract class RemoteWorkOrderMixin {
 @Inject(method="canBuild",at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$localWork(IBuilding building,CallbackInfoReturnable<Boolean> result){if(!RemoteWorkerLocality.permitsWork(building,((IWorkOrder)this).getLocation()))result.setReturnValue(false);}
 @Inject(method="canBuildIgnoringDistance",at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$localManualWork(IBuilding building,BlockPos position,int level,CallbackInfoReturnable<Boolean> result){if(!RemoteWorkerLocality.permitsWork(building,((IWorkOrder)this).getLocation()))result.setReturnValue(false);}
}
