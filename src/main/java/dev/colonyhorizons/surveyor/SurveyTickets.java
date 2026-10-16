// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Nonpersistent inspection leases. No forceChunk/getChunk/join on the server thread. */
public final class SurveyTickets {
 private SurveyTickets(){}
 private static final TicketType<UUID> TYPE=TicketType.create("colonyhorizons_survey",Comparator.<UUID>naturalOrder());
 private static final Map<ServerLevel,Map<UUID,Lease>> LEASES=new WeakHashMap<>();
 private static final Map<MinecraftServer,Integer> LAST_REQUEST=new WeakHashMap<>();
 private static final class Lease {
  final List<ChunkPos> chunks;final int distance;final Map<ChunkPos,CompletableFuture<?>> requested=new LinkedHashMap<>();
  Lease(Set<ChunkPos> chunks,boolean entities){this.chunks=chunks.stream().sorted(Comparator.comparingLong(ChunkPos::toLong)).toList();distance=entities?2:0;}
 }
 public static int active(ServerLevel level){return LEASES.getOrDefault(level,Map.of()).size();}
 public static boolean isOpen(ServerLevel level,UUID id){return LEASES.getOrDefault(level,Map.of()).containsKey(id);}
 public static int requested(ServerLevel level,UUID id){var lease=LEASES.getOrDefault(level,Map.of()).get(id);return lease==null?0:lease.requested.size();}
 public static void open(ServerLevel level,UUID id,Set<ChunkPos> chunks){
  open(level,id,chunks,false);
 }
 public static void open(ServerLevel level,UUID id,Set<ChunkPos> chunks,boolean entities){
  if(chunks.isEmpty()||chunks.size()>49)throw new IllegalArgumentException("Survey footprint exceeds limit");
  for(var c:chunks)if(!level.getWorldBorder().isWithinBounds(c.getMinBlockX(),c.getMinBlockZ())||!level.getWorldBorder().isWithinBounds(c.getMaxBlockX(),c.getMaxBlockZ()))throw new IllegalArgumentException("Survey crosses world border");
  var map=LEASES.computeIfAbsent(level,k->new LinkedHashMap<>());var old=map.get(id);
  if(old!=null){if(!new HashSet<>(old.chunks).equals(chunks)||old.distance!=(entities?2:0))throw new IllegalStateException("Release old survey footprint first");return;}
  int total=LEASES.entrySet().stream().filter(e->e.getKey().getServer()==level.getServer()).mapToInt(e->e.getValue().size()).sum();
  if(total>=2)throw new IllegalStateException("Two terrain inspections are already running");map.put(id,new Lease(chunks,entities));
 }
 public static void advance(ServerLevel level,UUID id){
  var lease=LEASES.getOrDefault(level,Map.of()).get(id);if(lease==null)return;
  if(lease.requested.values().stream().anyMatch(CompletableFuture::isCompletedExceptionally)){release(level,id);throw new IllegalStateException("Terrain inspection failed");}
  if(lease.requested.size()==lease.chunks.size()||Objects.equals(LAST_REQUEST.get(level.getServer()),level.getServer().getTickCount()))return;
  var chunk=lease.chunks.get(lease.requested.size());LAST_REQUEST.put(level.getServer(),level.getServer().getTickCount());
  // Register before requesting generation so cleanup can remove even a failed request.
  lease.requested.put(chunk,new CompletableFuture<>());
  try{level.getChunkSource().addRegionTicket(TYPE,chunk,lease.distance,id);lease.requested.put(chunk,level.getChunkSource().getChunkFuture(chunk.x,chunk.z,ChunkStatus.FULL,true));}
  catch(RuntimeException failure){release(level,id);throw failure;}
 }
 public static boolean ready(ServerLevel level,UUID id){
  var lease=LEASES.getOrDefault(level,Map.of()).get(id);return lease!=null&&lease.requested.size()==lease.chunks.size()&&lease.requested.values().stream().allMatch(f->f.isDone()&&!f.isCompletedExceptionally())&&lease.chunks.stream().allMatch(c->level.getChunkSource().getChunkNow(c.x,c.z)!=null);
 }
 public static void release(ServerLevel level,UUID id){var map=LEASES.get(level);if(map==null)return;var lease=map.get(id);if(lease==null)return;for(var c:lease.requested.keySet())level.getChunkSource().removeRegionTicket(TYPE,c,lease.distance,id);map.remove(id);if(map.isEmpty())LEASES.remove(level);}
 public static void releaseAll(ServerLevel level){for(var id:new ArrayList<>(LEASES.getOrDefault(level,Map.of()).keySet()))release(level,id);}
}
