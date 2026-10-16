// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import java.util.*;
import com.minecolonies.api.colony.*;
import dev.colonyhorizons.remote.NativeFoundingCheckpoint;
import dev.colonyhorizons.supply.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import static dev.colonyhorizons.surveyor.GatheringRecord.State.*;
/** Simulated gathering has no wilderness entity or remote block operations. */
public final class GatheringService {
 private GatheringService(){}
 private static final Map<ServerLevel,Long> LAST_TICK=new WeakHashMap<>();
 public static String status(ServerLevel level,UUID id){return GatheringSavedData.existing(level).flatMap(d->d.find(id)).map(GatheringRecord::status).orElse("No gathering expedition");}
 public static UUID start(ServerPlayer actor,int colony,BlockPos hutPos,ResourceLocation target,boolean repeat){
  return start(actor,colony,hutPos,target,repeat,false);
 }
 private static UUID start(ServerPlayer actor,int colony,BlockPos hutPos,ResourceLocation target,boolean repeat,boolean automatic){
  var level=actor.serverLevel();if(level.dimension()!=Level.OVERWORLD||actor.server.getPlayerList().getPlayer(actor.getUUID())!=actor)throw new IllegalArgumentException("Owner must be online in the Overworld");
  var hut=automatic?SurveyorAccess.requireOwned(actor,colony,hutPos):SurveyorAccess.require(actor,colony,hutPos);var worker=SurveyorAccess.worker(hut).orElseThrow(()->new IllegalArgumentException("Assign a healthy, unpaused Explorer"));
  var entity=worker.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).orElseThrow(()->new IllegalArgumentException("Wait for the Explorer to return"));if(!entity.blockPosition().closerThan(hutPos,16))throw new IllegalArgumentException("Wait for the Explorer to reach the hut");
  ExpeditionAccess.requireIdle(level,colony);var evidence=SurveySavedData.get(level).unlocked(actor.getUUID(),colony,target).orElseThrow(()->new IllegalArgumentException("Discover this resource first"));
  if(!evidence.dimension().equals(level.dimension().location()))throw new IllegalArgumentException("Discovery belongs to another dimension");
  var offer=GatheringCatalog.find(target).orElseThrow(()->new IllegalArgumentException("This resource is discovery-only"));var payment=GatheringSupplies.prepare(hut.getTileEntity().getInventory(),offer);
  // Conservative preflight: do not spend another trip while its output has no home.
  var roomId=UUID.randomUUID();var room=new SupplyInventoryBatch(Map.of(roomId,hut.getTileEntity().getInventory()));if(!room.insert(roomId,new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(target),offer.count())).isEmpty())throw new IllegalArgumentException("Make room in the Exploration Hut for the reward");
  var data=GatheringSavedData.get(level);var r=GatheringRecord.create(UUID.randomUUID(),colony,actor.getUUID(),hutPos,worker.getId(),worker.getUUID(),evidence.survey(),offer,repeat);data.add(r);data.writeCargo(r.id(),GatheringCargo.empty().save(level.registryAccess()));data.checkpoint(level);
  try{payment.commit(level.registryAccess(),t->{data.writeCargo(r.id(),t);data.checkpoint(level);},()->{hut.getTileEntity().setChanged();NativeFoundingCheckpoint.save(level,hut.getColony());});data.replace(r,r.transition(GATHERING,"Gathering resources"));data.checkpoint(level);ExpeditionTravel.depart(level,worker);return r.id();}catch(RuntimeException e){recover(level,r.id(),e);throw e;}
 }
 public static void tick(ServerLevel level){if(level.dimension()!=Level.OVERWORLD||Objects.equals(LAST_TICK.get(level),level.getGameTime()))return;LAST_TICK.put(level,level.getGameTime());GatheringSavedData.existing(level).ifPresent(data->{for(var r:data.records())if(r.active())try{advance(level,r);}catch(RuntimeException e){recover(level,r.id(),e);}});}
 private static void advance(ServerLevel level,GatheringRecord r){
  var d=GatheringSavedData.get(level);if(r.state()==RECOVERY_REQUIRED){SurveyTickets.release(level,r.id());return;}var owner=level.getServer().getPlayerList().getPlayer(r.owner());if(owner==null||owner.serverLevel()!=level){SurveyTickets.release(level,r.id());message(d,r,"Paused while owner is offline or in another dimension");return;}
  var colony=IColonyManager.getInstance().getColonyByDimension(r.colony(),level.dimension());if(colony==null||!r.owner().equals(colony.getPermissions().getOwner()))throw new IllegalStateException("Colony ownership changed");
  var worker=colony.getCitizenManager().getCivilian(r.worker());if(worker==null||!worker.getUUID().equals(r.workerUuid()))throw new IllegalStateException("Original Explorer is missing");if(r.state()==PREPARING)throw new IllegalStateException("Interrupted departure needs recovery");
  var cargo=GatheringCargo.load(d.cargo(r.id()),level.registryAccess());cargo.verify(r);
  if(r.state()==RETURNING){returnHome(level,r,worker,cargo);return;}
  if(cargo.earned()||cargo.delivered())throw new IllegalStateException("Gathering custody disagrees with timer");
  if(!(worker.getWorkBuilding() instanceof BuildingSurveyor hut)||!hut.getPosition().equals(r.hut())||SurveyorAccess.worker(hut).filter(w->w==worker).isEmpty()){d.replace(r,r.returning("Work interrupted; returning existing custody"));d.checkpoint(level);return;}
  if(SurveySavedData.get(level).unlocked(r.owner(),r.colony(),r.offer().target()).filter(e->e.survey().equals(r.discovery())).isEmpty())throw new IllegalStateException("Original discovery is unavailable");
  ExpeditionTravel.depart(level,worker);var next=r.tick();d.replace(r,next);if(next.remaining()==0)d.writeCargo(r.id(),cargo.earn(r.offer()).save(level.registryAccess()));if(next.remaining()%100==0)d.checkpoint(level);
 }
 private static void returnHome(ServerLevel level,GatheringRecord r,ICitizenData worker,GatheringCargo cargo){
  var d=GatheringSavedData.get(level);var arrival=ExpeditionTravel.arrival(level,r.id(),r.hut());if(arrival.isEmpty()){message(d,r,"Waiting for a safe hut entrance");return;}
  var building=worker.getColony().getServerBuildingManager().getBuilding(r.hut());if(!(building instanceof BuildingSurveyor hut)||hut.getTileEntity()==null||!level.getBlockState(r.hut()).is(SurveyorRegistration.HUT.get()))throw new IllegalStateException("Exploration Hut is missing; custody held for recovery");
  if(!cargo.delivered()){
   var batch=new SupplyInventoryBatch(Map.of(r.id(),hut.getTileEntity().getInventory()));if(!batch.insert(r.id(),cargo.reward()).isEmpty()||!batch.insert(r.id(),cargo.tool()).isEmpty()){var stopped=r.returning("Make room in the hut; repeat stopped");if(!stopped.equals(r)){d.replace(r,stopped);d.checkpoint(level);}return;}
   new SupplyCheckpoint(t->{d.writeCargo(r.id(),t);d.checkpoint(level);}).commit(cargo.save(level.registryAccess()),cargo.deliveredCargo().save(level.registryAccess()),batch::apply,()->{hut.getTileEntity().setChanged();NativeFoundingCheckpoint.save(level,worker.getColony());});
  }
  if(cargo.earned())hut.createPickupRequest(r.offer().count(),true);
  ExpeditionTravel.release(level,worker,arrival.orElseThrow());var complete=r.transition(COMPLETE,"Expedition complete; resources delivered");d.replace(r,complete);d.checkpoint(level);SurveyTickets.release(level,r.id());
  if(r.repeat()){var owner=level.getServer().getPlayerList().getPlayer(r.owner());try{start(owner,r.colony(),r.hut(),r.offer().target(),true,true);}catch(IllegalArgumentException|IllegalStateException e){var current=d.find(r.id()).orElseThrow();message(d,current,"Repeat stopped: "+e.getMessage());d.checkpoint(level);}}
 }
 public static void recall(ServerPlayer actor,UUID id,long revision){var d=GatheringSavedData.get(actor.serverLevel());var r=d.find(id).orElseThrow(()->new IllegalArgumentException("Unknown expedition"));SurveyorAccess.require(actor,r.colony(),r.hut());if(!r.owner().equals(actor.getUUID())||r.revision()!=revision)throw new IllegalArgumentException("Expedition changed; refresh first");var next=r.returning("Recalled; returning existing custody");if(!next.equals(r)){d.replace(r,next);d.checkpoint(actor.serverLevel());}SurveyTickets.release(actor.serverLevel(),id);}
 private static void message(GatheringSavedData d,GatheringRecord r,String text){if(text.length()>256)text=text.substring(0,256);var next=r.message(text);if(next!=r)d.replace(r,next);}
 private static void recover(ServerLevel level,UUID id,RuntimeException e){SurveyTickets.release(level,id);var d=GatheringSavedData.get(level);var r=d.find(id).orElseThrow();if(r.active()&&r.state()!=RECOVERY_REQUIRED){String why="Recovery required: "+e.getMessage();d.replace(r,r.transition(RECOVERY_REQUIRED,why.substring(0,Math.min(256,why.length()))));d.checkpoint(level);}}
 public static void unload(ServerLevel level){GatheringSavedData.existing(level).ifPresent(d->d.records().stream().filter(GatheringRecord::active).forEach(r->SurveyTickets.release(level,r.id())));LAST_TICK.remove(level);}
}
