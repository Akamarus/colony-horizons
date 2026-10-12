package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.buildings.modules.QuarryModule;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned MineColonies1.1.1403 adds positive quarry depth to the lower corner. */
@Mixin(value=QuarryModule.class,remap=false)
public abstract class QuarryFootprintMixin {
 @Inject(method="getAdditionalCorners",at=@At("RETURN"),cancellable=true,require=1)
 private void colonyHorizons$descendingQuarryBounds(CallbackInfoReturnable<Tuple<BlockPos,BlockPos>> cir){
  var corners=cir.getReturnValue();var lower=corners.getA();
  // Keep native horizontal footprint, upper limit and already-correct offsets.
  if(lower.getY()>0)cir.setReturnValue(new Tuple<>(new BlockPos(lower.getX(),-lower.getY(),lower.getZ()),corners.getB()));
 }
}
