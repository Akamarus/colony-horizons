package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.colony.requestsystem.resolvers.WarehouseRequestResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.WarehouseConcreteRequestResolver;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import dev.colonyhorizons.supply.NativeCourierGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={WarehouseRequestResolver.class,WarehouseConcreteRequestResolver.class},remap=false)
public abstract class WarehouseCountLocalityMixin {
 @Inject(method="getWarehouseInternalCount",at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$count(BuildingWareHouse warehouse,IRequest<?> request,CallbackInfoReturnable<Integer> cir){if(!NativeCourierGuard.warehouseStockAllowed(warehouse.getColony().getRequestManager(),warehouse.getPosition(),request))cir.setReturnValue(0);}
}
