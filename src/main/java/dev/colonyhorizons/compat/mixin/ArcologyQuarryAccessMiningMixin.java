package dev.colonyhorizons.compat.mixin;

import com.ldtteam.structurize.placement.BlockPlacementResult;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlockInfo;
import com.ldtteam.structurize.util.ChangeStorage;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Access is installed before clearing: route occupied access cells through native mining. */
@Mixin(value=StructurePlacer.class,remap=false)
public abstract class ArcologyQuarryAccessMiningMixin {
 @Shadow @Final private IStructureHandler handler;
 @Inject(method="handleBlockPlacement",at=@At("HEAD"),cancellable=true,require=1)
 private void colonyHorizons$mineAccessObstruction(Level world,BlockPos pos,ChangeStorage changes,BlockInfo info,CallbackInfoReturnable<BlockPlacementResult> cir){
  if(!(handler instanceof BuildingStructureHandler<?,?> buildingHandler)||buildingHandler.getStage()!=BuildingProgressStage.DECORATE)return;
  var blueprint=handler.getBluePrint();
  if(!"Arcology Horizons".equals(blueprint.getPackName())||!("simplequarryshaft1.blueprint".equals(blueprint.getName())||"mediumquarryshaft1.blueprint".equals(blueprint.getName())))return;
  var desired=info.getState();var existing=world.getBlockState(pos);
  if((desired.is(Blocks.LADDER)||desired.is(Blocks.WALL_TORCH))&&!existing.isAir()&&!existing.equals(desired))
   cir.setReturnValue(new BlockPlacementResult(pos,BlockPlacementResult.Result.BREAK_BLOCK));
 }
}
