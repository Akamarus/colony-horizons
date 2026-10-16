package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
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
import java.nio.file.*;
import java.util.*;

/** Save a working planter after an actual harvest; reload continues original crop/workplace without fixture reset. */
@PrefixGameTestTemplate(false)
public final class ArcologyCropActiveRestartTests {
 static final String KEY="arcology_crop_active_restart";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=700000)
 public static void crop_restart(GameTestHelper h){
  for(int x=29;x<=39;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{try{
   if(System.getProperty("colonyhorizons.probePhase","seed").equals("verify")){restore(h,0);return;}
   var fields=new ArcologyCropExtensionTests.Run(h);fields.afterVerification=f->{var work=new ArcologyCropProductionTests.Run(f);work.afterProduction=r->seed(h,r);work.start();};fields.start();
  }catch(Throwable e){h.fail(e.toString());}});
 }
 static void seed(GameTestHelper h,ArcologyCropProductionTests.Run r){try{
  check(r.crop==0&&r.stat()>0&&r.produced,"Seed must observe real first cocoa production");var e=new Expected();e.owner=r.f.fixture.ownerId();e.colony=r.f.fixture.colony().getID();e.worker=r.worker.getId();e.entity=r.id;e.hut=r.f.plantation.getPosition();e.field=r.f.fields.getFirst().getPosition();e.origin=r.f.origins.getFirst();e.stat=r.stat();e.positions=r.f.fields.getFirst().getWorkingPositions().stream().mapToLong(BlockPos::asLong).sorted().toArray();
  check(!r.worker.isPaused(),"Seed paused worker");h.getLevel().getDataStorage().set(KEY,e);e.setDirty();h.getLevel().getServer().getProfileCache().save();report(h,"crop_seed","Active original planter planted, grew and harvested cocoa with real inventory/statistic gain. Saved worker/entity, field/workplace, exact work positions and statistics without pausing/freeing assignment or replacing plants.");
 }catch(Throwable e){h.fail(e.toString());}}
 static void restore(GameTestHelper h,int waited){try{
  var e=h.getLevel().getDataStorage().get(FACTORY,KEY);check(e!=null,"Missing active crop checkpoint");var colony=IColonyManager.getInstance().getColonyByDimension(e.colony,h.getLevel().dimension());check(colony!=null,"Colony missing");
  if(waited==0){var profile=new GameProfile(e.owner,"CropFieldProbe");var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),profile);var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(512,16,0);colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);}
  var c=colony.getCitizenManager().getCivilian(e.worker);if(c==null||c.getEntity().isEmpty()){check(waited<12000,"Saved planter failed to load");h.runAfterDelay(20,()->restore(h,waited+20));return;}
  new Monitor(h,colony,e,c).start();
 }catch(Throwable e){h.fail(e.toString());}}
 static final class Monitor {
  final GameTestHelper h;final IColony colony;final Expected e;final ICitizenData worker;final BuildingPlantation hut;final PlantationField field;int ticks,baseline,last;Vec3 previous;boolean visited;
  Monitor(GameTestHelper h,IColony colony,Expected e,ICitizenData c){this.h=h;this.colony=colony;this.e=e;worker=c;hut=(BuildingPlantation)colony.getServerBuildingManager().getBuilding(e.hut);var fields=colony.getServerBuildingManager().getBuildingExtensions(f->f.getPosition().equals(e.field));check(fields.size()==1&&fields.getFirst() instanceof PlantationField,"Field missing or duplicated");field=(PlantationField)fields.getFirst();}
  void start(){check(hut!=null&&field.getBuildingId().equals(e.hut),"Field ownership changed");check(Arrays.equals(e.positions,field.getWorkingPositions().stream().mapToLong(BlockPos::asLong).sorted().toArray()),"Work positions changed");check(stat()>=e.stat,"Harvest statistics rolled back");baseline=stat();last=count();previous=worker.getEntity().orElseThrow().position();((MinecoloniesAdvancedPathNavigate)worker.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());schedule();}
  int stat(){return hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("item_obtained;"+Items.COCOA_BEANS.getDescriptionId());}
  int count(){return ArcologyOperationsFixture.count(worker.getInventory(),new ItemStack(Items.COCOA_BEANS))+ArcologyOperationsFixture.count(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.COCOA_BEANS));}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable ex){h.fail(ex.toString());}});}
  void tick()throws Exception{
   ticks+=20;var entity=worker.getEntity().orElseThrow();check(entity.isAlive()&&entity.getUUID().equals(e.entity)&&worker.getWorkBuilding()==hut&&!worker.isPaused(),"Active planter identity/assignment changed");check(previous.distanceTo(entity.position())<=12,"Recovery teleport");previous=entity.position();worker.setSaturation(20);
   var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();for(int dx:new int[]{0,9})for(int dz:new int[]{0,9})chunks.add(new net.minecraft.world.level.ChunkPos(e.origin.offset(dx,0,dz)));for(var chunk:chunks)h.getLevel().tickChunk(h.getLevel().getChunk(chunk.x,chunk.z),64);
   var p=entity.blockPosition();visited|=p.getX()>=e.origin.getX()&&p.getX()<e.origin.getX()+10&&p.getZ()>=e.origin.getZ()&&p.getZ()<e.origin.getZ()+10;int now=count();
   if(visited&&stat()>baseline&&now>last){report(h,"crop_verify","Second server restored identical active planter/entity, workplace, field ownership and work positions. Native field visit, fresh harvest statistics beyond reload baseline and observed inventory gain prove resumed work; no rebuilding, replanting, item restocking, reassignment or output injection. Native random growth accelerated in playerless fixture; normal daily schedule. Clean restart, cocoa at level5 only; not crash recovery or all crop types.");return;}last=now;
   if(ticks%400==0)Files.writeString(Path.of(System.getProperty("colonyhorizons.probeOutput")).resolve("crop-active-diagnostics.txt"),"ticks="+ticks+" stat="+stat()+" baseline="+baseline+" count="+now+" visited="+visited+" pos="+entity.position()+" ai="+worker.getJob().getWorkerAI().getState()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<672000,"Active crop recovery timeout");schedule();
  }
 }
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static void report(GameTestHelper h,String id,String evidence)throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyCropActiveRestart",List.of(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence)));h.succeed();}
 static final class Expected extends SavedData {
  UUID owner,entity;int colony,worker,stat;BlockPos hut,field,origin;long[] positions;
  public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putUUID("owner",owner);t.putUUID("entity",entity);t.putInt("colony",colony);t.putInt("worker",worker);t.putInt("stat",stat);t.putLong("hut",hut.asLong());t.putLong("field",field.asLong());t.putLong("origin",origin.asLong());t.putLongArray("positions",positions);return t;}
  static Expected load(CompoundTag t,HolderLookup.Provider p){var e=new Expected();e.owner=t.getUUID("owner");e.entity=t.getUUID("entity");e.colony=t.getInt("colony");e.worker=t.getInt("worker");e.stat=t.getInt("stat");e.hut=BlockPos.of(t.getLong("hut"));e.field=BlockPos.of(t.getLong("field"));e.origin=BlockPos.of(t.getLong("origin"));e.positions=t.getLongArray("positions");return e;}
 }
}
