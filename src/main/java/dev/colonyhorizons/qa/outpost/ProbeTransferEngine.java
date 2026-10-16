package dev.colonyhorizons.qa.outpost;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.List;
/** QA prototype only; its caller owns endpoint authorization and persistence. */
public final class ProbeTransferEngine {
    public record TransferResult(int movedItems,ProbeShipment.State state) {}
    public static TransferResult load(ProbeShipment shipment,IItemHandler source,List<ItemStack> requested){
        if(shipment.state!=ProbeShipment.State.RESERVED)return new TransferResult(0,shipment.state);
        shipment.pending.clear();requested.stream().filter(stack->!stack.isEmpty()).map(ItemStack::copy).forEach(shipment.pending::add);
        shipment.state=ProbeShipment.State.LOADING;
        int moved=0;
        for(var wanted:requested){
            int remaining=wanted.getCount();
            for(int slot=0;slot<source.getSlots() && remaining>0;slot++){
                if(!ItemStack.isSameItemSameComponents(wanted,source.getStackInSlot(slot)))continue;
                var extracted=source.extractItem(slot,remaining,false);
                if(!extracted.isEmpty()){
                    shipment.cargo.add(extracted.copy());remaining-=extracted.getCount();moved+=extracted.getCount();
                }
            }
        }
        shipment.pending.clear();
        shipment.state=shipment.cargo.isEmpty()?ProbeShipment.State.DELAYED:ProbeShipment.State.IN_TRANSIT;
        return new TransferResult(moved,shipment.state);
    }
    public static TransferResult unload(ProbeShipment shipment,IItemHandler destination){
        if(shipment.state!=ProbeShipment.State.IN_TRANSIT && shipment.state!=ProbeShipment.State.UNLOADING && shipment.state!=ProbeShipment.State.DELAYED)return new TransferResult(0,shipment.state);
        shipment.pending.clear();shipment.cargo.stream().map(ItemStack::copy).forEach(shipment.pending::add);
        shipment.state=ProbeShipment.State.UNLOADING;
        int moved=0;
        for(int index=0;index<shipment.cargo.size();index++){
            ItemStack remainder=shipment.cargo.get(index).copy();
            for(int slot=0;slot<destination.getSlots() && !remainder.isEmpty();slot++){
                int before=remainder.getCount();
                remainder=destination.insertItem(slot,remainder,false);
                int inserted=before-remainder.getCount();
                moved+=inserted;shipment.delivered+=inserted;
                shipment.cargo.set(index,remainder.copy());
            }
        }
        shipment.cargo.removeIf(ItemStack::isEmpty);
        shipment.pending.clear();
        shipment.state=shipment.cargo.isEmpty()?ProbeShipment.State.DELIVERED:ProbeShipment.State.DELAYED;
        return new TransferResult(moved,shipment.state);
    }
}
