// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationGatheringTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=14000)
 public static void gathering(GameTestHelper h){var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(5,2,5)));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(chunk.x+x,chunk.z+z,true);h.runAfterDelay(40,()->{try{
  var l=h.getLevel();l.setDayTime(6000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
  var f=OutpostProbeFixture.create(l,h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());var owner=RemoteActivityTests.loginAtHome(l,f.ownerId());owner.setPos(f.center().getX(),f.center().getY(),f.center().getZ());var pos=f.center().offset(0,0,4);var block=SurveyorRegistration.HUT.get();var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,l.registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);h.assertTrue(block.setup(owner,l,pos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/surveyor1.blueprint"),"Native hut setup");var hut=(BuildingSurveyor)f.colony().getServerBuildingManager().getBuilding(pos);hut.setBuildingLevel(1);var worker=f.createCitizen();hut.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(worker);var inv=hut.getTileEntity().getInventory();inv.setStackInSlot(0,new ItemStack(Items.BREAD,4));var target=ResourceLocation.withDefaultNamespace("kelp");
  // The real-discovery path is covered separately. Seed one already returned discovery for this trip fixture.
  var sd=SurveySavedData.get(l);var sr=SurveyRecord.create(UUID.randomUUID(),f.colony().getID(),owner.getUUID(),pos,worker.getId(),worker.getUUID(),target);sd.add(sr);var searching=sr.next(SurveyRecord.State.SEARCHING);sd.replace(sr,searching);var source=pos.offset(512,0,0);var op=UUID.randomUUID();sd.putSamples(sr.id(),sd.samples(sr.id()).prepare(op,source));sd.recordEvidence(new DiscoveryEvidence(sr.id(),sr.colony(),sr.owner(),l.dimension().location(),ResourceLocation.withDefaultNamespace("ocean"),target,source));sd.putSamples(sr.id(),sd.samples(sr.id()).collected(op,1));var returning=searching.next(SurveyRecord.State.RETURNING);sd.replace(searching,returning);sd.writeCargo(sr.id(),new SurveyCargo(true,ItemStack.EMPTY,List.of()).save(l.registryAccess()));sd.replace(returning,returning.next(SurveyRecord.State.COMPLETE));sd.checkpoint(l);
  var id=GatheringService.start(owner,f.colony().getID(),pos,target,true);var rows=new ArrayList<ProbeResult>();var started=l.getGameTime();owner.setPos(f.center().getX()+24,f.center().getY(),f.center().getZ());l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack(),"tick sprint 14000");
  SupplyTests.run(rows,"native_worker_departure",()->SupplyTests.check(worker.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isEmpty()&&inv.getStackInSlot(0).getCount()==2));
  var saw=new boolean[2];
  Runnable inspectTick=()->{var active=GatheringSavedData.get(l).active(f.colony().getID());active.ifPresent(r->{if(r.state()==GatheringRecord.State.GATHERING){h.assertTrue(SurveyTickets.active(l)==0,"Gathering loaded terrain");var before=r.remaining();GatheringService.tick(l);var once=GatheringSavedData.get(l).find(r.id()).orElseThrow();GatheringService.tick(l);h.assertTrue(GatheringSavedData.get(l).find(r.id()).orElseThrow().remaining()==once.remaining(),"Duplicate tick earned time");if(before==1){saw[0]=true;h.assertTrue(l.getGameTime()-started>=5999,"Reward earned too early");}}});};
  h.succeedWhen(()->{inspectTick.run();var d=GatheringSavedData.get(l);h.assertTrue(d.active(f.colony().getID()).isEmpty(),"Waiting: "+GatheringService.status(l,id));var trips=d.records().stream().filter(r->r.colony()==f.colony().getID()).toList();h.assertTrue(trips.size()==2,"Two paid trips expected");rows.removeIf(r->!r.caseId().equals("native_worker_departure"));
   SupplyTests.run(rows,"real_6000_tick_trip",()->SupplyTests.check(saw[0]&&l.getGameTime()-started>=12000));
   SupplyTests.run(rows,"exact_output_once",()->{int count=0;for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(Items.KELP))count+=inv.getStackInSlot(i).getCount();SupplyTests.check(count==32);});
   SupplyTests.run(rows,"same_worker_returns",()->SupplyTests.check(worker.getEntity().filter(e->e.isAlive()&&!e.isRemoved()&&e.getUUID().equals(worker.getUUID())).isPresent()&&worker.getWorkBuilding()==hut));
   SupplyTests.run(rows,"owner_stays_home",()->SupplyTests.check(owner.blockPosition().closerThan(f.center().offset(24,0,0),4)));
   SupplyTests.run(rows,"no_remote_tickets_or_edits",()->SupplyTests.check(SurveyTickets.active(l)==0&&sd.siteSearch(sr.id()).isEmpty()));
   SupplyTests.run(rows,"repeat_pays_each_trip",()->{int bread=0;for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(Items.BREAD))bread+=inv.getStackInSlot(i).getCount();SupplyTests.check(bread==0);});
   SupplyTests.run(rows,"shortage_stops_repeat",()->SupplyTests.check(trips.stream().allMatch(r->r.state()==GatheringRecord.State.COMPLETE)&&trips.stream().anyMatch(r->r.status().contains("Repeat stopped"))));
   try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationGathering",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());try{RemoteActivityTests.connect(owner,false);}catch(Exception e){throw new RuntimeException(e);}
  });
 }catch(Exception e){e.printStackTrace();throw new RuntimeException(e);}});}
}
