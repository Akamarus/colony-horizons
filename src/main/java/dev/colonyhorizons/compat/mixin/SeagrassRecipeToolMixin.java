package dev.colonyhorizons.compat.mixin;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.core.colony.buildings.workerbuildings.plantation.modules.specific.SeagrassPlantModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Keep the native plantation recipe display consistent with the tool needed for drops. */
@Mixin(value=SeagrassPlantModule.class,remap=false)
public abstract class SeagrassRecipeToolMixin {
 @Inject(method="getRequiredTool",at=@At("HEAD"),cancellable=true,require=1)
 private void colonyHorizons$displayShears(CallbackInfoReturnable<EquipmentTypeEntry> cir){cir.setReturnValue(ModEquipmentTypes.shears.get());}
}
