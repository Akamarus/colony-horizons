package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Same built/ upgraded huts and citizens across a real server restart; never reseeds verify. */
@PrefixGameTestTemplate(false)
public final class ArcologyLifecycleTests {
 static final String KEY="arcology_lifecycle_fixture";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=550000)
 public static void upgraded_workers_restart(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{
   if(System.getProperty("colonyhorizons.probePhase","seed").equals("seed"))new ArcologyUpgradeTests.Run(h).start();
   else resume(h);
  });
 }
 static void afterUpgrades(GameTestHelper h,OutpostProbeFixture f,List<IBuilding> buildings,List<ProbeResult> upgrades){
  var run=new ArcologyBehaviorTests.Run(h,f,buildings);run.suite="ArcologyLifecycle";run.rows.addAll(upgrades);
  run.completed=()->{
   var expected=new Expected();expected.owner=f.ownerId();expected.colony=f.colony().getID();
   expected.workers=run.workers.stream().mapToInt(c->c.getId()).toArray();
   expected.anchors=buildings.stream().map(IBuilding::getPosition).toList();
   expected.population=f.colony().getCitizenManager().getCurrentCitizenCount();
   h.getLevel().getDataStorage().set(KEY,expected);expected.setDirty();
   h.getLevel().getServer().getProfileCache().save();h.succeed();
  };
  run.start();
  try{
   var placement=new ArcologyUpgradeTests.Run(h);placement.fixture=f;
   placement.owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new GameProfile(f.ownerId(),"UpgradeProbe"));
   var home=placement.place("fundamentals/citizen5.blueprint",new BlockPos(544,16,32),true);
   for(var c:run.workers)h.assertTrue(home.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.LIVING).assignCitizen(c),"Support housing assignment");
  }catch(Throwable e){h.fail(e.toString());}
 }
 static void resume(GameTestHelper h){try{
  var level=h.getLevel();var expected=level.getDataStorage().get(FACTORY,KEY);
  h.assertTrue(expected!=null,"Missing seed state; verify must never rebuild/reseed");
  var colony=IColonyManager.getInstance().getColonyByDimension(expected.colony,level.dimension());
  h.assertTrue(colony!=null,"Native colony not restored");
  h.assertTrue(IColonyManager.getInstance().getIColonyByOwner(level,expected.owner)==colony,"Owner changed");
  h.assertTrue(colony.getCitizenManager().getCurrentCitizenCount()==expected.population,"Population changed on reload");
  var buildings=expected.anchors.stream().map(p->colony.getServerBuildingManager().getBuilding(p)).toList();
  for(var b:buildings)h.assertTrue(b!=null && b.getBuildingLevel()==5 && b.getStructurePack().equals("Arcology Horizons"),"Upgraded hut lost on reload");
  var profile=new GameProfile(expected.owner,"UpgradeProbe");
  var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
  var owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(512,16,0);
  colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);
  // Native Nether allowance resets through multi-day wake-up cycles, not a clock jump.
  level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,level.getServer());
  awaitWorkers(h,expected,colony,buildings,0);
 }catch(Throwable e){h.fail(e.toString());}}
 static void awaitWorkers(GameTestHelper h,Expected expected,com.minecolonies.api.colony.IColony colony,List<IBuilding> buildings,int waited){try{
  boolean ready=true;
  for(int i=0;i<expected.workers.length;i++){
   var c=colony.getCitizenManager().getCivilian(expected.workers[i]);
   h.assertTrue(c!=null && c.getWorkBuilding()==buildings.get(i),"Original worker/assignment not restored: "+expected.workers[i]);
   h.assertTrue(c.isPaused(),"Saved worker pause lost");
   h.assertTrue(c.getHomeBuilding()!=null && c.getHomeBuilding().getPosition().equals(new BlockPos(544,16,32)),"Original housing assignment lost");
   ready &= c.getEntity().isPresent() && c.getEntity().orElseThrow().isAlive();
  }
  // CitizenManager starts a 6000-tick recovery timer and checks it every colony tick.
  // Observe native recovery; never manually spawn missing saved citizens.
  if(!ready){h.assertTrue(waited<10000,"Original worker entities did not become ready after the native five-minute recovery interval");h.runAfterDelay(20,()->awaitWorkers(h,expected,colony,buildings,waited+20));return;}
  java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("colonyhorizons.probeOutput")).resolve("reload-readiness.txt"),"Original citizen IDs="+java.util.Arrays.toString(expected.workers)+"; native readiness wait="+waited+" ticks; no fixture respawn/reassignment/rebuild.\n");
  var run=new ArcologyBehaviorTests.Run(h,new OutpostProbeFixture(colony,expected.owner,new BlockPos(480,16,0)),buildings);
  run.suite="ArcologyLifecycle";run.reload=true;
  for(int id:expected.workers)run.workers.add(colony.getCitizenManager().getCivilian(id));
  h.assertTrue(run.workers.size()==3,"Three original workers required");run.start();
 }catch(Throwable e){h.fail(e.toString());}}
 static final class Expected extends SavedData {
  UUID owner;int colony,population;int[] workers;List<BlockPos> anchors;
  @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putUUID("owner",owner);t.putInt("colony",colony);t.putInt("population",population);t.putIntArray("workers",workers);t.putLongArray("anchors",anchors.stream().mapToLong(BlockPos::asLong).toArray());return t;}
  static Expected load(CompoundTag t,HolderLookup.Provider p){var e=new Expected();e.owner=t.getUUID("owner");e.colony=t.getInt("colony");e.population=t.getInt("population");e.workers=t.getIntArray("workers");e.anchors=Arrays.stream(t.getLongArray("anchors")).mapToObj(BlockPos::of).toList();return e;}
 }
}
