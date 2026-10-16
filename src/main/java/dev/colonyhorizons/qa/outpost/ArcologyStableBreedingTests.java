package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Eligible adult inputs only; native worker feeding and natural birth, with optional real process restart. */
@PrefixGameTestTemplate(false)
public final class ArcologyStableBreedingTests {
 static final String KEY="arcology_stable_breeding";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=180000)
 public static void breeding(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=5;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{try{
   if(System.getProperty("colonyhorizons.probePhase","seed").equals("verify")){restore(h,0);return;}
   var setup=new ArcologyStableTests.Run(h);setup.afterSetup=r->{try{new Monitor(h,r).start();}catch(Throwable e){h.fail(e.toString());}};setup.start();
  }catch(Throwable e){h.fail(e.toString());}});
 }
 static void restore(GameTestHelper h,int waited){try{
  var e=h.getLevel().getDataStorage().get(FACTORY,KEY);check(e!=null,"Missing saved breeding checkpoint; no reseeding");
  var colony=IColonyManager.getInstance().getColonyByDimension(e.colony,h.getLevel().dimension());check(colony!=null,"Colony missing");
  if(waited==0){var profile=new GameProfile(e.owner,"StableProbe");var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),profile);var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(544,16,40);colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);}
  var worker=colony.getCitizenManager().getCivilian(e.worker);
  if(worker==null||worker.getEntity().isEmpty()||h.getLevel().getEntity(e.parents[0])==null||h.getLevel().getEntity(e.parents[1])==null){check(waited<12000,"Saved entities not restored");h.runAfterDelay(1,()->restore(h,waited+1));return;}
  new Monitor(h,colony,e).start();
 }catch(Throwable e){h.fail(e.toString());}}
 static final class Monitor {
  final GameTestHelper h;final ServerLevel level;final IColony colony;final BuildingStable stable;final ICitizenData worker;final Expected expected;final boolean verify,restart;
  final AABB area=new AABB(530,15,37,549,23,61);final Map<UUID,Vec3> previous=new HashMap<>();int ticks,reloadedAttempts,reloadedFood;boolean sawLove;boolean noFeed;final List<ProbeResult> rows=new ArrayList<>();
  Monitor(GameTestHelper h,ArcologyStableTests.Run f){this.h=h;level=h.getLevel();colony=f.fixture.colony();stable=f.stable;worker=f.worker;verify=false;restart=System.getProperty("colonyhorizons.foundationSuite").endsWith("Restart");expected=new Expected();expected.owner=f.fixture.ownerId();expected.colony=colony.getID();expected.worker=worker.getId();expected.entity=worker.getEntity().orElseThrow().getUUID();expected.hut=stable.getPosition();f.source.discard();
   for(int i=0;i<2;i++){var horse=EntityType.HORSE.create(level);check(horse!=null,"Adult fixture creation");horse.setPos(535.5+i*8,16,44.5);horse.setTamed(true);horse.setPersistenceRequired();check(level.addFreshEntity(horse),"Adult input spawn");var cav=CavalryHorseEntity.createFromVanilla(colony,level,horse);check(cav!=null,"Adult cavalry fixture registration");cav.getAnimalData().setHomeBuilding(stable);cav.setHealth(cav.getMaxHealth());check(!cav.isBaby()&&!cav.isInLove()&&!cav.isNoAi(),"Invalid adult input");expected.parents[i]=cav.getUUID();}
   expected.food=food();check(expected.food>=2&&attempts()==0&&babies().isEmpty(),"Initial feed/stat/output contract");
   noFeed=true;for(var inventory:List.of(worker.getInventory(),stable.getItemHandlerCap((Direction)null)))for(int slot=0;slot<inventory.getSlots();slot++)if(inventory.getStackInSlot(slot).is(Items.GOLDEN_APPLE))inventory.extractItem(slot,inventory.getStackInSlot(slot).getCount(),false);check(food()==0,"Shortage fixture still has golden apples");
  }
  Monitor(GameTestHelper h,IColony colony,Expected e){this.h=h;level=h.getLevel();this.colony=colony;expected=e;stable=(BuildingStable)colony.getServerBuildingManager().getBuilding(e.hut);worker=colony.getCitizenManager().getCivilian(e.worker);verify=true;restart=true;}
  void start(){
   check(stable!=null&&stable.getBuildingLevel()==5&&stable.getSetting(AbstractBuilding.BREEDING).getValue(),"Stable/breeding setting lost");
   check(worker.getWorkBuilding()==stable&&!worker.isPaused()&&worker.getEntity().orElseThrow().getUUID().equals(expected.entity),"Active worker identity/assignment lost");
   if(verify){check(attempts()>=expected.attempts,"Breeding statistic rolled back");check(food()<=expected.savedFood,"Feed duplicated across save");check(babies().isEmpty(),"Birth already completed before reload monitor; not active-work evidence");reloadedAttempts=attempts();reloadedFood=food();}
   disableRecovery(worker.getEntity().orElseThrow());for(UUID id:expected.parents){var horse=parent(id);check(horse.getAnimalData()!=null&&horse.getAnimalData().getHomeBuilding()==stable,"Parent home lost");disableRecovery(horse);}schedule();
  }
  void disableRecovery(net.minecraft.world.entity.Mob e){if(e.getNavigation() instanceof MinecoloniesAdvancedPathNavigate n)n.setStuckHandler(PathingStuckHandler.createStuckHandler());previous.put(e.getUUID(),e.position());}
  CavalryHorseEntity parent(UUID id){check(level.getEntity(id) instanceof CavalryHorseEntity,"Same parent missing/replaced");return (CavalryHorseEntity)level.getEntity(id);}
  int attempts(){return stable.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("breeding_attempts");}
  int food(){return ArcologyOperationsFixture.count(worker.getInventory(),new ItemStack(Items.GOLDEN_APPLE))+ArcologyOperationsFixture.count(stable.getItemHandlerCap((Direction)null),new ItemStack(Items.GOLDEN_APPLE));}
  List<AbstractHorse> babies(){return level.getEntitiesOfClass(AbstractHorse.class,area,e->e.isBaby()&&!Arrays.asList(expected.parents).contains(e.getUUID()));}
  void schedule(){h.runAfterDelay(1,()->{try{tick();}catch(Throwable e){h.fail(e.toString());}});}
  void tick()throws Exception{
   ticks++;var citizen=worker.getEntity().orElseThrow();check(citizen.isAlive()&&citizen.getUUID().equals(expected.entity)&&worker.getWorkBuilding()==stable&&!worker.isPaused(),"Worker lost");worker.setSaturation(20);
   int loving=0;for(UUID id:expected.parents){var p=parent(id);check(p.isAlive()&&!p.isNoAi()&&stable.isInBuilding(p.blockPosition()),"Parent died/escaped");if(p.isInLove())loving++;}
   sawLove|=loving>0;
   for(var entry:previous.entrySet()){var entity=level.getEntity(entry.getKey());check(entity!=null&&entity.position().distanceTo(entry.getValue())<=12,"Entity recovery teleport");entry.setValue(entity.position());}
   var babies=babies();check(babies.size()<=1,"Unexpected duplicate offspring");
   if(noFeed){
    check(attempts()==0&&loving==0&&babies.isEmpty()&&food()==0,"Breeding occurred without supplied feed");
    if(ticks>=2000){check(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(stable.getItemHandlerCap((Direction)null),new ItemStack(Items.GOLDEN_APPLE,expected.food),false).isEmpty(),"Feed restock failed");noFeed=false;rows.add(new ProbeResult("stable_feed_shortage",ProbeResult.Outcome.PASS,"Two eligible adults remained without love, breeding statistics or offspring through 2000 native ticks with zero golden apples in worker/hut. Restocked original food amount only into the hut; worker must collect and use it normally."));}
    schedule();return;
   }
   if(restart&&!verify&&attempts()>0&&loving==1&&babies.isEmpty()){
    expected.attempts=attempts();expected.savedFood=food();check(expected.savedFood<expected.food,"No actual feed consumption before restart");level.getDataStorage().set(KEY,expected);expected.setDirty();level.getServer().getProfileCache().save();report("breeding_seed","Saved active same worker and two adult parent UUIDs after native feeding, with exactly one parent in love and no baby. Food consumed; no pause, love-state or offspring injection.");return;
   }
   if(!babies.isEmpty()&&attempts()>=2&&expected.food-food()>=2&&(!restart||verify)){
    if(verify)check(attempts()>reloadedAttempts&&food()<reloadedFood,"No fresh native feeding after reload monitor began");
    check(sawLove||verify,"No observed parent love state");
    report(verify?"breeding_verify":"stable_breeding","Native stablemaster consumed at least two golden apples; breeding statistics="+attempts()+", fresh baby UUID="+babies.getFirst().getUUID()+". "+(verify?"Second independent server restored same worker/parents, home and active assignment; fresh feeding beyond saved checkpoint completed natural birth. ":"")+"Two eligible full-health adult cavalry horses, tools/feed, daytime and two-high external fixture fence supplied. No love/baby/AI-action injection; recovery disabled. Restart variant is clean shutdown only; no crashes, every-level or outdoor-containment claim.");return;
   }
   if(ticks%400==0)Files.writeString(Path.of(System.getProperty("colonyhorizons.probeOutput")).resolve("breeding-diagnostics.txt"),"ticks="+ticks+" attempts="+attempts()+" food="+food()+" love="+loving+" babies="+babies.size()+" worker="+citizen.position()+" ai="+worker.getJob().getWorkerAI().getState()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<170000,"Breeding timeout");schedule();
  }
  void report(String id,String evidence)throws Exception{rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),System.getProperty("colonyhorizons.foundationSuite"),rows);h.succeed();}
 }
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static final class Expected extends SavedData {
  UUID owner,entity;UUID[] parents=new UUID[2];int colony,worker,food,savedFood,attempts;BlockPos hut;
  public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putUUID("owner",owner);t.putUUID("entity",entity);for(int i=0;i<2;i++)t.putUUID("parent"+i,parents[i]);t.putInt("colony",colony);t.putInt("worker",worker);t.putInt("food",food);t.putInt("savedFood",savedFood);t.putInt("attempts",attempts);t.putLong("hut",hut.asLong());return t;}
  static Expected load(CompoundTag t,HolderLookup.Provider p){var e=new Expected();e.owner=t.getUUID("owner");e.entity=t.getUUID("entity");for(int i=0;i<2;i++)e.parents[i]=t.getUUID("parent"+i);e.colony=t.getInt("colony");e.worker=t.getInt("worker");e.food=t.getInt("food");e.savedFood=t.getInt("savedFood");e.attempts=t.getInt("attempts");e.hut=BlockPos.of(t.getLong("hut"));return e;}
 }
}
