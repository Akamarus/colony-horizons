package dev.colonyhorizons.qa.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** NeoForge normally disables GameTests in installed servers. This separate harness enables tests only. */
@Mixin(value=net.neoforged.neoforge.gametest.GameTestHooks.class,remap=false)
public abstract class PackagedGameTestMixin {
    @Inject(method="isGametestEnabled",at=@At("HEAD"),cancellable=true)
    private static void enabled(CallbackInfoReturnable<Boolean> cir){if(Boolean.getBoolean("colonyhorizons.packagedQa"))cir.setReturnValue(true);}
}
