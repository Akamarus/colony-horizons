// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import dev.colonyhorizons.remote.RemoteModels.*;
import com.ldtteam.structurize.placement.*;
import com.minecolonies.api.util.LoadOnlyStructureHandler;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Server-derived, finite material kit for establishing the warehouse and courier infrastructure. */
public final class FoundingConstructionSupplies {
 private FoundingConstructionSupplies(){}
 public static Map<ItemKey,Integer> operatingReserve(){var result=new HashMap<ItemKey,Integer>(Map.of(new ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD)),64,new ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_AXE)),2,new ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE)),2,new ItemKey(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SHOVEL)),2));RemoteCare.starterMedicines().forEach((k,v)->result.merge(k,v,Math::max));return Map.copyOf(result);}

 public static boolean warehouseFirst(Development record){return FoundingRoster.warehouseFirst(record) && record.draft().buildings().stream().anyMatch(p->p.role().equals("warehouse"));}
 public static StepResult supply(ServerLevel level,UUID id,net.minecraft.core.BlockPos destination,net.minecraft.world.item.ItemStack item,int amount){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();
  if(!warehouseFirst(r)||!Set.of(Phase.STAGING,Phase.BUILDING).contains(r.phase())||!dev.colonyhorizons.mayor.MayorService.available(level,id)||!RemoteTickets.snapshot(level).containsKey(id)||level.getServer().getPlayerList().getPlayer(r.owner())==null)return new StepResult("Founding paused",List.of("Needs owner, original Mayor and active settlement"),false);
  if(data.bootstrapPending(id))return new StepResult("Founding recovery needed",List.of("Interrupted construction-kit handoff"),false);
  var active=data.construction(id).values().stream().filter(e->e.state()==ConstructionJournal.State.QUEUED).findFirst();
  if(!operatingReserve().containsKey(new ItemKey(item))&&(active.isEmpty()||r.draft().buildings().stream().noneMatch(p->p.operationId().equals(active.orElseThrow().building())&&Set.of("builder","warehouse","courier").contains(p.role()))))return new StepResult("Waiting for logistics",List.of("Complete the warehouse and courier hut first"),false);
  var builder=r.draft().buildings().stream().filter(p->p.role().equals("builder")).findFirst().orElseThrow();
  if(!destination.equals(builder.anchor())||!FoundingPlacement.isPaidAnchor(level,builder)||!(level.getBlockEntity(destination) instanceof com.minecolonies.core.tileentities.TileEntityColonyBuilding hut))return new StepResult("Waiting for logistics",List.of("Original builder hut unavailable"),false);
  var key=new ItemKey(item);int available=FoundingPlacement.remaining(level,id).getOrDefault(key,0);int count=Math.min(amount,available);
  if(count<=0)return new StepResult("Founding supplies exhausted",List.of("Review paid construction kit: "+item.getHoverName().getString()),false);
  return handoff(level,id,dev.colonyhorizons.supply.WarehouseInventory.resolve(level,hut),key,count);
 }
 public static StepResult finish(ServerLevel level,UUID id){
  var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElseThrow();
  if(!warehouseFirst(r)||!dev.colonyhorizons.mayor.MayorLogistics.ready(level,id)||!RemoteTickets.snapshot(level).containsKey(id)||level.getServer().getPlayerList().getPlayer(r.owner())==null)return new StepResult("Founding stores retained",List.of(),false);
  if(data.bootstrapPending(id))return new StepResult("Founding recovery needed",List.of("Interrupted founding handoff"),false);
  var remaining=FoundingPlacement.remaining(level,id);if(remaining.isEmpty())return new StepResult("Founding stores delivered",List.of(),false);
  var warehouse=dev.colonyhorizons.mayor.MayorLogistics.warehouse(level,id).orElseThrow();
  final dev.colonyhorizons.supply.WarehouseInventory.View view;
  try{view=dev.colonyhorizons.supply.WarehouseInventory.resolve(level,warehouse.getTileEntity());}catch(dev.colonyhorizons.supply.WarehouseInventory.Unavailable unavailable){return new StepResult("Waiting for warehouse",List.of(unavailable.getMessage()),false);}
  for(var e:remaining.entrySet()){var result=handoff(level,id,view,e.getKey(),Math.min(64,e.getValue()));if(result.progressed()||!result.blockers().isEmpty())return result;}
  return new StepResult("Make room in warehouse for founding stores",List.of(),false);
 }
 private static StepResult handoff(ServerLevel level,UUID id,dev.colonyhorizons.supply.WarehouseInventory.View view,ItemKey key,int count){
  var data=RemoteDevelopmentData.get(level);var batch=new dev.colonyhorizons.supply.SupplyInventoryBatch(Map.of(id,view.inventory()));var rest=batch.insert(id,key.stack().copyWithCount(count));count-=rest.getCount();
  if(count<=0)return new StepResult("Founding destination full",List.of(),false);
  try{data.beginBootstrapHandoff(id,key,count);data.checkpoint(level);view.validate(level);batch.apply();view.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);dev.colonyhorizons.supply.NativeSupplyCheckpoint.flushAndVerify(level,view.entities());data.finishBootstrapHandoff(id);data.checkpoint(level);return new StepResult("Using paid founding supplies",List.of(),true);}
  catch(RuntimeException failure){var current=data.find(id).orElseThrow();data.transition(id,current.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(level);throw failure;}
 }

 public static Map<ItemKey,Integer> bill(ServerLevel level,UUID id){
  return bill(level,RemoteDevelopmentData.get(level).find(id).orElseThrow());
 }
 public static Map<ItemKey,Integer> bill(ServerLevel level,Development record){
  if(!warehouseFirst(record))return Map.of();
  var result=new HashMap<ItemKey,Integer>(operatingReserve());
  for(var plan:record.draft().buildings())if(Set.of("builder","warehouse","courier").contains(plan.role())){
   var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint(plan.pack(),plan.blueprint(),level.registryAccess());
   var placer=new StructurePlacer(new LoadOnlyStructureHandler(level,plan.anchor(),bp,plan.transform()));var progress=AbstractBlueprintIterator.NULL_POS;
   for(int guard=0;;guard++){
    if(guard>=10000)throw new IllegalStateException("Native founding bill exceeds review limit");
    var step=placer.executeStructureStep(level,null,progress,StructurePlacer.Operation.GET_RES_REQUIREMENTS,()->placer.getIterator().increment((info,pos,handler)->false),true);
    for(var item:step.getBlockResult().getRequiredItems())if(!item.isEmpty()&&!(item.getItem() instanceof net.minecraft.world.item.BlockItem block&&block.getBlock() instanceof com.minecolonies.api.blocks.AbstractBlockHut<?>))result.merge(new ItemKey(item),item.getCount(),Math::addExact);
    progress=step.getIteratorPos();if(step.getBlockResult().getResult()==BlockPlacementResult.Result.FINISHED)break;
   }
  }
  return Map.copyOf(result);
 }
}
