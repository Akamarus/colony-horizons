// SPDX-License-Identifier: GPL-3.0-only
// Adapts MineColonies code by LDTTeam contributors; see NOTICE.
// Modified by Akamarus / Colony Horizons, 2026-10-11.
package dev.colonyhorizons.compat.mixin;

import com.ldtteam.structurize.placement.StructurePhasePlacementResult;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobQuarrier;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import com.minecolonies.core.entity.ai.workers.util.LayerBlueprintIterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage.*;

/** These shafts use wall-mounted decorations: finish access before clearing that layer. */
@Mixin(value=EntityAIQuarrier.class,remap=false)
public abstract class ArcologyQuarryLadderMixin extends AbstractEntityAIStructureWithWorkOrder<JobQuarrier,BuildingMiner> {
 protected ArcologyQuarryLadderMixin(JobQuarrier job){super(job);}
 @Inject(method="goToNextStage",at=@At("HEAD"),cancellable=true,require=1)
 private void colonyHorizons$completeAccessLayer(StructurePhasePlacementResult result,CallbackInfoReturnable<Boolean> cir){
  var order=building.getWorkOrder();
  if(order==null||!"Arcology Horizons".equals(order.getStructurePack())||!(order.getStructurePath().equals("infrastructure/mineshafts/simplequarryshaft1.blueprint")||order.getStructurePath().equals("infrastructure/mineshafts/mediumquarryshaft1.blueprint")))return;
  var iterator=(LayerBlueprintIterator)structurePlacer.getA().getIterator();
  var handler=structurePlacer.getB();int layer=iterator.getLayer();
  // Preserve native placement/material handling and protected-block predicates.
  // Unlike shafts with floor-standing decorations, these wall-mounted ladders
  // and torches can be placed immediately after their own layer's liner is done.
  if(!handler.nextStage()){
   if(layer==0){cir.setReturnValue(false);return;}
   iterator.setLayer(layer-1);building.setTotalStages(3);handler.setStage(BUILD_SOLID);
  }else if(handler.getStage()==CLEAR&&result.getBlockResult().getWorldPos().getY()<=worker.level().getMinBuildHeight()){
   cir.setReturnValue(false);return;
  }
  cir.setReturnValue(true);
 }
}
