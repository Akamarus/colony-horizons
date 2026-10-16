// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
public final class GatheringSavedData extends SavedData {
 private static final String KEY="colonyhorizons_gathering";
 private final Map<UUID,GatheringRecord> records=new LinkedHashMap<>();
 private final Map<UUID,CompoundTag> cargo=new HashMap<>();
 public static GatheringSavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(GatheringSavedData::new,GatheringSavedData::load),KEY);}
 public static Optional<GatheringSavedData> existing(ServerLevel level){return Optional.ofNullable(level.getDataStorage().get(new Factory<>(GatheringSavedData::new,GatheringSavedData::load),KEY));}
 public Optional<GatheringRecord> find(UUID id){return Optional.ofNullable(records.get(id));}
 public List<GatheringRecord> records(){return List.copyOf(records.values());}
 public Optional<GatheringRecord> active(int colony){return records.values().stream().filter(r->r.colony()==colony&&r.active()).findFirst();}
 public void add(GatheringRecord r){
  if(records.containsKey(r.id())||active(r.colony()).isPresent()||r.revision()!=0||r.state()!=GatheringRecord.State.PREPARING)throw new IllegalStateException("Another expedition is active or record is invalid");
  if(records.size()>=4096){var oldest=records.values().stream().filter(this::safelyDelivered).findFirst().orElseThrow(()->new IllegalStateException("Expedition history is full of unresolved custody"));records.remove(oldest.id());cargo.remove(oldest.id());}
  records.put(r.id(),r);setDirty();
 }
 private boolean safelyDelivered(GatheringRecord r){
  if(r.state()!=GatheringRecord.State.COMPLETE)return false;var t=cargo.get(r.id());
  return t!=null&&t.getInt("version")==1&&t.contains("paid",Tag.TAG_BYTE)&&t.getBoolean("paid")&&t.contains("delivered",Tag.TAG_BYTE)&&t.getBoolean("delivered")&&t.contains("earned",Tag.TAG_BYTE)&&t.getBoolean("earned")== (r.remaining()==0)&&!t.contains("pending")&&!t.contains("tool")&&!t.contains("reward");
 }
 public void replace(GatheringRecord expected,GatheringRecord next){
  if(!expected.equals(records.get(expected.id()))||!expected.id().equals(next.id())||(next.revision()!=expected.revision()+1&&!(expected.state()==GatheringRecord.State.GATHERING&&expected.remaining()>1&&expected.tick().equals(next)))||expected.colony()!=next.colony()||!expected.owner().equals(next.owner())||!expected.hut().equals(next.hut())||expected.worker()!=next.worker()||!expected.workerUuid().equals(next.workerUuid())||!expected.discovery().equals(next.discovery())||!expected.offer().equals(next.offer()))throw new IllegalStateException("Gathering identity or revision changed");
  boolean legal=false;
  if(expected.state()==GatheringRecord.State.GATHERING)legal=expected.tick().equals(next);
  if(!legal&&next.state()==GatheringRecord.State.RETURNING&&expected.state()!=GatheringRecord.State.PREPARING&&expected.state()!=GatheringRecord.State.RECOVERY_REQUIRED&&expected.active())legal=expected.returning(next.status()).equals(next);
  if(!legal&&expected.state()!=next.state())try{legal=expected.transition(next.state(),next.status()).equals(next);}catch(IllegalStateException ignored){}
  if(!legal&&expected.state()==next.state())legal=expected.message(next.status()).equals(next);
  if(!legal)throw new IllegalStateException("Invalid gathering progress");records.put(next.id(),next);setDirty();
 }
 public CompoundTag cargo(UUID id){if(!records.containsKey(id))throw new IllegalArgumentException("Unknown expedition");return cargo.getOrDefault(id,new CompoundTag()).copy();}
 public void writeCargo(UUID id,CompoundTag next){if(!records.containsKey(id))throw new IllegalArgumentException("Unknown expedition");cargo.put(id,next.copy());setDirty();}
 public void checkpoint(ServerLevel level){if(get(level)!=this)throw new IllegalStateException("Detached gathering state");var expected=save(new CompoundTag(),level.registryAccess());setDirty();level.getDataStorage().save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();var file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");try{if(!NbtIo.readCompressed(file,NbtAccounter.create(16L*1024*1024)).getCompound("data").equals(expected))throw new IllegalStateException("Gathering save verification failed");}catch(java.io.IOException e){throw new IllegalStateException("Gathering save failed",e);}}
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider regs){tag.putInt("version",1);var list=new ListTag();for(var r:records.values()){var t=r.save();if(cargo.containsKey(r.id()))t.put("cargo",cargo(r.id()));list.add(t);}tag.put("expeditions",list);return tag;}
 public static GatheringSavedData load(CompoundTag tag,HolderLookup.Provider regs){if(tag.getInt("version")!=1||!tag.contains("expeditions",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid gathering save");var rawList=(ListTag)tag.get("expeditions");if(!rawList.isEmpty()&&rawList.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid expedition list type");var list=tag.getList("expeditions",Tag.TAG_COMPOUND);if(list.size()>4096)throw new IllegalArgumentException("Too many expeditions");var d=new GatheringSavedData();var active=new HashSet<Integer>();for(var raw:list){var t=(CompoundTag)raw;var r=GatheringRecord.load(t);if(d.records.putIfAbsent(r.id(),r)!=null||r.active()&&!active.add(r.colony()))throw new IllegalArgumentException("Duplicate active expedition");if(t.contains("cargo"))d.cargo.put(r.id(),t.getCompound("cargo").copy());}return d;}
}
