// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.NativeSupplyCheckpoint;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Founding inventory boundary. The coordinator supplies a server-derived bill, never a client-authored one. */
public final class RemoteFoundingService {
    private static final String SOURCE="colonyhorizons:founding_source";
    private RemoteFoundingService() {}
    public static Map<ItemKey,Integer> bootstrapBill(net.minecraft.server.level.ServerLevel level,UUID development) {
        var bill=new HashMap<>(FoundingPlacement.bill(level,development));
        FoundingSupplies.bill(RemoteDevelopmentData.get(level).find(development).orElseThrow().draft().request()).forEach((k,v)->bill.merge(k,v,Math::addExact));
        FoundingConstructionSupplies.bill(level,development).forEach((k,v)->bill.merge(k,v,Math::addExact));
        PreparedFounding.bill(RemoteDevelopmentData.get(level).find(development).orElseThrow().draft()).forEach((k,v)->bill.merge(k,v,Math::addExact));return Map.copyOf(bill);
    }
    public static StepResult approve(ServerPlayer actor,UUID development,long revision) {
        var level=actor.serverLevel();var data=RemoteDevelopmentData.get(level);var r=data.find(development).orElseThrow();
        var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
        if(!actor.getUUID().equals(r.owner())||colony==null||!r.owner().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only the current colony owner may approve founding");
        if(r.phase()!=Phase.PROPOSED||r.revision()!=revision)return blocked("Review changed; approve the current proposal");
        try {
            var party=FoundingRoster.free(r.draft().request())?List.<FoundingTravel.Member>of():FoundingTravel.review(actor,development);if(FoundingRoster.free(r.draft().request())){var review=FoundingGrantService.review(level,development);if(!review.blockers().isEmpty())return review;}var bill=bootstrapBill(level,development);var pos=r.draft().request().homeSupply();
            var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(pos));
            if(!level.hasChunkAt(pos)||claim==null||claim.getOwningColony()!=colony.getID()||colony.getServerBuildingManager().getBuilding(pos)==null||r.draft().chunks().contains(new ChunkPos(pos))||!(level.getBlockEntity(pos) instanceof TileEntityColonyBuilding hut))return blocked("Choose an available registered Home supply hut");
            if(FoundingConstructionSupplies.warehouseFirst(r)&&!(hut.getBuilding() instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse warehouse&&warehouse.isBuilt()&&warehouse.getBuildingLevel()>0))return blocked("Choose a completed Home warehouse");
            // Read-only inventory staging precedes claims: a shortage cannot reserve remote land.
            FoundingCargo.prepare(FoundingCargo.operationFor(development),UUID.randomUUID(),dev.colonyhorizons.supply.WarehouseInventory.resolve(level,hut).inventory(),r.draft().request().budget(),bill);
            var fresh=RemoteSitePlanner.refresh(level,actor.getUUID(),r.draft());
            if(!fresh.blockers().isEmpty())return new StepResult("Review required",fresh.blockers(),false);
            if(!fresh.fingerprint().equals(r.draft().fingerprint()))return blocked("Site or blueprints changed; review again");
            if(FoundingRoster.free(r.draft().request())){var reserved=FoundingGrantService.reserve(actor,development,revision);if(!reserved.blockers().isEmpty())return reserved;}else{data.enableFounding(development,party);data.checkpoint(level);}
            var claims=RemoteClaimService.commit(level,development,revision);if(!claims.blockers().isEmpty())return claims;
            escrow(actor,development,data.find(development).orElseThrow().revision(),bill);
            return new StepResult("Founding supplies secured",List.of(),true);
        }catch(IllegalArgumentException|IllegalStateException failure){return blocked(failure.getMessage());}
    }
    public static StepResult advance(net.minecraft.server.level.ServerLevel level,UUID development) {
        var data=RemoteDevelopmentData.get(level);var r=data.find(development).orElseThrow();
        if(!data.foundingManaged(development))return blocked("No approved founding coordinator");
        if(r.phase()!=Phase.STAGING)return blocked("Settlement is not staging");
        var owner=level.getServer().getPlayerList().getPlayer(r.owner());
        if(owner==null||owner.serverLevel()!=level||!RemoteTickets.snapshot(level).containsKey(development))return blocked("Waiting for owner and approved terrain");
        try {
            if(data.foundingRefunded(development))return cancel(owner,development,r.revision());
            if(data.foundingCargo(development).isEmpty()){escrow(owner,development,r.revision(),bootstrapBill(level,development));return new StepResult("Founding supplies secured",List.of(),true);}
            var preparing=PreparedFounding.advance(level,development);if(preparing!=null)return preparing;
            for(var plan:r.draft().buildings())if(!data.anchorReceipts(development).containsKey(plan.operationId())){FoundingPlacement.place(owner,development,plan.operationId());return new StepResult("Placing "+plan.role()+" hut",List.of(),true);}
            if(data.anchorReceipts(development).values().stream().anyMatch(a->!a.complete()))return blocked("Interrupted placement needs recovery");
            if(r.draft().preparation().isPresent())PreparedFounding.handoff(level,development);
            if(FoundingRoster.free(r.draft().request())&&data.founderRoster(development).isEmpty())return FoundingGrantService.advance(level,development);
            if(data.journey(development).isEmpty()){if(FoundingRoster.free(r.draft().request()))return FoundingTravel.beginGranted(level,development);FoundingTravel.begin(level,development,FoundingRoster.citizenIds(data,development));return new StepResult("Founders travelling",List.of(),true);}
            FoundingTravel.reconcile(level,development);
            if(data.journey(development).orElseThrow().state()==FoundingTravel.State.ARRIVED){boolean delivered=FoundingSupplies.deliver(level,development);if(FoundingConstructionSupplies.warehouseFirst(r)&&(!data.mayorInitialized(development)||data.mayor(development).isPresent()&&!dev.colonyhorizons.mayor.MayorService.available(level,development)))dev.colonyhorizons.mayor.MayorService.appoint(owner,development,FoundingRoster.mayorId(data,development));return new StepResult("Founders arrived",List.of(),delivered);}
            return new StepResult(data.journey(development).orElseThrow().state()==FoundingTravel.State.ARRIVED?"Founders arrived":"Founders travelling",List.of(),false);
        }catch(IllegalArgumentException|IllegalStateException failure){return blocked(failure.getMessage());}
    }
    private static StepResult blocked(String message){return new StepResult("Founding blocked",List.of(message==null?"Founding requires review":message.length()>500?message.substring(0,500):message),false);}
    public static StepResult cancel(ServerPlayer actor,UUID development,long revision) {
        var level=actor.serverLevel();var data=RemoteDevelopmentData.get(level);var r=data.find(development).orElseThrow();
        var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
        if(!actor.getUUID().equals(r.owner())||!(colony instanceof com.minecolonies.core.colony.Colony nativeColony)||!r.owner().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only the current owner may cancel founding");
        if(r.revision()!=revision)return blocked("Settlement changed; refresh before cancelling");
        if(data.grant(development).filter(FoundingGrantJournal::consumed).isPresent())return FoundingGrantService.cancel(actor,development,revision);
        if(FoundingRoster.free(r.draft().request())&&data.grant(development).isPresent()&&(!data.anchorReceipts(development).isEmpty()||data.preparationProgress(development).isPresent())){
            if(r.phase()!=Phase.CANCELLED)data.transition(development,r.revision(),Phase.CANCELLED);data.checkpoint(level);RemoteActivityService.tick(level);
            return new StepResult("Founding cancelled; population places released, site supplies retained",List.of(),true);
        }
        if(!data.anchorReceipts(development).isEmpty()||data.journey(development).isPresent()) {
            if(r.phase()!=Phase.RECOVERY_NEEDED&&r.phase()!=Phase.CANCELLED){data.transition(development,r.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(level);}
            return blocked("Founding has native effects; preserve buildings, citizens and supplies for recovery");
        }
        if(r.phase()!=Phase.PROPOSED&&r.phase()!=Phase.STAGING&&r.phase()!=Phase.CLAIMING&&r.phase()!=Phase.CANCELLED)return blocked("Settlement requires recovery before cancellation");
        // Do not release a footprint subsequently occupied by another approved settlement or native hut.
        for(var other:data.all())if(!other.id().equals(development)&&other.phase()!=Phase.PROPOSED&&other.phase()!=Phase.CANCELLED&&!Collections.disjoint(other.draft().chunks(),r.draft().chunks()))return blocked("Remote land is in use by another settlement");
        if(data.hasClaimJournal(development))for(var chunk:r.draft().chunks()) {
            if(!level.hasChunk(chunk.x,chunk.z))return blocked("Load the approved site before cancelling its claims");
            for(var building:colony.getServerBuildingManager().getBuildings().values())if(new ChunkPos(building.getPosition()).equals(chunk))return blocked("Native buildings now occupy the reserved site");
        }
        if(data.foundingCargo(development).isPresent()&&!data.foundingRefunded(development)) {
            if(data.refundPending(development))return blocked("Interrupted refund requires recovery");
            FoundingCargo cargo;
            try{cargo=FoundingCargo.load(data.foundingCargo(development).orElseThrow(),level.registryAccess());}catch(RuntimeException failure){return blocked("Founding custody requires recovery");}
            var pos=r.draft().request().homeSupply();
            if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof TileEntityColonyBuilding hut)||!hut.getPersistentData().hasUUID(SOURCE)||!cargo.source().equals(hut.getPersistentData().getUUID(SOURCE))||colony.getServerBuildingManager().getBuilding(pos)==null)return blocked("Original Home supply hut is unavailable; cargo retained");
            var staged=new dev.colonyhorizons.supply.SupplyInventoryBatch(Map.of(cargo.source(),dev.colonyhorizons.supply.WarehouseInventory.resolve(level,hut).inventory()));
            for(var e:cargo.items().entrySet())if(!staged.insert(cargo.source(),e.getKey().stack().copyWithCount(e.getValue())).isEmpty())return blocked("Make room in the original Home supply hut for the refund");
            try {
                data.refundStage(development,1);data.checkpoint(level);staged.apply();var view=dev.colonyhorizons.supply.WarehouseInventory.resolve(level,hut);view.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);NativeSupplyCheckpoint.flushAndVerify(level,view.entities());data.refundStage(development,2);data.checkpoint(level);
            }catch(RuntimeException failure){var current=data.find(development).orElseThrow();data.transition(development,current.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(level);return blocked("Refund interrupted; no automatic replay");}
        }
        if(r.phase()!=Phase.CANCELLED){data.transition(development,r.revision(),Phase.CANCELLED);data.checkpoint(level);}
        if(data.hasClaimJournal(development))for(var chunk:r.draft().chunks()) {
            var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
            if(claim!=null&&claim.getOwningColony()==colony.getID())com.minecolonies.core.util.ChunkDataHelper.tryClaim(level,chunk.getWorldPosition(),false,nativeColony,false);
        }
        NativeFoundingCheckpoint.save(level,colony);RemoteActivityService.tick(level);
        return new StepResult("Founding cancelled",List.of(),true);
    }
    public static FoundingCargo escrow(ServerPlayer actor,UUID development,long revision,Map<ItemKey,Integer> bill) {
        var level=actor.serverLevel();var data=RemoteDevelopmentData.get(level);var record=data.find(development).orElseThrow(()->new IllegalArgumentException("Unknown founding proposal"));
        var request=record.draft().request();var colony=IColonyManager.getInstance().getColonyByDimension(request.colonyId(),level.dimension());
        if(!actor.getUUID().equals(record.owner()) || colony==null || !actor.getUUID().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only the current colony owner can supply its founding party");
        if(record.revision()!=revision || record.phase()!=Phase.STAGING || !data.hasClaimJournal(development))throw new IllegalArgumentException("Stale or unapproved founding operation");
        var prior=data.foundingCargo(development);
        if(prior.isPresent()) {
            var cargo=FoundingCargo.load(prior.orElseThrow(),level.registryAccess());
            if(!cargo.items().equals(bill))throw new IllegalArgumentException("Founding bill differs from the saved operation");
            return cargo;
        }
        if(!FoundingRoster.free(request))FoundingTravel.review(actor,development);
        var pos=request.homeSupply();var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(pos));
        if(!level.hasChunkAt(pos) || claim==null || claim.getOwningColony()!=colony.getID() || colony.getServerBuildingManager().getBuilding(pos)==null || !(level.getBlockEntity(pos) instanceof TileEntityColonyBuilding hut))throw new IllegalArgumentException("Choose an available registered Home supply hut");
        if(FoundingConstructionSupplies.warehouseFirst(record)&&!(hut.getBuilding() instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse warehouse&&warehouse.isBuilt()&&warehouse.getBuildingLevel()>0))throw new IllegalArgumentException("Choose a completed Home warehouse");
        if(record.draft().chunks().contains(new ChunkPos(pos)))throw new IllegalArgumentException("Founding supplies must come from Home");
        var marker=hut.getPersistentData();var source=marker.hasUUID(SOURCE)?marker.getUUID(SOURCE):UUID.randomUUID();
        var view=dev.colonyhorizons.supply.WarehouseInventory.resolve(level,hut);
        var withdrawal=FoundingCargo.prepare(FoundingCargo.operationFor(development),source,view.inventory(),request.budget(),bill);
        // Identity is persisted with the native source before its contents leave. Failed staging never creates goods.
        marker.putUUID(SOURCE,source);hut.setChanged();
        try {
            NativeSupplyCheckpoint.flushAndVerify(level,view.entities());
            return withdrawal.commit(level.registryAccess(),tag->data.storeFoundingCargo(level,development,tag),()->{
                if(level.getBlockEntity(pos)!=hut || !source.equals(hut.getPersistentData().getUUID(SOURCE)))throw new IllegalStateException("Founding source was replaced during withdrawal");
                view.validate(level);view.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged);NativeSupplyCheckpoint.flushAndVerify(level,view.entities());
            });
        }catch(RuntimeException failure) {
            var current=data.find(development).orElseThrow();data.transition(development,current.revision(),Phase.RECOVERY_NEEDED);
            try{data.checkpoint(level);}catch(RuntimeException saveFailure){failure.addSuppressed(saveFailure);}
            throw failure;
        }
    }
}
