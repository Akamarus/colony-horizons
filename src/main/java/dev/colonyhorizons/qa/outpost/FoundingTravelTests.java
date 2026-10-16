package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class FoundingTravelTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=6000)
    public static void protected_original_party(GameTestHelper h){h.runAfterDelay(40,()->new Run(h).start());}
    private static final class Run {
        final GameTestHelper h;final List<ProbeResult> rows=new ArrayList<>();RemoteTestEnvironment env;net.minecraft.server.level.ServerPlayer owner;List<FoundingTravel.Member> originals;int before;long waited;
        Run(GameTestHelper h){this.h=h;}
        void start(){try{
            var l=h.getLevel();var bread=new ItemKey(new ItemStack(Items.BREAD));env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(bread,1),Map.of()));
            var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());
            h.assertTrue(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder.setup(env.owner,l,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"Source hut");
            var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(env.home.center());hut.getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,1));hut.setChanged();
            h.assertTrue(RemoteClaimService.commit(l,env.draft.id(),0).blockers().isEmpty(),"Remote claims");var r=RemoteDevelopmentData.get(l).find(env.draft.id()).orElseThrow();RemoteFoundingService.escrow(env.owner,r.id(),r.revision(),Map.of(bread,1));
            originals=FoundingTravel.review(env.owner,r.id());before=env.home.colony().getCitizenManager().getCitizens().size();
            for(var m:originals){var stack=new ItemStack(Items.OAK_LOG,3);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Original cargo"));env.home.colony().getCitizenManager().getCivilian(m.citizenId()).getInventory().setStackInSlot(0,stack);}
            owner=RemoteActivityTests.loginAtHome(env);RemoteActivityService.tick(l);FoundingTravel.begin(l,r.id(),r.draft().request().citizenIds());
            assertParty(false);pass("departure_preserves_originals");
            FoundingTravel.begin(l,r.id(),r.draft().request().citizenIds());assertParty(false);pass("duplicate_departure_inert");
            var journey=RemoteDevelopmentData.get(l).journey(r.id()).orElseThrow();
            for(var pos:journey.targets())l.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            RemoteActivityTests.connect(owner,false);int remaining=journey.remainingTicks();
            h.runAfterDelay(40,()->{try{h.assertTrue(RemoteDevelopmentData.get(l).journey(r.id()).orElseThrow().remainingTicks()==remaining,"Offline owner pauses protected journey");pass("offline_travel_paused");RemoteActivityTests.connect(owner,true);poll();}catch(Throwable e){fail(e);}});
        }catch(Throwable e){fail(e);}}
        void poll(){h.runAfterDelay(20,()->{try{
            var l=h.getLevel();waited+=20;var journey=RemoteDevelopmentData.get(l).journey(env.draft.id()).orElseThrow();
            if(journey.remainingTicks()>0){h.assertTrue(waited<5000,"Production timer never advanced");poll();return;}
            assertParty(false);h.assertTrue(owner.blockPosition().closerThan(env.home.center(),3),"Owner stayed at Home");
            for(var m:originals){var c=env.home.colony().getCitizenManager().getCivilian(m.citizenId());env.home.colony().getTravellingManager().startTravellingTo(c,journey.targets().get(originals.indexOf(m)),0);c.updateEntityIfNecessary();}
            assertParty(false);pass("blocked_arrival_guarded");
            for(var pos:journey.targets())l.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> interruption=event->{if(event.getEntity().getUUID().equals(originals.get(1).entityId()))event.setCanceled(true);};
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(interruption);
            try {FoundingTravel.reconcile(l,env.draft.id());h.fail("Expected native arrival interruption");}
            catch(IllegalStateException expected){h.assertTrue(expected.getMessage().contains("arrival"),"Expected guarded native arrival failure");}
            finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(interruption);}
            h.assertTrue(RemoteDevelopmentData.get(l).journey(env.draft.id()).orElseThrow().state()==FoundingTravel.State.ARRIVING,"Partial arrival retains durable intent");
            h.assertTrue(env.home.colony().getCitizenManager().getCivilian(originals.getFirst().citizenId()).getEntity().isPresent(),"First original already arrived");
            FoundingTravel.reconcile(l,env.draft.id());assertParty(true);
            pass("partial_arrival_retry");
            h.assertTrue(RemoteDevelopmentData.get(l).journey(env.draft.id()).orElseThrow().state()==FoundingTravel.State.ARRIVED,"Arrival journal committed");
            FoundingTravel.reconcile(l,env.draft.id());assertParty(true);pass("safe_arrival_once");
            RemoteActivityTests.connect(owner,false);RemoteActivityService.tick(l);l.removePlayerImmediately(owner,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingTravel",rows);h.succeed();
        }catch(Throwable e){fail(e);}});}
        void assertParty(boolean present){
            h.assertTrue(env.home.colony().getCitizenManager().getCitizens().size()==before,"No new or lost logical citizens");
            for(var m:originals){var c=env.home.colony().getCitizenManager().getCivilian(m.citizenId());h.assertTrue(c.getUUID().equals(m.entityId()),"Original native UUID");h.assertTrue(c.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isPresent()==present,"Expected live representation");h.assertTrue(c.getInventory().getStackInSlot(0).getCount()==3 && c.getInventory().getStackInSlot(0).getHoverName().getString().equals("Original cargo"),"Original component-bearing inventory");if(present){var e=c.getEntity().orElseThrow();h.assertTrue(h.getLevel().noCollision(e) && e.blockPosition().closerThan(env.draft.request().center(),20),"Dry collision-free remote arrival");}}
        }
        void pass(String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Original native party, Home owner, production travel timer and guarded arrival"));}
        void fail(Throwable e){try{rows.add(new ProbeResult("travel_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingTravel",rows);}catch(Exception ignored){}h.fail(e.toString());}
    }
}
