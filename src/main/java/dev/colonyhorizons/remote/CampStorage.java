// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.items.wrapper.*;
import java.util.*;
/** Paid, bounded physical storage. Never a cross-settlement shipment endpoint. */
public final class CampStorage {
 private CampStorage(){}
 private static final String MARKER="colonyhorizons:preparation_storage";
 public record View(List<BlockEntity> entities,IItemHandlerModifiable inventory){public View{entities=List.copyOf(entities);}public void dirty(){entities.forEach(BlockEntity::setChanged);}}
 static void place(ServerLevel level,ServerPlayer owner,UUID id){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
  for(var pos:r.draft().preparation().orElseThrow().storage()){
   if(!PreparedSite.dry(level,pos)||!level.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(pos),e->!e.isRemoved()&&!e.isSpectator()).isEmpty())throw new IllegalStateException("Surveyed storage space changed");
   var event=new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,pos),level.getBlockState(pos.below()),owner){@Override public net.minecraft.world.level.block.state.BlockState getPlacedBlock(){return Blocks.BARREL.defaultBlockState();}@Override public net.minecraft.world.level.block.state.BlockState getState(){return Blocks.BARREL.defaultBlockState();}};
   net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);if(event.isCanceled())throw new IllegalStateException("Temporary storage placement is protected");
   var key=new RemoteModels.ItemKey(new ItemStack(Items.BARREL));if(FoundingPlacement.remaining(level,id).getOrDefault(key,0)<1)throw new IllegalStateException("Missing paid temporary barrel");
   data.beginBootstrapHandoff(id,key,1);data.checkpoint(level);
   if(!level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState())||!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel))throw new IllegalStateException("Temporary barrel placement failed");
   barrel.getPersistentData().putUUID(MARKER,id);barrel.setChanged();NativeFoundingCheckpoint.save(level,colony);data.finishBootstrapHandoff(id);data.checkpoint(level);
  }
 }
 public static View resolve(ServerLevel level,UUID id,TileEntityColonyBuilding hut){
  var p=RemoteDevelopmentData.get(level).find(id).orElseThrow().draft().preparation().orElseThrow();
  if(!hut.getBlockPos().equals(p.camp())||level.getBlockEntity(p.camp())!=hut)throw new IllegalStateException("Temporary hut changed");
  var entities=new ArrayList<BlockEntity>();var handlers=new ArrayList<IItemHandlerModifiable>();entities.add(hut);handlers.add(hut.getInventory());
  for(var pos:p.storage()){
   if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)||!barrel.getPersistentData().hasUUID(MARKER)||!id.equals(barrel.getPersistentData().getUUID(MARKER)))throw new IllegalStateException("Original temporary storage unavailable; contents retained");
   entities.add(barrel);handlers.add(new InvWrapper(barrel));
  }
  return new View(entities,new CombinedInvWrapper(handlers.toArray(IItemHandlerModifiable[]::new)));
 }
}
