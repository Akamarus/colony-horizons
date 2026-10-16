// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationLifecycleTests {
 static final String[] CASES={"owner_logout_pauses","owner_dimension_pauses","worker_paused_returns","worker_reassigned_returns","worker_ill_returns","ownership_change_locks","missing_worker_quarantines","recall_before_reward","recall_after_reward","hut_removed_holds_cargo","full_hut_holds_reward","wrong_reward_quarantines","early_reward_quarantines","missing_reward_quarantines","wrong_quantity_quarantines","repeat_requires_manual_restart"};
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void lifecycle(GameTestHelper h){new Run(h).next();}
 static final class Run {
  final GameTestHelper h; final List<ProbeResult> rows=new ArrayList<>();int index;ExplorationFixture f;UUID id;
  Run(GameTestHelper h){this.h=h;}
  void next(){if(index==CASES.length){finish();return;}var center=h.absolutePos(new BlockPos(5+index*512,2,5));var chunk=new net.minecraft.world.level.ChunkPos(center);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(chunk.x+x,chunk.z+z,true);h.runAfterDelay(40,()->{try{f=ExplorationFixture.create(h.getLevel(),center);id=f.start(true);exercise();}catch(Throwable e){fail(e);}});}
  GatheringRecord record(){return GatheringSavedData.get(h.getLevel()).find(id).orElseThrow();}
  GatheringCargo cargo(){return GatheringCargo.load(GatheringSavedData.get(h.getLevel()).cargo(id),h.getLevel().registryAccess());}
  void tick(){var l=h.getLevel();((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(l.getGameTime()+1);GatheringService.tick(l);}
  void earned(){var d=GatheringSavedData.get(h.getLevel());var r=record();while(r.state()==GatheringRecord.State.GATHERING){var n=r.tick();d.replace(r,n);r=n;}d.writeCargo(id,cargo().earn(r.offer()).save(h.getLevel().registryAccess()));d.checkpoint(h.getLevel());}
  void exercise()throws Exception{var l=h.getLevel();var initial=record();switch(CASES[index]){
   case "owner_logout_pauses"->{RemoteActivityTests.connect(f.owner(),false);tick();SupplyTests.check(record().remaining()==initial.remaining()&&SurveyTickets.active(l)==0);RemoteActivityTests.connect(f.owner(),true);}
   case "owner_dimension_pauses"->{var old=f.owner().serverLevel();f.owner().setServerLevel(l.getServer().getLevel(net.minecraft.world.level.Level.NETHER));try{tick();SupplyTests.check(record().remaining()==initial.remaining()&&SurveyTickets.active(l)==0);}finally{f.owner().setServerLevel(old);}}
   case "worker_paused_returns"->{f.worker().setPaused(true);tick();returning();}
   case "worker_reassigned_returns"->{f.hut().getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).removeCitizen(f.worker());tick();returning();}
   case "worker_ill_returns"->{var field=f.worker().getCitizenDiseaseHandler().getClass().getDeclaredField("disease");field.setAccessible(true);field.set(f.worker().getCitizenDiseaseHandler(),com.minecolonies.core.datalistener.DiseasesListener.getDiseases().getFirst());tick();returning();}
   case "ownership_change_locks"->{var other=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Other"));f.colony().colony().getPermissions().setOwner(other);tick();SupplyTests.check(record().state()==GatheringRecord.State.RECOVERY_REQUIRED&&cargo().paid()&&!cargo().earned());}
   case "missing_worker_quarantines"->{f.colony().colony().getCitizenManager().removeCivilian(f.worker());tick();SupplyTests.check(record().state()==GatheringRecord.State.RECOVERY_REQUIRED&&cargo().paid()&&!cargo().earned());}
   case "recall_before_reward"->{GatheringService.recall(f.owner(),id,initial.revision());returning();SupplyTests.check(!cargo().earned()&&cargo().reward().isEmpty()&&f.hut().getTileEntity().getInventory().getStackInSlot(0).getCount()==2);}
   case "recall_after_reward"->{earned();GatheringService.recall(f.owner(),id,record().revision());SupplyTests.check(cargo().earned()&&cargo().reward().getCount()==16&&!record().repeat());}
   case "repeat_requires_manual_restart"->{f.hut().getTileEntity().getInventory().setStackInSlot(0,ItemStack.EMPTY);earned();manualRestart(0);return;}
   case "wrong_reward_quarantines"->{earned();GatheringSavedData.get(l).writeCargo(id,new GatheringCargo(true,ItemStack.EMPTY,new ItemStack(Items.DIAMOND,16),true,false).save(l.registryAccess()));poll(false,0);return;}
   case "missing_reward_quarantines"->{earned();GatheringSavedData.get(l).writeCargo(id,new GatheringCargo(true,ItemStack.EMPTY,ItemStack.EMPTY,false,false).save(l.registryAccess()));poll(false,0);return;}
   case "wrong_quantity_quarantines"->{earned();GatheringSavedData.get(l).writeCargo(id,new GatheringCargo(true,ItemStack.EMPTY,new ItemStack(Items.KELP,8),true,false).save(l.registryAccess()));poll(false,0);return;}
   case "early_reward_quarantines"->{var d=GatheringSavedData.get(l);var current=record();d.replace(current,current.returning("Returning early"));d.writeCargo(id,cargo().earn(current.offer()).save(l.registryAccess()));poll(false,0);return;}
   case "hut_removed_holds_cargo"->{earned();l.removeBlock(f.hut().getPosition(),false);poll(false,0);return;}
   case "full_hut_holds_reward"->{earned();var inv=f.hut().getTileEntity().getInventory();for(int i=0;i<inv.getSlots();i++)inv.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));poll(true,0);return;}
  }passed();}
  void returning(){SupplyTests.check(record().state()==GatheringRecord.State.RETURNING&&!record().repeat()&&cargo().paid()&&!cargo().earned());}
  void poll(boolean full,int count){h.runAfterDelay(1,()->{try{tick();if(full?record().status().startsWith("Make room"):record().state()==GatheringRecord.State.RECOVERY_REQUIRED){SupplyTests.check(cargo().reward().getCount()==(CASES[index].equals("missing_reward_quarantines")?0:CASES[index].equals("wrong_quantity_quarantines")?8:16)&&!cargo().delivered()&&!record().repeat());if(full){long revision=record().revision();tick();SupplyTests.check(record().revision()==revision);}passed();return;}if(count>=200)throw new AssertionError("Return never reached hut: "+record());poll(full,count+1);}catch(Throwable e){fail(e);}});}
  void manualRestart(int attempts){h.runAfterDelay(1,()->{try{tick();if(record().state()!=GatheringRecord.State.COMPLETE){if(attempts>=200)throw new AssertionError("Return did not finish");manualRestart(attempts+1);return;}SupplyTests.check(record().status().contains("Repeat stopped"));f.hut().getTileEntity().getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,4));for(int i=0;i<20;i++)tick();SupplyTests.check(GatheringSavedData.get(h.getLevel()).active(f.colony().colony().getID()).isEmpty());id=f.start(false);SupplyTests.check(record().state()==GatheringRecord.State.GATHERING&&f.hut().getTileEntity().getInventory().getStackInSlot(0).getCount()==2);passed();}catch(Throwable e){fail(e);}});}
  void passed()throws Exception{rows.add(new ProbeResult(CASES[index],ProbeResult.Outcome.PASS,"Native hut/worker, paid custody and interruption checked; short timer boundary is a QA journal fixture."));RemoteActivityTests.connect(f.owner(),false);SurveyTickets.release(h.getLevel(),id);index++;next();}
  void fail(Throwable e){e.printStackTrace();rows.add(new ProbeResult(CASES[index],ProbeResult.Outcome.FAIL,e.toString()));finish();}
  void finish(){try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationLifecycle",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.size()==CASES.length&&rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();}
 }
}
