package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RemoteActivityTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=14000)
    public static void unattended_native_activity(GameTestHelper h){h.runAfterDelay(40,()->new Run(h).start());}
    private static final class Run {
        final GameTestHelper h;final List<ProbeResult> rows=new ArrayList<>();RemoteTestEnvironment env;ServerPlayer owner;List<BlockPos> saplings=new ArrayList<>();int elapsed;
        Run(GameTestHelper h){this.h=h;}
        void start(){try {
            var l=h.getLevel();env=new RemoteTestEnvironment(l,1024);RemoteClaimService.commit(l,env.draft.id(),0);
            RemoteActivityService.tick(l);h.assertTrue(RemoteTickets.snapshot(l).isEmpty(),"Offline owner gets no tickets");pass("offline_no_tickets");
            owner=loginAtHome(env);
            // No remote subscriber or QA chunk ticket. The connected-owner surrogate stays at Home.
            RemoteActivityService.tick(l);
            h.assertTrue(RemoteTickets.snapshot(l).values().stream().mapToInt(Set::size).sum()==50,"49 approved footprint chunks plus one shared home endpoint");
            RemoteActivityService.tick(l);h.assertTrue(RemoteTickets.snapshot(l).values().stream().mapToInt(Set::size).sum()==50,"Repeated demand reuses tickets");pass("bounded_production_tickets");
            var config=RemoteLimits.class.getDeclaredField("DEVELOPING");config.setAccessible(true);var limit=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)config.get(null);
            try{limit.set(0);RemoteActivityService.tick(l);h.assertTrue(RemoteTickets.snapshot(l).isEmpty(),"Server may disable developing settlements");}finally{limit.set(1);}
            RemoteActivityService.tick(l);h.assertTrue(RemoteTickets.snapshot(l).size()==2,"Restored config rebuilds two ticket owners");pass("reduced_runtime_limits");
            var missing=env.draft.chunks().iterator().next();com.minecolonies.core.util.ChunkDataHelper.tryClaim(l,missing.getWorldPosition(),false,(com.minecolonies.core.colony.Colony)env.home.colony(),false);
            RemoteActivityService.tick(l);var repaired=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(l.dimension(),missing);
            h.assertTrue(repaired!=null && repaired.getOwningColony()==env.home.colony().getID(),"Activity reconciles lost approved claims after loading");pass("activity_reconciles_claims");
            var data=RemoteDevelopmentData.get(l);var current=data.find(env.draft.id()).orElseThrow();data.transition(current.id(),current.revision(),dev.colonyhorizons.remote.RemoteModels.Phase.PAUSED);RemoteActivityService.tick(l);
            h.assertTrue(RemoteTickets.snapshot(l).isEmpty(),"Pause releases remote tickets");current=data.find(env.draft.id()).orElseThrow();data.transition(current.id(),current.revision(),dev.colonyhorizons.remote.RemoteModels.Phase.STAGING);RemoteActivityService.tick(l);pass("pause_resume_tickets");
            l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
            l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,l.getServer());
            for(int x=990;x<=1050;x+=8)for(int z=-32;z<=24;z+=8){var p=new BlockPos(x,-59,z);l.setBlockAndUpdate(p,Blocks.OAK_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE,1));saplings.add(p);}
            h.runAfterDelay(200,()->checkZero());
        }catch(Throwable e){fail(e);}}
        void checkZero(){try{
            var l=h.getLevel();h.assertTrue(saplings.stream().allMatch(p->l.getBlockState(p).is(Blocks.OAK_SAPLING)),"randomTickSpeed zero prevents growth");pass("zero_random_ticks");
            h.assertTrue(env.home.colony().isActive(),"Shared native colony stays active with owner at Home");pass("native_colony_active");
            l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3,l.getServer());poll();
        }catch(Throwable e){fail(e);}}
        void poll(){h.runAfterDelay(100,()->{try{
            elapsed+=100;var l=h.getLevel();h.assertTrue(owner.blockPosition().closerThan(env.home.center(),3),"Owner remains at Home");
            if(saplings.stream().noneMatch(p->l.getBlockState(p).is(Blocks.OAK_LOG))){h.assertTrue(elapsed<12000,"Native random growth never ran remotely");poll();return;}
            pass("natural_growth_from_home");
            connect(owner,false);RemoteActivityService.tick(l);
            h.assertTrue(RemoteTickets.snapshot(l).isEmpty(),"Logout releases only addon tickets");pass("logout_releases_tickets");
            l.removePlayerImmediately(owner,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteActivity",rows);h.succeed();
        }catch(Throwable e){fail(e);}});}
        void pass(String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Production ticket path; owner at Home; native randomTickSpeed respected"));}
        void fail(Throwable e){try{rows.add(new ProbeResult("activity_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteActivity",rows);}catch(Exception ignored){}h.fail(e.toString());}
    }
    @SuppressWarnings("unchecked")
    static void connect(ServerPlayer player,boolean present)throws Exception {
        var list=player.server.getPlayerList();var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);
        var players=(List<ServerPlayer>)field.get(list);var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");byId.setAccessible(true);var ids=(Map<UUID,ServerPlayer>)byId.get(list);
        if(present){players.add(player);ids.put(player.getUUID(),player);}else{players.remove(player);ids.remove(player.getUUID());}
    }
    static ServerPlayer loginAtHome(RemoteTestEnvironment env)throws Exception {
        return loginAtHome(env.level,env.owner.getUUID());
    }
    static ServerPlayer loginAtHome(net.minecraft.server.level.ServerLevel l,UUID id)throws Exception {
        var networkSink=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new GameProfile(id,"RemoteOwner"));
        var owner=new ServerPlayer(l.getServer(),l,new GameProfile(id,"RemoteOwner"),ClientInformation.createDefault());owner.connection=networkSink.connection;
        var channel=net.minecraft.network.Connection.class.getDeclaredField("channel");channel.setAccessible(true);channel.set(owner.connection.getConnection(),new io.netty.channel.embedded.EmbeddedChannel());
        owner.setPos(0.5,-59,0.5);connect(owner,true);l.addNewPlayer(owner);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(owner));return owner;
    }
}
