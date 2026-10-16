// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import java.util.*;
import com.minecolonies.api.colony.ICitizenData;
import dev.colonyhorizons.remote.NativeFoundingCheckpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
public final class ExpeditionTravel {
 private ExpeditionTravel(){}
 private static final ThreadLocal<UUID> RELEASING=new ThreadLocal<>();
 public static void depart(ServerLevel level,ICitizenData worker){if(worker.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isEmpty())return;worker.getColony().getTravellingManager().startTravellingTo(worker,worker.getWorkBuilding().getPosition(),Integer.MAX_VALUE);worker.getEntity().ifPresent(Entity::discard);NativeFoundingCheckpoint.save(level,worker.getColony());}
 public static boolean blocksEntity(ServerLevel level,UUID entity){if(entity.equals(RELEASING.get()))return false;return SurveySavedData.existing(level).stream().flatMap(d->d.records().stream()).anyMatch(r->r.workerUuid().equals(entity)&&r.active()&&r.state()!=SurveyRecord.State.PREPARING)||GatheringSavedData.existing(level).stream().flatMap(d->d.records().stream()).anyMatch(r->r.workerUuid().equals(entity)&&r.active()&&r.state()!=GatheringRecord.State.PREPARING);}
 public static boolean blocksSpawn(ICitizenData worker){return worker.getColony().getWorld() instanceof ServerLevel level&&blocksEntity(level,worker.getUUID());}
 public static Optional<BlockPos> arrival(ServerLevel level,UUID id,BlockPos hut){var home=new ChunkPos(hut);var chunks=new HashSet<ChunkPos>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)chunks.add(new ChunkPos(home.x+x,home.z+z));if(!SurveyTickets.isOpen(level,id)&&SurveyTickets.active(level)>=2)return Optional.empty();SurveyTickets.open(level,id,chunks,true);SurveyTickets.advance(level,id);if(!SurveyTickets.ready(level,id)||!com.minecolonies.api.util.WorldUtil.isEntityBlockLoaded(level,hut))return Optional.empty();for(var p:BlockPos.betweenClosed(hut.offset(-4,0,-4),hut.offset(4,2,4)))if(level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP)&&level.getFluidState(p).isEmpty()&&level.getFluidState(p.above()).isEmpty()&&level.noCollision(new net.minecraft.world.phys.AABB(p).expandTowards(0,1,0)))return Optional.of(p.immutable());return Optional.empty();}
 public static void release(ServerLevel level,ICitizenData worker,BlockPos arrival){RELEASING.set(worker.getUUID());try{if(worker.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isEmpty()){worker.getColony().getTravellingManager().startTravellingTo(worker,arrival,0);worker.updateEntityIfNecessary();}var entity=worker.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).orElseThrow(()->new IllegalStateException("Original explorer could not return"));entity.setPos(arrival.getX()+.5,arrival.getY(),arrival.getZ()+.5);worker.getColony().getTravellingManager().finishTravellingFor(worker.getId());}finally{RELEASING.remove();}NativeFoundingCheckpoint.save(level,worker.getColony());}
}
