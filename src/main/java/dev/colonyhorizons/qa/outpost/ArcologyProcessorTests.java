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

/** Native processor AI; resources supplied to huts, no output or AI injection. */
@PrefixGameTestTemplate(false)
public final class ArcologyProcessorTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void processors(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<IBuilding> huts=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();final List<UUID> ids=new ArrayList<>();final List<net.minecraft.world.phys.Vec3> positions=new ArrayList<>();final List<ProbeResult> rows=new ArrayList<>();
  final String[] names={"crusher","concretemixer","smeltery"};final Item[] inputs={Items.COBBLESTONE,Items.WHITE_CONCRETE_POWDER,Items.RAW_IRON},products={Items.GRAVEL,Items.WHITE_CONCRETE,Items.IRON_INGOT};final int[] supplied={8,4,4};final boolean[] requested=new boolean[3],done=new boolean[3];
  OutpostProbeFixture fixture;IBuilding requester;int ticks;boolean furnaceLit;int initialPickDamage;
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

   String[] paths={"craftsmanship/masonry/crusher5.blueprint","craftsmanship/luxury/concretemixer5.blueprint","craftsmanship/metallurgy/smeltery5.blueprint"};
   BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(512,16,32)};
   for(int i=0;i<3;i++){
    var b=setup.place(paths[i],anchors[i],true);huts.add(b);var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Spawn");workers.add(c);c.getEntity().orElseThrow().setPos(anchors[i].getX()+2.5,16,anchors[i].getZ()-4.5);ids.add(c.getEntity().orElseThrow().getUUID());positions.add(c.getEntity().orElseThrow().position());
    boolean assigned=switch(i){case 0->b.getModule(BuildingModules.CRUSHER_WORK).assignCitizen(c);case 1->b.getModule(BuildingModules.CONCRETEMIXER_WORK).assignCitizen(c);default->b.getModule(BuildingModules.SMELTER_WORK).assignCitizen(c);};check(assigned,"Assignment "+names[i]);
    ((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());c.setSaturation(20);c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));c.getInventory().setStackInSlot(0,new ItemStack(Items.DIAMOND_PICKAXE));
    check(ItemHandlerHelper.insertItemStacked(b.getItemHandlerCap((Direction)null),new ItemStack(inputs[i],supplied[i]),false).isEmpty(),"Input insertion");check(count(i,products[i])==0,"Output initially present");
   }
   var furnaceModule=huts.get(2).getModule(BuildingModules.FURNACE);check(!furnaceModule.getFurnaces().isEmpty(),"Smelter furnace registration");
   for(var pos:furnaceModule.getFurnaces()){var tile=(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(pos);check(tile!=null&&tile.isEmpty()&&!level.getBlockState(pos).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Furnaces must start cold/empty");}
   ItemHandlerHelper.insertItemStacked(huts.get(2).getItemHandlerCap((Direction)null),new ItemStack(Items.COAL,8),false);
   schedule();
  }catch(Throwable e){fail(e);}}
  int count(int i,Item item){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item));}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<3;i++){
    var c=workers.get(i);var e=c.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(ids.get(i))&&c.getWorkBuilding()==huts.get(i),"Worker identity/assignment");check(e.position().distanceTo(positions.get(i))<=12,"Recovery teleport "+names[i]);positions.set(i,e.position());c.setSaturation(20);
    if(i<2&&!requested[i]){final Item product=products[i],input=inputs[i];for(var module:huts.get(i).getModulesByType(ICraftingBuildingModule.class)){var recipe=module.getFirstRecipe(stack->stack.is(product));if(recipe!=null&&recipe.getCleanedInput().size()==1&&recipe.getCleanedInput().get(0).getItem()==input){check(recipe.getCleanedInput().get(0).getAmount()==(i==0?2:1),"Unexpected native recipe ratio");requester.createRequest(new com.minecolonies.api.colony.requestsystem.requestable.Stack(new ItemStack(product,4)),false);requested[i]=true;break;}}check(requested[i]||ticks<2000,"Native recipe unavailable "+names[i]);}
    if(i==2)for(var pos:huts.get(i).getModule(BuildingModules.FURNACE).getFurnaces())furnaceLit|=level.getBlockState(pos).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
    if(!done[i]&&count(i,products[i])>=4){
     check(count(i,inputs[i])==0,"Inputs not consumed "+names[i]);
     if(i==1){int wear=0;for(var inventory:List.of(c.getInventory(),huts.get(i).getItemHandlerCap((Direction)null)))for(int slot=0;slot<inventory.getSlots();slot++){var tool=inventory.getStackInSlot(slot);if(tool.is(Items.DIAMOND_PICKAXE))wear+=tool.getDamageValue();}check(wear>initialPickDamage,"Concrete tool wear missing");check(huts.get(i).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("items_crafted_detail;"+Items.WHITE_CONCRETE.getDescriptionId())>=4,"Concrete harvest statistics missing");}
     if(i==2){int coal=count(i,Items.COAL);for(var pos:huts.get(i).getModule(BuildingModules.FURNACE).getFurnaces()){var tile=(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(pos);check(tile.getItem(0).isEmpty()&&tile.getItem(2).isEmpty(),"Furnace leftovers");if(tile.getItem(1).is(Items.COAL))coal+=tile.getItem(1).getCount();}check(furnaceLit&&coal<8,"Furnace/fuel evidence missing");}
     done[i]=true;c.setPaused(true);rows.add(new ProbeResult(names[i]+"_production",ProbeResult.Outcome.PASS,"Native "+names[i]+" consumed hut-supplied inputs and produced "+count(i,products[i])+" "+products[i]+". Native recipe initialization/request retained for crusher/concrete; concrete pickaxe wear and smelter cold-start/fuel/collection asserted. Prebuilt level5, tools/food/materials supplied; other recipes/levels, delivery and restart untested."));ProbeReport.write(output,"ArcologyProcessors",rows);
    }
   }
   if(ticks%400==0){
    var concrete=(com.minecolonies.core.colony.buildings.workerbuildings.BuildingConcreteMixer)huts.get(1);
    var nav=(MinecoloniesAdvancedPathNavigate)workers.get(1).getEntity().orElseThrow().getNavigation();
    Files.writeString(output.resolve("concrete-targets.txt"),"tick="+ticks+" place="+concrete.getBlockToPlace()+" mine="+concrete.getBlockToMine()+" registered="+concrete.getMaxConcretePlaced()+" target="+nav.getSafeDestination()+" path="+(nav.getPath()==null?"none":nav.getPath().getTarget())+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    var s=new StringBuilder("ticks="+ticks+"\n");for(int i=0;i<3;i++){var c=workers.get(i);s.append(names[i]).append(" pos=").append(c.getEntity().orElseThrow().position()).append(" AI=").append(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState()).append(" inputs=").append(count(i,inputs[i])).append(" output=").append(count(i,products[i])).append("\n");for(var r:((com.minecolonies.core.colony.buildings.AbstractBuilding)huts.get(i)).getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(r.getLongDisplayString().getString()).append("\n");}Files.writeString(output.resolve("processor-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   if(rows.size()==3){h.succeed();return;}check(ticks<60000,"Processor timeout");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("processor_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyProcessors",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
