package dev.colonyhorizons.supply;

import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;

/** Write-ahead custody barrier. An interrupted handoff is quarantined, never blindly replayed. */
public final class SupplyCheckpoint {
    private final Consumer<CompoundTag> writer;
    private boolean blocked;
    public SupplyCheckpoint(Consumer<CompoundTag> writer){this.writer=writer;}
    public boolean blocked(){return blocked;}
    public void commit(CompoundTag before,CompoundTag after,Runnable apply,Runnable flush){
        if(blocked)throw new IllegalStateException("Supply handoff requires recovery");
        try{
            var pending=before.copy();pending.put("pending",after.copy());
            writer.accept(pending); // durable before the first external inventory mutation
            apply.run();
            flush.run(); // native inventory persistence must finish before custody is published
            writer.accept(after.copy());
        }catch(RuntimeException e){blocked=true;throw e;}
    }
}
