package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class OutpostIntegrationTests {
    @GameTest(template="outpost",templateNamespace="colonyhorizons",timeoutTicks=400)
    public static void fixture_owns_one_real_colony(GameTestHelper h) throws Exception {
        h.runAfterDelay(20,()->{try{runChecks(h);}catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void runChecks(GameTestHelper h) throws Exception {
        var results=new ArrayList<ProbeResult>();
        try {
            var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
            h.assertTrue(f.colony()!=null,"Fixture must create a real native colony");
            h.assertTrue(IColonyManager.getInstance().getIColonyByOwner(h.getLevel(),f.ownerId()).getID()==f.colony().getID(),"Owner lookup must find fixture colony");
            var c=f.createCitizen();
            h.assertTrue(c!=null && c.getEntity().isPresent(),"Native citizen must spawn");
            h.assertTrue(f.colony().getCitizenManager().getCurrentCitizenCount()==1,"Exactly one registered citizen");
            results.add(new ProbeResult("fixture_owns_one_real_colony",ProbeResult.Outcome.PASS,"Real native colony and one citizen"));
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Native",results);
            int radius=com.minecolonies.api.IMinecoloniesAPI.getInstance().getConfig().getServer().maxColonySize.get();
            var remote=f.center().offset((radius+2)*16,0,0);
            h.getLevel().getChunkAt(remote);
            h.assertTrue(IColonyManager.getInstance().getIColony(h.getLevel(),remote)==null,"Probe location must be outside all claims");
            var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(f.ownerId(),"OutpostProbe"));
            var hut=com.minecolonies.api.blocks.ModBlocks.blockHutLumberjack;
            var blueprint=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,h.getLevel().registryAccess());
            blueprint.addBlockState(BlockPos.ZERO,hut.defaultBlockState());
            blueprint.setCachePrimaryOffset(BlockPos.ZERO);
            boolean accepted=hut.setup(player,h.getLevel(),remote,blueprint,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Medieval Oak","lumberjack1.blueprint");
            h.assertTrue(IColonyManager.getInstance().getIColonyByOwner(h.getLevel(),f.ownerId()).getID()==f.colony().getID(),"Remote probe must not replace owned colony");
            results.add(new ProbeResult("remote_site_preserves_colony_identity",ProbeResult.Outcome.PASS,"Native owner unchanged; tested "+((radius+2)*16)+" blocks away"));
            if(!accepted){
                results.add(new ProbeResult("remote_workplace_placement",ProbeResult.Outcome.BLOCKED,"Native build-tool setup rejected out-of-colony logger hut"));
                for(String id:List.of("remote_builder_places_supplied_blocks","remote_logger_harvests_and_replants","remote_worker_returns_without_duplicate_identity"))
                    results.add(new ProbeResult(id,ProbeResult.Outcome.BLOCKED,"Not executed: no authorized remote native workplace"));
                ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Native",results);
                h.fail("REMOTE_WORKPLACE_BLOCKED: native build-tool placement rejected site outside maximum colony radius");
                return;
            }
            h.fail("Remote placement accepted: subsequent lifecycle probes must now be implemented before gate can pass");
        } catch(Throwable e) {
            if(results.stream().noneMatch(r->r.outcome()==ProbeResult.Outcome.BLOCKED)) results.add(new ProbeResult("native_probe_exception",ProbeResult.Outcome.FAIL,e.toString()));
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Native",results);
            throw e;
        }
    }
}
