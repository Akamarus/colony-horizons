package dev.colonyhorizons.outpost;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class OutpostSavedData extends SavedData {
 private static final String KEY="colonyhorizons_outposts";
 private final ResourceKey<Level> dimension;
 private final Map<UUID,OutpostRecord> records=new LinkedHashMap<>();
 public OutpostSavedData(ResourceKey<Level> dimension){this.dimension=Objects.requireNonNull(dimension);}
 public static OutpostSavedData get(ServerLevel level){
  return level.getDataStorage().computeIfAbsent(new Factory<>(()->{
   var file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");
   // Vanilla swallows deserializer errors. An existing file must never become a fresh writable index.
   if(java.nio.file.Files.exists(file))throw new IllegalStateException("Outpost data exists but could not be loaded; preserve it for recovery: "+file);
   return new OutpostSavedData(level.dimension());
  },
   (tag,registries)->load(tag,registries,level.dimension())),KEY);
 }
 public Optional<OutpostRecord> find(UUID id){return Optional.ofNullable(records.get(id));}
 public List<OutpostRecord> all(){return List.copyOf(records.values());}
 public void register(OutpostRecord record){
  if(!dimension.equals(record.dimension()))throw new IllegalArgumentException("Dimension mismatch");
  var prior=records.get(record.id());
  if(prior!=null && prior.colonyId()!=record.colonyId())throw new IllegalArgumentException("Cannot change outpost owner");
  for(var other:records.values()){
   if(other.id().equals(record.id()))continue;
   if(!Collections.disjoint(other.approvedChunks(),record.approvedChunks()))throw new IllegalArgumentException("Outpost claims overlap");
   if(other.colonyId()==record.colonyId() && !Collections.disjoint(other.residentIds(),record.residentIds()))
    throw new IllegalArgumentException("Citizen already belongs to another district");
  }
  records.put(record.id(),record);setDirty();
 }
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
  tag.putInt("schema",1);tag.putString("dimension",dimension.location().toString());
  var list=new ListTag();
  for(var r:records.values()){
   var t=new CompoundTag();t.putUUID("id",r.id());t.putInt("colony",r.colonyId());t.putString("name",r.name());
   t.putLongArray("chunks",r.approvedChunks().stream().mapToLong(ChunkPos::toLong).sorted().toArray());
   t.putIntArray("development",box(r.development()));t.putIntArray("forestry",box(r.forestry()));
   t.putIntArray("residents",r.residentIds().stream().mapToInt(i->i).sorted().toArray());t.putIntArray("workers",r.workerIds().stream().mapToInt(i->i).sorted().toArray());
   t.putString("state",r.state().name());r.terminal().ifPresent(p->t.putLong("terminal",p.asLong()));
   var blockers=new ListTag();r.blockers().forEach(b->blockers.add(StringTag.valueOf(b)));t.put("blockers",blockers);
   var buildings=new ListTag();for(var b:r.buildings().values()){
    var bt=new CompoundTag();bt.putLong("anchor",b.anchor().asLong());bt.putString("type",b.type().toString());
    bt.putString("pack",b.packName());bt.putString("blueprint",b.blueprintPath());bt.putIntArray("volume",box(b.reservedVolume()));buildings.add(bt);
   }t.put("buildings",buildings);list.add(t);
  }tag.put("outposts",list);return tag;
 }
 public static OutpostSavedData load(CompoundTag tag,HolderLookup.Provider registries,ResourceKey<Level> dimension){
  require(tag,"schema",Tag.TAG_INT);require(tag,"dimension",Tag.TAG_STRING);requireList(tag,"outposts",Tag.TAG_COMPOUND);
  if(tag.getInt("schema")!=1)throw new IllegalArgumentException("Unsupported outpost schema");
  if(!tag.getString("dimension").equals(dimension.location().toString()))throw new IllegalArgumentException("Stored dimension mismatch");
  var data=new OutpostSavedData(dimension);
  for(var element:tag.getList("outposts",Tag.TAG_COMPOUND)){
   var t=(CompoundTag)element;
   if(!t.hasUUID("id"))throw new IllegalArgumentException("Invalid outpost UUID");
   require(t,"colony",Tag.TAG_INT);require(t,"name",Tag.TAG_STRING);require(t,"chunks",Tag.TAG_LONG_ARRAY);
   for(String key:List.of("development","forestry","residents","workers"))require(t,key,Tag.TAG_INT_ARRAY);
   require(t,"state",Tag.TAG_STRING);requireList(t,"buildings",Tag.TAG_COMPOUND);requireList(t,"blockers",Tag.TAG_STRING);
   if(t.contains("terminal"))require(t,"terminal",Tag.TAG_LONG);
   UUID id=t.getUUID("id");
   if(data.records.containsKey(id))throw new IllegalArgumentException("Duplicate outpost ID");
   var chunks=new HashSet<ChunkPos>();for(long c:t.getLongArray("chunks"))chunks.add(new ChunkPos(c));
   var refs=new HashMap<BlockPos,OutpostRecord.BuildingRef>();
   for(var entry:t.getList("buildings",Tag.TAG_COMPOUND)){
    var b=(CompoundTag)entry;require(b,"anchor",Tag.TAG_LONG);require(b,"volume",Tag.TAG_INT_ARRAY);
    for(String key:List.of("type","pack","blueprint"))require(b,key,Tag.TAG_STRING);
    var pos=BlockPos.of(b.getLong("anchor"));
    if(refs.put(pos,new OutpostRecord.BuildingRef(pos,ResourceLocation.parse(b.getString("type")),b.getString("pack"),b.getString("blueprint"),readBox(b.getIntArray("volume"))))!=null)throw new IllegalArgumentException("Duplicate building anchor");
   }
   var blockers=new ArrayList<String>();for(var b:t.getList("blockers",Tag.TAG_STRING))blockers.add(b.getAsString());
   data.register(new OutpostRecord(id,t.getInt("colony"),dimension,t.getString("name"),chunks,readBox(t.getIntArray("development")),readBox(t.getIntArray("forestry")),refs,ids(t.getIntArray("residents")),ids(t.getIntArray("workers")),t.contains("terminal",Tag.TAG_LONG)?Optional.of(BlockPos.of(t.getLong("terminal"))):Optional.empty(),OutpostRecord.State.valueOf(t.getString("state")),blockers));
  }
  data.setDirty(false);return data;
 }
 private static void require(CompoundTag tag,String key,int type){
  if(!tag.contains(key,type))throw new IllegalArgumentException("Missing or invalid outpost field: "+key);
 }
 private static void requireList(CompoundTag tag,String key,int elementType){
  require(tag,key,Tag.TAG_LIST);
  var list=(ListTag)tag.get(key);
  if(!list.isEmpty() && list.getElementType()!=elementType)throw new IllegalArgumentException("Invalid outpost list: "+key);
 }
 private static Set<Integer> ids(int[] values){var set=new HashSet<Integer>();for(int i:values)if(!set.add(i))throw new IllegalArgumentException("Duplicate citizen ID");return set;}
 private static int[] box(BoundingBox b){return new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()};}
 private static BoundingBox readBox(int[] a){if(a.length!=6 || a[0]>a[3] || a[1]>a[4] || a[2]>a[5])throw new IllegalArgumentException("Invalid saved volume");return new BoundingBox(a[0],a[1],a[2],a[3],a[4],a[5]);}
}
