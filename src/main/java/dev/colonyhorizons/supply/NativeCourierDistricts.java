package dev.colonyhorizons.supply;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.util.*;
import dev.colonyhorizons.supply.CourierDistrictPolicy.*;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.integration.NativeOutpostMembership;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.registries.BuiltInRegistries;

public final class NativeCourierDistricts {
    private NativeCourierDistricts() {}
    static List<dev.colonyhorizons.remote.RemoteModels.Development> remoteRecords(ServerLevel level,int colonyId){
        var existing=dev.colonyhorizons.remote.RemoteDevelopmentData.existing(level);if(existing.isEmpty())return List.of();var data=existing.orElseThrow();
        return data.all().stream().filter(r->r.draft().request().colonyId()==colonyId&&data.foundingManaged(r.id())&&r.phase()!=dev.colonyhorizons.remote.RemoteModels.Phase.PROPOSED).toList();
    }
    public static Endpoint resolve(ServerLevel level,int colonyId,BlockPos anchor) {
        if(level==null || anchor==null || colonyId<=0)return blocked("Missing courier endpoint");
        final List<OutpostRecord> records;
        final List<dev.colonyhorizons.remote.RemoteModels.Development> remote;
        try { records=OutpostSavedData.get(level).all();remote=remoteRecords(level,colonyId); }
        catch(RuntimeException failure) { return blocked("Outpost index unavailable; preserve data for recovery"); }
        var colony=IColonyManager.getInstance().getColonyByDimension(colonyId,level.dimension());
        if(colony==null)return blocked("Native colony unavailable");
        var local=records.stream().filter(r->r.colonyId()==colonyId).toList();
        boolean marked=colony instanceof ManagedCourierColony marker && marker.colonyHorizons$managedCouriers();
        if(local.isEmpty()&&remote.isEmpty())return marked?blocked("Managed colony has no outpost index"):new Endpoint(Status.UNMANAGED,Optional.empty(),"");
        if(!(colony instanceof ManagedCourierColony marker))return blocked("Courier colony marker hook unavailable");
        marker.colonyHorizons$markManagedCouriers();
        if(!level.hasChunkAt(anchor))return blocked("Unloaded courier endpoint");
        var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(anchor));
        if(claim==null || claim.getOwningColony()!=colonyId)return blocked("Missing or foreign claim");
        var building=colony.getServerBuildingManager().getBuilding(anchor);
        if(building==null && level.getBlockEntity(anchor) instanceof com.minecolonies.core.tileentities.TileEntityRack) {
            var owners=colony.getServerBuildingManager().getWareHouses().stream().filter(w->w.getContainers().contains(anchor)).toList();
            if(owners.size()!=1)return blocked("Warehouse rack ownership unavailable or ambiguous");
            var parent=resolve(level,colonyId,owners.getFirst().getPosition());
            if(parent.status()!=Status.ACTIVE)return blocked("Warehouse rack's hut is unavailable");
            var containing=records.stream().filter(r->r.approvedChunks().contains(new ChunkPos(anchor))).toList();
            var expected=parent.district().orElseThrow().outpostId();
            var remoteContaining=remote.stream().filter(r->r.draft().chunks().contains(new ChunkPos(anchor))).toList();
            if(!remoteContaining.isEmpty()){
                if(remoteContaining.size()!=1||expected.isEmpty()||!expected.orElseThrow().equals(remoteContaining.getFirst().id()))return blocked("Warehouse rack crosses remote boundary");
                return parent;
            }
            if(expected.isEmpty()? !containing.isEmpty(): containing.size()!=1 || !containing.getFirst().id().equals(expected.get()))
                return blocked("Warehouse rack crosses district boundary");
            return parent;
        }
        if(building==null || building.getColony()!=colony || !(level.getBlockEntity(anchor) instanceof TileEntityColonyBuilding tile)
            || tile.getBuilding()!=building)return blocked("Native hut identity unavailable");
        for(var r:remote)if(r.draft().chunks().contains(new ChunkPos(anchor))){
            if(!r.owner().equals(colony.getPermissions().getOwner())||!Set.of(dev.colonyhorizons.remote.RemoteModels.Phase.STAGING,dev.colonyhorizons.remote.RemoteModels.Phase.BUILDING,dev.colonyhorizons.remote.RemoteModels.Phase.OPERATING).contains(r.phase()))return blocked("Remote settlement is paused or needs recovery");
            var plan=dev.colonyhorizons.remote.RemoteDevelopmentData.get(level).buildings(r.id()).stream().filter(p->p.anchor().equals(anchor)).findFirst();
            if(plan.isEmpty()||!dev.colonyhorizons.remote.FoundingPlacement.isPaidAnchor(level,plan.orElseThrow()))return blocked("Remote hut identity unavailable");
            return new Endpoint(Status.ACTIVE,Optional.of(new District(level.dimension(),colonyId,Optional.of(r.id()))),"");
        }
        for(var record:records) {
            if(!record.approvedChunks().contains(new ChunkPos(anchor)))continue;
            if(record.colonyId()!=colonyId)return blocked("Foreign outpost footprint");
            var reference=record.buildings().get(anchor);
            if(reference==null || !BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock()).equals(reference.type()))
                return blocked("Hut is not registered in this district");
            if(record.state()!=OutpostRecord.State.ACTIVE || !record.blockers().isEmpty())return blocked("Outpost is not active");
            return new Endpoint(Status.ACTIVE,Optional.of(new District(level.dimension(),colonyId,Optional.of(record.id()))),"");
        }
        return new Endpoint(Status.ACTIVE,Optional.of(new District(level.dimension(),colonyId,Optional.empty())),"");
    }
    public static void activate(ServerLevel level,UUID outpostId) {
        var data=OutpostSavedData.get(level);
        var record=data.find(outpostId).orElseThrow(()->new IllegalStateException("Unknown outpost"));
        if(record.buildings().isEmpty() || !record.blockers().isEmpty())throw new IllegalStateException("Outpost has unresolved blockers");
        var report=NativeOutpostMembership.inspect(level,record);
        if(!report.ready())throw new IllegalStateException("Outpost membership is not ready: "+report);
        var colony=IColonyManager.getInstance().getColonyByDimension(record.colonyId(),level.dimension());
        if(!(colony instanceof ManagedCourierColony marker))throw new IllegalStateException("Courier colony marker hook unavailable");
        marker.colonyHorizons$markManagedCouriers();
        data.register(record.withState(OutpostRecord.State.ACTIVE));
    }
    private static Endpoint blocked(String reason) {return new Endpoint(Status.BLOCKED,Optional.empty(),reason);}
}
