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
public final class ArcologySmeltingTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void smelting(GameTestHelper h){
  for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new Run(h).start());
 }
 static final class Run {
  final GameTestHelper h; final ServerLevel level;
  final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ProbeResult> rows=new ArrayList<>();
  final List<IBuilding> huts=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();
  final String[] names={"glassblower","stonesmeltery"};
  final Item[] products={Items.GLASS,Items.STONE};
  final Item[][] inputs={{Items.SAND},{Items.COBBLESTONE}};
  final int[][] quantities={{1},{1}};
  final List<UUID> workerIds=new ArrayList<>();final List<net.minecraft.world.phys.Vec3> workerPositions=new ArrayList<>();
  final int[] yields={4,4};final boolean[] done=new boolean[2],lit=new boolean[2];
  OutpostProbeFixture fixture;int ticks;IBuilding requester;
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
   String[] paths={"craftsmanship/luxury/glassblower5.blueprint","craftsmanship/masonry/stonesmeltery5.blueprint"};
   BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(512,16,32)};
   for(int i=0;i<2;i++){
    var b=setup.place(paths[i],anchors[i],true);huts.add(b);
    var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Worker spawn");workers.add(c);
    c.getEntity().orElseThrow().setPos(anchors[i].getX()+2.5,16,anchors[i].getZ()-4.5);c.setSaturation(20);workerIds.add(c.getEntity().orElseThrow().getUUID());workerPositions.add(c.getEntity().orElseThrow().position());
    var module=i==0?b.getModule(BuildingModules.GLASSBLOWER_WORK):b.getModule(BuildingModules.STONESMELTER_WORK);
    check(module.assignCitizen(c),"Assignment "+names[i]);
    ((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
    c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    var ingredients=new ArrayList<ItemStorage>();
    for(int j=0;j<inputs[i].length;j++){
     ingredients.add(new ItemStorage(new ItemStack(inputs[i][j],quantities[i][j])));
     check(ItemHandlerHelper.insertItemStacked(b.getItemHandlerCap((Direction)null),new ItemStack(inputs[i][j],4),false).isEmpty(),"Input insertion");
    }
    var cookingInput=new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(inputs[i][0]));
    var vanilla=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,cookingInput,level).orElseThrow();
    var expected=vanilla.value().assemble(cookingInput,level.registryAccess());
    check(expected.is(products[i])&&expected.getCount()==1,"Fixture differs from native smelting recipe");
    check(!b.getModule(BuildingModules.FURNACE).getFurnaces().isEmpty(),"No native registered furnaces "+names[i]);
    for(var pos:b.getModule(BuildingModules.FURNACE).getFurnaces()){
     var tile=(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(pos);
     check(tile!=null&&tile.isEmpty()&&!level.getBlockState(pos).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Furnaces must start empty and cold");
    }
    check(b.getModule(BuildingModules.FURNACE).isAllowedFuel(new ItemStack(Items.COAL)),"Coal must be allowed");
    check(ItemHandlerHelper.insertItemStacked(b.getItemHandlerCap((Direction)null),new ItemStack(Items.COAL,8),false).isEmpty(),"Fuel insertion");
    var recipe=RecipeStorage.builder().withInputs(ingredients).withPrimaryOutput(new ItemStack(products[i])).withGridSize(1).withIntermediate(Blocks.FURNACE).build();
    var token=IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(recipe);
    boolean taught=false;
    if(System.getProperty("colonyhorizons.foundationSuite", "").equals("RecipeCatalogSmelting")){
     for(var craft:b.getModulesByType(com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule.class)){
      for(var entry:dev.colonyhorizons.recipes.RecipeCatalog.entries(level,craft))if(entry.teachable()&&entry.recipe().getPrimaryOutput().is(products[i])){
       dev.colonyhorizons.recipes.RecipeCatalog.learn(level,craft,entry.key(),new int[0]);taught=true;break;
      }if(taught)break;
     }
    }else for(var craft:b.getModulesByType(ICraftingBuildingModule.class))if(craft.addRecipe(token)){taught=true;break;}
    check(taught,"Native recipe teaching rejected "+names[i]);
    check(count(i,products[i])==0,"Output must start absent");
    requester.createRequest(new com.minecolonies.api.colony.requestsystem.requestable.Stack(new ItemStack(products[i],yields[i])),false);
   }
   schedule();
  }catch(Throwable e){fail(e);}}
  int count(int i,Item item){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item));}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<2;i++){
    var c=workers.get(i);check(c.getEntity().isPresent()&&c.getEntity().orElseThrow().isAlive()&&c.getWorkBuilding()==huts.get(i),"Worker lost "+names[i]);c.setSaturation(20);
    var e=c.getEntity().orElseThrow();check(e.getUUID().equals(workerIds.get(i))&&e.position().distanceTo(workerPositions.get(i))<=12,"Crafter identity/recovery teleport "+names[i]);workerPositions.set(i,e.position());
    int furnaceInputs=0,furnaceFuel=0,furnaceOutput=0;
    for(var pos:huts.get(i).getModule(BuildingModules.FURNACE).getFurnaces()){
     var tile=(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(pos);
     check(tile!=null,"Registered furnace lost");
     lit[i]|=level.getBlockState(pos).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
     if(tile.getItem(2).is(products[i]))furnaceOutput+=tile.getItem(2).getCount();
     if(tile.getItem(0).is(inputs[i][0]))furnaceInputs+=tile.getItem(0).getCount();
     if(tile.getItem(1).is(Items.COAL))furnaceFuel+=tile.getItem(1).getCount();
    }
    if(!done[i]&&count(i,products[i])==yields[i]){
     for(var item:inputs[i])check(count(i,item)==0,"Output without full ingredient consumption "+names[i]);
     check(furnaceInputs==0&&furnaceOutput==0&&lit[i]&&count(i,Items.COAL)+furnaceFuel<8,"Missing actual furnace/input/fuel consumption "+names[i]);
     done[i]=true;c.setPaused(true);rows.add(new ProbeResult(names[i]+"_smelt",ProbeResult.Outcome.PASS,"Native worker fetched hut ingredients/fuel, lit a registered blueprint furnace, consumed four inputs and coal, and collected four "+products[i]+" into worker/hut. Loaded vanilla recipe validated; prebuilt level5 hut, taught recipe and food supplied; recovery disabled. Courier delivery, other recipes/levels and restart not certified."));ProbeReport.write(output,System.getProperty("colonyhorizons.foundationSuite","ArcologySmelting"),rows);
    }
   }
   if(ticks%200==0){var s=new StringBuilder("tick="+ticks+" active="+fixture.colony().isActive()+"\n");for(int i=0;i<2;i++){var c=workers.get(i);s.append(names[i]).append(" pos=").append(c.getEntity().orElseThrow().position()).append(" AI=").append(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState()).append(" output=").append(count(i,products[i])).append(" stats=").append(huts.get(i).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("items_crafted_detail;"+new ItemStack(products[i]).getHoverName().getString())).append("\n");for(var r:((com.minecolonies.core.colony.buildings.AbstractBuilding)huts.get(i)).getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(r.getLongDisplayString().getString()).append(" ").append(r.getState()).append("\n");}Files.writeString(output.resolve("smelting-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   if(rows.size()==2){h.succeed();return;}
   check(ticks<60000,"Smelting timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("smelt_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,System.getProperty("colonyhorizons.foundationSuite","ArcologySmelting"),rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
