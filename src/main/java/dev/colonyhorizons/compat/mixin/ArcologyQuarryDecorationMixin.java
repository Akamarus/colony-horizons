package dev.colonyhorizons.compat.mixin;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlueprintPositionInfo;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Current-layer access must not apply blueprint air before native mining collects it. */
@Mixin(value=AbstractEntityAIStructure.class,remap=false)
public abstract class ArcologyQuarryDecorationMixin {
 @Inject(method="skipDecorate",at=@At("HEAD"),cancellable=true,require=1)
 private void colonyHorizons$wallMountedAccessOnly(BlueprintPositionInfo info,BlockPos pos,IStructureHandler handler,CallbackInfoReturnable<Boolean> cir){
  if(!((Object)this instanceof EntityAIQuarrier))return;
  var blueprint=handler.getBluePrint();
  if(!"Arcology Horizons".equals(blueprint.getPackName())||!("simplequarryshaft1.blueprint".equals(blueprint.getName())||"mediumquarryshaft1.blueprint".equals(blueprint.getName())))return;
  var state=info.getBlockInfo().getState();
  if(!state.is(Blocks.LADDER)&&!state.is(Blocks.WALL_TORCH))cir.setReturnValue(true);
  // Let the native predicate retain protected-block checks for access blocks.
 }
}
