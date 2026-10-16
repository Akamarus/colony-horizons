package dev.colonyhorizons.compat.mixin;
import com.ldtteam.structurize.placement.structure.AbstractStructureHandler;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=AbstractStructureHandler.class,remap=false)
public abstract class BarracksUpgradePreservationMixin {
 @Unique private java.util.List<Object> colonyHorizons$signature=java.util.List.of();
 @Unique private Blueprint colonyHorizons$original;
 @Unique private Blueprint colonyHorizons$protected;
 @Inject(method="getBluePrint",at=@At("RETURN"),cancellable=true)
 private void colonyHorizons$preserveTowers(CallbackInfoReturnable<Blueprint> cir){
  var handler=(AbstractStructureHandler)(Object)this;
  if(!(handler instanceof com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler)&&!(handler instanceof com.minecolonies.api.util.CreativeBuildingStructureHandler))return;
  if(!(handler.getWorld() instanceof ServerLevel level)||cir.getReturnValue()==null)return;
  var signature=dev.colonyhorizons.integration.BarracksTowerPreservation.signature(level,handler.getCenterPos(),cir.getReturnValue());
  if(colonyHorizons$original!=cir.getReturnValue()||!signature.equals(colonyHorizons$signature)){
   colonyHorizons$signature=signature;
   colonyHorizons$original=cir.getReturnValue();
   colonyHorizons$protected=dev.colonyhorizons.integration.BarracksTowerPreservation.protect(level,handler.getCenterPos(),colonyHorizons$original);
  }
  cir.setReturnValue(colonyHorizons$protected);
 }
}
