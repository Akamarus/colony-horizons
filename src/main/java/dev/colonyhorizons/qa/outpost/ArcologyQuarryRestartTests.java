package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobQuarrier;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Two real server processes; verification restores and continues existing shaft work only. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryRestartTests {
 static final String KEY="arcology_quarry_restart";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=240000)
 public static void quarry_restart(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=8;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{try{
   if(System.getProperty("colonyhorizons.probePhase","seed").equals("seed")){
    var f=new ArcologyQuarryTests.Run(h);f.afterSetup=r->{try{new Monitor(h,r).start();}catch(Exception e){h.fail(e.toString());}};f.start();
   }else restore(h,0);
  }catch(Throwable e){h.fail(e.toString());}});
 }
 static void restore(GameTestHelper h,int waited){try{
  var e=h.getLevel().getDataStorage().get(FACTORY,KEY);check(e!=null,"Saved quarry fixture missing; no reseeding allowed");
  var colony=IColonyManager.getInstance().getColonyByDimension(e.colony,h.getLevel().dimension());check(colony!=null,"Colony not restored");
  if(waited==0){var profile=new GameProfile(e.owner,"QuarryProbe");var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),profile);var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(544,16,24);colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);}
  check(IColonyManager.getInstance().getIColonyByOwner(h.getLevel(),e.owner)==colony,"Saved owner changed");
  for(int id:e.workers){var c=colony.getCitizenManager().getCivilian(id);check(c!=null,"Saved citizen missing");if(c.getEntity().isEmpty()){check(waited<12000,"Citizen reload timeout");h.runAfterDelay(20,()->restore(h,waited+20));return;}}
  new Monitor(h,colony,e).start();
 }catch(Throwable e){h.fail(e.toString());}}
 static final class Monitor {
  final GameTestHelper h;final ServerLevel level;final IColony colony;final Expected expected;final boolean seed;
  final List<BuildingMiner> mines=new ArrayList<>();final List<IBuilding> quarries=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();final List<List<BlockPos>> cells=new ArrayList<>();final List<Vec3> previous=new ArrayList<>();final int[] baseline=new int[2];int ticks;
  Monitor(GameTestHelper h,ArcologyQuarryTests.Run f){this.h=h;level=h.getLevel();colony=f.fixture.colony();seed=true;expected=new Expected();expected.colony=colony.getID();expected.owner=f.fixture.ownerId();for(int i=0;i<2;i++){expected.mines[i]=f.mines.get(i).getPosition();expected.quarries[i]=f.quarries.get(i).getPosition();expected.workers[i]=f.workers.get(i).getId();expected.entities[i]=f.ids.get(i);}}
  Monitor(GameTestHelper h,IColony colony,Expected expected){this.h=h;level=h.getLevel();this.colony=colony;this.expected=expected;seed=false;}
  void start()throws Exception{
   for(int i=0;i<2;i++){
    var mine=(BuildingMiner)colony.getServerBuildingManager().getBuilding(expected.mines[i]);var quarry=colony.getServerBuildingManager().getBuilding(expected.quarries[i]);var c=colony.getCitizenManager().getCivilian(expected.workers[i]);mines.add(mine);quarries.add(quarry);workers.add(c);var entity=c.getEntity().orElseThrow();previous.add(entity.position());((MinecoloniesAdvancedPathNavigate)entity.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
    String path="infrastructure/mineshafts/"+(i==0?"simple":"medium")+"quarryshaft1.blueprint";var bp=StructurePacks.getBlueprintFuture("Arcology Horizons",path,level.registryAccess()).get();var origin=quarry.getPosition().below(2).subtract(bp.getPrimaryBlockOffset());cells.add(bp.getBlockInfoAsList().stream().filter(info->info.getState().isAir()).map(info->origin.offset(info.getPos())).toList());baseline[i]=air(i);
    if(!seed){check(mine.getWorkOrder()!=null&&mine.getWorkOrder().getID()==expected.orders[i],"Saved work order identity lost");check(baseline[i]>=expected.air[i],"Excavation reverted on restart");}
   }
   schedule();
  }
  int air(int i){int count=0;for(var pos:cells.get(i))if(level.getBlockState(pos).isAir())count++;return count;}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){h.fail(e.toString());}});}
  void tick()throws Exception{
   ticks+=20;boolean ready=true;
   for(int i=0;i<2;i++){
    var c=workers.get(i);var entity=c.getEntity().orElseThrow();var mine=mines.get(i);var quarry=quarries.get(i);
    check(entity.isAlive()&&entity.getUUID().equals(expected.entities[i])&&!c.isPaused()&&c.getWorkBuilding()==mine&&c.getJob() instanceof JobQuarrier&&((JobQuarrier)c.getJob()).findQuarry()==quarry&&quarry.getFirstModuleOccurance(QuarryModule.class).hasAssignedCitizen(c),"Saved worker identity/dual assignment changed");
    check(previous.get(i).distanceTo(entity.position())<=12,"Quarry restart recovery teleport");previous.set(i,entity.position());c.setSaturation(20);
    var order=mine.getWorkOrder();if(order==null){check(seed,"Saved order vanished");ready=false;}else{check(order instanceof WorkOrderMiner&&order.getStructurePack().equals("Arcology Horizons")&&order.getClaimedBy().equals(mine.getPosition()),"Order ownership/type changed");if(!seed)check(order.getID()==expected.orders[i],"Work order recreated instead of resumed");}
    for(var resource:new ArrayList<>(mine.getNeededResources().values())){var stack=resource.getItemStack();check(!stack.is(Items.COAL)&&!stack.is(Items.COAL_ORE),"Output supplied");int have=ArcologyOperationsFixture.count(mine.getItemHandlerCap((Direction)null),stack)+ArcologyOperationsFixture.count(c.getInventory(),stack);int target=Math.min(64,resource.getAmount());if(have<target)ItemHandlerHelper.insertItemStacked(mine.getItemHandlerCap((Direction)null),stack.copyWithCount(target-have),false);}
    for(var item:List.of(Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL)){var probe=new ItemStack(item);if(ArcologyOperationsFixture.count(c.getInventory(),probe)+ArcologyOperationsFixture.count(mine.getItemHandlerCap((Direction)null),probe)<2)ItemHandlerHelper.insertItemStacked(mine.getItemHandlerCap((Direction)null),probe,false);}
    int mined=air(i);if(seed)ready&=mined-baseline[i]>=24&&mine.getProgress()!=null&&mine.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("item_obtained;"+Items.COAL.getDescriptionId())>0;else ready&=mined-baseline[i]>=100;
    if(ticks%400==0)Files.writeString(Path.of(System.getProperty("colonyhorizons.probeOutput")).resolve("quarry-restart-diagnostics.txt"),"ticks="+ticks+" shaft="+i+" air="+mined+" baseline="+baseline[i]+" pos="+entity.position()+" ai="+c.getJob().getWorkerAI().getState()+" order="+(order==null?"none":order.getID())+" progress="+mine.getProgress()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(ready){
    if(seed){for(int i=0;i<2;i++){expected.air[i]=air(i);expected.orders[i]=mines.get(i).getWorkOrder().getID();}level.getDataStorage().set(KEY,expected);expected.setDirty();level.getServer().getProfileCache().save();}
    String phase=seed?"seed":"verify";String detail=seed?"Both actual native quarriers excavated at least24 cells and collected coal; existing Mine-owned orders/progress, worker/entity IDs, dual assignments and air counts saved while workers remain active.":"Second server restored the same two workers/entity UUIDs and exact Mine-owned work-order IDs; both continued clearing at least100 additional interior cells after verification began, without rebuild, reassignment, respawn or progress injection. Recovery disabled; requested materials/replacement tools/food supplied; dry terrain. Clean restart, not crash recovery or full-depth completion.";
    ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyQuarryRestart",List.of(new ProbeResult("quarry_"+phase,ProbeResult.Outcome.PASS,detail)));h.succeed();return;
   }
   check(ticks<220000,"Quarry continuation timeout");schedule();
  }
 }
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static final class Expected extends SavedData {
  UUID owner;int colony;final BlockPos[] mines=new BlockPos[2],quarries=new BlockPos[2];final int[] workers=new int[2],orders=new int[2],air=new int[2];final UUID[] entities=new UUID[2];
  public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putUUID("owner",owner);t.putInt("colony",colony);for(int i=0;i<2;i++){t.putLong("mine"+i,mines[i].asLong());t.putLong("quarry"+i,quarries[i].asLong());t.putUUID("entity"+i,entities[i]);}t.putIntArray("workers",workers);t.putIntArray("orders",orders);t.putIntArray("air",air);return t;}
  static Expected load(CompoundTag t,HolderLookup.Provider p){var e=new Expected();e.owner=t.getUUID("owner");e.colony=t.getInt("colony");for(int i=0;i<2;i++){e.mines[i]=BlockPos.of(t.getLong("mine"+i));e.quarries[i]=BlockPos.of(t.getLong("quarry"+i));e.entities[i]=t.getUUID("entity"+i);e.workers[i]=t.getIntArray("workers")[i];e.orders[i]=t.getIntArray("orders")[i];e.air[i]=t.getIntArray("air")[i];}return e;}
 }
}
