// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.requestsystem.requestable.*;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.ai.minimal.EntityAISickTask.DiseaseState;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import dev.colonyhorizons.mayor.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Local self-care breaks the sick-courier deadlock. Native illness AI still treats and consumes cures. */
public final class RemoteCare {
 private RemoteCare(){}
 public static Map<ItemKey,Integer> starterMedicines(){var result=new HashMap<ItemKey,Integer>();for(var disease:com.minecolonies.core.datalistener.DiseasesListener.getDiseases())for(var cure:disease.cureItems())result.merge(new ItemKey(cure.getItemStack()),8,Math::max);if(result.size()>32)throw new IllegalArgumentException("Too many starter medicine types; review disease configuration");return Map.copyOf(result);}
 private static boolean active(ServerLevel level,Development r){var owner=level.getServer().getPlayerList().getPlayer(r.owner());var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());return Set.of(Phase.STAGING,Phase.BUILDING,Phase.OPERATING).contains(r.phase())&&owner!=null&&!owner.isRemoved()&&owner.serverLevel()==level&&colony!=null&&r.owner().equals(colony.getPermissions().getOwner())&&RemoteTickets.snapshot(level).containsKey(r.id());}
 private static Optional<TileEntityColonyBuilding> store(ServerLevel level,Development r){var warehouse=MayorLogistics.warehouse(level,r.id());if(warehouse.isPresent())return Optional.of(warehouse.orElseThrow().getTileEntity());return RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(p->p.role().equals("builder")&&FoundingPlacement.isPaidAnchor(level,p)).map(p->level.getBlockEntity(p.anchor())).filter(b->b instanceof TileEntityColonyBuilding).map(b->(TileEntityColonyBuilding)b).findFirst();}
 private static boolean has(net.neoforged.neoforge.items.IItemHandler inventory,com.minecolonies.api.crafting.ItemStorage cure){for(int i=0;i<inventory.getSlots();i++)if(com.minecolonies.core.datalistener.model.Disease.hasCureItem(cure).test(inventory.getStackInSlot(i)))return true;return false;}
 public static DiseaseState recover(EntityCitizen entity){
  var citizen=entity.getCitizenData();var settlement=RemoteWorkerLocality.settlement(citizen);var disease=citizen.getCitizenDiseaseHandler().getDisease();if(settlement.isEmpty()||disease==null)return null;
  var r=settlement.orElseThrow();var level=(ServerLevel)entity.level();if(!active(level,r))return DiseaseState.WANDER;
  if(disease.cureItems().stream().allMatch(c->has(citizen.getInventory(),c))){for(var local:stores(level,r,citizen))acceptCollectedCures(citizen,local,disease);entity.getNavigation().stop();return DiseaseState.APPLY_CURE;}
  for(var hut:stores(level,r,citizen)){
   final WarehouseInventory.View view;try{view=WarehouseInventory.resolve(level,hut);}catch(WarehouseInventory.Unavailable e){continue;}
   if(disease.cureItems().stream().noneMatch(c->!has(citizen.getInventory(),c)&&has(view.inventory(),c)))continue;
   if(!entity.blockPosition().closerThan(hut.getBlockPos(),4)){EntityNavigationUtils.walkToBuilding(entity,hut.getBuilding());return DiseaseState.SEARCH_HOSPITAL;}
   var batch=new SupplyInventoryBatch(Map.of(r.id(),view.inventory(),citizen.getUUID(),citizen.getInventory()));
   for(var cure:disease.cureItems())if(!has(citizen.getInventory(),cure)){
    ItemStack item=ItemStack.EMPTY;for(int slot=0;slot<view.inventory().getSlots();slot++)if(com.minecolonies.core.datalistener.model.Disease.hasCureItem(cure).test(view.inventory().getStackInSlot(slot))){item=view.inventory().getStackInSlot(slot);break;}
    if(item.isEmpty())continue;var medicine=batch.extract(r.id(),item,1);if(medicine.isEmpty()||!batch.insert(citizen.getUUID(),medicine).isEmpty())return DiseaseState.WANDER;
   }
   view.validate(level);batch.apply();view.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);citizen.markDirty(0);acceptCollectedCures(citizen,hut,disease);entity.getNavigation().stop();return disease.cureItems().stream().allMatch(c->has(citizen.getInventory(),c))?DiseaseState.APPLY_CURE:DiseaseState.SEARCH_HOSPITAL;
  }return DiseaseState.WANDER;
 }
 private static void acceptCollectedCures(ICitizenData citizen,TileEntityColonyBuilding hut,com.minecolonies.core.datalistener.model.Disease disease){
  var building=hut.getBuilding();for(var request:new ArrayList<>(building.getCompletedRequestsOfCitizenOrBuilding(citizen)))if(request.getRequest() instanceof StackList delivery&&disease.cureItems().stream().anyMatch(c->delivery.matches(c.getItemStack())&&has(citizen.getInventory(),c)))building.markRequestAsAccepted(citizen,request.getId());
 }
 private static List<TileEntityColonyBuilding> stores(ServerLevel level,Development r,ICitizenData citizen){
  var result=new LinkedHashSet<TileEntityColonyBuilding>();var work=citizen.getWorkBuilding();if(work!=null&&RemoteDevelopmentData.get(level).buildings(r.id()).stream().anyMatch(p->p.anchor().equals(work.getPosition())&&FoundingPlacement.isPaidAnchor(level,p))&&level.getBlockEntity(work.getPosition()) instanceof TileEntityColonyBuilding hut)result.add(hut);store(level,r).ifPresent(result::add);return List.copyOf(result);
 }
 /** Emergency local food collection: no food creation and no journey to a Home restaurant. */
 public static com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState food(EntityCitizen entity){
  var citizen=entity.getCitizenData();var settlement=RemoteWorkerLocality.settlement(citizen);if(settlement.isEmpty())return null;var r=settlement.orElseThrow();var level=(ServerLevel)entity.level();var waiting=com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.CHECK_FOR_FOOD;if(!active(level,r))return waiting;
  if(com.minecolonies.api.util.FoodUtils.getBestFoodForCitizen(citizen.getInventory(),citizen,null)>=0)return com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.EAT;
  for(var hut:stores(level,r,citizen)){
   final WarehouseInventory.View view;try{view=WarehouseInventory.resolve(level,hut);}catch(WarehouseInventory.Unavailable e){continue;}
   var selected=com.minecolonies.api.util.FoodUtils.checkForFoodInBuilding(citizen,null,hut.getBuilding());
   if(selected==null&&citizen.getSaturation()<com.minecolonies.api.util.constant.CitizenConstants.AVERAGE_SATURATION&&citizen.getJob() instanceof com.minecolonies.core.colony.jobs.JobDeliveryman&&hut.getBuilding() instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse){
    // A bread-delivery task forbids eating carried bread. Eat a separate finite
    // warehouse portion instead; never bypass that restriction on delivery cargo.
    int food=-1;for(int i=0;i<view.inventory().getSlots();i++)if(com.minecolonies.api.util.FoodUtils.canEat(view.inventory().getStackInSlot(i),citizen.getHomeBuilding(),null)){food=i;break;}
    if(food>=0){
     if(!entity.blockPosition().closerThan(hut.getBlockPos(),4)){EntityNavigationUtils.walkToBuilding(entity,hut.getBuilding());return com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.SEARCH_RESTAURANT;}
     var meal=new SupplyInventoryBatch(Map.of(r.id(),view.inventory()));var portion=meal.extract(r.id(),view.inventory().getStackInSlot(food),1);if(portion.isEmpty())return waiting;
     view.validate(level);meal.apply();view.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);var eaten=portion.getItem();com.minecolonies.api.util.ItemStackUtils.consumeFood(portion,entity,null);citizen.getCitizenFoodHandler().addLastEaten(eaten);entity.getNavigation().stop();return waiting;
    }
   }
   if(selected==null)continue;
   int slot=-1;for(int i=0;i<view.inventory().getSlots();i++)if(net.minecraft.world.item.ItemStack.isSameItemSameComponents(view.inventory().getStackInSlot(i),selected.getItemStack())){slot=i;break;}if(slot<0)continue;
   if(!entity.blockPosition().closerThan(hut.getBlockPos(),4)){EntityNavigationUtils.walkToBuilding(entity,hut.getBuilding());return com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.SEARCH_RESTAURANT;}
   var batch=new SupplyInventoryBatch(Map.of(r.id(),view.inventory(),citizen.getUUID(),citizen.getInventory()));var item=batch.extract(r.id(),view.inventory().getStackInSlot(slot),1);if(item.isEmpty()||!batch.insert(citizen.getUUID(),item).isEmpty())return waiting;
   view.validate(level);batch.apply();view.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);citizen.markDirty(0);entity.getNavigation().stop();return com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.EAT;
  }return waiting;
 }

 /** Standing care orders remain active when illness suspends the appointed Mayor's work AI. */
 public static void tick(ServerLevel level,UUID id){
  var r=RemoteDevelopmentData.get(level).find(id).orElseThrow();if(!active(level,r)||!MayorService.available(level,id))return;
  var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());var mayor=colony.getCitizenManager().getCivilian(FoundingRoster.mayorId(RemoteDevelopmentData.get(level),id));if(mayor==null||mayor.isPaused())return;
  MayorOperatingSupplies.reconcile(level,id);
  var destination=store(level,r);if(destination.isEmpty())return;var hut=destination.orElseThrow();
  for(int citizenId:FoundingRoster.citizenIds(RemoteDevelopmentData.get(level),id)){
   var citizen=colony.getCitizenManager().getCivilian(citizenId);if(citizen==null||!citizen.getUUID().equals(RemoteDevelopmentData.get(level).approvedParty(id).get(citizenId)))continue;
   var disease=citizen.getCitizenDiseaseHandler().getDisease();if(disease==null)continue;
   for(var cure:disease.cureItems()){
    if(has(citizen.getInventory(),cure))continue;
    final WarehouseInventory.View view;try{view=WarehouseInventory.resolve(level,hut);}catch(WarehouseInventory.Unavailable e){return;}
    if(has(view.inventory(),cure)||stores(level,r,citizen).stream().anyMatch(local->has(local.getInventory(),cure)))continue;
    var building=citizen.getWorkBuilding();if(building==null)building=hut.getBuilding();
    var requests=new ArrayList<>(building.getOpenRequests(citizenId));requests.addAll(building.getCompletedRequestsOfCitizenOrBuilding(citizen));
    var request=requests.stream().filter(q->q.getRequest() instanceof IDeliverable d&&d.matches(cure.getItemStack())).findFirst();
    if(request.isEmpty()){building.createRequest(citizen,new StackList(List.of(cure.getItemStack()),"Medicine for "+disease.name().getString(),1),citizen.getWorkBuilding()!=null&&citizen.getJob()!=null);NativeFoundingCheckpoint.save(level,colony);return;}
    var operation=UUID.nameUUIDFromBytes((id+":care:"+citizen.getUUID()+":"+request.orElseThrow().getId().getIdentifier()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var result=MayorSupplyController.ensure(level,id,operation,hut.getBlockPos(),cure.getItemStack(),8);if(result.progressed())return;
   }
  }
 }
}

