// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Reservation accounting. The server coordinator owns authorization and durable checkpoints.
 * Dispatched items remain charged even if returned. Closing requires proof the dispatch route stopped.
 */
public final class MayorReservations extends net.minecraft.world.level.saveddata.SavedData {
 private static final String KEY="colonyhorizons_mayor_reservations";
 private static java.nio.file.Path file(net.minecraft.server.level.ServerLevel level){return net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");}
 public static MayorReservations get(net.minecraft.server.level.ServerLevel level){
  return level.getDataStorage().computeIfAbsent(new Factory<>(()->{if(java.nio.file.Files.exists(file(level)))throw new IllegalStateException("Unreadable Mayor reservations; preserve for recovery");return new MayorReservations();},MayorReservations::load),KEY);
 }
 @Override public CompoundTag save(CompoundTag target,HolderLookup.Provider registries){return target.merge(save(registries));}
 public void checkpoint(net.minecraft.server.level.ServerLevel level){
  if(get(level)!=this)throw new IllegalStateException("Detached reservation ledger");var expected=save(level.registryAccess());setDirty();level.getDataStorage().save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
  try{if(!NbtIo.readCompressed(file(level),NbtAccounter.create(32L*1024*1024)).getCompound("data").equals(expected))throw new IllegalStateException("Mayor reservation save verification failed");}
  catch(java.io.IOException e){throw new IllegalStateException("Could not checkpoint Mayor reservations",e);}
 }
 /** Reserve before any supply route is created. Home inventories remain untouched. */
 public static boolean reserve(net.minecraft.server.level.ServerLevel level,UUID development,UUID operation,Map<ItemKey,Integer> cost){
  var data=dev.colonyhorizons.remote.RemoteDevelopmentData.get(level);var record=data.find(development).orElseThrow();
  if(!data.foundingManaged(development)||!MayorService.available(level,development))return false;
  var owner=level.getServer().getPlayerList().getPlayer(record.owner());
  if(owner==null||owner.isRemoved()||owner instanceof net.neoforged.neoforge.common.util.FakePlayer||owner.serverLevel()!=level||!dev.colonyhorizons.remote.RemoteTickets.snapshot(level).containsKey(development))return false;
  var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(record.draft().request().colonyId(),level.dimension());
  var source=record.draft().request().homeSupply();var chunk=new net.minecraft.world.level.ChunkPos(source);var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
  if(colony==null||!record.owner().equals(colony.getPermissions().getOwner())||!level.hasChunkAt(source)||claim==null||claim.getOwningColony()!=colony.getID()||record.draft().chunks().contains(chunk)||colony.getServerBuildingManager().getBuilding(source)==null||!(level.getBlockEntity(source) instanceof com.minecolonies.core.tileentities.TileEntityColonyBuilding hut))return false;
  var inventory=dev.colonyhorizons.supply.WarehouseInventory.resolve(level,hut).inventory();var stock=new HashMap<ItemKey,Integer>();for(int i=0;i<inventory.getSlots();i++){var stack=inventory.getStackInSlot(i);if(!stack.isEmpty())stock.merge(new ItemKey(stack),stack.getCount(),Math::addExact);}
  var founding=dev.colonyhorizons.remote.FoundingCargo.load(data.foundingCargo(development).orElseThrow(),level.registryAccess());var ledger=get(level);
  if(!ledger.reserve(record.owner(),development,source,operation,record.draft().request().budget(),founding.items(),stock,cost))return false;
  ledger.checkpoint(level);return true;
 }
 public record Entry(UUID operation,UUID owner,UUID development,BlockPos source,Map<ItemKey,Integer> cost,Map<ItemKey,Integer> dispatched,Map<ItemKey,Integer> reserves,boolean closed){
  public Entry{Objects.requireNonNull(operation);Objects.requireNonNull(owner);Objects.requireNonNull(development);source=Objects.requireNonNull(source).immutable();cost=quantities(cost);dispatched=quantities(dispatched);reserves=quantities(reserves);if(cost.isEmpty())throw new IllegalArgumentException("Empty reservation");for(var e:dispatched.entrySet())if(e.getValue()>cost.getOrDefault(e.getKey(),0))throw new IllegalArgumentException("Invalid reservation custody");}
 }
 private final Map<UUID,Entry> entries=new LinkedHashMap<>();
 public Map<UUID,Entry> entries(){return Map.copyOf(entries);}
 public boolean reserve(UUID owner,UUID development,BlockPos source,UUID operation,Budget budget,Map<ItemKey,Integer> priorSpent,Map<ItemKey,Integer> stock,Map<ItemKey,Integer> cost){
  var candidate=new Entry(operation,owner,development,source,cost,Map.of(),budget.homeReserves(),false);priorSpent=quantities(priorSpent);stock=quantities(stock);
  var old=entries.get(operation);if(old!=null){if(!old.owner().equals(owner)||!old.development().equals(development)||!old.source().equals(source)||!old.cost().equals(candidate.cost())||!old.reserves().equals(candidate.reserves()))throw new IllegalArgumentException("Reservation identity conflict");return !old.closed();}
  if(entries.size()>=4096)throw new IllegalArgumentException("Reservation ledger full");
  for(var request:candidate.cost().entrySet()){
   var key=request.getKey();long charged=priorSpent.getOrDefault(key,0),held=0;int reserve=budget.homeReserves().getOrDefault(key,0);
   for(var e:entries.values()){
    if(e.development().equals(development)){if(!e.owner().equals(owner)||!e.source().equals(source))throw new IllegalArgumentException("Development reservation identity changed");charged+=e.closed()?e.dispatched().getOrDefault(key,0):e.cost().getOrDefault(key,0);}
    if(!e.closed()&&e.owner().equals(owner)&&e.source().equals(source)){held+=e.cost().getOrDefault(key,0)-e.dispatched().getOrDefault(key,0);reserve=Math.max(reserve,e.reserves().getOrDefault(key,0));}
   }
   if(charged+request.getValue()>budget.allowances().getOrDefault(key,0)||(long)stock.getOrDefault(key,0)-reserve-held<request.getValue())return false;
  }
  entries.put(operation,candidate);setDirty();return true;
 }
 public void recordDispatched(UUID operation,Map<ItemKey,Integer> dispatched){
  var e=Objects.requireNonNull(entries.get(operation),"Unknown reservation");var next=quantities(dispatched);
  if(e.closed()||e.dispatched().entrySet().stream().anyMatch(v->next.getOrDefault(v.getKey(),0)<v.getValue()))throw new IllegalArgumentException("Reservation spending cannot be reset");
  entries.put(operation,new Entry(e.operation(),e.owner(),e.development(),e.source(),e.cost(),next,e.reserves(),false));setDirty();
 }
 public void close(UUID operation){var e=Objects.requireNonNull(entries.get(operation),"Unknown reservation");entries.put(operation,new Entry(e.operation(),e.owner(),e.development(),e.source(),e.cost(),e.dispatched(),e.reserves(),true));setDirty();}
 private static Map<ItemKey,Integer> quantities(Map<ItemKey,Integer> source){var result=Map.copyOf(source);if(result.size()>128||result.values().stream().anyMatch(n->n<0||n>1_000_000))throw new IllegalArgumentException("Invalid reservation quantities");return result;}
 public CompoundTag save(HolderLookup.Provider registries){
  var tag=new CompoundTag();tag.putInt("schema",1);var list=new ListTag();
  for(var e:entries.values()){var t=new CompoundTag();t.putUUID("operation",e.operation());t.putUUID("owner",e.owner());t.putUUID("development",e.development());t.putLong("source",e.source().asLong());t.putBoolean("closed",e.closed());t.put("cost",items(e.cost(),registries));t.put("dispatched",items(e.dispatched(),registries));t.put("reserves",items(e.reserves(),registries));list.add(t);}tag.put("entries",list);return tag;
 }
 public static MayorReservations load(CompoundTag tag,HolderLookup.Provider registries){
  if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1)throw new IllegalArgumentException("Unknown reservation schema");var result=new MayorReservations();
  for(var element:list(tag,"entries",4096)){var t=(CompoundTag)element;if(!t.hasUUID("operation")||!t.hasUUID("owner")||!t.hasUUID("development")||!t.contains("source",Tag.TAG_LONG)||!t.contains("closed",Tag.TAG_BYTE)||(t.getByte("closed")!=0&&t.getByte("closed")!=1))throw new IllegalArgumentException("Invalid reservation identity");
   var e=new Entry(t.getUUID("operation"),t.getUUID("owner"),t.getUUID("development"),BlockPos.of(t.getLong("source")),read(t,"cost",registries),read(t,"dispatched",registries),read(t,"reserves",registries),t.getBoolean("closed"));if(result.entries.putIfAbsent(e.operation(),e)!=null)throw new IllegalArgumentException("Duplicate reservation");
  }return result;
 }
 private static ListTag items(Map<ItemKey,Integer> items,HolderLookup.Provider registries){var list=new ListTag();items.forEach((key,count)->{var t=new CompoundTag();t.put("item",key.stack().save(registries));t.putInt("count",count);list.add(t);});return list;}
 private static Map<ItemKey,Integer> read(CompoundTag tag,String key,HolderLookup.Provider registries){var items=new HashMap<ItemKey,Integer>();for(var element:list(tag,key,128)){var t=(CompoundTag)element;if(!t.contains("item",Tag.TAG_COMPOUND)||!t.contains("count",Tag.TAG_INT))throw new IllegalArgumentException("Invalid reservation item");var item=ItemStack.parse(registries,t.getCompound("item")).orElseThrow();if(item.getCount()!=1||items.putIfAbsent(new ItemKey(item),t.getInt("count"))!=null)throw new IllegalArgumentException("Duplicate reservation item");}return quantities(items);}
 private static ListTag list(CompoundTag tag,String key,int max){if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Missing reservation list");var list=(ListTag)tag.get(key);if(list.size()>max||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid reservation list");return list;}
}

