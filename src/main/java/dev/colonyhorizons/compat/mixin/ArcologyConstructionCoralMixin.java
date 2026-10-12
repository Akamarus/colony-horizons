package dev.colonyhorizons.compat.mixin;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.workorders.WorkOrderPlantationField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CoralBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Let a native builder add the pond water before live coral dries out. */
@Mixin(CoralBlock.class)
public abstract class ArcologyConstructionCoralMixin {
 @Inject(method="tick",at=@At("HEAD"),cancellable=true,require=1)
 private void colonyHorizons$pendingPondWater(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci){
  for(var direction:Direction.values())if(level.getFluidState(pos.relative(direction)).is(FluidTags.WATER))return;
  var colony=IColonyManager.getInstance().getColonyByPosFromWorld(level,pos);if(colony==null)return;
  for(var order:colony.getWorkManager().getWorkOrders().values()){
   if(order instanceof WorkOrderPlantationField && "Arcology Horizons".equals(order.getStructurePack())
     && "infrastructure/plantationfields/seapickle1.blueprint".equals(order.getStructurePath())
     && order.getBlueprint()!=null && order.getBoundingBox().contains(Vec3.atCenterOf(pos))){
    // Recheck after cancellation/completion. Ordinary dry coral still dies.
    level.scheduleTick(pos,state.getBlock(),40);ci.cancel();return;
   }
  }
 }
}
