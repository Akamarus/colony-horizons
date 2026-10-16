package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class FoundingPlacementRestartTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2400)
 public static void restart_paid_huts(GameTestHelper h){h.runAfterDelay(60,()->{var rows=new ArrayList<ProbeResult>();String phase=System.getProperty("colonyhorizons.probePhase","seed");try{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);net.minecraft.server.level.ServerPlayer actor;UUID id;
  if(phase.equals("seed")){
   var stock=Map.of(new ItemKey(new ItemStack(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder)),1,new ItemKey(new ItemStack(com.minecolonies.api.blocks.ModBlocks.blockHutLumberjack)),2,new ItemKey(new ItemStack(com.minecolonies.api.blocks.ModBlocks.blockHutHome)),4,new ItemKey(new ItemStack(Items.DIRT)),5);
   var env=new RemoteTestEnvironment(l,1024,new Budget(stock,Map.of()));id=env.draft.id();actor=env.owner;
   var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());
   h.assertTrue(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder.setup(actor,l,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"Home fixture");
   var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(env.home.center());int slot=0;for(var e:stock.entrySet())hut.getInventory().setStackInSlot(slot++,e.getKey().stack().copyWithCount(e.getValue()));hut.setChanged();
   RemoteClaimService.commit(l,id,0);RemoteFoundingService.escrow(actor,id,data.find(id).orElseThrow().revision(),FoundingPlacement.bill(l,id));FoundingPlacement.place(actor,id,env.draft.buildings().getFirst().operationId());
  }else{
   h.assertTrue(data.all().size()==1,"No reseeding");var r=data.all().getFirst();id=r.id();actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(r.owner(),"RemoteOwner"));
   // Explicit inspection loads saved terrain only; production ticket behaviour has its own restart suite.
   for(var c:r.draft().chunks())l.getChunk(c.x,c.z);l.getChunkAt(r.draft().request().homeSupply());
   h.assertTrue(data.anchorReceipts(id).size()==(phase.equals("reload")?1:7),"Saved paid receipts, not reconstructed huts");
   var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());
   for(var plan:r.draft().buildings())if(data.anchorReceipts(id).containsKey(plan.operationId()))h.assertTrue(colony.getServerBuildingManager().getBuilding(plan.anchor())!=null&&colony.getServerBuildingManager().getBuilding(plan.anchor()).getBuildingLevel()==0,"Unfinished native hut survives process restart");
   var home=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(r.draft().request().homeSupply());for(int slot=0;slot<home.getInventory().getSlots();slot++)h.assertTrue(home.getInventory().getStackInSlot(slot).isEmpty(),"Original source was not refilled");
   for(var plan:r.draft().buildings())FoundingPlacement.place(actor,id,plan.operationId());
   h.assertTrue(FoundingPlacement.remaining(l,id).isEmpty()&&data.anchorReceipts(id).size()==7,"All original paid items used once");
  }
  data.checkpoint(l);rows.add(new ProbeResult("placement_"+phase,ProbeResult.Outcome.PASS,"Native unfinished huts and finite paid receipts survive separate processes"));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingPlacementRestart",rows);h.succeed();
 }catch(Throwable e){try{rows.add(new ProbeResult("placement_"+phase,ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingPlacementRestart",rows);}catch(Exception ignored){}h.fail(e.toString());}});}
}
