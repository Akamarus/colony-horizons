package dev.colonyhorizons.compat.mixin;

import com.minecolonies.core.colony.Colony;
import dev.colonyhorizons.supply.ManagedCourierColony;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=Colony.class,remap=false)
public abstract class CourierColonyMarkerMixin implements ManagedCourierColony {
    @Unique private boolean colonyHorizons$managed;
    @Override public boolean colonyHorizons$managedCouriers() { return colonyHorizons$managed; }
    @Override public void colonyHorizons$markManagedCouriers() {
        if (!colonyHorizons$managed) {
            colonyHorizons$managed=true;
            ((Colony)(Object)this).markDirty();
        }
    }
    @Inject(method="read",at=@At("HEAD"))
    private void colonyHorizons$read(CompoundTag tag,HolderLookup.Provider provider,CallbackInfo ci) {
        // Any present marker, even malformed, keeps management enabled and fails closed.
        colonyHorizons$managed=tag.contains("colonyhorizons:managed_couriers");
    }
    @Inject(method="write",at=@At("RETURN"))
    private void colonyHorizons$write(CompoundTag tag,HolderLookup.Provider provider,CallbackInfoReturnable<CompoundTag> cir) {
        if(colonyHorizons$managed)cir.getReturnValue().putBoolean("colonyhorizons:managed_couriers",true);
    }
}
