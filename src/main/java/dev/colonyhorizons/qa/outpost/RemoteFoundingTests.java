package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RemoteFoundingTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
    public static void existing_founding_party(GameTestHelper h){h.runAfterDelay(40,()->run(h));}
    private static void run(GameTestHelper h){var rows=new ArrayList<ProbeResult>();try {
        var env=new RemoteTestEnvironment(h.getLevel(),1024);var manager=env.home.colony().getCitizenManager();var count=manager.getCitizens().size();
        var members=FoundingTravel.review(env.owner,env.draft.id());
        h.assertTrue(members.size()==4,"Mayor, builder and two foresters are existing adults");
        for(var member:members)h.assertTrue(manager.getCivilian(member.citizenId()).getUUID().equals(member.entityId()),"Original native UUID retained");
        h.assertTrue(manager.getCitizens().size()==count,"Review creates no citizens");pass(rows,"existing_party_identity");
        var foreign=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ForeignOwner"));
        reject(()->FoundingTravel.review(foreign,env.draft.id()));pass(rows,"party_owner_rejected");
        var citizen=manager.getCivilian(members.getFirst().citizenId());citizen.setIsChild(true);
        reject(()->FoundingTravel.review(env.owner,env.draft.id()));citizen.setIsChild(false);pass(rows,"party_child_rejected");
        var workplace=env.home.center().offset(16,0,0);
        h.getLevel().setBlockAndUpdate(workplace.below(),net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        var blueprint=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",h.getLevel().registryAccess());
        h.assertTrue(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder.setup(env.owner,h.getLevel(),workplace,blueprint,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"Native Home workplace fixture");
        var building=env.home.colony().getServerBuildingManager().getBuilding(workplace);
        h.assertTrue(building.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.BUILDER_WORK).assignCitizen(citizen),"Real native worker assignment");
        h.assertTrue(citizen.getJob()!=null,"Busy fixture retains its native job");
        reject(()->FoundingTravel.review(env.owner,env.draft.id()));citizen.setJob(null);pass(rows,"party_busy_rejected");
        env.home.colony().getTravellingManager().startTravellingTo(citizen.getId(),env.draft.request().center(),200);
        reject(()->FoundingTravel.review(env.owner,env.draft.id()));env.home.colony().getTravellingManager().finishTravellingFor(citizen.getId());pass(rows,"party_travelling_rejected");
        var entity=citizen.getEntity().orElseThrow();float health=entity.getHealth();entity.setHealth(0);
        reject(()->FoundingTravel.review(env.owner,env.draft.id()));entity.setHealth(health);pass(rows,"party_dead_rejected");
        h.assertTrue(manager.getCitizens().size()==count && FoundingTravel.review(env.owner,env.draft.id()).equals(members),"Failed reviews preserve original party and assignments");
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteFounding",rows);h.succeed();
    }catch(Throwable e){try{rows.add(new ProbeResult("founding_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteFounding",rows);}catch(Exception ignored){}h.fail(e.toString());}}
    private static void reject(Runnable action){boolean failed=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException|SecurityException expected){failed=true;}SupplyTests.check(failed);}
    private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Existing native party preflight; no citizen or item creation"));}
}
