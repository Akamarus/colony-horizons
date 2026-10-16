// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.ChunkDataHelper;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Journal before effects; every native claim is rechecked without forcing ownership. */
public final class RemoteClaimService {
    private RemoteClaimService() {}
    public static StepResult commit(ServerLevel level,UUID id,long revision) {
        var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElse(null);
        if(r==null || r.revision()!=revision || r.phase()!=Phase.PROPOSED)return blocked("Review changed; approve the current proposal");
        var fresh=RemoteSitePlanner.refresh(level,r.owner(),r.draft());
        if(!fresh.blockers().isEmpty())return new StepResult("Review required",fresh.blockers(),false);
        if(!fresh.fingerprint().equals(r.draft().fingerprint()))return blocked("Site or blueprints changed; review again");
        data.transition(id,revision,Phase.CLAIMING);data.beginClaims(id);data.checkpoint(level);
        return reconcile(level,id);
    }
    public static StepResult reconcile(ServerLevel level,UUID id) {
        var data=RemoteDevelopmentData.get(level);var r=data.find(id).orElse(null);
        if(r==null || !data.hasClaimJournal(id) || r.phase()==Phase.CANCELLED || r.phase()==Phase.PROPOSED)return blocked("No approved remote claim journal");
        var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
        if(!(colony instanceof Colony nativeColony) || !r.owner().equals(colony.getPermissions().getOwner()))return recovery(level,data,r,"Colony ownership changed");
        // Check the entire footprint before any mutation, including previously completed entries.
        boolean needsReconcile=!data.confirmedClaims(id).containsAll(r.draft().chunks());
        for(var chunk:r.draft().chunks()) {
            if(!level.hasChunk(chunk.x,chunk.z))return blocked("Approved terrain is not loaded");
            if(!level.getWorldBorder().isWithinBounds(chunk.getWorldPosition())||!level.getWorldBorder().isWithinBounds(chunk.getWorldPosition().offset(15,0,15)))return recovery(level,data,r,"World border changed");
            var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
            if(claim!=null && claim.getOwningColony()!=0 && claim.getOwningColony()!=colony.getID())return recovery(level,data,r,"Approved land now belongs to another colony");
            if(claim==null || claim.getOwningColony()!=colony.getID())needsReconcile=true;
        }
        if(!needsReconcile && r.phase()!=Phase.CLAIMING)return new StepResult("Remote land reserved",List.of(),false);
        boolean changed=false;
        try {
            data.checkpoint(level);
            for(var chunk:r.draft().chunks()) {
                var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
                if(claim==null || claim.getOwningColony()!=colony.getID()) {
                    ChunkDataHelper.tryClaim(level,chunk.getWorldPosition(),true,nativeColony,false);
                    claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
                    if(claim==null || claim.getOwningColony()!=colony.getID())throw new IllegalStateException("Native claim was not established");changed=true;
                }
                data.confirmClaim(id,chunk);
            }
            // Confirmations are recoverable intent. Native loss on restart replays only these approved claims.
            data.checkpoint(level);
            if(r.phase()==Phase.CLAIMING) {data.transition(id,r.revision(),Phase.STAGING);data.checkpoint(level);changed=true;}
            return new StepResult("Remote land reserved",List.of(),changed);
        }catch(RuntimeException failure){return recovery(level,data,data.find(id).orElseThrow(),"Claim interrupted: "+failure.getMessage());}
    }
    public static void reconcileColony(Colony colony) {
        var level=colony.getWorld();if(level==null)return;
        var existing=RemoteDevelopmentData.existing(level);if(existing.isEmpty())return;
        for(var r:existing.orElseThrow().all())if(r.draft().request().colonyId()==colony.getID() && r.phase()!=Phase.CANCELLED && r.phase()!=Phase.PROPOSED)reconcile(level,r.id());
    }
    private static StepResult recovery(ServerLevel level,RemoteDevelopmentData data,Development r,String message) {
        if(r.phase()!=Phase.RECOVERY_NEEDED){data.transition(r.id(),r.revision(),Phase.RECOVERY_NEEDED);data.checkpoint(level);}
        return blocked(message);
    }
    private static StepResult blocked(String why){return new StepResult("Claim blocked",List.of(why),false);}
}
