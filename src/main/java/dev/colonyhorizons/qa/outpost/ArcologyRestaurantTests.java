package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.minecolonies.core.entity.citizen.EntityCitizen;
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

/** Native restaurant service: a hungry empty-inventory customer walks in, is served by the cook and eats. */
@PrefixGameTestTemplate(false)
public final class ArcologyRestaurantTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=35000)
 public static void restaurant(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  OutpostProbeFixture fixture;BuildingCook hut;ICitizenData cook,customer;UUID cookId,customerId;
  net.minecraft.world.phys.Vec3 cookPrevious,customerPrevious;int ticks;boolean arrived,received,eating,serving;int maxCustomerBread;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"DiningProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(512,16,16),5,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   hut=(BuildingCook)setup.place("fundamentals/cook5.blueprint",new BlockPos(512,16,0),true);
   hut.getModule(BuildingModules.RESTAURANT_MENU).addMenuItem(new ItemStack(Items.BREAD));
   check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.BREAD,64),false).isEmpty(),"Prepared bread insertion");
   cook=fixture.createCitizen();check(cook!=null&&cook.getEntity().isPresent(),"Cook spawn");var ce=cook.getEntity().orElseThrow();ce.setPos(514.5,16,-4.5);cookId=ce.getUUID();cookPrevious=ce.position();
   check(hut.getModule(BuildingModules.COOK_WORK).assignCitizen(cook),"Native cook assignment");cook.getInventory().setStackInSlot(8,new ItemStack(Items.GOLDEN_CARROT,64));cook.setSaturation(20);
   ((MinecoloniesAdvancedPathNavigate)ce.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   customer=fixture.createCitizen();check(customer!=null&&customer.getEntity().isPresent(),"Customer spawn");var guest=customer.getEntity().orElseThrow();guest.setPos(514.5,16,-14.5);customerId=guest.getUUID();customerPrevious=guest.position();
   check(!hut.isInBuilding(guest.blockPosition()),"Customer must start outside restaurant");
   for(int s=0;s<customer.getInventory().getSlots();s++)check(customer.getInventory().getStackInSlot(s).isEmpty(),"Customer must begin with empty inventory");
   customer.setSaturation(1);customer.setJustAte(false);
   ((MinecoloniesAdvancedPathNavigate)guest.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   check(totalBread()==64&&served()==0,"Service fixture must begin unserved");schedule();
  }catch(Throwable e){fail(e);}}
  int count(ICitizenData c){return ArcologyOperationsFixture.count(c.getInventory(),new ItemStack(Items.BREAD));}
  int totalBread(){return count(cook)+count(customer)+ArcologyOperationsFixture.count(hut.getItemHandlerCap((Direction)null),new ItemStack(Items.BREAD));}
  int served(){return hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("food_served_detail;"+Items.BREAD.getDescriptionId());}
  void schedule(){h.runAfterDelay(10,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=10;var ce=cook.getEntity().orElseThrow();var guest=customer.getEntity().orElseThrow();
   check(ce.isAlive()&&guest.isAlive()&&ce.getUUID().equals(cookId)&&guest.getUUID().equals(customerId)&&cook.getWorkBuilding()==hut,"Cook/customer identity or assignment lost");
   check(ce.position().distanceTo(cookPrevious)<=8&&guest.position().distanceTo(customerPrevious)<=8,"Recovery teleport");cookPrevious=ce.position();customerPrevious=guest.position();cook.setSaturation(20);
   var guestState=((EntityCitizen)guest).getCitizenAI().getState();
   check(guestState!=com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.GET_FOOD_YOURSELF,"Customer used self-service instead of cook");
   arrived|=hut.isInBuilding(guest.blockPosition());serving|=cook.getJob().getWorkerAI()!=null&&cook.getJob().getWorkerAI().getState()==com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.COOK_SERVE_FOOD_TO_CITIZEN;
   int bread=count(customer);maxCustomerBread=Math.max(maxCustomerBread,bread);received|=bread>0&&served()>0;eating|=received&&guestState==com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.EAT;
   if(received&&eating&&customer.getSaturation()>1&&bread<maxCustomerBread&&totalBread()<64){check(arrived&&serving&&served()>0,"Missing native restaurant/cook service evidence");
    ProbeReport.write(output,"ArcologyRestaurant",List.of(new ProbeResult("cook_service",ProbeResult.Outcome.PASS,"Hungry empty-inventory customer independently walked into prebuilt level5 Arcology restaurant. Native cook fetched supplied prepared bread from hut, served customer with native food-served statistics; customer entered native eating state, consumed bread and increased saturation. Same cook/customer identities; recovery disabled; self-service fallback rejected. Prepared bread, menu selection, cook food, initial customer hunger and claim/research fixtures supplied. Raw-food cooking, menu variety, delivery, other levels and restart not certified.")));h.succeed();return;}
   if(ticks%200==0)Files.writeString(output.resolve("restaurant-diagnostics.txt"),"tick="+ticks+" cook="+ce.position()+" AI="+(cook.getJob().getWorkerAI()==null?"none":cook.getJob().getWorkerAI().getState())+" customer="+guest.position()+" state="+guestState+" saturation="+customer.getSaturation()+" bread="+bread+" total="+totalBread()+" served="+served()+" arrived="+arrived+" serving="+serving+" eating="+eating+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<30000,"Restaurant timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{ProbeReport.write(output,"ArcologyRestaurant",List.of(new ProbeResult("restaurant_failure",ProbeResult.Outcome.FAIL,e.toString())));}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
