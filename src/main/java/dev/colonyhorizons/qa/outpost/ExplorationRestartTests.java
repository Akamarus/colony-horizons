// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import dev.colonyhorizons.supply.*;
import dev.colonyhorizons.remote.NativeFoundingCheckpoint;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationRestartTests {
 static final String KEY="colonyhorizons_exploration_expected";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 static final String FAULT=System.getProperty("colonyhorizons.explorationFault","timer");
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2000)
 public static void restart(GameTestHelper h){for(int base:new int[]{128,640}){var c=new net.minecraft.world.level.ChunkPos(new BlockPos(base,-59,128));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(c.x+x,c.z+z,true);}h.runAfterDelay(40,()->{try{if(System.getProperty("colonyhorizons.probePhase","seed").equals("seed"))seed(h);else reload(h);}catch(Throwable e){report(h,"FAIL",e.toString());}});}
 static void seed(GameTestHelper h)throws Exception{
  var l=h.getLevel();SupplyTests.check(l.getDataStorage().get(FACTORY,KEY)==null);var f=ExplorationFixture.create(l,new BlockPos(128,-59,128));var d=GatheringSavedData.get(l);UUID id;
  if(FAULT.startsWith("payment")){
   var r=GatheringRecord.create(UUID.randomUUID(),f.colony().colony().getID(),f.owner().getUUID(),f.hut().getPosition(),f.worker().getId(),f.worker().getUUID(),f.discovery(),GatheringCatalog.find(net.minecraft.resources.ResourceLocation.withDefaultNamespace("kelp")).orElseThrow(),false);id=r.id();d.add(r);d.writeCargo(id,GatheringCargo.empty().save(l.registryAccess()));d.checkpoint(l);
   var p=GatheringSupplies.prepare(f.hut().getTileEntity().getInventory(),r.offer());try{p.commit(l.registryAccess(),tag->{d.writeCargo(id,tag);d.checkpoint(l);if(FAULT.equals("payment-pending"))throw new IllegalStateException("QA stop after pending write");},()->{f.hut().getTileEntity().setChanged();NativeFoundingCheckpoint.save(l,f.colony().colony());throw new IllegalStateException("QA stop after native payment save");});throw new AssertionError("Fault was not injected");}catch(IllegalStateException expected){}
  }else{
   id=f.start(false);var r=d.find(id).orElseThrow();int end=FAULT.equals("timer")?100:0;while(r.remaining()>end){var n=r.tick();d.replace(r,n);r=n;}if(end==0)d.writeCargo(id,GatheringCargo.load(d.cargo(id),l.registryAccess()).earn(r.offer()).save(l.registryAccess()));d.checkpoint(l);
   if(FAULT.startsWith("delivery")||FAULT.equals("delivered")){
    var cargo=GatheringCargo.load(d.cargo(id),l.registryAccess());var batch=new SupplyInventoryBatch(Map.of(id,f.hut().getTileEntity().getInventory()));SupplyTests.check(batch.insert(id,cargo.reward()).isEmpty());
    try{new SupplyCheckpoint(tag->{d.writeCargo(id,tag);d.checkpoint(l);if(FAULT.equals("delivery-pending"))throw new IllegalStateException("QA stop after delivery intent");}).commit(cargo.save(l.registryAccess()),cargo.deliveredCargo().save(l.registryAccess()),batch::apply,()->{f.hut().getTileEntity().setChanged();NativeFoundingCheckpoint.save(l,f.colony().colony());if(FAULT.equals("delivery-native"))throw new IllegalStateException("QA stop after native delivery");});SupplyTests.check(FAULT.equals("delivered"));}catch(IllegalStateException expected){SupplyTests.check(!FAULT.equals("delivered"));}
   }
  }
  RemoteActivityTests.connect(f.owner(),false);var e=new Expected();e.tag.putUUID("id",id);e.tag.putUUID("owner",f.owner().getUUID());e.tag.putInt("colony",f.colony().colony().getID());e.tag.putLong("hut",f.hut().getPosition().asLong());e.tag.putUUID("workerUuid",f.worker().getUUID());e.tag.putInt("worker",f.worker().getId());e.tag.putString("fault",FAULT);e.tag.putInt("remaining",d.find(id).orElseThrow().remaining());seedLegacy(l,e);l.getDataStorage().set(KEY,e);e.setDirty();NativeFoundingCheckpoint.save(l,f.colony().colony());d.checkpoint(l);report(h,"PASS","Persisted exact "+FAULT+" boundary, native inventory and original citizen; no final receipt synthesized.");
 }
 static void reload(GameTestHelper h)throws Exception{
  var l=h.getLevel();var e=l.getDataStorage().get(FACTORY,KEY);SupplyTests.check(e!=null&&e.tag.getString("fault").equals(FAULT));var t=e.tag;var id=t.getUUID("id");var d=GatheringSavedData.get(l);var r=d.find(id).orElseThrow();SupplyTests.check(r.remaining()==t.getInt("remaining")&&r.workerUuid().equals(t.getUUID("workerUuid")));
  var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(t.getInt("colony"),l.dimension());SupplyTests.check(colony!=null);var worker=colony.getCitizenManager().getCivilian(t.getInt("worker"));SupplyTests.check(worker!=null&&worker.getUUID().equals(t.getUUID("workerUuid")));var hut=(BuildingSurveyor)colony.getServerBuildingManager().getBuilding(BlockPos.of(t.getLong("hut")));SupplyTests.check(hut!=null&&hut.getTileEntity()!=null);var inv=hut.getTileEntity().getInventory();SupplyTests.check(count(inv,Items.BREAD)==(FAULT.equals("payment-pending")?4:2));SupplyTests.check(count(inv,Items.KELP)==(FAULT.equals("delivery-native")||FAULT.equals("delivered")?16:0));
  var legacyOwner=RemoteActivityTests.loginAtHome(l,t.getUUID("legacyOwner"));var owner=RemoteActivityTests.loginAtHome(l,t.getUUID("owner"));owner.setPos(hut.getPosition().getX(),hut.getPosition().getY(),hut.getPosition().getZ());l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack(),"tick sprint 1500");
  h.succeedWhen(()->{var now=d.find(id).orElseThrow();boolean ambiguous=FAULT.startsWith("payment")||FAULT.startsWith("delivery");h.assertTrue(now.state()==(ambiguous?GatheringRecord.State.RECOVERY_REQUIRED:GatheringRecord.State.COMPLETE),"Waiting for recovered state: "+now.status());SupplyTests.check(count(inv,Items.BREAD)==(FAULT.equals("payment-pending")?4:2));SupplyTests.check(count(inv,Items.KELP)==(ambiguous?(FAULT.equals("delivery-native")?16:0):16));SupplyTests.check(colony.getCitizenManager().getCivilian(r.worker()).getUUID().equals(r.workerUuid()));if(ambiguous)SupplyTests.check(d.cargo(id).contains("pending"));else SupplyTests.check(GatheringCargo.load(d.cargo(id),l.registryAccess()).delivered()&&worker.getEntity().filter(x->x.isAlive()&&!x.isRemoved()).isPresent());verifyLegacy(h,e);SupplyTests.check(SurveyTickets.active(l)==0);try{RemoteActivityTests.connect(legacyOwner,false);RemoteActivityTests.connect(owner,false);write("PASS","Separate JVM resumed "+FAULT+" without extra payment/reward; native citizen and cargo identity retained.");}catch(Exception ex){throw new RuntimeException(ex);}});
 }
 static void seedLegacy(ServerLevel l,Expected e)throws Exception{
  var f=ExplorationFixture.create(l,new BlockPos(640,-59,128));var d=SurveySavedData.get(l);var target=net.minecraft.resources.ResourceLocation.withDefaultNamespace("vine");var r=SurveyRecord.create(UUID.randomUUID(),f.colony().colony().getID(),f.owner().getUUID(),f.hut().getPosition(),f.worker().getId(),f.worker().getUUID(),target);d.add(r);var search=r.next(SurveyRecord.State.SEARCHING);d.replace(r,search);var op=UUID.randomUUID();d.putSamples(r.id(),d.samples(r.id()).prepare(op,f.hut().getPosition().offset(256,0,0)));d.putSamples(r.id(),d.samples(r.id()).collected(op,4));d.writeCargo(r.id(),new SurveyCargo(true,ItemStack.EMPTY,List.of(new ItemStack(Items.VINE,4))).save(l.registryAccess()));d.replace(search,search.next(SurveyRecord.State.RETURNING));ExpeditionTravel.depart(l,f.worker());RemoteActivityTests.connect(f.owner(),false);e.tag.putUUID("legacy",r.id());e.tag.putUUID("legacyOwner",r.owner());e.tag.putInt("legacyColony",r.colony());NativeFoundingCheckpoint.save(l,f.colony().colony());d.checkpoint(l);
 }
 static void verifyLegacy(GameTestHelper h,Expected e){
  var l=h.getLevel();var d=SurveySavedData.get(l);var r=d.find(e.tag.getUUID("legacy")).orElseThrow();h.assertTrue(r.state()==SurveyRecord.State.COMPLETE,"Legacy survey still returning");var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.colony(),l.dimension());var hut=(BuildingSurveyor)colony.getServerBuildingManager().getBuilding(r.hut());SupplyTests.check(count(hut.getTileEntity().getInventory(),Items.VINE)==4&&count(hut.getTileEntity().getInventory(),Items.BREAD)==4&&d.evidence(r.id()).isEmpty()&&d.unlocked(r.owner(),r.colony(),r.target()).isEmpty()&&colony.getCitizenManager().getCivilian(r.worker()).getUUID().equals(r.workerUuid()));
 }
 static int count(net.neoforged.neoforge.items.IItemHandler inv,Item item){int n=0;for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(item))n+=inv.getStackInSlot(i).getCount();return n;}
 static void write(String result,String detail)throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationRestart",List.of(new ProbeResult("restart_"+System.getProperty("colonyhorizons.probePhase","seed"),ProbeResult.Outcome.valueOf(result),detail)));}
 static void report(GameTestHelper h,String result,String detail){try{write(result,detail);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(result.equals("PASS"),detail);h.succeed();}
 public static final class Expected extends SavedData {CompoundTag tag=new CompoundTag();static Expected load(CompoundTag t,HolderLookup.Provider r){var e=new Expected();e.tag=t.copy();return e;}@Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){return tag.copy();}}
}
