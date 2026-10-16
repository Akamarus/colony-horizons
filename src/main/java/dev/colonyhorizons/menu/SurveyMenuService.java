// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.menu;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class SurveyMenuService {
 private SurveyMenuService(){}
 public static CompoundTag view(ServerPlayer actor,int colony,BlockPos position){
  var hut=SurveyorAccess.require(actor,colony,position);var discoveries=SurveySavedData.get(actor.serverLevel());var tag=new CompoundTag();tag.putLong("hut",position.asLong());var resources=new ListTag();
  for(var r:SurveyResourceCatalog.all()){var row=new CompoundTag();var item=new ItemStack(BuiltInRegistries.ITEM.get(r.item()));row.putString("target",r.item().toString());row.putString("name",item.getHoverName().getString());row.put("item",item.save(actor.registryAccess()));row.putString("tool",r.tool()==SurveyResource.Tool.HAND?"No tool":r.tool()==SurveyResource.Tool.AXE?"Axe":"Shears");var evidence=discoveries.unlocked(actor.getUUID(),colony,r.item());row.putBoolean("unlocked",evidence.isPresent());evidence.ifPresent(e->row.putString("biome",e.biome().toString()));var offer=GatheringCatalog.find(r.item());row.putBoolean("gatherable",offer.isPresent());offer.ifPresent(o->{row.putInt("yield",o.count());row.putInt("durationTicks",o.ticks());row.putInt("bread",o.bread());row.putInt("wear",o.wear());});resources.add(row);}tag.put("resources",resources);
  tag.putString("status",SurveyorAccess.worker(hut).isEmpty()?"Assign a healthy Explorer in the Workers tab.":"Ready. Discover first, then gather from known biomes.");
  for(var r:SurveySavedData.get(actor.serverLevel()).records())if(r.colony()==colony&&r.hut().equals(position)&&r.owner().equals(actor.getUUID())){tag.putString("kind","discovery");tag.putUUID("id",r.id());tag.putLong("revision",r.revision());tag.putBoolean("active",r.active());tag.putBoolean("returning",r.state()==SurveyRecord.State.RETURNING);tag.putBoolean("recovery",r.state()==SurveyRecord.State.RECOVERY_REQUIRED);tag.putString("target",r.target().toString());tag.putString("status",r.state()==SurveyRecord.State.COMPLETE?"Discovery complete. Samples delivered.":SurveyService.status(actor.serverLevel(),r.id()));var journal=SurveySavedData.get(actor.serverLevel()).samples(r.id());if(journal.count()>0){tag.putLong("source",journal.source().asLong());tag.putInt("collected",journal.count());}}
  var findings=new ListTag();var records=SurveySavedData.get(actor.serverLevel()).records();
  for(int i=records.size()-1;i>=0&&findings.size()<32;i--){var r=records.get(i);if(r.colony()!=colony||!r.owner().equals(actor.getUUID())||r.state()!=SurveyRecord.State.COMPLETE)continue;
   var samples=SurveySavedData.get(actor.serverLevel()).samples(r.id());if(samples.pending().isPresent())continue;
   var row=new CompoundTag();var item=new ItemStack(BuiltInRegistries.ITEM.get(r.target()));row.putUUID("id",r.id());row.putString("target",r.target().toString());row.putString("name",item.getHoverName().getString());row.put("item",item.save(actor.registryAccess()));row.putInt("collected",samples.count());
   row.putString("evidence",samples.count()>0?"Collected "+samples.count()+" real samples":"No samples found within the search budget");if(samples.count()>0)row.putLong("source",samples.source().asLong());
   var evidence=discoveries.unlocked(actor.getUUID(),colony,r.target()).filter(e->e.survey().equals(r.id()));row.putString("production",evidence.isPresent()?(GatheringCatalog.find(r.target()).isPresent()?"Simulated gathering unlocked.":"Discovery only; no gathering offer."):"Rediscover this resource to unlock gathering.");evidence.ifPresent(e->row.putString("biome",e.biome().toString()));row.putBoolean("renewable",false);row.putBoolean("readyToFound",false);findings.add(row);
  }tag.put("findings",findings);
  // An active expedition in another hut still reserves the colony's one slot.
  var activeDiscovery=discoveries.records().stream().filter(r->r.colony()==colony&&r.active()&&r.owner().equals(actor.getUUID())).findFirst();
  if(activeDiscovery.isPresent()&&!activeDiscovery.get().hut().equals(position)){tag.putBoolean("active",true);tag.putBoolean("otherHut",true);tag.putString("status","An Explorer is already travelling from another hut.");}
  GatheringSavedData.existing(actor.serverLevel()).ifPresent(d->{var recordsHere=d.records().stream().filter(r->r.colony()==colony&&r.owner().equals(actor.getUUID())&&r.hut().equals(position)).toList();var active=d.active(colony).filter(r->r.owner().equals(actor.getUUID()));var shown=active.orElse(recordsHere.isEmpty()?null:recordsHere.getLast());if(shown!=null&&(active.isPresent()||!tag.getBoolean("active"))){tag.putString("kind","gathering");tag.putUUID("id",shown.id());tag.putLong("revision",shown.revision());tag.putBoolean("active",shown.active());tag.putBoolean("otherHut",!shown.hut().equals(position));tag.putBoolean("returning",shown.state()==GatheringRecord.State.RETURNING);tag.putBoolean("recovery",shown.state()==GatheringRecord.State.RECOVERY_REQUIRED);tag.putInt("remainingTicks",shown.remaining());tag.putBoolean("repeat",shown.repeat());tag.putString("status",shown.status());}});
  return tag;
 }
}
