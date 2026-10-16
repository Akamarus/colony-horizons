package dev.colonyhorizons.integration;
import com.ldtteam.structurize.blueprints.v1.*;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.NBTUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Protect completed child buildings in this work order's private blueprint, never the shared pack. */
public final class BarracksTowerPreservation {
 private BarracksTowerPreservation(){}
 public static List<Object> signature(ServerLevel level,BlockPos anchor,Blueprint source){
  if(source.getPackName()==null||!source.getPackName().toLowerCase(Locale.ROOT).contains("arcology"))return List.of();
  var colony=IColonyManager.getInstance().getColonyByPosFromWorld(level,anchor);
  if(colony==null||!(colony.getServerBuildingManager().getBuilding(anchor) instanceof BuildingBarracks parent))return List.of();
  var result=new ArrayList<Object>();result.add(parent.getBuildingLevel());
  for(var pos:parent.getTowers())if(colony.getServerBuildingManager().getBuilding(pos) instanceof BuildingBarracksTower tower){
   var corners=tower.getCorners();result.add(List.of(pos,tower.getBuildingLevel(),corners.getA(),corners.getB()));
  }
  return result;
 }
 public static Blueprint protect(ServerLevel level,BlockPos anchor,Blueprint source){
  if(source==null||source.getPackName()==null||!source.getPackName().toLowerCase(Locale.ROOT).contains("arcology"))return source;
  var colony=IColonyManager.getInstance().getColonyByPosFromWorld(level,anchor);
  if(colony==null||!(colony.getServerBuildingManager().getBuilding(anchor) instanceof BuildingBarracks parent)||parent.getBuildingLevel()<1)return source;
  if(source.getBlockState(source.getPrimaryBlockOffset()).getBlock()!=com.minecolonies.api.blocks.ModBlocks.blockHutBarracks)return source;
  var protectedPositions=new HashSet<BlockPos>();var origin=anchor.subtract(source.getPrimaryBlockOffset());
  for(var pos:parent.getTowers()){
   if(!(colony.getServerBuildingManager().getBuilding(pos) instanceof BuildingBarracksTower tower)||tower.getBuildingLevel()<1)continue;
   if(!NBTUtils.readBlockPos(tower.serializeNBT(level.registryAccess()),"pos").equals(anchor))continue;
   var corners=tower.getCorners();var a=corners.getA();var b=corners.getB();
   // Intersect with the parent work order so malformed or oversized child bounds cannot expand work.
   int minX=Math.max(Math.min(a.getX(),b.getX()),origin.getX()),maxX=Math.min(Math.max(a.getX(),b.getX()),origin.getX()+source.getSizeX()-1);
   int minY=Math.max(Math.min(a.getY(),b.getY()),origin.getY()),maxY=Math.min(Math.max(a.getY(),b.getY()),origin.getY()+source.getSizeY()-1);
   int minZ=Math.max(Math.min(a.getZ(),b.getZ()),origin.getZ()),maxZ=Math.min(Math.max(a.getZ(),b.getZ()),origin.getZ()+source.getSizeZ()-1);
   for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){var worldPos=new BlockPos(x,y,z);if(!worldPos.equals(anchor))protectedPositions.add(worldPos.subtract(origin));}
  }
  if(protectedPositions.isEmpty())return source;
  var copy=BlueprintUtil.readBlueprintFromNBT(BlueprintUtil.writeBlueprintToNBT(source),level.registryAccess());
  copy.setName(source.getName()).setFileName(source.getFileName()).setFilePath(source.getFilePath()).setPackName(source.getPackName());
  var keep=com.ldtteam.structurize.blocks.ModBlocks.blockSubstitution.get().defaultBlockState();
  for(var pos:protectedPositions){copy.getTileEntities()[pos.getY()][pos.getZ()][pos.getX()]=null;copy.addBlockState(pos,keep);}
  copy.setCachePrimaryOffset(source.getPrimaryBlockOffset());
  return copy;
 }
}
