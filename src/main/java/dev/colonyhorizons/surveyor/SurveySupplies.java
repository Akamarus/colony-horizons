// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import dev.colonyhorizons.supply.SupplyInventoryBatch;
import dev.colonyhorizons.supply.SupplyCheckpoint;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;
import java.util.function.Consumer;

/** Reserve an expedition's real provisions before hiding its worker for protected travel. */
public final class SurveySupplies {
 private SurveySupplies(){}
 public static Departure prepare(IItemHandlerModifiable hut,SurveyResource resource){
  var key=UUID.randomUUID();var staged=new SupplyInventoryBatch(Map.of(key,hut));
  if(staged.extract(key,new ItemStack(Items.BREAD),8).getCount()!=8)throw new IllegalArgumentException("Put 8 bread in the Surveyor's Hut");
  ItemStack equipment=ItemStack.EMPTY;
  if(resource.tool()!=SurveyResource.Tool.HAND){
   for(int slot=0;slot<hut.getSlots();slot++){
    var candidate=hut.getStackInSlot(slot);
    boolean accepted=resource.tool()==SurveyResource.Tool.SHEARS?candidate.is(Items.SHEARS):candidate.getItem() instanceof AxeItem;
    if(!accepted||candidate.isDamageableItem()&&candidate.getDamageValue()>=candidate.getMaxDamage())continue;
    var extracted=staged.extract(key,candidate,1);if(!extracted.isEmpty()){equipment=extracted;break;}
   }
   if(equipment.isEmpty())throw new IllegalArgumentException(resource.tool()==SurveyResource.Tool.SHEARS?"Put usable shears in the Surveyor's Hut":"Put a usable axe in the Surveyor's Hut");
  }
  return new Departure(staged,new SurveyCargo(true,equipment,List.of()));
 }
 public static final class Departure {
  private final SupplyInventoryBatch staged;private final SurveyCargo cargo;private boolean attempted;
  private Departure(SupplyInventoryBatch staged,SurveyCargo cargo){this.staged=staged;this.cargo=cargo;}
  public SurveyCargo commit(HolderLookup.Provider registries,Consumer<CompoundTag> writer,Runnable flush){
   if(attempted)throw new IllegalStateException("Expedition supplies already attempted");attempted=true;
   new SupplyCheckpoint(writer).commit(SurveyCargo.empty().save(registries),cargo.save(registries),staged::apply,flush);return cargo;
  }
 }
}
