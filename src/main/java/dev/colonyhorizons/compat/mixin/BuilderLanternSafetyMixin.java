package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Salvage lanterns through normal mining before their support can trigger falling-light physics. */
@Mixin(value=EntityAIStructureBuilder.class,remap=false)
public abstract class BuilderLanternSafetyMixin extends AbstractEntityAIStructureWithWorkOrder<JobBuilder,BuildingBuilder> {
 protected BuilderLanternSafetyMixin(JobBuilder job){super(job);}
 @Inject(method="mineBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z",at=@At("HEAD"),cancellable=true,require=1)
 private void colonyHorizons$collectAttachedLantern(BlockPos support,BlockPos safeStand,CallbackInfoReturnable<Boolean> cir){
  var state=world.getBlockState(support);
  if(state.isAir()||state.is(Blocks.BEDROCK)||state.getDestroySpeed(world,support)<0)return;
  for(boolean hanging:new boolean[]{true,false}){
   var pos=hanging?support.below():support.above();var lamp=world.getBlockState(pos);
   if(lamp.getBlock() instanceof LanternBlock&&lamp.getValue(LanternBlock.HANGING)==hanging){
    // Five-argument overload preserves tools, delay, drops and XP without recursing.
    // Always retry the original support on a later builder step, even after collecting the light.
    mineBlock(pos,safeStand,true,true,null);
    cir.setReturnValue(false);return;
   }
  }
 }
}
