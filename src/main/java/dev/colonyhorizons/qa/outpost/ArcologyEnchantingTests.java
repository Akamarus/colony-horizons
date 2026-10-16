package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingEnchanter;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native ancient-tome production from a disclosed mature enchanter fixture. */
@PrefixGameTestTemplate(false)
public final class ArcologyEnchantingTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=45000)
 public static void ancient_tome(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));OutpostProbeFixture fixture;BuildingEnchanter hut;ICitizenData worker;UUID identity;Vec3 previous;int ticks,initialMana;boolean moved,recipeObserved;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"EnchantProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(512,16,16),5,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);hut=(BuildingEnchanter)setup.place("craftsmanship/luxury/enchanter5.blueprint",new BlockPos(512,16,0),true);
   worker=fixture.createCitizen();check(worker!=null&&worker.getEntity().isPresent(),"Worker spawn");var e=worker.getEntity().orElseThrow();e.setPos(514.5,16,-4.5);identity=e.getUUID();previous=e.position();
   // Disclosed one-time mature-worker fixture. No skill mutation occurs after start.
   worker.getCitizenSkillHandler().incrementLevel(Skill.Mana,60-worker.getCitizenSkillHandler().getLevel(Skill.Mana));initialMana=worker.getCitizenSkillHandler().getLevel(Skill.Mana);check(initialMana==60,"Mature Mana fixture");
   check(hut.getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(worker),"Enchanter assignment");worker.setSaturation(20);worker.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),new ItemStack(ModItems.ancientTome),false).isEmpty(),"Ancient tome insertion");check(count(Items.ENCHANTED_BOOK)==0,"No initial output allowed");schedule();
  }catch(Throwable e){fail(e);}}
  int count(Item item){int total=0;for(var inventory:List.of(worker.getInventory(),hut.getItemHandlerCap((Direction)null)))for(int slot=0;slot<inventory.getSlots();slot++){var stack=inventory.getStackInSlot(slot);if(stack.is(item))total+=stack.getCount();}return total;}
  List<ItemStack> books(){var result=new ArrayList<ItemStack>();for(var inventory:List.of(worker.getInventory(),hut.getItemHandlerCap((Direction)null)))for(int s=0;s<inventory.getSlots();s++){var stack=inventory.getStackInSlot(s);if(stack.is(Items.ENCHANTED_BOOK))result.add(stack.copy());}return result;}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=worker.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(identity)&&worker.getWorkBuilding()==hut,"Worker identity/assignment lost");double distance=e.position().distanceTo(previous);check(distance<=12,"Recovery teleport");moved|=distance>0.1;previous=e.position();worker.setSaturation(20);
   var module=hut.getFirstModuleOccurance(BuildingEnchanter.CraftingModule.class);
   for(var token:module.getRecipes()){
    var recipe=IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
    if(recipe!=null&&recipe.getPrimaryOutput().isEmpty()&&recipe.getCleanedInput().size()==1&&recipe.getCleanedInput().get(0).getItem()==ModItems.ancientTome&&recipe.getLootTable()!=null&&recipe.getLootTable().location().toString().equals("minecolonies:recipes/enchanter5"))recipeObserved=true;
   }
   if(ticks%1000==0||count(Items.ENCHANTED_BOOK)>0){
    var detail=new StringBuilder("inventory-detail tick="+ticks+"\n");int handlerIndex=0;
    for(var inventory:hut.getHandlers()){
     for(int slot=0;slot<inventory.getSlots();slot++){var stack=inventory.getStackInSlot(slot);if(!stack.isEmpty())detail.append(" handler=").append(handlerIndex).append(" slot=").append(slot).append(" item=").append(stack.save(level.registryAccess())).append("\n");}handlerIndex++;
    }
    var statManager=hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager();for(var key:statManager.getStatTypes())detail.append(" stat=").append(key).append(" total=").append(statManager.getStatTotal(key)).append("\n");
    for(var dropped:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(496,10,-16,528,32,16)))detail.append(" ground=").append(dropped.getItem().save(level.registryAccess())).append(" pos=").append(dropped.position()).append("\n");
    Files.writeString(output.resolve("enchanting-diagnostics.txt"),detail,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   int mana=worker.getCitizenSkillHandler().getLevel(Skill.Mana);
   if(count(Items.ENCHANTED_BOOK)>0){
    var result=books();check(count(Items.ENCHANTED_BOOK)==1&&result.size()==1&&count(ModItems.ancientTome)==0&&moved&&recipeObserved,"Missing native tome/input/recipe/navigation evidence");var book=result.get(0);var ench=EnchantmentHelper.getEnchantmentsForCrafting(book);check(!ench.isEmpty(),"Generated book has no enchantments");
    Component name=ench.size()==1?Enchantment.getFullname(ench.keySet().iterator().next(),ench.getLevel(ench.keySet().iterator().next())):ComponentUtils.formatList(ench.keySet().stream().map(key->Enchantment.getFullname(key,ench.getLevel(key))).toList(),Component.literal(", "));
    int highest=ench.entrySet().stream().mapToInt(entry->entry.getIntValue()).max().orElseThrow();
    check(hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("items_enchanted;"+name.getString())>=1,"Missing native enchantment statistic");
    String evidence="Native tome consumed; book="+name.getString()+" stored="+book.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY)+" direct="+book.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY)+" initialMana="+initialMana+" finalMana="+mana+" requiredCost="+highest;
    Files.writeString(output.resolve("enchanting-diagnostics.txt"),evidence+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    check(mana==initialMana-highest,"Native enchanted book produced but Mana cost incorrect: "+evidence);
    worker.setPaused(true);ProbeReport.write(output,"ArcologyEnchanting",List.of(new ProbeResult("enchanter_ancient_tome",ProbeResult.Outcome.PASS,evidence+". Native level5 recipe/loot, enchantment components and statistics verified. Prebuilt level5 hut, mature Mana60 worker, one ancient tome and food supplied as initial fixtures; no skill/output/AI injection after start, recovery disabled. Station/Mana progression, other levels and restart not certified.")));h.succeed();return;
   }
   if(ticks%1000==0)Files.writeString(output.resolve("enchanting-diagnostics.txt"),"tick="+ticks+" pos="+e.position()+" state="+(worker.getJob().getWorkerAI()==null?"none":worker.getJob().getWorkerAI().getState())+" tome="+count(ModItems.ancientTome)+" books="+count(Items.ENCHANTED_BOOK)+" mana="+mana+" recipeObserved="+recipeObserved+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<40000,"Native enchanting timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{ProbeReport.write(output,"ArcologyEnchanting",List.of(new ProbeResult("enchanter_ancient_tome",ProbeResult.Outcome.FAIL,e.toString())));}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
