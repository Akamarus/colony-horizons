package dev.colonyhorizons.compat.mixin;

import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import com.minecolonies.core.util.WorkerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned native detection recognizes short seagrass, but harvests tall seagrass barehanded. */
@Mixin(value=WorkerUtil.class,remap=false)
public abstract class PlanterSeagrassToolsMixin {
 @Inject(method="getBestToolForBlock",at=@At("HEAD"),cancellable=true,require=1)
 private static void colonyHorizons$tallSeagrassShears(BlockState state,float hardness,AbstractBuilding building,BlockGetter level,BlockPos pos,CallbackInfoReturnable<EquipmentTypeEntry> cir){
  if(building instanceof BuildingPlantation&&state.is(Blocks.TALL_SEAGRASS)
    &&(!building.hasModule(SettingsModule.class)||building.getFirstModuleOccurance(SettingsModule.class).getSettingValueOrDefault(AbstractBuilding.USE_SHEARS,true)))
   cir.setReturnValue(ModEquipmentTypes.shears.get());
 }
}
