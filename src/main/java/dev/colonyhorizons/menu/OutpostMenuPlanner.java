package dev.colonyhorizons.menu;
import java.util.*;
import dev.colonyhorizons.outpost.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
public final class OutpostMenuPlanner {
 private OutpostMenuPlanner(){}
 public record DistrictDraft(OutpostRecord record,Set<BlockPos> selected,String fingerprint){public DistrictDraft{selected=Set.copyOf(selected);}}
 public static DistrictDraft review(ServerPlayer actor,int colonyId,String input,Set<BlockPos> selected){
  var c=SupplyMenuService.owned(actor,colonyId);var level=actor.serverLevel();String name=input.trim();
  if(name.isEmpty()||name.length()>32||name.equalsIgnoreCase("Home"))throw new IllegalArgumentException("Choose an outpost name of 1–32 characters other than Home");
  if(selected.isEmpty()||selected.size()>32)throw new IllegalArgumentException("Choose 1–32 huts");
  var data=OutpostSavedData.get(level);
  if(data.all().stream().anyMatch(r->r.colonyId()==colonyId&&r.name().equalsIgnoreCase(name)))throw new IllegalArgumentException("An outpost already uses this name");
  var facts=new TreeMap<BlockPos,NativeMenuBuildings.BuildingFacts>(Comparator.comparingLong(BlockPos::asLong));
  for(var pos:selected)facts.put(pos,NativeMenuBuildings.inspect(level,colonyId,pos));
  BoundingBox area=null;Set<ChunkPos> chunks=new HashSet<>();
  for(int pass=0;pass<=32;pass++){
   int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
   for(var f:facts.values()){
    var b=f.reference().reservedVolume();minX=Math.min(minX,b.minX());minZ=Math.min(minZ,b.minZ());maxX=Math.max(maxX,b.maxX());maxZ=Math.max(maxZ,b.maxZ());
    for(var p:f.racks()){minX=Math.min(minX,p.getX());minZ=Math.min(minZ,p.getZ());maxX=Math.max(maxX,p.getX());maxZ=Math.max(maxZ,p.getZ());}
   }
   int x0=minX>>4,z0=minZ>>4,x1=maxX>>4,z1=maxZ>>4;
   if((long)(x1-x0+1)*(z1-z0+1)>64||facts.size()>32)throw new IllegalArgumentException("Outpost is too large: maximum 64 chunks and 32 huts");
   area=new BoundingBox(x0*16,level.getMinBuildHeight(),z0*16,x1*16+15,level.getMaxBuildHeight()-1,z1*16+15);chunks.clear();
   for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
    var chunk=new ChunkPos(x,z);var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
    if(claim==null||claim.getOwningColony()!=colonyId)throw new IllegalArgumentException("Outpost must fit entirely inside this colony's existing claims");chunks.add(chunk);
   }
   for(var old:data.all())if(!Collections.disjoint(chunks,old.approvedChunks()))throw new IllegalArgumentException("Boundary overlaps another outpost");
   var hall=c.getServerBuildingManager().getTownHall();if(hall!=null&&area.isInside(hall.getPosition()))throw new IllegalArgumentException("The Town Hall must remain in Home");
   boolean added=false;
   for(var b:c.getServerBuildingManager().getBuildings().values())if(!facts.containsKey(b.getPosition())&&area.isInside(b.getPosition())){
    facts.put(b.getPosition(),NativeMenuBuildings.inspect(level,colonyId,b.getPosition()));added=true;
   }
   if(!added)break;if(pass==32)throw new IllegalArgumentException("Outpost boundary could not be resolved");
  }
  var citizens=new TreeSet<Integer>();var workers=new TreeSet<Integer>();var fingerprint=new StringBuilder(name).append(chunks.stream().map(ChunkPos::toLong).sorted().toList());
  facts.values().forEach(f->fingerprint.append(f.fingerprint()));
  for(var citizen:c.getCitizenManager().getCitizens()){
   var home=citizen.getHomeBuilding();var work=citizen.getWorkBuilding();boolean hi=home!=null&&facts.containsKey(home.getPosition()),wi=work!=null&&facts.containsKey(work.getPosition());
   fingerprint.append(citizen.getId()).append(':').append(home==null?"none":home.getPosition()).append(':').append(work==null?"none":work.getPosition());
   if(home!=null&&work!=null&&hi!=wi)throw new IllegalArgumentException("Worker "+citizen.getName()+" has home and work across this boundary; change native assignments first");
   if(citizen.getJob() instanceof JobDeliveryman courier){var warehouse=courier.findWareHouse();fingerprint.append(warehouse==null?"none":warehouse.getPosition());if(work!=null&&warehouse!=null&&wi!=facts.containsKey(warehouse.getPosition()))throw new IllegalArgumentException("Courier "+citizen.getName()+" and its warehouse must stay together");}
   if(hi||wi)citizens.add(citizen.getId());if(wi)workers.add(citizen.getId());
  }
  var refs=new LinkedHashMap<BlockPos,OutpostRecord.BuildingRef>();facts.forEach((pos,f)->refs.put(pos,f.reference()));
  var record=new OutpostRecord(UUID.randomUUID(),colonyId,level.dimension(),name,chunks,area,area,refs,citizens,workers,Optional.empty(),OutpostRecord.State.ACTIVE,List.of());
  return new DistrictDraft(record,selected,fingerprint.toString());
 }
 public static UUID activate(ServerPlayer actor,int colonyId,DistrictDraft reviewed){
  var fresh=review(actor,colonyId,reviewed.record().name(),reviewed.selected());
  if(!fresh.fingerprint().equals(reviewed.fingerprint()))throw new IllegalArgumentException("Buildings or assignments changed. Review the outpost again");
  // All native checks finish before register permanently marks the colony as managed.
  OutpostSavedData.get(actor.serverLevel()).register(fresh.record());return fresh.record().id();
 }
}

