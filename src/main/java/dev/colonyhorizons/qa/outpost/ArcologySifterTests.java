package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSifter;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.*;
import java.nio.file.*;
import java.util.*;

/** Native automatic sifting using the loaded string/gravel recipe and its unchanged random loot table. */
@PrefixGameTestTemplate(false)
public final class ArcologySifterTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void sifting(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  OutpostProbeFixture fixture;BuildingSifter hut;ICitizenData worker;UUID identity;net.minecraft.world.phys.Vec3 previous;
  int ticks;boolean siftObserved,recipeChecked;final Item[] products={Items.IRON_NUGGET,Items.FLINT,Items.COAL};
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"SiftProbe");level.getServer().getProfileCache().add(profile);
   fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;
   var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(512,16,16),5,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   hut=(BuildingSifter)setup.place("craftsmanship/masonry/sifter5.blueprint",new BlockPos(512,16,0),true);
   supply(Items.GRAVEL,64);supply(ModItems.sifterMeshString,1);
   check(hut.getCurrentDailyQuantity()==0&&meshDamage()==0,"Sifter must begin unused with fresh mesh");
   worker=fixture.createCitizen();check(worker!=null&&worker.getEntity().isPresent(),"Worker spawn");
   worker.getEntity().orElseThrow().setPos(514.5,16,-4.5);identity=worker.getEntity().orElseThrow().getUUID();previous=worker.getEntity().orElseThrow().position();
   check(hut.getModule(BuildingModules.SIFTER_WORK).assignCitizen(worker),"Native assignment");
   worker.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));worker.setSaturation(20);
   ((MinecoloniesAdvancedPathNavigate)worker.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   for(var item:products)check(count(item)==0,"Output initially present "+item);schedule();
  }catch(Throwable e){fail(e);}}
  void supply(Item item,int n){check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(item,n),false).isEmpty(),"Input insertion "+item);}
  int count(Item item){return ArcologyOperationsFixture.count(worker.getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(hut.getItemHandlerCap((Direction)null),new ItemStack(item));}
  int meshDamage(){var inv=hut.getItemHandlerCap((Direction)null);int damage=0;for(int s=0;s<inv.getSlots();s++){var stack=inv.getStackInSlot(s);if(stack.is(ModItems.sifterMeshString))damage+=stack.getDamageValue();}return damage;}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=worker.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(identity)&&worker.getWorkBuilding()==hut,"Worker identity/assignment lost");check(e.position().distanceTo(previous)<=12,"Recovery teleport");previous=e.position();worker.setSaturation(20);
   siftObserved|=worker.getJob().getWorkerAI()!=null&&worker.getJob().getWorkerAI().getState()==com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.SIFT;
   if(!recipeChecked){for(var token:hut.getModule(BuildingModules.SIFTER_CRAFT).getRecipes()){
    var recipe=IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
    if(recipe!=null&&recipe.getCleanedInput().stream().anyMatch(i->i.getItem()==Items.GRAVEL)&&recipe.getCraftingTools().stream().anyMatch(i->i.is(ModItems.sifterMeshString))){check(recipe.getPrimaryOutput().isEmpty(),"Native random recipe unexpectedly has fixed primary output");recipeChecked=true;break;}
   }}
   int outputs=0;var stats=hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager();
   for(var item:products){int n=count(item);outputs+=n;if(n>0)check(stats.getStatTotal("item_obtained;"+new ItemStack(item).getHoverName().getString())>=n,"Missing native output statistic "+item);}
   if(outputs>0){check(recipeChecked&&siftObserved&&hut.getCurrentDailyQuantity()>0,"Missing native recipe/sift/cycle evidence");check(count(Items.GRAVEL)<64&&meshDamage()>0,"Native cycle must consume gravel and damage hut mesh");check(stats.getStatTotal("item_used;"+new ItemStack(Items.GRAVEL).getHoverName().getString())>0,"Missing native input statistic");
    ProbeReport.write(output,"ArcologySifter",List.of(new ProbeResult("sifter_cycle",ProbeResult.Outcome.PASS,"Native sifter used loaded automatic string-mesh/gravel recipe, consumed gravel, damaged the hut mesh, incremented daily quantity and native input/output statistics, and produced "+outputs+" random-loot items in worker/hut inventory. No recipe/loot/output/AI injection. Prebuilt level5 Arcology hut, 64 gravel, fresh mesh, food and prerequisite fixture supplied; identity/movement checked with recovery disabled. Other meshes/inputs/levels, daily caps, delivery and restart not certified.")));h.succeed();return;}
   if(ticks%400==0)Files.writeString(output.resolve("sifter-diagnostics.txt"),"tick="+ticks+" pos="+e.position()+" state="+(worker.getJob().getWorkerAI()==null?"none":worker.getJob().getWorkerAI().getState())+" recipe="+recipeChecked+" gravel="+count(Items.GRAVEL)+" meshDamage="+meshDamage()+" daily="+hut.getCurrentDailyQuantity()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<60000,"Sifter timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{ProbeReport.write(output,"ArcologySifter",List.of(new ProbeResult("sifter_failure",ProbeResult.Outcome.FAIL,e.toString())));}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
