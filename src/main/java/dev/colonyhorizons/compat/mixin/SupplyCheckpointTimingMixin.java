package dev.colonyhorizons.compat.mixin;
import dev.colonyhorizons.supply.NativeSupplyCheckpoint;
import dev.colonyhorizons.qa.outpost.SupplyTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=NativeSupplyCheckpoint.class,remap=false)
public abstract class SupplyCheckpointTimingMixin {
    @Inject(method="flushAndVerify",at=@At("HEAD")) private static void before(CallbackInfo ci){SupplyTiming.checkpointBegin();}
    @Inject(method="flushAndVerify",at=@At("RETURN")) private static void after(CallbackInfo ci){SupplyTiming.checkpointEnd();}
}
