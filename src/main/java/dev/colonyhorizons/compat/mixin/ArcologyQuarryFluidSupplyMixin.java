package dev.colonyhorizons.compat.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobQuarrier;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.NEEDS_ITEM;

/** Keep the barrier intact until native filling can seal every adjacent fluid cell. */
@Mixin(value=EntityAIQuarrier.class,remap=false)
public abstract class ArcologyQuarryFluidSupplyMixin extends AbstractEntityAIStructureWithWorkOrder<JobQuarrier,BuildingMiner> {
 protected ArcologyQuarryFluidSupplyMixin(JobQuarrier job){super(job);}
 @Inject(method="doMining",at=@At("HEAD"),cancellable=true,require=1)
 private void colonyHorizons$requireFluidFill(CallbackInfoReturnable<IAIState> cir){
  if(blockToMine==null)return;
  var order=building.getWorkOrder();
  if(order==null||!"Arcology Horizons".equals(order.getStructurePack())
   ||!(order.getStructurePath().equals("infrastructure/mineshafts/simplequarryshaft1.blueprint")
    ||order.getStructurePath().equals("infrastructure/mineshafts/mediumquarryshaft1.blueprint")))return;
  int required=0;
  for(var direction:Direction.values())if(!world.getFluidState(blockToMine.relative(direction)).isEmpty())required++;
  if(required==0)return;
  var fill=building.getSetting(BuildingMiner.FILL_BLOCK).getValue().getBlock();
  if(!checkIfRequestForItemExistOrCreate(new ItemStack(fill),required,required))cir.setReturnValue(NEEDS_ITEM);
 }
}
