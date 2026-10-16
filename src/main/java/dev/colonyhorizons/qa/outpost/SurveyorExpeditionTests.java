// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorExpeditionTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2400)
 public static void expedition(GameTestHelper h){var center=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(5,2,5)));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(center.x+x,center.z+z,true);h.runAfterDelay(40,()->{try{
  var level=h.getLevel();var f=OutpostProbeFixture.create(level,h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
  var owner=RemoteActivityTests.loginAtHome(level,f.ownerId());owner.setPos(f.center().getX(),f.center().getY(),f.center().getZ());
  var hutPos=f.center().offset(0,0,4);var block=SurveyorRegistration.HUT.get();var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,level.registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);
  h.assertTrue(block.setup(owner,level,hutPos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/surveyor1.blueprint"),"Native Surveyor placement");
  var hut=(BuildingSurveyor)f.colony().getServerBuildingManager().getBuilding(hutPos);hut.setBuildingLevel(1);var worker=f.createCitizen();hut.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(worker);
  hut.getTileEntity().getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,8));
  var remote=SurveyService.candidate(hutPos,0);var source=new BlockPos(remote.getX(),level.getMinBuildHeight()+6,remote.getZ());level.getChunkAt(source);level.setBlock(source.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(source,Blocks.POPPY.defaultBlockState(),3);
  var id=SurveyService.start(owner,f.colony().getID(),hutPos,ResourceLocation.withDefaultNamespace("poppy"));
  var rows=new ArrayList<ProbeResult>();rows.add(new ProbeResult("paid_native_departure",ProbeResult.Outcome.PASS,"Started with assigned native worker and eight bread"));
  
  h.succeedWhen(()->{
   var record=SurveySavedData.get(level).find(id).orElseThrow();h.assertTrue(record.state()!=SurveyRecord.State.RECOVERY_REQUIRED,"Expedition entered recovery: "+SurveyService.status(level,id));h.assertTrue(record.state()==SurveyRecord.State.COMPLETE,"Waiting for survey return: "+SurveyService.status(level,id));
   rows.removeIf(row->!row.caseId().equals("paid_native_departure"));
   SupplyTests.run(rows,"actual_sample_delivered",()->{int count=0;var inv=hut.getTileEntity().getInventory();for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(Items.POPPY))count+=inv.getStackInSlot(i).getCount();SupplyTests.check(count==1&&!level.getBlockState(source).is(Blocks.POPPY));});
   SupplyTests.run(rows,"original_worker_returns",()->SupplyTests.check(worker.getEntity().filter(e->e.isAlive()&&e.getUUID().equals(worker.getUUID())).isPresent()&&worker.getJob() instanceof JobSurveyor));
   SupplyTests.run(rows,"owner_stays_home",()->SupplyTests.check(owner.blockPosition().closerThan(f.center(),4)));
   SupplyTests.run(rows,"no_settlement_site_search",()->SupplyTests.check(SurveySavedData.get(level).siteSearch(id).isEmpty()));
   SupplyTests.run(rows,"actual_biome_and_resource",()->{try{var data=SurveySavedData.get(level);var evidence=((Optional<?>)SurveySavedData.class.getMethod("evidence",UUID.class).invoke(data,id)).orElseThrow();var type=evidence.getClass();SupplyTests.check(type.getMethod("biome").invoke(evidence).equals(level.getBiome(source).unwrapKey().orElseThrow().location())&&type.getMethod("source").invoke(evidence).equals(source)&&type.getMethod("target").invoke(evidence).equals(ResourceLocation.withDefaultNamespace("poppy")));var unlocked=(Optional<?>)SurveySavedData.class.getMethod("unlocked",UUID.class,int.class,ResourceLocation.class).invoke(data,owner.getUUID(),f.colony().getID(),ResourceLocation.withDefaultNamespace("poppy"));SupplyTests.check(unlocked.isPresent());}catch(Exception e){throw new AssertionError("Discovery evidence unavailable",e);}});
   SupplyTests.run(rows,"tickets_released",()->SupplyTests.check(SurveyTickets.active(level)==0));
   try{RemoteActivityTests.connect(owner,false);ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorExpedition",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());
  });
 }catch(Exception e){throw new RuntimeException(e);}});}
}
