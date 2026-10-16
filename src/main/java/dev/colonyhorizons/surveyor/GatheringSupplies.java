// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import dev.colonyhorizons.supply.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;
import java.util.function.Consumer;
public final class GatheringSupplies {
 private GatheringSupplies(){}
 public static Departure prepare(IItemHandlerModifiable hut,GatheringOffer offer){
  if(!GatheringCatalog.find(offer.target()).filter(offer::equals).isPresent())throw new IllegalArgumentException("Unknown gathering offer");
  var id=UUID.randomUUID();var batch=new SupplyInventoryBatch(Map.of(id,hut));if(batch.extract(id,new ItemStack(Items.BREAD),offer.bread()).getCount()!=offer.bread())throw new IllegalArgumentException("Put two bread in the Exploration Hut");
  var tool=ItemStack.EMPTY;
  if(offer.tool()!=SurveyResource.Tool.HAND){
   for(int i=0;i<hut.getSlots();i++){var candidate=hut.getStackInSlot(i);boolean correct=offer.tool()==SurveyResource.Tool.AXE?candidate.getItem() instanceof AxeItem:candidate.is(Items.SHEARS);if(!correct||!candidate.isDamageableItem()||candidate.getMaxDamage()-candidate.getDamageValue()<offer.wear())continue;tool=batch.extract(id,candidate,1);if(!tool.isEmpty())break;}
   if(tool.isEmpty())throw new IllegalArgumentException("Supply the required tool with enough durability for a full trip");
   int damage=tool.getDamageValue()+offer.wear();if(damage>=tool.getMaxDamage())tool=ItemStack.EMPTY;else tool.setDamageValue(damage);
  }
  return new Departure(batch,new GatheringCargo(true,tool,ItemStack.EMPTY,false,false));
 }
 public static final class Departure {
  private final SupplyInventoryBatch batch;private final GatheringCargo cargo;private boolean attempted;
  private Departure(SupplyInventoryBatch batch,GatheringCargo cargo){this.batch=batch;this.cargo=cargo;}
  public GatheringCargo commit(HolderLookup.Provider regs,Consumer<CompoundTag> writer,Runnable flush){if(attempted)throw new IllegalStateException("Payment already attempted");attempted=true;new SupplyCheckpoint(writer).commit(GatheringCargo.empty().save(regs),cargo.save(regs),batch::apply,flush);return cargo;}
 }
}
