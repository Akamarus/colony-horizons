package dev.colonyhorizons.menu;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import dev.colonyhorizons.outpost.OutpostRecord;
import java.util.*;
public final class NativeMenuBuildings {
 private NativeMenuBuildings(){}
 public record BuildingFacts(OutpostRecord.BuildingRef reference,List<BlockPos> racks,String fingerprint){}
 public static BuildingFacts inspect(ServerLevel level,int colonyId,BlockPos anchor){
  var c=IColonyManager.getInstance().getColonyByDimension(colonyId,level.dimension());
  if(c==null||!level.hasChunkAt(anchor))throw new IllegalArgumentException("Hut is unavailable; visit it and try again");
  var b=c.getServerBuildingManager().getBuilding(anchor);
  if(b==null||b.getBuildingLevel()<1||!(level.getBlockEntity(anchor) instanceof TileEntityColonyBuilding tile)||tile.getBuilding()!=b)throw new IllegalArgumentException("Choose a completed, registered hut");
  var corners=b.getCorners();if(corners==null||corners.getA()==null||corners.getB()==null)throw new IllegalArgumentException("Hut blueprint bounds are unavailable");
  var box=BoundingBox.fromCorners(corners.getA(),corners.getB());
  var ref=new OutpostRecord.BuildingRef(anchor,BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock()),b.getStructurePack(),b.getBlueprintPath(),box);
  var racks=new ArrayList<BlockPos>();for(var w:c.getServerBuildingManager().getWareHouses())if(w.getPosition().equals(anchor))racks.addAll(w.getContainers());
  racks.sort(Comparator.comparingLong(BlockPos::asLong));
  return new BuildingFacts(ref,List.copyOf(racks),anchor+":"+ref.type()+":"+b.getStructurePack()+":"+b.getBlueprintPath()+":"+box+":"+b.getBuildingLevel()+":"+System.identityHashCode(tile)+":"+racks);
 }
}

