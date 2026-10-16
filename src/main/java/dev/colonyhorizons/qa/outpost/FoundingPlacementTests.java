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
public final class FoundingPlacementTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2400)
 public static void paid_unbuilt_native_huts(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();var stock=Map.of(new ItemKey(new ItemStack(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder)),1,new ItemKey(new ItemStack(com.minecolonies.api.blocks.ModBlocks.blockHutLumberjack)),2,new ItemKey(new ItemStack(com.minecolonies.api.blocks.ModBlocks.blockHutHome)),4,new ItemKey(new ItemStack(Items.DIRT)),5);
  var env=new RemoteTestEnvironment(l,1024,new Budget(stock,Map.of()));var id=env.draft.id();var data=RemoteDevelopmentData.get(l);
  h.assertTrue(FoundingPlacement.bill(l,id).equals(stock),"Exact server-derived seven hut items and five paid supports");pass(rows,"native_anchor_bill");
  var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());
  h.assertTrue(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder.setup(env.owner,l,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"Fixture Home supply");
  var source=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(env.home.center());int slot=0;for(var e:stock.entrySet()){var s=e.getKey().stack();s.setCount(e.getValue());source.getInventory().setStackInSlot(slot++,s);}source.setChanged();
  RemoteClaimService.commit(l,id,0);var first=env.draft.buildings().getFirst();
  reject(()->FoundingPlacement.place(env.owner,id,first.operationId()));h.assertTrue(l.isEmptyBlock(first.anchor()),"No free anchor before payment");pass(rows,"unpaid_anchor_rejected");
  RemoteFoundingService.escrow(env.owner,id,data.find(id).orElseThrow().revision(),stock);
  var stranger=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Stranger"));reject(()->FoundingPlacement.place(stranger,id,first.operationId()));h.assertTrue(FoundingPlacement.remaining(l,id).equals(stock),"Foreign attempt cannot spend");pass(rows,"foreign_anchor_rejected");
  l.setBlockAndUpdate(first.anchor(),Blocks.CHEST.defaultBlockState());reject(()->FoundingPlacement.place(env.owner,id,first.operationId()));h.assertTrue(l.getBlockState(first.anchor()).is(Blocks.CHEST)&&FoundingPlacement.remaining(l,id).equals(stock),"Obstruction preserved without spending");l.setBlockAndUpdate(first.anchor(),Blocks.AIR.defaultBlockState());pass(rows,"occupied_anchor_rejected");
  for(var plan:env.draft.buildings()){
   FoundingPlacement.place(env.owner,id,plan.operationId());var b=env.home.colony().getServerBuildingManager().getBuilding(plan.anchor());
   h.assertTrue(b!=null&&b.getBuildingLevel()==0,"Paid native hut is unfinished");h.assertTrue(b.getStructurePack().equals(plan.pack())&&b.getBlueprintPath().equals(plan.blueprint()),"Native blueprint attached");
   h.assertTrue(l.getBlockState(plan.anchor().below()).isFaceSturdy(l,plan.anchor().below(),net.minecraft.core.Direction.UP),"Paid supported anchor");
  }pass(rows,"seven_native_level_zero_huts");h.assertTrue(FoundingPlacement.remaining(l,id).isEmpty(),"Exactly seven hut items and five supports consumed");pass(rows,"finite_anchor_conservation");
  FoundingPlacement.place(env.owner,id,first.operationId());h.assertTrue(FoundingPlacement.remaining(l,id).isEmpty(),"Duplicate inert");pass(rows,"duplicate_placement_inert");
  var restored=RemoteDevelopmentData.load(data.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess()),l.registryAccess(),l.dimension());h.assertTrue(restored.anchorReceipts(id).size()==7&&restored.anchorReceipts(id).values().stream().allMatch(FoundingPlacement.Receipt::complete),"Paid journal survives serialization");pass(rows,"anchor_journal_roundtrip");
  var interrupted=data.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());interrupted.getList("developments",10).getCompound(0).getList("anchors",10).getCompound(0).putBoolean("complete",false);
  var recovered=RemoteDevelopmentData.load(interrupted,l.registryAccess(),l.dimension());h.assertTrue(recovered.find(id).orElseThrow().phase()==Phase.RECOVERY_NEEDED,"Uncertain paid placement cannot resume or spend on reload");pass(rows,"interrupted_anchor_quarantined");
  h.assertTrue(!RemoteFoundingService.cancel(env.owner,id,data.find(id).orElseThrow().revision()).blockers().isEmpty()&&data.find(id).orElseThrow().phase()==Phase.RECOVERY_NEEDED,"Partially built site stays recoverable");
  h.assertTrue(l.getBlockEntity(first.anchor())!=null&&FoundingPlacement.remaining(l,id).isEmpty()&&!data.foundingRefunded(id),"Spent materials cannot be refunded or buildings erased");pass(rows,"built_site_cancel_preserves_effects");
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingPlacement",rows);h.succeed();
 }catch(Throwable e){try{rows.add(new ProbeResult("placement_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingPlacement",rows);}catch(Exception ignored){}h.fail(e.toString());}});}
 private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException|IllegalStateException|SecurityException expected){return;}throw new AssertionError("Expected refusal");}
 private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Finite paid native anchor; no completed blueprint paste"));}
}
