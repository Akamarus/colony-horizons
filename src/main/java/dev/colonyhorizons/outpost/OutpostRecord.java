package dev.colonyhorizons.outpost;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Membership only: native MineColonies objects remain authoritative. */
public record OutpostRecord(UUID id, int colonyId, ResourceKey<Level> dimension, String name,
 Set<ChunkPos> approvedChunks, BoundingBox development, BoundingBox forestry,
 Map<BlockPos,BuildingRef> buildings, Set<Integer> residentIds, Set<Integer> workerIds,
 Optional<BlockPos> terminal, State state, List<String> blockers) {
 public enum State { PLANNED, PAUSED, RECOVERY_NEEDED, ACTIVE }
 public record BuildingRef(BlockPos anchor, ResourceLocation type, String packName,
   String blueprintPath, BoundingBox reservedVolume) {
  public BuildingRef {
   anchor=Objects.requireNonNull(anchor).immutable();Objects.requireNonNull(type);
   if(packName==null || packName.isBlank() || blueprintPath==null || blueprintPath.isBlank() ||
      blueprintPath.startsWith("/") || blueprintPath.contains("..") || blueprintPath.contains("\\"))
    throw new IllegalArgumentException("Invalid blueprint reference");
   reservedVolume=copy(reservedVolume);
   if(!reservedVolume.isInside(anchor))throw new IllegalArgumentException("Anchor outside reserved volume");
  }
  @Override public BoundingBox reservedVolume(){return copy(reservedVolume);}
 }
 public OutpostRecord {
  Objects.requireNonNull(id);Objects.requireNonNull(dimension);Objects.requireNonNull(state);
  if(colonyId<=0 || name==null || name.isBlank())throw new IllegalArgumentException("Invalid identity");
  approvedChunks=Set.copyOf(approvedChunks);development=copy(development);forestry=copy(forestry);
  if(approvedChunks.isEmpty())throw new IllegalArgumentException("Empty approved footprint");
  var refs=new HashMap<BlockPos,BuildingRef>();
  buildings.forEach((pos,ref)->{if(!pos.equals(ref.anchor()))throw new IllegalArgumentException("Anchor key mismatch");refs.put(pos.immutable(),ref);});
  buildings=Map.copyOf(refs);residentIds=Set.copyOf(residentIds);workerIds=Set.copyOf(workerIds);
  if(residentIds.stream().anyMatch(i->i<=0) || !residentIds.containsAll(workerIds))throw new IllegalArgumentException("Invalid citizen membership");
  terminal=terminal.map(BlockPos::immutable);blockers=List.copyOf(blockers);
  if(terminal.isPresent() && !development.isInside(terminal.get()))throw new IllegalArgumentException("Terminal outside settlement");
  for(var ref:buildings.values()){
   if(!contains(development,ref.reservedVolume()))throw new IllegalArgumentException("Building outside development");
   if(!approvedChunks.contains(new ChunkPos(ref.anchor())))throw new IllegalArgumentException("Anchor outside approved claims");
  }
  var list=new ArrayList<>(buildings.values());
  for(int i=0;i<list.size();i++)for(int j=i+1;j<list.size();j++)
   if(list.get(i).reservedVolume().intersects(list.get(j).reservedVolume()))throw new IllegalArgumentException("Reserved building volumes overlap");
 }
 @Override public BoundingBox development(){return copy(development);}
 @Override public BoundingBox forestry(){return copy(forestry);}
 public OutpostRecord withState(State value){return new OutpostRecord(id,colonyId,dimension,name,approvedChunks,development,forestry,buildings,residentIds,workerIds,terminal,value,blockers);}
 private static BoundingBox copy(BoundingBox box){Objects.requireNonNull(box);return new BoundingBox(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());}
 private static boolean contains(BoundingBox outer,BoundingBox inner){return outer.isInside(new BlockPos(inner.minX(),inner.minY(),inner.minZ()))&&outer.isInside(new BlockPos(inner.maxX(),inner.maxY(),inner.maxZ()));}
}
