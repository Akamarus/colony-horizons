package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobQuarrier;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Rechoose a native work position when excavation has removed its footing. */
@Mixin(value=EntityAIQuarrier.class,remap=false)
public abstract class ArcologyQuarryWorkPositionMixin extends AbstractEntityAIStructureWithWorkOrder<JobQuarrier,BuildingMiner> {
 protected ArcologyQuarryWorkPositionMixin(JobQuarrier job){super(job);}
 @Inject(method="walkToConstructionSite",at=@At("HEAD"),require=1)
 private void colonyHorizons$forgetExcavatedFooting(BlockPos target,CallbackInfoReturnable<Boolean> cir){
  if(workFrom==null)return;
  var citizen=job.getCitizen();var worker=citizen.getEntity().orElse(null);
  if(worker==null||BlockPosUtil.getDistance(worker.blockPosition(),target)<=10||!(citizen.getWorkBuilding() instanceof BuildingMiner mine))return;
  var order=mine.getWorkOrder();
  if(order==null||!"Arcology Horizons".equals(order.getStructurePack())||!(order.getStructurePath().equals("infrastructure/mineshafts/simplequarryshaft1.blueprint")||order.getStructurePath().equals("infrastructure/mineshafts/mediumquarryshaft1.blueprint")))return;
  var world=worker.level();var feet=world.getBlockState(workFrom);var below=world.getBlockState(workFrom.below());
  if(feet.is(BlockTags.CLIMBABLE)||below.is(BlockTags.CLIMBABLE)||!world.getFluidState(workFrom).isEmpty()||!world.getFluidState(workFrom.below()).isEmpty())return;
  if(feet.getCollisionShape(world,workFrom).isEmpty()&&below.getCollisionShape(world,workFrom.below()).isEmpty())workFrom=null;
 }
}
