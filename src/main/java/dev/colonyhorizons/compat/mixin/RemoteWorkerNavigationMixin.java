package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import dev.colonyhorizons.remote.RemoteWorkerLocality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=MinecoloniesAdvancedPathNavigate.class,remap=false)
public abstract class RemoteWorkerNavigationMixin extends AbstractAdvancedPathNavigate {
 protected RemoteWorkerNavigationMixin(Mob entity,Level world){super(entity,world);}
 @Inject(method="setPathJob",at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$localDestination(AbstractPathJob job,BlockPos destination,double speed,boolean safe,CallbackInfoReturnable<PathResult<?>> result){
  // Null-destination flee/combat jobs retain native emergency movement.
  if(destination!=null&&ourEntity instanceof AbstractEntityCitizen citizen&&!RemoteWorkerLocality.permits(citizen.getCitizenData(),destination)){
   stop();result.setReturnValue(null);
  }
 }
}
