package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.ChunkDataHelper;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RemoteClaimRestartTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1800)
    public static void restart_pending_claims(GameTestHelper h){h.runAfterDelay(60,()->run(h));}
    private static void run(GameTestHelper h){var rows=new ArrayList<ProbeResult>();String phase=System.getProperty("colonyhorizons.probePhase","seed");try {
        var level=h.getLevel();var data=RemoteDevelopmentData.get(level);
        if(phase.equals("seed")) {
            h.assertTrue(data.all().isEmpty(),"Fresh fixture only");var env=new RemoteTestEnvironment(level,1024);
            data.transition(env.draft.id(),0,Phase.CLAIMING);data.beginClaims(env.draft.id());data.checkpoint(level);
            // Fault injection: interrupt after one native claim but before its journal confirmation.
            ChunkDataHelper.tryClaim(level,env.draft.chunks().iterator().next().getWorldPosition(),true,(Colony)env.home.colony(),false);
            h.assertTrue(data.confirmedClaims(env.draft.id()).isEmpty(),"Unconfirmed interrupted write");
        }else {
            h.assertTrue(data.all().size()==1,"Reload cannot reconstruct or reseed missing intent");var r=data.all().getFirst();
            for(var chunk:r.draft().chunks())level.getChunk(chunk.x,chunk.z); // Inspection loads existing saved terrain only.
            var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());h.assertTrue(colony!=null && colony.getPermissions().getOwner().equals(r.owner()),"Native primary owner survived restart");
            if(phase.equals("reload")) {
                h.assertTrue(r.phase()==Phase.CLAIMING && data.confirmedClaims(r.id()).isEmpty(),"Interrupted journal retained exactly");
                h.assertTrue(RemoteClaimService.reconcile(level,r.id()).blockers().isEmpty(),"Pending claim reconciliation");
                var updated=data.find(r.id()).orElseThrow();h.assertTrue(updated.phase()==Phase.STAGING,"Resume advances once");data.transition(r.id(),updated.revision(),Phase.PAUSED);data.checkpoint(level);
            }else h.assertTrue(phase.equals("paused-reload") && r.phase()==Phase.PAUSED,"Pause survives separate process");
            for(var chunk:r.draft().chunks()){var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);h.assertTrue(claim!=null && claim.getOwningColony()==colony.getID(),"Exact remote native claims persist");}
            var gap=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(32,0));h.assertTrue(gap==null||gap.getOwningColony()==0,"No corridor on reload");
            RemoteActivityService.tick(level);h.assertTrue(RemoteTickets.snapshot(level).isEmpty(),"Offline paused outpost has no tickets");
        }
        rows.add(new ProbeResult("claims_"+phase,ProbeResult.Outcome.PASS,"Separate-process journal and native claim recovery"));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteClaimRestart",rows);h.succeed();
    }catch(Throwable e){try{rows.add(new ProbeResult("claims_"+phase,ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteClaimRestart",rows);}catch(Exception ignored){}h.fail(e.toString());}}
}
