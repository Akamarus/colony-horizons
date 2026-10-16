package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.supply.NativeSupplyService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RemoteEndpointTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1600)
    public static void home_endpoint_budget(GameTestHelper h){h.runAfterDelay(40,()->run(h));}
    private static void run(GameTestHelper h){var rows=new ArrayList<ProbeResult>();try{
        var l=h.getLevel();var env=new RemoteTestEnvironment(l,1024);RemoteClaimService.commit(l,env.draft.id(),0);
        var owner=RemoteActivityTests.loginAtHome(env);var service=NativeSupplyService.get(l);
        var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/lumberjack1.blueprint",l.registryAccess());var hut=com.minecolonies.api.blocks.ModBlocks.blockHutLumberjack;
        var remote=env.draft.buildings().get(1).anchor();h.assertTrue(hut.setup(env.owner,l,remote,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/lumberjack1.blueprint"),"Remote native endpoint");service.bind(env.owner.getUUID(),remote,"remote",true);
        var routes=new ArrayList<UUID>();
        for(int i=0;i<17;i++) {
            var pos=new BlockPos(i*16,-59,0);l.setBlockAndUpdate(pos.below(),Blocks.DIRT.defaultBlockState());
            com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)env.home.colony(),true,pos,0,l,false);
            h.assertTrue(hut.setup(env.owner,l,pos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/lumberjack1.blueprint"),"Home native endpoint");
            service.bind(env.owner.getUUID(),pos,"home"+i,false);routes.add(service.link(env.owner.getUUID(),"home"+i,"remote",new ItemStack(Items.BREAD),1,0,1));
            if(i==15){RemoteActivityService.tick(l);h.assertTrue(RemoteTickets.snapshot(l).values().stream().mapToInt(Set::size).sum()==65,"49 remote plus 16 distinct Home endpoint chunks");pass(rows,"sixteen_endpoints");}
        }
        RemoteActivityService.tick(l);h.assertTrue(RemoteTickets.snapshot(l).isEmpty(),"Seventeenth endpoint rejects entire over-budget load plan");pass(rows,"endpoint_overflow_blocks");
        routes.forEach(id->service.cancel(env.owner.getUUID(),id));RemoteActivityService.tick(l);
        h.assertTrue(RemoteTickets.snapshot(l).values().stream().mapToInt(Set::size).sum()==50,"Cancelled routes release endpoint demand");pass(rows,"cancelled_endpoints_released");
        RemoteActivityTests.connect(owner,false);RemoteActivityService.tick(l);l.removePlayerImmediately(owner,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        // Fault injection after the first controller acquisition must release that acquisition too.
        var ticketOwner=UUID.randomUUID();var ticketChunk=new net.minecraft.world.level.ChunkPos(remote);
        var replacement=new LinkedHashMap<UUID,Set<net.minecraft.world.level.ChunkPos>>();replacement.put(ticketOwner,Set.of(ticketChunk));replacement.put(UUID.randomUUID(),null);
        var replace=RemoteTickets.class.getDeclaredMethod("replace",net.minecraft.server.level.ServerLevel.class,Map.class);replace.setAccessible(true);
        boolean failed=false;try{replace.invoke(null,l,replacement);}catch(java.lang.reflect.InvocationTargetException expected){failed=true;}
        h.assertTrue(failed,"Injected ticket replacement failure");
        var controllerField=RemoteTickets.class.getDeclaredField("CONTROLLER");controllerField.setAccessible(true);
        var controller=(net.neoforged.neoforge.common.world.chunk.TicketController)controllerField.get(null);
        h.assertTrue(!controller.forceChunk(l,ticketOwner,ticketChunk.x,ticketChunk.z,false,true),"Failed replacement must not leak earlier acquired native tickets");
        h.assertTrue(RemoteTickets.snapshot(l).isEmpty(),"Failed replacement leaves no advertised partial settlement");pass(rows,"ticket_failure_releases_acquired");
        var unloaded=new net.minecraft.world.level.ChunkPos(10000,10000);var partial=new net.minecraft.nbt.CompoundTag();
        partial.putString("Status","minecraft:noise");partial.putInt("xPos",unloaded.x);partial.putInt("zPos",unloaded.z);
        l.getChunkSource().chunkMap.write(unloaded,partial).join();
        var exists=RemoteTickets.class.getDeclaredMethod("exists",net.minecraft.server.level.ServerLevel.class,net.minecraft.world.level.ChunkPos.class);exists.setAccessible(true);
        int loadedBefore=l.getChunkSource().getLoadedChunksCount();
        h.assertTrue(!(boolean)exists.invoke(null,l,unloaded),"Partially generated saved terrain must not authorize ticking tickets");
        h.assertTrue(l.getChunkSource().getLoadedChunksCount()==loadedBefore,"Terrain existence check must not load or generate chunks");pass(rows,"partial_terrain_rejected");
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteEndpoints",rows);h.succeed();
    }catch(Throwable e){try{rows.add(new ProbeResult("endpoint_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteEndpoints",rows);}catch(Exception ignored){}h.fail(e.toString());}}
    private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native route endpoint union and cap verified"));}
}
