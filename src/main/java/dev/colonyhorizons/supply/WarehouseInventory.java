// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.supply;

import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import java.util.*;

/** A bounded native warehouse view; every touched rack participates in custody checkpoints. */
public final class WarehouseInventory {
 private WarehouseInventory(){}
 public static final class Unavailable extends IllegalStateException { public Unavailable(String message){super(message);} }
 public record View(TileEntityColonyBuilding hut,List<BlockEntity> entities,IItemHandlerModifiable inventory){
  public View{entities=List.copyOf(entities);}
  public void validate(ServerLevel level){
   for(var entity:entities)if(!level.hasChunkAt(entity.getBlockPos())||level.getBlockEntity(entity.getBlockPos())!=entity)throw new IllegalStateException("Warehouse inventory changed during handoff");
   var current=resolve(level,hut);if(!current.entities().equals(entities)||current.inventory().getSlots()!=inventory.getSlots())throw new IllegalStateException("Warehouse racks changed during handoff");
  }
 }
 public static View resolve(ServerLevel level,TileEntityColonyBuilding hut){
  if(!level.hasChunkAt(hut.getBlockPos())||level.getBlockEntity(hut.getBlockPos())!=hut)throw new Unavailable("Supply hut unavailable");
  var entities=new ArrayList<BlockEntity>();var inventories=new ArrayList<IItemHandlerModifiable>();entities.add(hut);inventories.add(hut.getInventory());
  if(hut.getBuilding() instanceof BuildingWareHouse warehouse){
   var positions=warehouse.getContainers().stream().filter(p->!p.equals(hut.getBlockPos())).distinct().sorted().toList();
   if(positions.size()>128)throw new Unavailable("Warehouse has too many registered containers");
   for(var pos:positions){
    if(!level.hasChunkAt(pos))throw new Unavailable("Waiting for warehouse racks to load");
    if(level.getBlockEntity(pos) instanceof TileEntityRack rack){
     var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(level.dimension(),new net.minecraft.world.level.ChunkPos(pos));
     if(claim==null||claim.getOwningColony()!=warehouse.getColony().getID())throw new Unavailable("Warehouse rack claim changed");
     var home=NativeCourierDistricts.resolve(level,warehouse.getColony().getID(),hut.getBlockPos());var target=NativeCourierDistricts.resolve(level,warehouse.getColony().getID(),pos);
     if(home.status()==CourierDistrictPolicy.Status.BLOCKED||target.status()==CourierDistrictPolicy.Status.BLOCKED||!home.district().equals(target.district()))throw new Unavailable("Warehouse rack crosses settlement boundary");
     entities.add(rack);inventories.add(rack.getInventory());
    }
   }
  }
  return new View(hut,entities,new CombinedInvWrapper(inventories.toArray(IItemHandlerModifiable[]::new)));
 }
}
