package dev.colonyhorizons.integration;
import dev.colonyhorizons.outpost.OutpostRecord;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Read-only diagnostics; never creates native entities, claims, or chunk tickets. */
public final class NativeOutpostMembership {
 public record MembershipReport(List<BlockPos> missingBuildings,List<Integer> missingCitizens,List<String> blockers){
  public MembershipReport {missingBuildings=List.copyOf(missingBuildings);missingCitizens=List.copyOf(missingCitizens);blockers=List.copyOf(blockers);}
  public boolean ready(){return missingBuildings.isEmpty() && missingCitizens.isEmpty() && blockers.isEmpty();}
 }
 public static MembershipReport inspect(ServerLevel level,OutpostRecord record){
  var buildings=new ArrayList<BlockPos>();var citizens=new ArrayList<Integer>();var blockers=new ArrayList<String>();
  if(!level.dimension().equals(record.dimension()))return new MembershipReport(buildings,citizens,List.of("Dimension mismatch"));
  var colony=IColonyManager.getInstance().getColonyByDimension(record.colonyId(),level.dimension());
  if(colony==null)return new MembershipReport(buildings,citizens,List.of("Home colony missing"));
    for(var chunk:record.approvedChunks()){
   var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
   if(claim==null || claim.getOwningColony()!=record.colonyId())blockers.add("Missing or foreign claim: "+chunk);
  }
  for(var ref:record.buildings().values()){
   if(!level.hasChunkAt(ref.anchor())){blockers.add("Unloaded building: "+ref.anchor().toShortString());continue;}
   var building=colony.getServerBuildingManager().getBuilding(ref.anchor());
   if(building==null || building.getColony().getID()!=record.colonyId() ||
      !BuiltInRegistries.BLOCK.getKey(level.getBlockState(ref.anchor()).getBlock()).equals(ref.type()))buildings.add(ref.anchor());
  }
  for(int id:record.residentIds()){
   var citizen=colony.getCitizenManager().getCivilian(id);
   if(citizen==null){citizens.add(id);continue;}
   if(record.workerIds().contains(id)){
    var work=citizen.getWorkBuilding();
    if(work==null || !record.buildings().containsKey(work.getPosition()))blockers.add("Worker "+id+" is not assigned to this district");
   }
  }
  return new MembershipReport(buildings,citizens,blockers);
 }
}
