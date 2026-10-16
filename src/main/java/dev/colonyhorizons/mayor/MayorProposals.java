// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;

import dev.colonyhorizons.remote.RemoteModels.ItemKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Persisted reviewed intent. Approval alone neither spends stock nor changes a native building. */
public final class MayorProposals extends SavedData {
 private static final String KEY="colonyhorizons_mayor_proposals";
 public enum State { PROPOSED, APPROVED, REJECTED, COMPLETE }
 public record Entry(UUID id,UUID settlement,UUID owner,UUID building,int from,int target,String blueprint,String hash,Map<ItemKey,Integer> cost,State state,long revision){
  public Entry {
   Objects.requireNonNull(id);Objects.requireNonNull(settlement);Objects.requireNonNull(owner);Objects.requireNonNull(building);Objects.requireNonNull(state);
   if(from<1||from>4||target!=from+1||revision<0||blueprint==null||blueprint.isBlank()||blueprint.length()>256||hash==null||!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid upgrade review");
   if(!id.equals(identity(settlement,building,target))||revision!=(state==State.PROPOSED?0:state==State.COMPLETE?2:1))throw new IllegalArgumentException("Invalid upgrade identity or decision revision");
   cost=Map.copyOf(cost);if(cost.size()>128||cost.values().stream().anyMatch(n->n<=0||n>1000000))throw new IllegalArgumentException("Invalid upgrade cost");
  }
  Entry withState(State next){return new Entry(id,settlement,owner,building,from,target,blueprint,hash,cost,next,revision+1);}
 }
 private static UUID identity(UUID settlement,UUID building,int target){return UUID.nameUUIDFromBytes((settlement+":"+building+":"+target).getBytes(StandardCharsets.UTF_8));}
 private final Map<UUID,Entry> proposals=new LinkedHashMap<>();
 public Map<UUID,Entry> entries(){return Map.copyOf(proposals);}
 public Entry propose(UUID settlement,UUID owner,UUID building,int from,String path,String hash,Map<ItemKey,Integer> cost){
  var id=identity(settlement,building,from+1);var next=new Entry(id,settlement,owner,building,from,from+1,path,hash,cost,State.PROPOSED,0);var old=proposals.get(id);
  if(old!=null){if(!old.owner().equals(owner)||!old.blueprint().equals(path)||!old.hash().equals(hash)||!old.cost().equals(cost))throw new IllegalArgumentException("Existing upgrade review changed; preserve it for review");return old;}
  if(proposals.size()>=512)throw new IllegalArgumentException("Upgrade review limit reached");proposals.put(id,next);setDirty();return next;
 }
 public Entry decide(UUID actor,UUID id,long revision,boolean approve){
  var old=Optional.ofNullable(proposals.get(id)).orElseThrow(()->new IllegalArgumentException("Unknown upgrade review"));if(!old.owner().equals(actor))throw new SecurityException("Only the owner can approve this upgrade");if(old.revision()!=revision||old.state()!=State.PROPOSED)throw new IllegalArgumentException("Upgrade review changed; refresh first");var next=old.withState(approve?State.APPROVED:State.REJECTED);proposals.put(id,next);setDirty();return next;
 }
 public void complete(UUID id){var old=proposals.get(id);if(old==null||old.state()!=State.APPROVED)throw new IllegalArgumentException("Upgrade was not approved");proposals.put(id,old.withState(State.COMPLETE));setDirty();}
 private static java.nio.file.Path file(ServerLevel level){return net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");}
 public static MayorProposals get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(()->{if(java.nio.file.Files.exists(file(level)))throw new IllegalStateException("Unreadable upgrade approvals; preserve for recovery");return new MayorProposals();},MayorProposals::load),KEY);}
 public void checkpoint(ServerLevel level){if(get(level)!=this)throw new IllegalStateException("Detached upgrade ledger");var expected=save(new CompoundTag(),level.registryAccess());setDirty();level.getDataStorage().save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();try{if(!NbtIo.readCompressed(file(level),NbtAccounter.create(32L*1024*1024)).getCompound("data").equals(expected))throw new IllegalStateException("Upgrade review save failed");}catch(java.io.IOException e){throw new IllegalStateException("Could not save upgrade review",e);}}
 @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){root.putInt("schema",1);var list=new ListTag();for(var e:proposals.values()){var t=new CompoundTag();t.putUUID("id",e.id());t.putUUID("settlement",e.settlement());t.putUUID("owner",e.owner());t.putUUID("building",e.building());t.putInt("from",e.from());t.putInt("target",e.target());t.putString("blueprint",e.blueprint());t.putString("hash",e.hash());t.putString("state",e.state().name());t.putLong("revision",e.revision());var costs=new ListTag();for(var c:e.cost().entrySet()){var row=new CompoundTag();row.put("item",c.getKey().stack().save(registries));row.putInt("count",c.getValue());costs.add(row);}t.put("cost",costs);list.add(t);}root.put("proposals",list);return root;}
 public static MayorProposals load(CompoundTag root,HolderLookup.Provider registries){if(root.getInt("schema")!=1||!root.contains("proposals",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid upgrade ledger");var result=new MayorProposals();var raw=root.get("proposals");if(!(raw instanceof ListTag rawList)||!rawList.isEmpty()&&rawList.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid proposal list");var list=root.getList("proposals",Tag.TAG_COMPOUND);if(list.size()>512)throw new IllegalArgumentException("Too many upgrade reviews");for(var value:list){var t=(CompoundTag)value;if(!t.hasUUID("id")||!t.hasUUID("owner")||!t.hasUUID("settlement")||!t.hasUUID("building")||!t.contains("cost",Tag.TAG_LIST)||!t.contains("revision",Tag.TAG_LONG)||!(t.get("cost") instanceof ListTag rawCost)||!rawCost.isEmpty()&&rawCost.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Incomplete upgrade review");var costs=new HashMap<ItemKey,Integer>();for(var entry:t.getList("cost",Tag.TAG_COMPOUND)){var row=(CompoundTag)entry;var key=new ItemKey(ItemStack.parseOptional(registries,row.getCompound("item")));if(costs.put(key,row.getInt("count"))!=null)throw new IllegalArgumentException("Duplicate upgrade material");}var e=new Entry(t.getUUID("id"),t.getUUID("settlement"),t.getUUID("owner"),t.getUUID("building"),t.getInt("from"),t.getInt("target"),t.getString("blueprint"),t.getString("hash"),costs,State.valueOf(t.getString("state")),t.getLong("revision"));if(result.proposals.put(e.id(),e)!=null)throw new IllegalArgumentException("Duplicate upgrade review");}return result;}
}
