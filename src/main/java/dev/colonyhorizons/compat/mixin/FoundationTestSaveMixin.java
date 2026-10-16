package dev.colonyhorizons.compat.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disposable foundation tests still flush on shutdown, without a disk barrier per chunk write. */
@Mixin(MinecraftServer.class)
public abstract class FoundationTestSaveMixin {
    @Inject(method = "forceSynchronousWrites", at = @At("HEAD"), cancellable = true)
    private void colonyHorizons$batchTestWrites(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof GameTestServer && Boolean.getBoolean("colonyhorizons.foundation")) {
            cir.setReturnValue(false);
        }
    }
}
