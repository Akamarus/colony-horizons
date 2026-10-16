// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.*;
import java.util.*;

/** Addon-owned, ticking tickets, rebuilt from authority after login. Never changes vanilla forced chunks. */
public final class RemoteTickets {
    static final TicketController CONTROLLER=new TicketController(ResourceLocation.fromNamespaceAndPath("colonyhorizons","remote_settlements"),(level,helper)->{
        // Login and current policy are not available while persisted tickets are being loaded.
        new ArrayList<>(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets);
        new ArrayList<>(helper.getBlockTickets().keySet()).forEach(helper::removeAllTickets);
    });
    private static final Map<ServerLevel,Map<UUID,Set<ChunkPos>>> HELD=new WeakHashMap<>();
    private RemoteTickets() {}
    public static Map<UUID,Set<ChunkPos>> snapshot(ServerLevel level){return Map.copyOf(HELD.getOrDefault(level,Map.of()));}
    static void replace(ServerLevel level,Map<UUID,Set<ChunkPos>> requested) {
        var old=HELD.computeIfAbsent(level,k->new HashMap<>());
        try {
        for(var entry:new ArrayList<>(old.entrySet())) for(var chunk:entry.getValue())
            if(!requested.getOrDefault(entry.getKey(),Set.of()).contains(chunk))CONTROLLER.forceChunk(level,entry.getKey(),chunk.x,chunk.z,false,true);
        for(var entry:requested.entrySet()) {
            var held=new HashSet<ChunkPos>();
            for(var chunk:entry.getValue()) {
                boolean alreadyHeld=old.getOrDefault(entry.getKey(),Set.of()).contains(chunk);
                // Register each attempt before calling the controller, so any later exception can release it.
                held.add(chunk);var tracked=new HashSet<>(old.getOrDefault(entry.getKey(),Set.of()));tracked.add(chunk);old.put(entry.getKey(),Set.copyOf(tracked));
                if(!alreadyHeld && !CONTROLLER.forceChunk(level,entry.getKey(),chunk.x,chunk.z,true,true))
                    throw new IllegalStateException("Remote ticket acquisition failed; no partially loaded settlement is allowed");
            }
            old.put(entry.getKey(),Set.copyOf(held));
        }
        old.keySet().retainAll(requested.keySet());
        }catch(RuntimeException failure) {
            for(var entry:new ArrayList<>(old.entrySet())) {
                boolean released=true;
                for(var chunk:entry.getValue())try{CONTROLLER.forceChunk(level,entry.getKey(),chunk.x,chunk.z,false,true);}
                catch(RuntimeException cleanup){released=false;failure.addSuppressed(cleanup);}
                if(released)old.remove(entry.getKey());
            }
            throw failure;
        }
    }
    static boolean exists(ServerLevel level,ChunkPos chunk) {
        if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)!=null)return true;
        try {return level.getChunkSource().chunkMap.read(chunk).join().filter(tag->"minecraft:full".equals(tag.getString("Status")) && tag.getInt("xPos")==chunk.x && tag.getInt("zPos")==chunk.z).isPresent();}
        catch(RuntimeException e){return false;}
    }
    static void release(ServerLevel level){replace(level,Map.of());HELD.remove(level);}
}
