// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.SupplyCheckpoint;
import dev.colonyhorizons.supply.SupplyInventoryBatch;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;
import java.util.function.Consumer;

/** Finite bootstrap custody. The founding coordinator owns operation authorization and durable storage. */
public record FoundingCargo(UUID operation,UUID source,Map<ItemKey,Integer> items) {
    public static UUID operationFor(UUID development){return UUID.nameUUIDFromBytes((development+":founding-supplies").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public FoundingCargo {
        Objects.requireNonNull(operation);Objects.requireNonNull(source);items=Map.copyOf(items);
        if(items.size()>128 || items.values().stream().anyMatch(n->n<=0 || n>1_000_000))throw new IllegalArgumentException("Invalid founding cargo quantities");
    }

    /** Staging is read-only; every shortage is discovered before the first live mutation. */
    public static Withdrawal prepare(UUID operation,UUID source,IItemHandlerModifiable inventory,Budget budget,Map<ItemKey,Integer> bill) {
        var cargo=new FoundingCargo(operation,source,bill);
        if(cargo.items.isEmpty())throw new IllegalArgumentException("Empty founding bill");
        var staged=new SupplyInventoryBatch(Map.of(source,inventory));
        for(var entry:cargo.items.entrySet()) {
            var key=entry.getKey();int count=entry.getValue();
            if(count>budget.allowances().getOrDefault(key,0))throw new IllegalArgumentException("Founding bill exceeds allowance");
            if((long)staged.stock(source,key.stack())-count<budget.homeReserves().getOrDefault(key,0))throw new IllegalArgumentException("Founding bill would spend Home reserves");
            if(staged.extract(source,key.stack(),count).getCount()!=count)throw new IllegalArgumentException("Founding material unavailable for extraction");
        }
        return new Withdrawal(cargo,staged);
    }

    public static final class Withdrawal {
        private final FoundingCargo cargo;
        private final SupplyInventoryBatch staged;
        private boolean attempted;
        private Withdrawal(FoundingCargo cargo,SupplyInventoryBatch staged){this.cargo=cargo;this.staged=staged;}
        /** Writer must durably verify each journal; flush must verify the actual native inventory save. */
        public FoundingCargo commit(HolderLookup.Provider registries,Consumer<CompoundTag> writer,Runnable flush) {
            if(attempted)throw new IllegalStateException("Founding transfer already attempted; reconcile its saved custody");
            attempted=true;
            var before=new FoundingCargo(cargo.operation,cargo.source,Map.of()).save(registries);
            new SupplyCheckpoint(writer).commit(before,cargo.save(registries),staged::apply,flush);
            return cargo;
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putUUID("operation",operation);tag.putUUID("source",source);
        var list=new ListTag();items.forEach((key,count)->{var entry=new CompoundTag();entry.put("item",key.stack().save(registries));entry.putInt("quantity",count);list.add(entry);});tag.put("items",list);return tag;
    }
    public static FoundingCargo load(CompoundTag tag,HolderLookup.Provider registries) {
        if(tag.contains("pending"))throw new IllegalStateException("Interrupted founding custody requires recovery; no automatic replay");
        if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1||!tag.hasUUID("operation")||!tag.hasUUID("source")||!tag.contains("items",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid founding custody schema");
        var raw=(ListTag)tag.get("items");
        if(raw.size()>128 || !raw.isEmpty() && raw.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid founding cargo list");
        var items=new HashMap<ItemKey,Integer>();
        for(var element:raw) {
            var entry=(CompoundTag)element;
            if(!entry.contains("item",Tag.TAG_COMPOUND)||!entry.contains("quantity",Tag.TAG_INT))throw new IllegalArgumentException("Invalid founding cargo item");
            var stack=ItemStack.parse(registries,entry.getCompound("item")).orElseThrow(()->new IllegalArgumentException("Unknown founding cargo item"));
            if(stack.getCount()!=1||stack.isEmpty())throw new IllegalArgumentException("Founding cargo identity must have count one");
            if(items.putIfAbsent(new ItemKey(stack),entry.getInt("quantity"))!=null)throw new IllegalArgumentException("Duplicate founding cargo item");
        }
        return new FoundingCargo(tag.getUUID("operation"),tag.getUUID("source"),items);
    }
}
