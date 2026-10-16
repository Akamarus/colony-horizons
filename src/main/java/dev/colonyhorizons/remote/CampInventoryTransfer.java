// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import dev.colonyhorizons.supply.SupplyInventoryBatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;
/** One bounded, durably journaled transfer from the worker into their actual camp inventory. */
public final class CampInventoryTransfer {
 private CampInventoryTransfer(){}
 public static boolean unload(IItemHandlerModifiable worker,IItemHandlerModifiable camp,boolean keepDirt,Runnable intent,Runnable flush,Runnable receipt){
  if(worker==camp)throw new IllegalArgumentException("Camp cannot be the worker inventory");
  var from=UUID.randomUUID();var to=UUID.randomUUID();
  int dirtReserve=keepDirt?64:0;
  for(int slot=0;slot<worker.getSlots();slot++){
   var stack=worker.getStackInSlot(slot);if(stack.isEmpty()||stack.isDamageableItem()||stack.has(DataComponents.FOOD))continue;
   int count=stack.getCount();if(stack.is(Items.DIRT)){int retained=Math.min(dirtReserve,count);dirtReserve-=retained;count-=retained;}if(count==0)continue;
   var batch=new SupplyInventoryBatch(Map.of(from,worker,to,camp));var extracted=batch.extract(from,stack,count);if(extracted.isEmpty())continue;
   var remaining=batch.insert(to,extracted);int moved=extracted.getCount()-remaining.getCount();if(moved==0)continue;
   if(!remaining.isEmpty()&&!batch.insert(from,remaining).isEmpty())throw new IllegalStateException("Cannot restore unstored cargo");
   intent.run();batch.apply();flush.run();receipt.run();return true;
  }
  return false;
 }
}
