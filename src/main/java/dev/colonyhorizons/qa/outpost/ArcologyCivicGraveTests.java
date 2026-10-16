package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGraveyard;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
/** A real fixture death followed by native undertaker retrieval; no grave data/AI injection. */
@PrefixGameTestTemplate(false)
public final class ArcologyCivicGraveTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void recovery(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));OutpostProbeFixture fixture;BuildingGraveyard hut;ICitizenData worker;UUID identity;net.minecraft.world.phys.Vec3 previous;BlockPos grave;int ticks,deathHits;boolean emptied;com.minecolonies.api.entity.citizen.AbstractEntityCitizen victimEntity;UUID victimIdentity;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"GraveProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(512,16,16),5,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);hut=(BuildingGraveyard)setup.place("mystic/graveyard5.blueprint",new BlockPos(512,16,0),true);
   worker=fixture.createCitizen();check(worker!=null&&worker.getEntity().isPresent(),"Worker spawn");worker.getEntity().orElseThrow().setPos(514.5,16,-4.5);identity=worker.getEntity().orElseThrow().getUUID();previous=worker.getEntity().orElseThrow().position();
   check(hut.getModuleMatching(WorkerBuildingModule.class,m->m.getJobEntry()==com.minecolonies.api.colony.jobs.ModJobs.undertaker.get()).assignCitizen(worker),"Undertaker assignment");worker.getInventory().setStackInSlot(0,new ItemStack(Items.DIAMOND_SHOVEL));worker.getInventory().setStackInSlot(8,new ItemStack(Items.GOLDEN_CARROT,64));worker.setSaturation(20);((MinecoloniesAdvancedPathNavigate)worker.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   var victim=fixture.createCitizen();check(victim!=null&&victim.getEntity().isPresent(),"Victim fixture spawn");victim.getEntity().orElseThrow().setPos(520.5,16,-8.5);victim.getInventory().setStackInSlot(0,new ItemStack(Items.EMERALD,7));check(count()==0,"Recovery item already present");
   victimEntity=victim.getEntity().orElseThrow();victimIdentity=victimEntity.getUUID();check(fixture.colony().getGraveManager().getGraves().isEmpty(),"Fixture must start without graves");inflictFixtureDamage();
  }catch(Throwable e){fail(e);}}
  void inflictFixtureDamage(){try{
   check(victimEntity.getUUID().equals(victimIdentity),"Fixture victim identity changed");
   if(victimEntity.isAlive()){
    check(deathHits<12,"Fixture victim survived twelve native damage hits; health="+victimEntity.getHealth());
    float before=victimEntity.getHealth();boolean accepted=victimEntity.hurt(level.damageSources().genericKill(),10000);deathHits++;
    Files.writeString(output.resolve("civic-grave-diagnostics.txt"),"fixture_hit="+deathHits+" accepted="+accepted+" before="+before+" after="+victimEntity.getHealth()+" alive="+victimEntity.isAlive()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    if(victimEntity.isAlive()){h.runAfterDelay(25,()->inflictFixtureDamage());return;}
   }
   check(!victimEntity.isAlive(),"Victim must actually die");
   check(fixture.colony().getGraveManager().getGraves().size()==1,"Native death did not create one grave");
   grave=fixture.colony().getGraveManager().getGraves().keySet().iterator().next();
   check(level.getBlockEntity(grave) instanceof com.minecolonies.core.tileentities.TileEntityGrave,"Missing native grave tile");
   var tile=(com.minecolonies.core.tileentities.TileEntityGrave)level.getBlockEntity(grave);
   check(ArcologyOperationsFixture.count(tile.getInventory(),new ItemStack(Items.EMERALD))==7,"Real death grave must contain all seven fixture emeralds");
   schedule();
  }catch(Throwable e){fail(e);}}
  int count(){return ArcologyOperationsFixture.count(worker.getInventory(),new ItemStack(Items.EMERALD))+ArcologyOperationsFixture.count(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.EMERALD));}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{ticks+=20;var e=worker.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(identity)&&worker.getWorkBuilding()==hut,"Undertaker identity/assignment lost");check(e.position().distanceTo(previous)<=12,"Recovery teleport");previous=e.position();worker.setSaturation(20);
   var tile=level.getBlockEntity(grave);emptied|=tile instanceof com.minecolonies.core.tileentities.TileEntityGrave g&&g.isEmpty();var stats=hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager();
   if(count()==7&&emptied&&!(tile instanceof com.minecolonies.core.tileentities.TileEntityGrave)&&(stats.getStatTotal("graves_dug")>0||stats.getStatTotal("citizens_resurrected")>0)){ProbeReport.write(output,"ArcologyCivicGrave",List.of(new ProbeResult("graveyard_recovery",ProbeResult.Outcome.PASS,"Native fixture citizen died after spaced damage hits (native damage cap respected), creating a grave verified to contain seven emeralds. Undertaker naturally emptied grave, recovered all seven emeralds and completed native digging or resurrection with corresponding statistics; original grave removed. Level5 hut, shovel, worker food and victim inventory supplied; no grave/AI state injection. Named burial placement, resurrection probability and restart not certified.")));h.succeed();return;}
   if(ticks%1000==0)Files.writeString(output.resolve("civic-grave-diagnostics.txt"),"tick="+ticks+" position="+e.position()+" state="+worker.getJob().getWorkerAI().getState()+" emeralds="+count()+" emptied="+emptied+" dug="+stats.getStatTotal("graves_dug")+" resurrected="+stats.getStatTotal("citizens_resurrected")+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<60000,"Graveyard timeout");schedule();}
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{ProbeReport.write(output,"ArcologyCivicGrave",List.of(new ProbeResult("graveyard_failure",ProbeResult.Outcome.FAIL,e.toString())));}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
