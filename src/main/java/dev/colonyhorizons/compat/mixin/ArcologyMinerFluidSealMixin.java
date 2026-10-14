package dev.colonyhorizons.compat.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.MINER_MINING_SHAFT;

/** Seal inflow before removing a shaft plug, including water above the native horizontal scan. */
@Mixin(value=EntityAIStructureMiner.class,remap=false)
public abstract class ArcologyMinerFluidSealMixin extends AbstractEntityAIStructureWithWorkOrder<JobMiner,BuildingMiner> {
 protected ArcologyMinerFluidSealMixin(JobMiner job){super(job);}
 @Shadow private BlockPos minerWorkingLocation;
 @Shadow private void setBlockFromInventory(BlockPos pos,Block block,BlockState state){throw new AssertionError();}

 @Inject(method="doShaftMining",at=@At(value="INVOKE",target="Lcom/minecolonies/core/entity/ai/workers/production/EntityAIStructureMiner;mineBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"),cancellable=true,require=1)
 private void colonyHorizons$sealBeforeExcavating(CallbackInfoReturnable<IAIState> cir){
  if(!"Arcology Horizons".equals(building.getStructurePack())||minerWorkingLocation==null)return;
  var fluids=new ArrayList<BlockPos>();
  for(var direction:Direction.values()){
   var pos=minerWorkingLocation.relative(direction);
   if(!world.hasChunkAt(pos)){cir.setReturnValue(MINER_MINING_SHAFT);return;}
   if(world.getFluidState(pos).isEmpty())continue;
   // Never replace a waterlogged structure or its inventory to seal a mine.
   if(!world.getBlockState(pos).canBeReplaced()||world.getBlockEntity(pos)!=null){cir.setReturnValue(MINER_MINING_SHAFT);return;}
   fluids.add(pos);
  }
  if(fluids.isEmpty())return;
  var fill=building.getSetting(BuildingMiner.FILL_BLOCK).getValue().getBlock();
  if(!checkIfRequestForItemExistOrCreate(new ItemStack(fill),fluids.size(),fluids.size())){
   cir.setReturnValue(MINER_MINING_SHAFT);return;
  }
  for(var pos:fluids){
   setBlockFromInventory(pos,fill,fill.defaultBlockState());
   if(!world.getBlockState(pos).is(fill)||!world.getFluidState(pos).isEmpty()){
    cir.setReturnValue(MINER_MINING_SHAFT);return;
   }
  }
 }
}
