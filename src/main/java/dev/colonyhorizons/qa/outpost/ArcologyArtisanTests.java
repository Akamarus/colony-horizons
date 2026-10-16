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

/** Real request-driven crafting in prebuilt Arcology huts. No output or AI-state injection. */
@PrefixGameTestTemplate(false)
public final class ArcologyArtisanTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void manufacturing(GameTestHelper h){
  for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new Run(h).start());
 }
 static final class Run {
  final GameTestHelper h; final ServerLevel level;
  final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ProbeResult> rows=new ArrayList<>();
  final List<IBuilding> huts=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();
  final String[] names={"blacksmith","mechanic","dyer"};
  final Item[] products={Items.IRON_PICKAXE,Items.MINECART,Items.RED_WOOL};
  final Item[][] inputs={{Items.IRON_INGOT,Items.STICK},{Items.IRON_INGOT},{Items.WHITE_WOOL,Items.RED_DYE}};
  final int[][] quantities={{3,2},{5},{1,1}};
  final List<UUID> workerIds=new ArrayList<>();final List<net.minecraft.world.phys.Vec3> workerPositions=new ArrayList<>();
  final int[] yields={1,1,1};final boolean[] done=new boolean[3];
  OutpostProbeFixture fixture;int ticks;IBuilding requester,courierHut,warehouse;ICitizenData courier;
  boolean courierStarted;final boolean[] carried=new boolean[3];net.minecraft.world.phys.Vec3 lastCourierPos;UUID courierIdentity;
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
   requester=setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   String[] paths={"craftsmanship/metallurgy/blacksmith5.blueprint","craftsmanship/metallurgy/mechanic5.blueprint","craftsmanship/luxury/dyer5.blueprint"};
   BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(512,16,32)};
   for(int i=0;i<3;i++){
    var b=setup.place(paths[i],anchors[i],true);huts.add(b);
    var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Worker spawn");workers.add(c);
    c.getEntity().orElseThrow().setPos(anchors[i].getX()+2.5,16,anchors[i].getZ()-4.5);c.setSaturation(20);workerIds.add(c.getEntity().orElseThrow().getUUID());workerPositions.add(c.getEntity().orElseThrow().position());
    var module=switch(i){case 0->b.getModule(BuildingModules.BLACKSMITH_WORK);case 1->b.getModule(BuildingModules.MECHANIC_WORK);default->b.getModule(BuildingModules.DYER_WORK);};
    check(module.assignCitizen(c),"Assignment "+names[i]);
    ((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
    c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    var ingredients=new ArrayList<ItemStorage>();
    for(int j=0;j<inputs[i].length;j++){
     ingredients.add(new ItemStorage(new ItemStack(inputs[i][j],quantities[i][j])));
     check(ItemHandlerHelper.insertItemStacked(b.getItemHandlerCap((Direction)null),new ItemStack(inputs[i][j],quantities[i][j]),false).isEmpty(),"Input insertion");
    }
    var grid=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));
    if(i==0){for(int slot=0;slot<3;slot++)grid.set(slot,new ItemStack(Items.IRON_INGOT));grid.set(4,new ItemStack(Items.STICK));grid.set(7,new ItemStack(Items.STICK));}
    else if(i==1){for(int slot:new int[]{0,2,3,4,5})grid.set(slot,new ItemStack(Items.IRON_INGOT));}
    else{grid.set(0,new ItemStack(Items.WHITE_WOOL));grid.set(1,new ItemStack(Items.RED_DYE));}
    var craftingInput=net.minecraft.world.item.crafting.CraftingInput.of(3,3,grid);
    var vanilla=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,craftingInput,level).orElseThrow();
    var expected=vanilla.value().assemble(craftingInput,level.registryAccess());
    check(expected.is(products[i])&&expected.getCount()==yields[i],"Fixture recipe differs from real crafting recipe");
    var recipe=RecipeStorage.builder().withInputs(ingredients).withPrimaryOutput(new ItemStack(products[i],yields[i])).withGridSize(3).withIntermediate(Blocks.AIR).build();
    var token=IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(recipe);
    boolean taught=false;for(var craft:b.getModulesByType(ICraftingBuildingModule.class))if(craft.addRecipe(token)){taught=true;break;}
    check(taught,"Native recipe teaching rejected "+names[i]);
    check(count(i,products[i])==0,"Output must start absent");
    requester.createRequest(new com.minecolonies.api.colony.requestsystem.requestable.Stack(new ItemStack(products[i],yields[i])),false);
   }
   warehouse=setup.place("craftsmanship/storage/warehouse5.blueprint",new BlockPos(544,16,32),true);
   courierHut=setup.place("craftsmanship/storage/deliveryman5.blueprint",new BlockPos(480,16,32),true);
   courier=fixture.createCitizen();check(courier!=null&&courier.getEntity().isPresent(),"Courier spawn");
   courier.getEntity().orElseThrow().setPos(482.5,16,27.5);courierIdentity=courier.getEntity().orElseThrow().getUUID();lastCourierPos=courier.getEntity().orElseThrow().position();
   check(courierHut.getModule(BuildingModules.COURIER_WORK).assignCitizen(courier),"Courier job assignment");
   check(warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS).assignCitizen(courier),"Warehouse link");
   courier.setPaused(true);courier.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
   ((MinecoloniesAdvancedPathNavigate)courier.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   for(var item:products)check(ArcologyOperationsFixture.count(requester.getItemHandlerCap((Direction)null),new ItemStack(item))==0,"Requester starts empty");
   schedule();
  }catch(Throwable e){fail(e);}}
  int count(int i,Item item){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item));}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   courier.setSaturation(20);var entity=courier.getEntity().orElseThrow();
   check(entity.isAlive()&&entity.getUUID().equals(courierIdentity)&&courier.getWorkBuilding()==courierHut,"Courier identity/assignment changed");
   // Sample every interval, including leisure movement while awaiting crafted goods.
   check(entity.position().distanceTo(lastCourierPos)<=12,"Courier recovery teleport between 20-tick samples: "+lastCourierPos+" -> "+entity.position());lastCourierPos=entity.position();
   for(int i=0;i<3;i++){
    var c=workers.get(i);check(c.getEntity().isPresent()&&c.getEntity().orElseThrow().isAlive()&&c.getWorkBuilding()==huts.get(i),"Worker lost "+names[i]);c.setSaturation(20);
    var e=c.getEntity().orElseThrow();check(e.getUUID().equals(workerIds.get(i))&&e.position().distanceTo(workerPositions.get(i))<=12,"Crafter identity/recovery teleport "+names[i]);workerPositions.set(i,e.position());
    if(!done[i]&&count(i,products[i])>=yields[i]){
     for(var item:inputs[i])check(count(i,item)==0,"Output without full ingredient consumption "+names[i]);
     check(huts.get(i).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("items_crafted_detail;"+new ItemStack(products[i]).getHoverName().getString())>=yields[i],"Missing native craft statistic "+names[i]);
     done[i]=true;rows.add(new ProbeResult(names[i]+"_craft",ProbeResult.Outcome.PASS,"Native requested recipe produced "+yields[i]+" "+products[i]+" and consumed exact supplied ingredients; prebuilt level5 hut, taught recipe and food supplied. Product observed in worker/hut; courier delivery is checked separately."));ProbeReport.write(output,"ArcologyArtisans",rows);
    }
   }
   if(ticks%200==0){var s=new StringBuilder("tick="+ticks+" active="+fixture.colony().isActive()+"\n");for(int i=0;i<3;i++){var c=workers.get(i);s.append(names[i]).append(" pos=").append(c.getEntity().orElseThrow().position()).append(" AI=").append(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState()).append(" output=").append(count(i,products[i])).append(" stats=").append(huts.get(i).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("items_crafted_detail;"+new ItemStack(products[i]).getHoverName().getString())).append("\n");for(var r:((com.minecolonies.core.colony.buildings.AbstractBuilding)huts.get(i)).getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(r.getLongDisplayString().getString()).append(" ").append(r.getState()).append("\n");}Files.writeString(output.resolve("crafting-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   if(rows.size()==3){
    if(!courierStarted){courier.setPaused(false);courierStarted=true;}
    boolean delivered=true;
    for(int i=0;i<3;i++){
     carried[i]|=ArcologyOperationsFixture.count(courier.getInventory(),new ItemStack(products[i]))>0;
     int received=ArcologyOperationsFixture.count(requester.getItemHandlerCap((Direction)null),new ItemStack(products[i]));
     delivered&=received==yields[i]&&carried[i];
     if(received==yields[i])check(count(i,products[i])==0&&ArcologyOperationsFixture.count(courier.getInventory(),new ItemStack(products[i]))==0&&ArcologyOperationsFixture.count(warehouse.getItemHandlerCap((Direction)null),new ItemStack(products[i]))==0,"Duplicate delivered output");
    }
    if(ticks%200==0)Files.writeString(output.resolve("crafting-diagnostics.txt"),"courier pos="+entity.position()+" AI="+courier.getJob().getWorkerAI().getState()+" carried="+Arrays.toString(carried)+" delivered="+delivered+"\n",StandardOpenOption.APPEND);
    if(delivered){rows.add(new ProbeResult("courier_delivery",ProbeResult.Outcome.PASS,"Same native courier carried all three newly crafted products from workplaces to requesting Town Hall, exact quantities arrived with none left in source workers/huts or courier; native warehouse link, recovery disabled. Prepared ground-level layout and food supplied; warehouse rack throughput, upgrades and restart not certified."));ProbeReport.write(output,"ArcologyArtisans",rows);h.succeed();return;}
   }
   check(ticks<60000,"Crafting/delivery timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("craft_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyArtisans",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
