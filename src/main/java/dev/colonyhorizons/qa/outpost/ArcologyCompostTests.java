package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.api.colony.buildings.modules.*;
import com.minecolonies.api.crafting.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native compost cycle; no barrel timer, contents or worker state injection. */
@PrefixGameTestTemplate(false)
public final class ArcologyCompostTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void composting(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  OutpostProbeFixture fixture;com.minecolonies.core.colony.buildings.workerbuildings.BuildingComposter hut;ICitizenData worker;
  UUID identity;net.minecraft.world.phys.Vec3 previous;int ticks;boolean processing;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
   level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"CraftProbe");level.getServer().getProfileCache().add(profile);
   fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;
   var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(512,16,16),5,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);

   hut=(com.minecolonies.core.colony.buildings.workerbuildings.BuildingComposter)setup.place("agriculture/horticulture/composter5.blueprint",new BlockPos(512,16,0),true);
   check(!hut.getBarrels().isEmpty(),"No blueprint barrels registered");
   for(var pos:hut.getBarrels()){var barrel=(com.minecolonies.core.tileentities.TileEntityBarrel)level.getBlockEntity(pos);check(barrel!=null&&barrel.getItems()==0&&!barrel.isDone(),"Barrels must start empty");}
   check(!hut.getSetting(com.minecolonies.core.colony.buildings.workerbuildings.BuildingComposter.PRODUCE_DIRT).getValue(),"Expected compost setting");
   hut.getModuleMatching(com.minecolonies.core.colony.buildings.modules.ItemListModule.class,m->m.getId().equals("compostables")).addItem(new ItemStorage(Items.WHEAT));
   check(IColonyManager.getInstance().getCompatibilityManager().getCopyOfCompostRecipes().containsKey(Items.WHEAT),"No native wheat compost recipe");
   check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.WHEAT,64),false).isEmpty(),"Wheat insertion");
   worker=fixture.createCitizen();check(worker!=null&&worker.getEntity().isPresent(),"Worker spawn");worker.getEntity().orElseThrow().setPos(514.5,16,-4.5);identity=worker.getEntity().orElseThrow().getUUID();previous=worker.getEntity().orElseThrow().position();
   check(hut.getModule(BuildingModules.COMPOSTER_WORK).assignCitizen(worker),"Native assignment");
   worker.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));worker.setSaturation(20);
   ((MinecoloniesAdvancedPathNavigate)worker.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   check(count(com.minecolonies.api.items.ModItems.compost)==0,"No initial compost allowed");schedule();
  }catch(Throwable e){fail(e);}}
  int count(Item item){return ArcologyOperationsFixture.count(worker.getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(hut.getItemHandlerCap((Direction)null),new ItemStack(item));}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=worker.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(identity)&&worker.getWorkBuilding()==hut,"Worker identity/assignment lost");check(e.position().distanceTo(previous)<=12,"Recovery teleport");previous=e.position();worker.setSaturation(20);
   for(var pos:hut.getBarrels()){var barrel=(com.minecolonies.core.tileentities.TileEntityBarrel)level.getBlockEntity(pos);processing|=barrel.checkIfWorking();}
   var stats=hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager();int made=count(com.minecolonies.api.items.ModItems.compost);
   if(made>0){check(processing&&count(Items.WHEAT)<64,"Missing native input/barrel processing");check(stats.getStatTotal("items_composted;"+Items.WHEAT.getDescriptionId())>0&&stats.getStatTotal("product_collected;"+com.minecolonies.api.items.ModItems.compost.getDescriptionId())>=made,"Missing native compost statistics");ProbeReport.write(output,"ArcologyCompost",List.of(new ProbeResult("composter_cycle",ProbeResult.Outcome.PASS,"Native worker collected allowed wheat from hut, filled registered blueprint barrel, waited for native processing and harvested "+made+" compost. No timer/content injection; identity and movement checked with recovery disabled. Prebuilt level5 hut, wheat, allowed-input configuration and food supplied; dirt mode, other levels, delivery and restart not tested.")));h.succeed();return;}
   if(ticks%1000==0)Files.writeString(output.resolve("compost-diagnostics.txt"),"tick="+ticks+" position="+e.position()+" state="+worker.getJob().getWorkerAI().getState()+" wheat="+count(Items.WHEAT)+" processing="+processing+" compost="+made+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<60000,"Compost timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{ProbeReport.write(output,"ArcologyCompost",List.of(new ProbeResult("compost_failure",ProbeResult.Outcome.FAIL,e.toString())));}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
