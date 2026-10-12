package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.jobs.JobNetherWorker;
import com.minecolonies.core.entity.ai.workers.production.EntityAIWorkNether;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MineColonies 1.1.1403 loses the live AI on travel; its period trip cap then blocks the new AI from resuming. */
@Mixin(value = EntityAIWorkNether.class, remap = false)
public abstract class NetherWorkerResumeMixin {
    @Unique private JobNetherWorker colonyHorizons$job;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void colonyHorizons$rememberJob(JobNetherWorker job, CallbackInfo ci) {
        colonyHorizons$job = job;
    }

    @Inject(method = "hasWorkToDo", at = @At("HEAD"), cancellable = true)
    private void colonyHorizons$resumeExpedition(CallbackInfoReturnable<Boolean> cir) {
        // Pending work is the current expedition, not permission to start another trip.
        if (colonyHorizons$job != null && colonyHorizons$job.isInNether()) {
            cir.setReturnValue(true);
        }
    }
}
