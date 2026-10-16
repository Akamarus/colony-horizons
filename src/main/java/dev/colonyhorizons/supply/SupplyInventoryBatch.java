package dev.colonyhorizons.supply;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Stages only native hut inventories, never combined warehouse/rack capabilities. Server thread only. */
public final class SupplyInventoryBatch implements SupplyNetwork.InventoryAccess {
    private record Inventory(IItemHandlerModifiable live,List<ItemStack> before,List<ItemStack> staged) {}
    private final Map<UUID,Inventory> inventories=new LinkedHashMap<>();
    public SupplyInventoryBatch(Map<UUID,? extends IItemHandlerModifiable> handlers){
        handlers.forEach((id,handler)->{
            var before=new ArrayList<ItemStack>();var staged=new ArrayList<ItemStack>();
            for(int i=0;i<handler.getSlots();i++){before.add(handler.getStackInSlot(i).copy());staged.add(handler.getStackInSlot(i).copy());}
            inventories.put(id,new Inventory(handler,before,staged));
        });
    }
    public int stock(UUID id,ItemStack item){return inventory(id).staged.stream().filter(s->ItemStack.isSameItemSameComponents(s,item)).mapToInt(ItemStack::getCount).sum();}
    public ItemStack insert(UUID id,ItemStack input){
        var v=inventory(id);var rest=input.copy();
        for(int i=0;i<v.staged.size()&&!rest.isEmpty();i++){
            var existing=v.staged.get(i);
            if(!v.live.isItemValid(i,rest)||(!existing.isEmpty()&&!ItemStack.isSameItemSameComponents(existing,rest)))continue;
            int amount=Math.min(rest.getCount(),Math.max(0,Math.min(v.live.getSlotLimit(i),rest.getMaxStackSize())-existing.getCount()));
            if(amount>0){v.staged.set(i,rest.copyWithCount(existing.getCount()+amount));rest.shrink(amount);}
        }
        return rest;
    }
    public ItemStack extract(UUID id,ItemStack item,int count){
        var v=inventory(id);int moved=0;
        for(int i=0;i<v.staged.size()&&moved<count;i++){
            var s=v.staged.get(i);if(!ItemStack.isSameItemSameComponents(s,item))continue;
            // Native hut extraction rules are checked against the live inventory too.
            int amount=Math.min(count-moved,Math.min(s.getCount(),v.live.extractItem(i,count-moved,true).getCount()));
            s.shrink(amount);moved+=amount;
        }
        return moved==0?ItemStack.EMPTY:item.copyWithCount(moved);
    }
    public boolean changed(){return inventories.values().stream().anyMatch(v->!same(v.before,v.staged));}
    public void apply(){
        // Validate every endpoint before changing any endpoint.
        for(var v:inventories.values())for(int i=0;i<v.before.size();i++)
            if(!ItemStack.matches(v.before.get(i),v.live.getStackInSlot(i)))throw new IllegalStateException("Hut inventory changed during handoff");
        for(var v:inventories.values())for(int i=0;i<v.staged.size();i++)
            if(!ItemStack.matches(v.before.get(i),v.staged.get(i)))v.live.setStackInSlot(i,v.staged.get(i).copy());
    }
    private Inventory inventory(UUID id){return Objects.requireNonNull(inventories.get(id),"Unavailable supply endpoint");}
    private static boolean same(List<ItemStack>a,List<ItemStack>b){for(int i=0;i<a.size();i++)if(!ItemStack.matches(a.get(i),b.get(i)))return false;return true;}
}
