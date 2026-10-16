package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class FoundingCancelTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2400)
 public static void refund_unstarted_founding(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();String phase=System.getProperty("colonyhorizons.probePhase","seed");if(!phase.equals("seed")){verifyReload(h,rows,phase);return;}var food=new ItemStack(Items.BREAD);food.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Travel food"));var key=new ItemKey(food);
  var env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(key,24),Map.of(key,16)));var data=RemoteDevelopmentData.get(l);
  h.assertTrue(RemoteFoundingService.cancel(env.owner,env.draft.id(),0).blockers().isEmpty()&&data.find(env.draft.id()).orElseThrow().phase()==Phase.CANCELLED,"Unused proposal cancels without native effects");pass(rows,"unapproved_cancel");
  var draft=RemoteSitePlanner.review(env.owner,env.draft.request());var id=draft.id();data.create(new Development(id,env.owner.getUUID(),draft,Mode.APPROVAL,Phase.PROPOSED,0));
  var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());com.minecolonies.api.blocks.ModBlocks.blockHutBuilder.setup(env.owner,l,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint");
  var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(env.home.center());hut.getInventory().setStackInSlot(0,food.copyWithCount(40));hut.setChanged();
  RemoteClaimService.commit(l,id,0);RemoteFoundingService.escrow(env.owner,id,data.find(id).orElseThrow().revision(),Map.of(key,24));
  for(int i=1;i<hut.getInventory().getSlots();i++)hut.getInventory().setStackInSlot(i,new ItemStack(Items.STONE,64));hut.getInventory().setStackInSlot(0,new ItemStack(Items.STONE,64));
  h.assertTrue(!RemoteFoundingService.cancel(env.owner,id,data.find(id).orElseThrow().revision()).blockers().isEmpty()&&!data.foundingRefunded(id),"Full source preserves cargo, no destructive return");pass(rows,"full_refund_waits");
  for(int i=0;i<hut.getInventory().getSlots();i++)hut.getInventory().setStackInSlot(i,ItemStack.EMPTY);hut.getInventory().setStackInSlot(0,food.copyWithCount(16));
  h.assertTrue(RemoteFoundingService.cancel(env.owner,id,data.find(id).orElseThrow().revision()).blockers().isEmpty(),"Unspent cargo returned");h.assertTrue(hut.getInventory().getStackInSlot(0).getCount()==40&&ItemStack.isSameItemSameComponents(hut.getInventory().getStackInSlot(0),food)&&data.foundingRefunded(id),"Same component stock conserved");pass(rows,"component_refund_once");
  for(var c:draft.chunks()){var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(l.dimension(),c);h.assertTrue(claim==null||claim.getOwningColony()!=env.home.colony().getID(),"Only empty approved remote claims released");}pass(rows,"empty_remote_claims_released");
  RemoteFoundingService.cancel(env.owner,id,data.find(id).orElseThrow().revision());h.assertTrue(hut.getInventory().getStackInSlot(0).getCount()==40&&FoundingPlacement.remaining(l,id).isEmpty(),"No refund replay and no spendable custody after return");pass(rows,"duplicate_cancel_inert");
  var restored=RemoteDevelopmentData.load(data.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess()),l.registryAccess(),l.dimension());h.assertTrue(restored.foundingRefunded(id),"Refund receipt persisted");pass(rows,"refund_journal_roundtrip");
  report(rows);h.succeed();
 }catch(Throwable e){try{rows.add(new ProbeResult("cancel_exception",ProbeResult.Outcome.FAIL,e.toString()));report(rows);}catch(Exception ignored){}h.fail(e.toString());}});}
 private static void verifyReload(GameTestHelper h,List<ProbeResult> rows,String phase)throws Exception {
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);h.assertTrue(data.all().size()==2,"No reseeding on refund restart");var r=data.all().stream().filter(v->data.foundingRefunded(v.id())).findFirst().orElseThrow();h.assertTrue(r.phase()==Phase.CANCELLED,"Cancellation persisted");
  for(var c:r.draft().chunks()){l.getChunk(c.x,c.z);var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(l.dimension(),c);h.assertTrue(claim==null||claim.getOwningColony()!=r.draft().request().colonyId(),"Cancelled remote claims not rebuilt");}
  l.getChunkAt(r.draft().request().homeSupply());var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(r.draft().request().homeSupply());var food=hut.getInventory().getStackInSlot(0);h.assertTrue(food.is(Items.BREAD)&&food.getCount()==40&&food.getHoverName().getString().equals("Travel food"),"Original component refund persisted in native source");
  var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(r.owner(),"RemoteOwner"));RemoteFoundingService.cancel(actor,r.id(),r.revision());h.assertTrue(hut.getInventory().getStackInSlot(0).getCount()==40&&FoundingPlacement.remaining(l,r.id()).isEmpty(),"Restart cannot replay refund");pass(rows,"cancel_"+phase);report(rows);h.succeed();
 }
 private static void report(List<ProbeResult> rows)throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),System.getProperty("colonyhorizons.foundationSuite","FoundingCancel"),rows);}
 private static void pass(List<ProbeResult> r,String id){r.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Unstarted founding cancellation conserves components and native claims"));}
}
