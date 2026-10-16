// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.IColonyManager;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.NativeSupplyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RemoteActivityService {
    private RemoteActivityService() {}
    public static void tick(ServerLevel level) {
        var existing=RemoteDevelopmentData.existing(level);if(existing.isEmpty()){RemoteTickets.release(level);return;}
        var data=existing.orElseThrow();var cap=RemoteLimits.forServer(level.getServer());
        var requested=new LinkedHashMap<UUID,Set<ChunkPos>>();var counts=new HashMap<UUID,Integer>();var developing=new HashMap<UUID,Integer>();var endpoints=new HashMap<UUID,Set<ChunkPos>>();
        var online=new HashSet<UUID>();for(var player:level.getServer().getPlayerList().getPlayers())if(!(player instanceof FakePlayer) && !player.isRemoved())online.add(player.getUUID());
        for(var r:data.all().stream().sorted(Comparator.comparing(Development::id)).toList()) {
            if(!online.contains(r.owner()) || !data.hasClaimJournal(r.id()) || !Set.of(Phase.CLAIMING,Phase.STAGING,Phase.BUILDING,Phase.OPERATING).contains(r.phase()))continue;
            var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
            if(colony==null || !r.owner().equals(colony.getPermissions().getOwner()) || r.draft().chunks().size()>cap.chunks() || counts.getOrDefault(r.owner(),0)>=cap.active())continue;
            if(r.phase()!=Phase.OPERATING && developing.getOrDefault(r.owner(),0)>=cap.developing())continue;
            var homeChunks=new HashSet<>(endpoints.getOrDefault(r.owner(),Set.of()));homeChunks.add(new ChunkPos(r.draft().request().homeSupply()));
            var supplies=NativeSupplyService.get(level);
            var bound=new HashMap<UUID,NativeSupplyService.Endpoint>();
            for(var endpoint:supplies.endpoints(r.owner()))if(endpoint.colony()==colony.getID())bound.put(endpoint.id(),endpoint);
            for(var route:supplies.routes(r.owner())) {
                var settings=supplies.settings(route.id());
                if((settings.disabled() || settings.paused()) && route.cargo().isEmpty())continue;
                var source=bound.get(route.source());var destination=bound.get(route.destination());
                if(source==null || destination==null)continue;
                if(source.outpost() && r.draft().chunks().contains(new ChunkPos(source.position())) && !destination.outpost())homeChunks.add(new ChunkPos(destination.position()));
                if(destination.outpost() && r.draft().chunks().contains(new ChunkPos(destination.position())) && !source.outpost())homeChunks.add(new ChunkPos(source.position()));
            }
            // Native warehouse racks can occupy neighboring Home chunks. Keep the same endpoint cap.
            for(var chunk:new HashSet<>(homeChunks))for(var building:colony.getServerBuildingManager().getWareHouses())if(new ChunkPos(building.getPosition()).equals(chunk))for(var pos:building.getContainers())homeChunks.add(new ChunkPos(pos));
            if(homeChunks.size()>cap.endpointChunks())continue;
            boolean valid=true;
            for(var chunk:r.draft().chunks()) {
                var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
                if((claim!=null && claim.getOwningColony()!=0 && claim.getOwningColony()!=colony.getID())||!RemoteTickets.exists(level,chunk)) {valid=false;break;}
            }
            for(var chunk:homeChunks)if(!RemoteTickets.exists(level,chunk))valid=false;
            if(!valid)continue;
            requested.put(r.id(),r.draft().chunks());endpoints.put(r.owner(),Set.copyOf(homeChunks));counts.merge(r.owner(),1,Integer::sum);
            if(r.phase()!=Phase.OPERATING)developing.merge(r.owner(),1,Integer::sum);
        }
        endpoints.forEach((owner,chunks)->requested.put(UUID.nameUUIDFromBytes((owner+":remote-home-endpoints").getBytes(StandardCharsets.UTF_8)),chunks));
        RemoteTickets.replace(level,requested);
        for(var r:data.all())if(requested.containsKey(r.id()))RemoteClaimService.reconcile(level,r.id());
    }
    public static boolean active(ServerLevel level,int colonyId) {
        var held=RemoteTickets.snapshot(level);
        return RemoteDevelopmentData.get(level).all().stream().anyMatch(r->r.draft().request().colonyId()==colonyId && held.containsKey(r.id()));
    }
}
