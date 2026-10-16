package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.ChunkDataHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RemoteClaimTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
    public static void remote_claims(GameTestHelper h){h.runAfterDelay(40,()->run(h));}
    private static void run(GameTestHelper h){var rows=new ArrayList<ProbeResult>();try {
        var l=h.getLevel();var env=new RemoteTestEnvironment(l,1024);var data=RemoteDevelopmentData.get(l);
        var result=RemoteClaimService.commit(l,env.draft.id(),0);
        h.assertTrue(result.progressed() && result.blockers().isEmpty(),"Production remote claim: "+result);
        for(var c:env.draft.chunks()) h.assertTrue(owner(l,c)==env.home.colony().getID(),"Approved native claim missing: "+c);pass(rows,"remote_claims_committed");
        h.assertTrue(owner(l,new ChunkPos(32,0))==0 && env.home.colony().getCenter().equals(new BlockPos(0,-59,0)),"No corridor or moved Home");pass(rows,"home_and_gap_unchanged");
        int size=data.all().size();var duplicate=RemoteClaimService.commit(l,env.draft.id(),0);
        h.assertTrue(!duplicate.progressed() && data.all().size()==size,"Repeated approval is not repeated founding");pass(rows,"duplicate_commit");
        var lost=env.draft.chunks().iterator().next();ChunkDataHelper.tryClaim(l,lost.getWorldPosition(),false,(Colony)env.home.colony(),false);
        h.assertTrue(owner(l,lost)==0,"Control removed native claim");RemoteClaimService.reconcile(l,env.draft.id());h.assertTrue(owner(l,lost)==env.home.colony().getID(),"Journal restores approved claim only");pass(rows,"claim_recovery");
        var foreign=OutpostProbeFixture.create(l,new BlockPos(2048,-59,0),UUID.randomUUID());
        ChunkDataHelper.tryClaim(l,lost.getWorldPosition(),false,(Colony)env.home.colony(),false);ChunkDataHelper.tryClaim(l,lost.getWorldPosition(),true,(Colony)foreign.colony(),false);
        var conflict=RemoteClaimService.reconcile(l,env.draft.id());h.assertTrue(!conflict.blockers().isEmpty() && owner(l,lost)==foreign.colony().getID(),"Recovery cannot steal changed ownership");pass(rows,"foreign_claim_preserved");
        h.assertTrue(data.find(env.draft.id()).orElseThrow().phase()==Phase.RECOVERY_NEEDED,"Conflict has explicit recovery state");pass(rows,"conflict_stops_development");
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteClaims",rows);h.succeed();
    }catch(Throwable e){try{rows.add(new ProbeResult("claim_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteClaims",rows);}catch(Exception ignored){}throw new RuntimeException(e);}}
    private static int owner(net.minecraft.server.level.ServerLevel l,ChunkPos p){var c=IColonyManager.getInstance().getClaimData(l.dimension(),p);return c==null?0:c.getOwningColony();}
    private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native claim ownership verified"));}
}
