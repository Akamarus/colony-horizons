package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import dev.colonyhorizons.supply.NativeCourierGuard;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import static com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState.*;

@Mixin(value=MinecoloniesAdvancedPathNavigate.class,remap=false)
public abstract class CourierNavigationMixin extends AbstractAdvancedPathNavigate {
    protected CourierNavigationMixin(Mob mob,Level level){super(mob,level);}
    @Unique private boolean colonyHorizons$managed(EntityCitizen citizen) {
        if(citizen==null)return false;
        var work=citizen.getCitizenData().getWorkBuilding();
        return dev.colonyhorizons.supply.NativeCourierDistricts.resolve((net.minecraft.server.level.ServerLevel)citizen.level(),
            citizen.getCitizenData().getColony().getID(),work==null?null:work.getPosition()).status()!=dev.colonyhorizons.supply.CourierDistrictPolicy.Status.UNMANAGED;
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lcom/minecolonies/api/entity/pathfinding/IStuckHandler;checkStuck(Lnet/minecraft/world/entity/ai/navigation/PathNavigation;)V"))
    private void colonyHorizons$noWorkTeleport(com.minecolonies.api.entity.pathfinding.IStuckHandler handler,net.minecraft.world.entity.ai.navigation.PathNavigation navigation) {
        if(!colonyHorizons$managed(colonyHorizons$workingCourier()))handler.checkStuck(navigation);
    }
    @Inject(method="setPathJob",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$dispatch(com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob job,net.minecraft.core.BlockPos dest,double speed,boolean safe,
        CallbackInfoReturnable<com.minecolonies.core.entity.pathfinding.pathresults.PathResult> cir) {
        var citizen=colonyHorizons$workingCourier();
        if(!colonyHorizons$managed(citizen))return;
        // Reject before native distance fallback/discard or asynchronous path submission.
        if(dest==null || job.getStart().distSqr(dest)>900*900 || !NativeCourierGuard.checkDestination(citizen,dest).allowed()) {
            colonyHorizons$stopWork();cir.setReturnValue(null);
        }
    }
    @Unique private EntityCitizen colonyHorizons$workingCourier() {
        if(!(ourEntity instanceof EntityCitizen citizen) || citizen.getCitizenData()==null ||
            !(citizen.getCitizenData().getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman))return null;
        var state=citizen.getCitizenAI().getState();
        return state==WORK || state==WORKING?citizen:null;
    }
    @Inject(method="moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$accept(Path candidate,double speed,CallbackInfoReturnable<Boolean> cir) {
        var citizen=colonyHorizons$workingCourier();
        if(citizen!=null && !NativeCourierGuard.checkPath(citizen,candidate).allowed()) {
            colonyHorizons$stopWork();cir.setReturnValue(false);
        }
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void colonyHorizons$advance(CallbackInfo ci) {
        var citizen=colonyHorizons$workingCourier();
        if(citizen!=null && !NativeCourierGuard.checkPath(citizen,getPath()).allowed()) {
            colonyHorizons$stopWork();ci.cancel();
        }
    }
    @Unique private void colonyHorizons$stopWork() {
        ((MinecoloniesAdvancedPathNavigate)(Object)this).stop();
        ourEntity.getMoveControl().setWantedPosition(ourEntity.getX(),ourEntity.getY(),ourEntity.getZ(),0);
        var velocity=ourEntity.getDeltaMovement();ourEntity.setDeltaMovement(0,velocity.y,0);
    }
}
