package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class FoundingEscrowTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1600)
    public static void native_hut_escrow(GameTestHelper h){h.runAfterDelay(60,()->run(h));}
    private static void run(GameTestHelper h){var rows=new ArrayList<ProbeResult>();String phase=System.getProperty("colonyhorizons.probePhase","seed");try {
        var level=h.getLevel();var bread=new ItemKey(new ItemStack(Items.BREAD));var data=RemoteDevelopmentData.get(level);
        if(phase.equals("seed")) {
            var env=new RemoteTestEnvironment(level,1024,new Budget(Map.of(bread,24),Map.of(bread,16)));
            var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",level.registryAccess());
            h.assertTrue(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder.setup(env.owner,level,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"Real source hut");
            var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)level.getBlockEntity(env.home.center());
            hut.getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,40));hut.setChanged();
            h.assertTrue(RemoteClaimService.commit(level,env.draft.id(),0).blockers().isEmpty(),"Approved native claims");var record=data.find(env.draft.id()).orElseThrow();
            var cargo=RemoteFoundingService.escrow(env.owner,record.id(),record.revision(),Map.of(bread,24));
            h.assertTrue(cargo.items().get(bread)==24 && hut.getInventory().getStackInSlot(0).getCount()==16,"40 native bread becomes 16 Home + 24 saved custody");
            var again=RemoteFoundingService.escrow(env.owner,record.id(),record.revision(),Map.of(bread,24));
            h.assertTrue(again.equals(cargo) && hut.getInventory().getStackInSlot(0).getCount()==16,"Duplicate delivery does not withdraw again");
            boolean rejected=false;try{RemoteFoundingService.escrow(env.owner,record.id(),record.revision(),Map.of(bread,8));}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected,"Changed bill cannot reuse stable operation");
        }else {
            h.assertTrue(data.all().size()==1,"Reload cannot recreate missing development");var r=data.all().getFirst();var pos=r.draft().request().homeSupply();level.getChunkAt(pos);
            var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)level.getBlockEntity(pos);
            h.assertTrue(hut!=null && hut.getInventory().getStackInSlot(0).getCount()==16,"Native Home inventory survives real restart");
            var saved=data.foundingCargo(r.id()).orElseThrow();var cargo=FoundingCargo.load(saved,level.registryAccess());
            h.assertTrue(cargo.items().get(bread)==24,"Real saved custody survives without reseeding");
            h.assertTrue(hut.getInventory().getStackInSlot(0).getCount()+cargo.items().get(bread)==40,"Restart conserves finite native stock plus cargo");
            if(phase.equals("reload")){data.transition(r.id(),r.revision(),Phase.PAUSED);data.checkpoint(level);}
            else h.assertTrue(phase.equals("paused-reload") && r.phase()==Phase.PAUSED,"Paused development retains custody");
        }
        rows.add(new ProbeResult("escrow_"+phase,ProbeResult.Outcome.PASS,"Real hut inventory plus durable founding custody, no reseeding"));
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingEscrow",rows);h.succeed();
    }catch(Throwable e){try{rows.add(new ProbeResult("escrow_"+phase,ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingEscrow",rows);}catch(Exception ignored){}h.fail(e.toString());}}
}
