// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class SurveySavedData extends SavedData {
 private static final String KEY="colonyhorizons_surveys";
 private final Map<UUID,SurveyRecord> records=new LinkedHashMap<>();
 private final Map<UUID,SampleJournal> samples=new HashMap<>();
 private final Map<UUID,CompoundTag> cargo=new HashMap<>();
 private final Map<UUID,SurveySiteSearch> sites=new HashMap<>();
 private final Map<UUID,DiscoveryEvidence> discoveries=new HashMap<>();
 public Optional<DiscoveryEvidence> evidence(UUID id){require(id);return Optional.ofNullable(discoveries.get(id));}
 public void recordEvidence(DiscoveryEvidence evidence){
  require(evidence.survey());var r=records.get(evidence.survey());var sample=samples(r.id());evidence.verify(r,sample);
  if(r.state()!=SurveyRecord.State.SEARCHING||sample.pending().isEmpty()||discoveries.containsKey(r.id()))throw new IllegalStateException("Discovery needs its original pending harvest");
  discoveries.put(r.id(),evidence);setDirty();
 }
 public Optional<DiscoveryEvidence> unlocked(UUID owner,int colony,net.minecraft.resources.ResourceLocation target){
  return records.values().stream().filter(r->r.owner().equals(owner)&&r.colony()==colony&&r.target().equals(target)&&r.state()==SurveyRecord.State.COMPLETE)
   .filter(r->samples(r.id()).count()>0&&samples(r.id()).pending().isEmpty()&&returnedCargo(r.id()))
   .map(r->discoveries.get(r.id())).filter(Objects::nonNull).findFirst();
 }
 private boolean returnedCargo(UUID id){var t=cargo(id);return t.getInt("version")==1&&t.contains("paid",Tag.TAG_BYTE)&&t.getBoolean("paid")&&!t.contains("pending")&&!t.contains("tool")&&t.contains("samples",Tag.TAG_LIST)&&((ListTag)t.get("samples")).isEmpty();}
 public Optional<SurveySiteSearch> siteSearch(UUID id){require(id);return Optional.ofNullable(sites.get(id));}
 public void writeSiteSearch(UUID id,SurveySiteSearch next){
  require(id);var r=records.get(id);var sample=samples(id);var old=sites.get(id);
  if(r.state()!=SurveyRecord.State.SEARCHING||sample.count()<1||sample.pending().isPresent()||!sample.source().equals(next.source()))throw new IllegalStateException("Site search evidence changed");
  if(old==null){if(!next.equals(SurveySiteSearch.start(sample.source())))throw new IllegalStateException("Invalid initial site search");}
  else if(old.complete()||!old.checked(next.site(),next.reason()).equals(next))throw new IllegalStateException("Stale site search");
  sites.put(id,next);setDirty();
 }
 public SampleJournal samples(UUID id){require(id);return samples.getOrDefault(id,SampleJournal.empty());}
 public void putSamples(UUID id,SampleJournal next){var old=samples(id);SampleJournal expected=old.pending().isPresent()?old.collected(old.pending().orElseThrow(),next.count()-old.count()):old.prepare(next.pending().orElseThrow(),next.source());if(!expected.equals(next))throw new IllegalStateException("Sample journal changed");samples.put(id,next);setDirty();}
 public CompoundTag cargo(UUID id){require(id);return cargo.getOrDefault(id,new CompoundTag()).copy();}
 public void writeCargo(UUID id,CompoundTag next){require(id);cargo.put(id,next.copy());setDirty();}
 private void require(UUID id){if(!records.containsKey(id))throw new IllegalArgumentException("Unknown survey");}
 public void checkpoint(ServerLevel level){
  if(get(level)!=this)throw new IllegalStateException("Detached survey state");var expected=save(new CompoundTag(),level.registryAccess());setDirty();level.getDataStorage().save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
  var file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");
  try{if(!NbtIo.readCompressed(file,NbtAccounter.create(16L*1024*1024)).getCompound("data").equals(expected))throw new IllegalStateException("Survey save verification failed");}catch(java.io.IOException e){throw new IllegalStateException("Survey save failed",e);}
 }
 public static SurveySavedData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new Factory<>(SurveySavedData::new,SurveySavedData::load),KEY);}
 public static Optional<SurveySavedData> existing(ServerLevel level){return Optional.ofNullable(level.getDataStorage().get(new Factory<>(SurveySavedData::new,SurveySavedData::load),KEY));}
 public Optional<SurveyRecord> find(UUID id){return Optional.ofNullable(records.get(id));}
 public List<SurveyRecord> records(){return List.copyOf(records.values());}
 public void add(SurveyRecord r){if(records.containsKey(r.id())||r.revision()!=0||r.state()!=SurveyRecord.State.PREPARING||records.values().stream().anyMatch(old->old.colony()==r.colony()&&old.active()))throw new IllegalStateException("Survey already active");records.put(r.id(),r);setDirty();}
 public void replace(SurveyRecord expected,SurveyRecord next){
  if(!expected.equals(records.get(expected.id()))||!expected.id().equals(next.id())||expected.colony()!=next.colony()||!expected.owner().equals(next.owner())||!expected.hut().equals(next.hut())||!expected.target().equals(next.target())||!next.belongsToWorker(expected.worker(),expected.workerUuid())||next.revision()!=expected.revision()+1)throw new IllegalStateException("Stale or changed survey identity");
  SurveyRecord legal=expected.state()==next.state()?expected.advanceCandidate():expected.next(next.state());if(!legal.equals(next))throw new IllegalStateException("Invalid survey progress");records.put(next.id(),next);setDirty();
 }
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putInt("version",1);var list=new ListTag();records.values().forEach(r->{var entry=r.save();entry.put("samples",samples(r.id()).save());if(cargo.containsKey(r.id()))entry.put("cargo",cargo(r.id()));if(sites.containsKey(r.id()))entry.put("siteSearch",sites.get(r.id()).save());if(discoveries.containsKey(r.id()))entry.put("discovery",discoveries.get(r.id()).save());list.add(entry);});tag.put("surveys",list);return tag;}
 public static SurveySavedData load(CompoundTag tag,HolderLookup.Provider registries){if(tag.getInt("version")!=1)throw new IllegalArgumentException("Unsupported survey data");var data=new SurveySavedData();var list=tag.getList("surveys",Tag.TAG_COMPOUND);if(list.size()>4096)throw new IllegalArgumentException("Too many surveys");for(var raw:list){var entry=(CompoundTag)raw;var r=SurveyRecord.load(entry);if(data.records.putIfAbsent(r.id(),r)!=null)throw new IllegalArgumentException("Duplicate survey");data.samples.put(r.id(),SampleJournal.load(entry.getCompound("samples")));if(entry.contains("discovery")){var evidence=DiscoveryEvidence.load(entry.getCompound("discovery"));evidence.verify(r,data.samples(r.id()));data.discoveries.put(r.id(),evidence);}if(entry.contains("siteSearch")){var site=SurveySiteSearch.load(entry.getCompound("siteSearch"));var sample=data.samples(r.id());if(sample.count()<1||sample.pending().isPresent()||!sample.source().equals(site.source()))throw new IllegalArgumentException("Saved site evidence changed");data.sites.put(r.id(),site);}if(entry.contains("cargo"))data.cargo.put(r.id(),entry.getCompound("cargo").copy());}var active=new HashSet<Integer>();for(var r:data.records.values())if(r.active()&&!active.add(r.colony()))throw new IllegalArgumentException("Multiple active colony surveys");return data;}
}
