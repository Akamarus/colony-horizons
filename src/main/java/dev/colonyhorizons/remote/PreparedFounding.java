// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import dev.colonyhorizons.remote.RemoteModels.*;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Preparation precedes permanent anchors; old drafts never enter this coordinator. */
public final class PreparedFounding {
 private PreparedFounding(){}
 private static final String MARKER="colonyhorizons:preparation_camp";
 public static BlockPos arrival(SiteDraft draft,int index){return draft.preparation().map(p->p.arrivals().get(index)).orElseGet(()->draft.request().center().offset(index*2-2,0,12));}
 public static Map<ItemKey,Integer> bill(SiteDraft draft){if(draft.preparation().isEmpty())return Map.of();var result=new HashMap<ItemKey,Integer>();result.put(new ItemKey(new ItemStack(ModBlocks.blockHutBuilder)),1);int barrels=draft.preparation().orElseThrow().storage().size();if(barrels>0)result.put(new ItemKey(new ItemStack(Items.BARREL)),barrels);int dirt=draft.preparation().orElseThrow().terrain().dirtRequired();if(dirt>0)result.put(new ItemKey(new ItemStack(Items.DIRT)),dirt);SitePreparationWork.toolBill(draft.preparation().orElseThrow().terrain()).forEach((k,v)->result.merge(k,v,Math::addExact));return Map.copyOf(result);}
 private static TileEntityColonyBuilding camp(ServerLevel l,UUID id){var p=RemoteDevelopmentData.get(l).find(id).orElseThrow().draft().preparation().orElseThrow().camp();if(!l.hasChunkAt(p)||!(l.getBlockEntity(p) instanceof TileEntityColonyBuilding t)||!t.getPersistentData().hasUUID(MARKER)||!id.equals(t.getPersistentData().getUUID(MARKER)))throw new IllegalStateException("Original preparation camp is unavailable");return t;}
 public static boolean readyForArrival(ServerLevel l,UUID id){try{CampStorage.resolve(l,id,camp(l,id));return RemoteDevelopmentData.get(l).preparationProgress(id).filter(p->p.stage()==PreparationProgress.Stage.CAMP_READY&&!p.work().pending()).isPresent()&&camp(l,id).getBuilding()!=null;}catch(IllegalStateException e){return false;}}
 public static StepResult advance(ServerLevel l,UUID id){
  var data=RemoteDevelopmentData.get(l);var r=data.find(id).orElseThrow();if(r.draft().preparation().isEmpty())return null;
  var p=r.draft().preparation().orElseThrow();var owner=l.getServer().getPlayerList().getPlayer(r.owner());var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());
  if(owner==null||owner.serverLevel()!=l||colony==null||!owner.getUUID().equals(colony.getPermissions().getOwner()))return waitFor("Waiting for outpost owner");
  var progress=data.preparationProgress(id);
  if(progress.isEmpty()){
   if(!PreparedSite.dry(l,p.camp())||p.arrivals().stream().anyMatch(a->!PreparedSite.dry(l,a)))return waitFor("Surveyed arrival camp changed; review required");
   var key=new ItemKey(new ItemStack(ModBlocks.blockHutBuilder));if(FoundingPlacement.remaining(l,id).getOrDefault(key,0)<1)return waitFor("Missing paid temporary builder hut");
   var state=new PreparationProgress(PreparationProgress.Stage.PLACING,SitePreparationJournal.create(id,p.terrain().fingerprint(),p.terrain().operations().size()));data.putPreparationProgress(id,state);data.checkpoint(l);
   data.beginBootstrapHandoff(id,key,1);data.checkpoint(l);
   var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,l.registryAccess());bp.addBlockState(BlockPos.ZERO,ModBlocks.blockHutBuilder.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);
   var b=r.draft().buildings().stream().filter(v->v.role().equals("builder")).findFirst().orElseThrow();
   if(!ModBlocks.blockHutBuilder.setup(owner,l,p.camp(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,b.pack(),b.blueprint()))throw new IllegalStateException("Temporary native builder hut placement failed");
   var tile=(TileEntityColonyBuilding)l.getBlockEntity(p.camp());tile.getPersistentData().putUUID(MARKER,id);tile.setChanged();NativeFoundingCheckpoint.save(l,colony);data.finishBootstrapHandoff(id);CampStorage.place(l,owner,id);data.putPreparationProgress(id,new PreparationProgress(PreparationProgress.Stage.CAMP_READY,state.work()));data.checkpoint(l);return changed("Paid temporary builder camp placed");
  }
  var state=progress.orElseThrow();if(state.uncertain())return waitFor("Interrupted preparation needs recovery; supplies retained");
  if(state.stage().ordinal()>=PreparationProgress.Stage.HANDED_OVER.ordinal())return null;
  if(data.founderRoster(id).isEmpty())return FoundingGrantService.advance(l,id);
  if(data.journey(id).isEmpty())return FoundingTravel.beginGranted(l,id);
  FoundingTravel.reconcile(l,id);if(data.journey(id).orElseThrow().state()!=FoundingTravel.State.ARRIVED)return waitFor("Settlers travelling to their temporary camp");
  FoundingSupplies.deliver(l,id);
  var member=FoundingRoster.member(data,id,FoundingRoster.Role.BUILDER).orElseThrow();var builder=colony.getCitizenManager().getCivilian(member.citizenId());if(builder==null||!builder.getUUID().equals(member.entityId()))return waitFor("Original builder unavailable; no replacement spawned");
  var hut=camp(l,id).getBuilding();if(builder.getWorkBuilding()!=hut){if(builder.getWorkBuilding()!=null)throw new IllegalStateException("Original builder was reassigned");if(!hut.getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(builder))throw new IllegalStateException("Temporary builder assignment refused");NativeFoundingCheckpoint.save(l,colony);}
  if(state.work().finished())return null;
  int free=0;for(int slot=0;slot<builder.getInventory().getSlots();slot++)if(builder.getInventory().getStackInSlot(slot).isEmpty())free++;
  // Finish unloading while already at camp; otherwise one new drop can cause another round trip.
  boolean atCamp=builder.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).map(e->e.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p.camp()))<=16).orElse(false);
  if(free<3||atCamp){
   if(builder.isPaused()||builder.getCitizenDiseaseHandler().getDisease()!=null)return waitFor("Waiting for the assigned healthy builder");
   var entity=builder.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).orElse(null);if(entity==null)return waitFor("Waiting for builder to return to camp");
   if(entity.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p.camp()))>16){com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils.walkToPos(entity,p.camp(),3,true);return waitFor("Builder returning to camp to unload");}
   var tile=camp(l,id);var stores=CampStorage.resolve(l,id,tile);boolean keepDirt=p.terrain().operations().stream().skip(state.work().cursor()).anyMatch(op->op.kind()==SitePreparationPlan.Kind.FILL);
   try{boolean moved=CampInventoryTransfer.unload(builder.getInventory(),stores.inventory(),keepDirt,
    ()->{data.putPreparationProgress(id,new PreparationProgress(state.stage(),state.work(),true));data.checkpoint(l);},
    ()->{builder.markDirty(0);stores.dirty();NativeFoundingCheckpoint.save(l,colony);},
    ()->{data.putPreparationProgress(id,state);data.checkpoint(l);});
    if(moved){entity.getNavigation().stop();return changed("Builder stored gathered materials at camp");}
   }catch(RuntimeException failure){var now=data.find(id).orElseThrow();data.transition(id,now.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(l);throw failure;}
   if(free<3)return waitFor("Make room in the temporary builder hut for gathered materials");
  }
  // Only finite, already paid supplies; the camp is not a warehouse route endpoint.
  var inventory=builder.getInventory();for(var item:List.of(Items.DIRT,Items.STONE_PICKAXE,Items.STONE_AXE,Items.STONE_SHOVEL)){
   long fillLeft=p.terrain().operations().stream().skip(state.work().cursor()).filter(op->op.kind()==SitePreparationPlan.Kind.FILL).count();if(item==Items.DIRT&&fillLeft==0)continue;
   boolean has=false;for(int slot=0;slot<inventory.getSlots();slot++)if(inventory.getStackInSlot(slot).is(item)){has=true;break;}
   if(has)continue;var key=new ItemKey(new ItemStack(item));int amount=Math.min(item==Items.DIRT?(int)Math.min(64,fillLeft):1,FoundingPlacement.remaining(l,id).getOrDefault(key,0));if(amount==0)continue;
   var batch=new dev.colonyhorizons.supply.SupplyInventoryBatch(Map.of(id,inventory));var rest=batch.insert(id,new ItemStack(item,amount));amount-=rest.getCount();if(amount==0)continue;data.beginBootstrapHandoff(id,key,amount);data.checkpoint(l);batch.apply();builder.markDirty(0);NativeFoundingCheckpoint.save(l,colony);data.finishBootstrapHandoff(id);data.checkpoint(l);
  }
  try{return SitePreparationService.advance(l,owner,builder,p.terrain(),state.work(),next->{data.putPreparationProgress(id,new PreparationProgress(PreparationProgress.Stage.CAMP_READY,next));data.checkpoint(l);});}
  catch(RuntimeException failure){if(data.preparationProgress(id).orElseThrow().work().pending()){var now=data.find(id).orElseThrow();data.transition(id,now.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(l);}throw failure;}
 }
 public static void handoff(ServerLevel l,UUID id){
  var data=RemoteDevelopmentData.get(l);var r=data.find(id).orElseThrow();var progress=data.preparationProgress(id).orElseThrow();if(progress.stage().ordinal()>=PreparationProgress.Stage.HANDED_OVER.ordinal())return;
  if(progress.stage()!=PreparationProgress.Stage.CAMP_READY||!progress.work().finished())throw new IllegalStateException("Terrain handoff not ready");
  var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());var member=FoundingRoster.member(data,id,FoundingRoster.Role.BUILDER).orElseThrow();var worker=colony.getCitizenManager().getCivilian(member.citizenId());if(worker==null||!worker.getUUID().equals(member.entityId()))throw new IllegalStateException("Original builder missing");
  var permanent=r.draft().buildings().stream().filter(p->p.role().equals("builder")).findFirst().orElseThrow();if(!FoundingPlacement.isPaidAnchor(l,permanent))throw new IllegalStateException("Permanent builder anchor not ready");
  var original=camp(l,id).getBuilding();if(worker.getWorkBuilding()!=original)throw new IllegalStateException("Temporary builder assignment changed");
  data.putPreparationProgress(id,new PreparationProgress(PreparationProgress.Stage.HANDOFF_INTENT,progress.work()));data.checkpoint(l);original.getFirstModuleOccurance(WorkerBuildingModule.class).removeCitizen(worker);
  if(!colony.getServerBuildingManager().getBuilding(permanent.anchor()).getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(worker))throw new IllegalStateException("Permanent builder assignment failed");
  NativeFoundingCheckpoint.save(l,colony);data.putPreparationProgress(id,new PreparationProgress(PreparationProgress.Stage.HANDED_OVER,progress.work()));data.checkpoint(l);
 }
 public static void cleanup(ServerLevel l,UUID id){
  var data=RemoteDevelopmentData.get(l);var r=data.find(id).orElseThrow();var state=data.preparationProgress(id);if(!Set.of(Phase.STAGING,Phase.BUILDING,Phase.OPERATING).contains(r.phase())||!RemoteTickets.snapshot(l).containsKey(id)||state.isEmpty()||state.orElseThrow().stage()!=PreparationProgress.Stage.HANDED_OVER||l.getServer().getPlayerList().getPlayer(r.owner())==null)return;
  var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());if(colony==null||!r.owner().equals(colony.getPermissions().getOwner()))return;
  var b=r.draft().buildings().stream().filter(p->p.role().equals("builder")).findFirst().orElseThrow();var permanent=colony.getServerBuildingManager().getBuilding(b.anchor());if(permanent==null||!permanent.isBuilt()||permanent.getBuildingLevel()<1||!(permanent.getTileEntity() instanceof TileEntityColonyBuilding dest))return;
  var source=camp(l,id);if(!source.getBuilding().getFirstModuleOccurance(WorkerBuildingModule.class).getAssignedCitizen().isEmpty())return;
  var stores=CampStorage.resolve(l,id,source);var warehousePlan=r.draft().buildings().stream().filter(p->p.role().equals("warehouse")).findFirst();if(warehousePlan.isPresent()){var warehouse=colony.getServerBuildingManager().getBuilding(warehousePlan.orElseThrow().anchor());if(warehouse!=null&&warehouse.isBuilt()&&warehouse.getBuildingLevel()>0&&warehouse.getTileEntity() instanceof TileEntityColonyBuilding warehouseTile)dest=warehouseTile;}var destination=dev.colonyhorizons.supply.WarehouseInventory.resolve(l,dest);
  var sourceId=UUID.randomUUID();var batch=new dev.colonyhorizons.supply.SupplyInventoryBatch(Map.of(sourceId,stores.inventory(),id,destination.inventory()));
  for(int slot=0;slot<stores.inventory().getSlots();slot++){var item=stores.inventory().getStackInSlot(slot);if(item.isEmpty())continue;var moved=batch.extract(sourceId,item,item.getCount());if(moved.getCount()!=item.getCount()||!batch.insert(id,moved).isEmpty())return;}
  int barrels=r.draft().preparation().orElseThrow().storage().size();if(barrels>0&&!batch.insert(id,new ItemStack(Items.BARREL,barrels)).isEmpty())return;
  if(!batch.insert(id,new ItemStack(ModBlocks.blockHutBuilder)).isEmpty())return;
  data.putPreparationProgress(id,new PreparationProgress(PreparationProgress.Stage.CLEANUP_INTENT,state.orElseThrow().work()));data.checkpoint(l);destination.validate(l);batch.apply();stores.dirty();destination.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);NativeFoundingCheckpoint.save(l,colony);
  for(var pos:r.draft().preparation().orElseThrow().storage())l.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());source.getBuilding().destroy();l.setBlockAndUpdate(source.getBlockPos(),Blocks.AIR.defaultBlockState());NativeFoundingCheckpoint.save(l,colony);data.putPreparationProgress(id,new PreparationProgress(PreparationProgress.Stage.COMPLETE,state.orElseThrow().work()));data.checkpoint(l);
 }
 private static StepResult waitFor(String text){return new StepResult(text,List.of(),false);}
 private static StepResult changed(String text){return new StepResult(text,List.of(),true);}
}
