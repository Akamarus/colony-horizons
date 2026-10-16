// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.MineColonies;
import dev.colonyhorizons.integration.ArcologyCatalog;
import dev.colonyhorizons.menu.SupplyMenuService;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Read-only validation. No chunk generation, block placement, claims or inventory mutation. */
public final class RemoteSitePlanner {
    private RemoteSitePlanner() {}
    /** Informational terrain result: deliberately carries no founding draft or approval token. */
    public record TerrainReview(boolean suitable,List<String> blockers,int clearBlocks,int fillBlocks) {
        public TerrainReview {blockers=List.copyOf(blockers);}
    }
    public static TerrainReview inspectCandidate(net.minecraft.server.level.ServerLevel level,UUID owner,SiteRequest request) {
        if(request.surveyOrigin().isEmpty())throw new IllegalArgumentException("Candidate inspection needs survey evidence");
        var draft=review(level,owner,request,true,true);
        return new TerrainReview(draft.blockers().isEmpty(),draft.blockers(),draft.preparation().map(p->p.terrain().clearRequired()).orElse(0),draft.preparation().map(p->p.terrain().dirtRequired()).orElse(0));
    }
    public static SiteDraft review(ServerPlayer actor,SiteRequest request) {
        SupplyMenuService.owned(actor,request.colonyId());return review(actor.serverLevel(),actor.getUUID(),request);
    }
    public static SiteDraft review(net.minecraft.server.level.ServerLevel level,UUID ownerId,SiteRequest request) {
        return review(level,ownerId,request,false);
    }
    public static SiteDraft reviewPrepared(net.minecraft.server.level.ServerLevel level,UUID ownerId,SiteRequest request){return review(level,ownerId,request,true);}
    public static SiteDraft refresh(net.minecraft.server.level.ServerLevel level,UUID ownerId,SiteDraft draft){return review(level,ownerId,draft.request(),draft.preparation().isPresent());}
    private static SiteDraft review(net.minecraft.server.level.ServerLevel level,UUID ownerId,SiteRequest request,boolean prepare) {
        return review(level,ownerId,request,prepare,false);
    }
    private static SiteDraft review(net.minecraft.server.level.ServerLevel level,UUID ownerId,SiteRequest request,boolean prepare,boolean inspectionOnly) {
        var colony=IColonyManager.getInstance().getColonyByDimension(request.colonyId(),level.dimension());
        if(colony==null || !ownerId.equals(colony.getPermissions().getOwner()))throw new SecurityException("Colony ownership changed");
        var id=UUID.randomUUID();
        var blockers=new LinkedHashSet<String>();var chunks=new HashSet<ChunkPos>();var buildings=List.<BuildingPlan>of();
        var fingerprint=new StringBuilder(ownerId.toString()).append(':').append(request.colonyId()).append(':').append(request.center()).append(':').append(request.allowedArea()).append(':').append(request.maxBuildingLevel());
        if(FoundingRoster.free(request))fingerprint.append(':').append(request.foundingPolicy().name());
        request.surveyOrigin().ifPresent(origin->{fingerprint.append(":SURVEY_V1:").append(origin.survey()).append(':').append(origin.source()).append(':').append(origin.profile().target());try{origin.verifyEvidence(level,ownerId,request.colonyId(),inspectionOnly);}catch(IllegalArgumentException e){blockers.add(e.getMessage());}
            // Fail closed until this profile's native construction and producer setup are wired.
            if(!inspectionOnly)blockers.add("Survey-bound producer construction is not yet enabled");});
        if(!level.dimension().equals(Level.OVERWORLD)) blockers.add("Remote forestry is currently Overworld only");
        var center=new ChunkPos(request.center());var home=new ChunkPos(colony.getCenter());
        int radius=MineColonies.getConfig().getServer().maxColonySize.get();
        if(radius==0) blockers.add("Configure a finite main-colony radius before founding remote settlements");
        boolean loaded=true;
        for(int x=center.x-3;x<=center.x+3;x++)for(int z=center.z-3;z<=center.z+3;z++) {
            var chunk=new ChunkPos(x,z);chunks.add(chunk);
            var low=new BlockPos(chunk.getMinBlockX(),request.center().getY(),chunk.getMinBlockZ());
            var high=low.offset(15,0,15);
            if(!request.allowedArea().isInside(low)||!request.allowedArea().isInside(high)) blockers.add("Entire footprint must fit the authorized area");
            long dx=(long)x-home.x,dz=(long)z-home.z;
            if(dx*dx+dz*dz<=(long)(radius+1)*(radius+1)) blockers.add("Choose a remote site with an unclaimed gap beyond Home's maximum radius");
            if(!level.getWorldBorder().isWithinBounds(low)||!level.getWorldBorder().isWithinBounds(high)) blockers.add("Site crosses the world border");
            if(!level.hasChunk(x,z)){loaded=false;blockers.add("Survey this terrain first; remote review does not generate or load chunks");continue;}
            var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);int owner=claim==null?0:claim.getOwningColony();
            fingerprint.append(':').append(chunk.toLong()).append('=').append(owner);
            if(owner!=0) blockers.add("Site overlaps existing colony claims");
        }
        for(var old:RemoteDevelopmentData.get(level).all())if(old.phase()!=Phase.CANCELLED && old.phase()!=Phase.PROPOSED && !Collections.disjoint(old.draft().chunks(),chunks)) blockers.add("Site overlaps an approved outpost");
        var pack=StructurePacks.getStructurePack("Arcology Horizons");if(pack==null) pack=StructurePacks.getStructurePack("Arcology");
        if(pack==null) blockers.add("Install the Arcology Horizons style pack");
        else try {buildings=ArcologySettlementLayout.plan(id,request,ArcologyCatalog.inspect(pack.getPath(),level.registryAccess(),request.surveyOrigin().map(SurveyOrigin::profile).orElse(null)));}
        catch(IllegalArgumentException e){blockers.add(e.getMessage());}
        for(var b:buildings) {
            fingerprint.append(':').append(b.blueprint()).append(':').append(b.hash()).append(':').append(b.anchor()).append(':').append(b.upgradeVolume());
            var volume=b.upgradeVolume();
            if(!RemoteModels.contains(request.allowedArea(),volume) || !chunks.contains(new ChunkPos(volume.minX()>>4,volume.minZ()>>4)) || !chunks.contains(new ChunkPos(volume.maxX()>>4,volume.maxZ()>>4))) {
                blockers.add("Upgrade envelope exceeds approved footprint");continue;
            }
            if(volume.minY()<=level.getMinBuildHeight() || volume.maxY()>=level.getMaxBuildHeight()) {blockers.add("Building exceeds world height");continue;}
            if(!loaded||prepare) continue;
            for(int x=volume.minX();x<=volume.maxX();x++)for(int z=volume.minZ();z<=volume.maxZ();z++) {
                var floor=new BlockPos(x,volume.minY()-1,z);var state=level.getBlockState(floor);fingerprint.append(Block.getId(state)).append(',');
                if(!state.isFaceSturdy(level,floor,net.minecraft.core.Direction.UP)||!state.getFluidState().isEmpty()||level.getBlockEntity(floor)!=null) blockers.add("Building foundations need dry solid support");
            }
            for(var pos:BlockPos.betweenClosed(volume.minX(),volume.minY(),volume.minZ(),volume.maxX(),volume.maxY(),volume.maxZ())) {
                var state=level.getBlockState(pos);fingerprint.append(Block.getId(state)).append(',');
                if(!state.getFluidState().isEmpty()) blockers.add("Remove liquid hazards from the building area");
                if(level.getBlockEntity(pos)!=null) blockers.add("Building area contains a protected container or block entity");
                if(!state.isAir() && !state.canBeReplaced()) blockers.add("Starter building and upgrade space must be clear");
            }
        }
        if(loaded&&!prepare) {
            // Open ground between the two rows, with approaches to each module. Never fill or clear during review.
            for(int dx=-28;dx<=30;dx++)for(int dz=-5;dz<=36;dz++) {
                var pos=request.center().offset(dx,0,dz);
                if(buildings.stream().anyMatch(b->b.upgradeVolume().isInside(pos))) continue;
                var floor=pos.below();var support=level.getBlockState(floor);var feet=level.getBlockState(pos);var head=level.getBlockState(pos.above());
                fingerprint.append(Block.getId(support)).append(',').append(Block.getId(feet)).append(',').append(Block.getId(head)).append(';');
                if(!support.isFaceSturdy(level,floor,net.minecraft.core.Direction.UP)||!support.getFluidState().isEmpty()||level.getBlockEntity(floor)!=null
                    ||!feet.getFluidState().isEmpty()||!head.getFluidState().isEmpty()||!feet.getCollisionShape(level,pos).isEmpty()||!head.getCollisionShape(level,pos.above()).isEmpty())
                    blockers.add("Paths between starter buildings need dry, supported, clear access");
            }
        }
        if(chunks.size()>RemoteLimits.forServer(level.getServer()).chunks()) blockers.add("Site exceeds the server's remote chunk limit");
        Optional<PreparedSite> preparation=Optional.empty();
        if(prepare&&loaded&&blockers.isEmpty())try{var p=PreparedSite.review(level,request,buildings);preparation=Optional.of(p);fingerprint.append(":PREPARED_V1:").append(p.fingerprint());}catch(IllegalArgumentException failure){blockers.add(failure.getMessage());}
        var draft=new SiteDraft(id,request,chunks,buildings,ArcologySettlementLayout.sha(fingerprint.toString()),List.copyOf(blockers),preparation);
        if(!inspectionOnly&&FoundingRoster.free(request)){blockers.addAll(FoundingGrantService.review(level,new Development(id,ownerId,draft,Mode.APPROVAL,Phase.PROPOSED,0)).blockers());draft=new SiteDraft(id,request,chunks,buildings,draft.fingerprint(),List.copyOf(blockers),preparation);}
        return draft;
    }
}

