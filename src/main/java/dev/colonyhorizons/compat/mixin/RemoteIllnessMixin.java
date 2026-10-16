package dev.colonyhorizons.compat.mixin;
import com.minecolonies.core.entity.ai.minimal.EntityAISickTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=EntityAISickTask.class,remap=false)
public abstract class RemoteIllnessMixin {
 @Shadow @Final private EntityCitizen citizen;
 @Inject(method={"searchHospital","goToHut","goToHospital","waitForCure"},at=@At("HEAD"),cancellable=true)
 private void colonyHorizons$localRecovery(CallbackInfoReturnable<IState> cir){var state=dev.colonyhorizons.remote.RemoteCare.recover(citizen);if(state!=null)cir.setReturnValue(state);}
}
