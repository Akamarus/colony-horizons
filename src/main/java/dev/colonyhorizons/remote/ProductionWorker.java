// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** Native item filters only; no job replacement, recipe simulation, seeds or output creation. */
public final class ProductionWorker {
 private ProductionWorker(){}
 public static WorkerBuildingModule module(IBuilding hut,ProductionProfile profile){
  var module=hut.getModulesByType(WorkerBuildingModule.class).stream().filter(m->m.getJobEntry().getKey().equals(profile.jobId())).findFirst().orElseThrow(()->new IllegalArgumentException("Wrong native producer hut"));
  if(!hut.isBuilt()||hut.getBuildingLevel()<profile.minimumLevel())throw new IllegalArgumentException("Producer needs native building level "+profile.minimumLevel());
  return module;
 }
 public static boolean configure(IBuilding hut,ProductionProfile profile){
  module(hut,profile);
  if(hut instanceof BuildingLumberjack){
   var filter=hut.getModule(BuildingModules.ITEMLIST_SAPLING);
   return select(filter,IColonyManager.getInstance().getCompatibilityManager().getCopyOfSaplings(),BuiltInRegistries.ITEM.get(profile.starterItem()));
  }
  if(hut instanceof BuildingFlorist){
   return select(hut.getModule(BuildingModules.FLORIST_ITEMS),BuildingFlorist.getPlantablesForBuildingLevel(5),BuiltInRegistries.ITEM.get(profile.target()));
  }
  return false;
 }
 private static boolean select(ItemListModule filter,Collection<ItemStorage> candidates,net.minecraft.world.item.Item wanted){
  if(candidates.stream().noneMatch(s->s.getItem()==wanted))throw new IllegalArgumentException("Requested resource is not supported by this native producer");
  boolean changed=false;
  for(var item:candidates){boolean blocked=filter.isItemInList(item),reject=item.getItem()!=wanted;if(blocked==reject)continue;if(reject)filter.addItem(item);else filter.removeItem(item);changed=true;}
  return changed;
 }
}
