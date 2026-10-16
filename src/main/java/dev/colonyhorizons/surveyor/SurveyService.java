// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import com.minecolonies.api.colony.*;
import dev.colonyhorizons.remote.NativeFoundingCheckpoint;
import dev.colonyhorizons.supply.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Protected native-worker expeditions; all world examination is bounded server-tick work. */
public final class SurveyService {
 private SurveyService(){}

 private static final Map<ServerLevel,Map<UUID,Cursor>> CURSORS=new WeakHashMap<>();
 private static final Map<ServerLevel,Map<UUID,String>> STATUS=new WeakHashMap<>();
 private static final Map<ServerLevel,Long> LAST_TICK=new WeakHashMap<>();
 private static final class Cursor {int candidate,column,depth;Cursor(int candidate){this.candidate=candidate;}}
 public static String status(ServerLevel level,UUID id){return STATUS.getOrDefault(level,Map.of()).getOrDefault(id,"Waiting for survey worker");}
 private static void status(ServerLevel level,UUID id,String message){STATUS.computeIfAbsent(level,k->new HashMap<>()).put(id,message);}
 public static BlockPos candidate(BlockPos origin,int index){if(index<0||index>=32)throw new IllegalArgumentException("Invalid candidate");double angle=index*2.399963229728653;int radius=512+index*112;return new BlockPos(origin.getX()+(int)Math.round(Math.cos(angle)*radius),origin.getY(),origin.getZ()+(int)Math.round(Math.sin(angle)*radius));}
 public static UUID start(ServerPlayer actor,int colony,BlockPos hutPos,ResourceLocation target){
  if(actor.level().dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("Surveys currently explore the Overworld");
  if(actor.server.getPlayerList().getPlayer(actor.getUUID())!=actor)throw new IllegalArgumentException("Owner must be online");
  var hut=SurveyorAccess.require(actor,colony,hutPos);var worker=SurveyorAccess.worker(hut).orElseThrow(()->new IllegalArgumentException("Assign a healthy, unpaused Surveyor"));
  var entity=worker.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).orElseThrow(()->new IllegalArgumentException("Recall the Surveyor before departure"));
  if(!entity.blockPosition().closerThan(hutPos,16))throw new IllegalArgumentException("Wait for the Surveyor to reach the hut");
  var resource=SurveyResourceCatalog.find(target).orElseThrow(()->new IllegalArgumentException("Unsupported survey resource"));var level=actor.serverLevel();var data=SurveySavedData.get(level);
  ExpeditionAccess.requireIdle(level,colony);
  var supplies=SurveySupplies.prepare(hut.getTileEntity().getInventory(),resource);var r=SurveyRecord.create(UUID.randomUUID(),colony,actor.getUUID(),hutPos,worker.getId(),worker.getUUID(),target);data.add(r);data.checkpoint(level);
  try{
   supplies.commit(level.registryAccess(),t->{data.writeCargo(r.id(),t);data.checkpoint(level);},()->{hut.getTileEntity().setChanged();NativeFoundingCheckpoint.save(level,hut.getColony());});
   data.replace(r,r.next(SurveyRecord.State.SEARCHING));data.checkpoint(level);depart(level,worker);status(level,r.id(),"Surveyor departed with provisions");return r.id();
  }catch(RuntimeException e){recover(level,r.id(),e);throw e;}
 }
 private static void depart(ServerLevel level,ICitizenData worker){ExpeditionTravel.depart(level,worker);}
 public static boolean blocksEntity(ServerLevel level,UUID entity){return ExpeditionTravel.blocksEntity(level,entity);}
 public static boolean blocksSpawn(ICitizenData worker){return ExpeditionTravel.blocksSpawn(worker);}
 public static void tick(ServerLevel level){
  if(level.dimension()!=Level.OVERWORLD||Objects.equals(LAST_TICK.get(level),level.getGameTime()))return;LAST_TICK.put(level,level.getGameTime());
  var existing=SurveySavedData.existing(level);if(existing.isEmpty())return;
  for(var r:existing.orElseThrow().records())if(r.active())try{advance(level,r);}catch(RuntimeException e){recover(level,r.id(),e);}
 }
 private static void advance(ServerLevel level,SurveyRecord r){
  var data=SurveySavedData.get(level);var owner=level.getServer().getPlayerList().getPlayer(r.owner());
  if(owner==null||owner.serverLevel()!=level){SurveyTickets.release(level,r.id());status(level,r.id(),"Paused while owner is offline or in another dimension");return;}
  if(r.state()==SurveyRecord.State.RECOVERY_REQUIRED){SurveyTickets.release(level,r.id());return;}
  if(r.state()==SurveyRecord.State.PREPARING)throw new IllegalStateException("Interrupted departure needs recovery");
  var colony=IColonyManager.getInstance().getColonyByDimension(r.colony(),level.dimension());
  if(colony==null||!r.owner().equals(colony.getPermissions().getOwner()))throw new IllegalStateException("Colony ownership changed");
  var worker=colony.getCitizenManager().getCivilian(r.worker());if(worker==null||!r.belongsToWorker(worker.getId(),worker.getUUID()))throw new IllegalStateException("Original Surveyor is missing");
  var cargo=SurveyCargo.load(data.cargo(r.id()),level.registryAccess());if(!cargo.paid()||data.samples(r.id()).pending().isPresent())throw new IllegalStateException("Interrupted sample custody needs recovery");
  if(r.state()==SurveyRecord.State.RETURNING){returnHome(level,r,worker,cargo);return;}
  if(!(worker.getWorkBuilding() instanceof BuildingSurveyor hut)||!hut.getPosition().equals(r.hut())||SurveyorAccess.worker(hut).filter(w->w==worker).isEmpty()){beginReturn(level,r,"Survey interrupted; returning existing supplies");return;}
  depart(level,worker);
  if(cargo.count()>0){beginReturn(level,r,"Returning discovered samples");return;}
  if(r.inspected()>=32){beginReturn(level,r,"Search complete; no matching resource found");return;}
  var center=new ChunkPos(candidate(r.hut(),r.inspected()));var chunks=new HashSet<ChunkPos>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)chunks.add(new ChunkPos(center.x+x,center.z+z));
  if(!SurveyTickets.isOpen(level,r.id())&&SurveyTickets.active(level)>=2)return;
  SurveyTickets.open(level,r.id(),chunks);SurveyTickets.advance(level,r.id());if(!SurveyTickets.ready(level,r.id())){status(level,r.id(),"Surveying terrain "+(r.inspected()+1)+" of 32");return;}
  var cursor=CURSORS.computeIfAbsent(level,k->new HashMap<>()).computeIfAbsent(r.id(),k->new Cursor(r.inspected()));var resource=SurveyResourceCatalog.find(r.target()).orElseThrow();
  for(int checks=0;checks<1024&&cursor.column<112*112;checks++){
   int x=center.getMinBlockX()-48+cursor.column%112,z=center.getMinBlockZ()-48+cursor.column/112;
   int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;int y=top-cursor.depth;var pos=new BlockPos(x,y,z);
   if(++cursor.depth>=80||y<=level.getMinBuildHeight()){cursor.depth=0;cursor.column++;}
   if(!resource.matches(level.getBlockState(pos)))continue;
   var tool=cargo.tool();var plan=SurveyHarvest.prepare(level,owner,pos,resource,tool,16-cargo.count());if(plan.isEmpty())continue;
   var harvest=plan.orElseThrow();var op=UUID.randomUUID();var journal=data.samples(r.id());
   harvest.commit(()->{data.putSamples(r.id(),journal.prepare(op,pos));data.recordEvidence(new DiscoveryEvidence(r.id(),r.colony(),r.owner(),level.dimension().location(),level.getBiome(pos).unwrapKey().orElseThrow().location(),r.target(),pos));data.checkpoint(level);},()->{
    // World removal must reach disk before publishing its actual drops as owned cargo.
    level.save(null,true,false);var stacks=new ArrayList<>(cargo.samples());stacks.addAll(harvest.drops());var next=new SurveyCargo(true,tool,stacks);data.writeCargo(r.id(),next.save(level.registryAccess()));data.putSamples(r.id(),data.samples(r.id()).collected(op,harvest.drops().stream().mapToInt(ItemStack::getCount).sum()));data.checkpoint(level);
   });SurveyTickets.release(level,r.id());CURSORS.get(level).remove(r.id());status(level,r.id(),"Sample collected; returning home");return;
  }
  if(cursor.column>=112*112){SurveyTickets.release(level,r.id());CURSORS.get(level).remove(r.id());data.replace(r,r.advanceCandidate());data.setDirty();}
 }
 private static void beginReturn(ServerLevel level,SurveyRecord r,String message){var data=SurveySavedData.get(level);data.replace(r,r.next(SurveyRecord.State.RETURNING));data.checkpoint(level);SurveyTickets.release(level,r.id());status(level,r.id(),message);}
 public static void cancel(ServerPlayer actor,UUID id,long revision){var data=SurveySavedData.get(actor.serverLevel());var r=data.find(id).orElseThrow(()->new IllegalArgumentException("Unknown survey"));SurveyorAccess.require(actor,r.colony(),r.hut());if(!actor.getUUID().equals(r.owner())||r.revision()!=revision)throw new IllegalArgumentException("Survey changed; refresh first");if(r.state()!=SurveyRecord.State.SEARCHING)throw new IllegalArgumentException("Survey cannot be recalled in this state");beginReturn(actor.serverLevel(),r,"Recalled; returning existing supplies");}
 private static void returnHome(ServerLevel level,SurveyRecord r,ICitizenData worker,SurveyCargo cargo){
  var home=new ChunkPos(r.hut());var chunks=new HashSet<ChunkPos>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)chunks.add(new ChunkPos(home.x+x,home.z+z));
  if(!SurveyTickets.isOpen(level,r.id())&&SurveyTickets.active(level)>=2)return;SurveyTickets.open(level,r.id(),chunks,true);SurveyTickets.advance(level,r.id());if(!SurveyTickets.ready(level,r.id())||!com.minecolonies.api.util.WorldUtil.isEntityBlockLoaded(level,r.hut()))return;
  var building=worker.getColony().getServerBuildingManager().getBuilding(r.hut());if(!(building instanceof BuildingSurveyor hut)||hut.getTileEntity()==null)throw new IllegalStateException("Surveyor's Hut is missing; cargo held for recovery");
  BlockPos arrival=null;for(var p:BlockPos.betweenClosed(r.hut().offset(-4,0,-4),r.hut().offset(4,2,4)))if(level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP)&&level.getFluidState(p).isEmpty()&&level.getFluidState(p.above()).isEmpty()&&level.noCollision(new net.minecraft.world.phys.AABB(p).expandTowards(0,1,0))){arrival=p.immutable();break;}
  if(arrival==null){status(level,r.id(),"Clear a safe entrance at the Surveyor's Hut");return;}
  var data=SurveySavedData.get(level);var inv=hut.getTileEntity().getInventory();var batch=new SupplyInventoryBatch(Map.of(r.id(),inv));
  for(var stack:cargo.samples())if(!batch.insert(r.id(),stack).isEmpty()){status(level,r.id(),"Make room in the Surveyor's Hut for samples");return;}
  if(!cargo.tool().isEmpty()&&!batch.insert(r.id(),cargo.tool()).isEmpty()){status(level,r.id(),"Make room in the Surveyor's Hut for the tool");return;}
  if(cargo.count()>0||!cargo.tool().isEmpty())new SupplyCheckpoint(t->{data.writeCargo(r.id(),t);data.checkpoint(level);}).commit(cargo.save(level.registryAccess()),new SurveyCargo(true,ItemStack.EMPTY,List.of()).save(level.registryAccess()),batch::apply,()->{hut.getTileEntity().setChanged();NativeFoundingCheckpoint.save(level,worker.getColony());});
  ExpeditionTravel.release(level,worker,arrival);
  NativeFoundingCheckpoint.save(level,worker.getColony());data.replace(r,r.next(SurveyRecord.State.COMPLETE));data.checkpoint(level);SurveyTickets.release(level,r.id());CURSORS.getOrDefault(level,new HashMap<>()).remove(r.id());status(level,r.id(),"Survey complete; samples delivered to hut");
 }
 private static void recover(ServerLevel level,UUID id,RuntimeException failure){SurveyTickets.release(level,id);var data=SurveySavedData.get(level);var r=data.find(id).orElseThrow();if(r.active()&&r.state()!=SurveyRecord.State.RECOVERY_REQUIRED){data.replace(r,r.next(SurveyRecord.State.RECOVERY_REQUIRED));data.checkpoint(level);}status(level,id,"Recovery required: "+failure.getMessage());}
 public static void unload(ServerLevel level){SurveyTickets.releaseAll(level);CURSORS.remove(level);STATUS.remove(level);LAST_TICK.remove(level);}
}
