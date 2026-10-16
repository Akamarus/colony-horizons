package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.api.crafting.*;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.*;
import java.nio.file.*;
import java.util.*;

/** Native MineColonies brewing and enchanter station visits; no Ars integration. */
@PrefixGameTestTemplate(false)
public final class ArcologyMagicTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void magic(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<IBuilding> huts=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();final List<UUID> identities=new ArrayList<>();final List<Vec3> previous=new ArrayList<>();final List<ProbeResult> rows=new ArrayList<>();
  final ItemStack input=PotionContents.createItemStack(Items.POTION,Potions.AWKWARD),product=PotionContents.createItemStack(Items.POTION,Potions.SWIFTNESS);
  OutpostProbeFixture fixture;BuildingAlchemist alchemist;BuildingEnchanter enchanter;int ticks,initialMana;boolean brewed,visited,processing,reachedStation;final boolean[] moved=new boolean[2];
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"MagicProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(528,16,16),5,level,false);
   var requester=setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   alchemist=(BuildingAlchemist)setup.place("craftsmanship/luxury/alchemist5.blueprint",new BlockPos(512,16,0),true);
   enchanter=(BuildingEnchanter)setup.place("craftsmanship/luxury/enchanter5.blueprint",new BlockPos(544,16,0),true);huts.add(alchemist);huts.add(enchanter);
   for(int i=0;i<2;i++){
    var b=huts.get(i);var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Worker spawn");workers.add(c);var e=c.getEntity().orElseThrow();e.setPos(b.getPosition().getX()+2.5,16,-4.5);identities.add(e.getUUID());previous.add(e.position());
    check(b.getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(c),"Assignment");c.setSaturation(20);c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   }
   check(!alchemist.getAllBrewingStandPositions().isEmpty(),"No registered blueprint brewing stands");
   for(var pos:alchemist.getAllBrewingStandPositions()){var stand=(BrewingStandBlockEntity)level.getBlockEntity(pos);check(stand!=null&&stand.isEmpty()&&stand.saveWithoutMetadata(level.registryAccess()).getShort("BrewTime")==0,"Stands must start empty/idle");}
   check(ItemStack.isSameItemSameComponents(level.potionBrewing().mix(new ItemStack(Items.SUGAR),input.copy()),product),"Loaded native potion recipe mismatch");
   for(int bottle=0;bottle<3;bottle++)insert(alchemist,input.copy());insert(alchemist,new ItemStack(Items.SUGAR));insert(alchemist,new ItemStack(Items.BLAZE_POWDER,8));
   var recipe=RecipeStorage.builder().withInputs(List.of(new ItemStorage(input.copy(),3,false),new ItemStorage(Items.SUGAR))).withPrimaryOutput(product.copyWithCount(3)).withGridSize(1).withIntermediate(Blocks.BREWING_STAND).build();
   var token=IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(recipe);check(alchemist.getFirstModuleOccurance(BuildingAlchemist.BrewingModule.class).addRecipe(token),"Native brewing recipe rejected");
   check(count(0,product)==0,"No initial potion output");requester.createRequest(new com.minecolonies.api.colony.requestsystem.requestable.Stack(product.copyWithCount(3)),false);
   enchanter.getModule(BuildingModules.ENCHANTER_STATIONS).addWorker(alchemist.getPosition());insert(enchanter,new ItemStack(Items.BOOK));initialMana=workers.get(1).getCitizenSkillHandler().getLevel(Skill.Mana);check(initialMana<50,"Fresh enchanter must require Mana");
   schedule();
  }catch(Throwable e){fail(e);}}
  void insert(IBuilding b,ItemStack stack){check(ItemHandlerHelper.insertItemStacked(b.getItemHandlerCap((Direction)null),stack,false).isEmpty(),"Fixture insertion");}
  int exact(IItemHandler inventory,ItemStack stack){int n=0;for(int slot=0;slot<inventory.getSlots();slot++){var found=inventory.getStackInSlot(slot);if(ItemStack.isSameItemSameComponents(found,stack))n+=found.getCount();}return n;}
  int count(int i,ItemStack stack){return exact(workers.get(i).getInventory(),stack)+exact(huts.get(i).getItemHandlerCap((Direction)null),stack);}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<2;i++){var c=workers.get(i);var e=c.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(identities.get(i))&&c.getWorkBuilding()==huts.get(i),"Worker identity/assignment lost");double distance=e.position().distanceTo(previous.get(i));check(distance<=12,"Recovery teleport");moved[i]|=distance>0.1;previous.set(i,e.position());c.setSaturation(20);}
   int standFuel=0,standInput=0,standSugar=0,standOutput=0;
   for(var pos:alchemist.getAllBrewingStandPositions()){
    var stand=(BrewingStandBlockEntity)level.getBlockEntity(pos);check(stand!=null,"Registered stand lost");processing|=stand.saveWithoutMetadata(level.registryAccess()).getShort("BrewTime")>0;
    for(int slot=0;slot<5;slot++){var stack=stand.getItem(slot);if(stack.is(Items.BLAZE_POWDER))standFuel+=stack.getCount();if(stack.is(Items.SUGAR))standSugar+=stack.getCount();if(ItemStack.isSameItemSameComponents(stack,input))standInput+=stack.getCount();if(ItemStack.isSameItemSameComponents(stack,product))standOutput+=stack.getCount();}
   }
   if(!brewed&&count(0,product)==3){check(moved[0]&&processing&&count(0,input)+standInput==0&&count(0,new ItemStack(Items.SUGAR))+standSugar==0&&count(0,new ItemStack(Items.BLAZE_POWDER))+standFuel<8&&standOutput==0,"Missing native brewing process/input/fuel evidence");check(alchemist.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("items_brewed;"+product.getDescriptionId())>=3,"Missing native brewed statistic");brewed=true;workers.get(0).setPaused(true);rows.add(new ProbeResult("alchemist_brew_swiftness",ProbeResult.Outcome.PASS,"Native alchemist fetched three supplied awkward potions, sugar and blaze powder; used a registered blueprint brewing stand; active native brewing observed; ingredient/fuel consumed; three swiftness potions collected and native brewing statistic recorded. Loaded potion recipe validated, recipe taught as configuration; prebuilt level5 hut, food and inputs supplied. Recovery disabled. Other potions, harvesting, other levels, delivery and restart excluded."));ProbeReport.write(output,"ArcologyMagic",rows);}
   var wizard=workers.get(1);reachedStation|=wizard.getEntity().orElseThrow().position().distanceTo(Vec3.atCenterOf(alchemist.getPosition()))<6;
   if(!visited&&enchanter.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("citizens_visited")>0){check(moved[1]&&reachedStation&&count(1,new ItemStack(Items.BOOK))==0&&wizard.getCitizenSkillHandler().getLevel(Skill.Mana)>initialMana,"Missing native station visit/book/Mana evidence");visited=true;wizard.setPaused(true);rows.add(new ProbeResult("enchanter_station_visit",ProbeResult.Outcome.PASS,"Native enchanter fetched supplied book, navigated to configured alchemist station, consumed book and gained Mana with native citizens_visited statistic. Prebuilt level5 huts, target worker, station selection, book and food supplied; no Mana or AI-state injection; identity/movement checked with recovery disabled. This verifies native visit/Mana acquisition, not donor XP transfer, enchanted-book production, other levels or restart."));ProbeReport.write(output,"ArcologyMagic",rows);}
   if(ticks%1000==0){var s=new StringBuilder("tick="+ticks+" processing="+processing+" input="+count(0,input)+" sugar="+count(0,new ItemStack(Items.SUGAR))+" fuel="+count(0,new ItemStack(Items.BLAZE_POWDER))+" product="+count(0,product)+" mana="+wizard.getCitizenSkillHandler().getLevel(Skill.Mana)+" books="+count(1,new ItemStack(Items.BOOK))+" reachedStation="+reachedStation+"\n");for(int i=0;i<2;i++){var c=workers.get(i);s.append(i).append(" pos=").append(c.getEntity().orElseThrow().position()).append(" state=").append(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState()).append("\n");}Files.writeString(output.resolve("magic-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   if(ticks%1000==0){
    var detail=new StringBuilder("stand-detail tick="+ticks+"\n");
    for(var pos:alchemist.getAllBrewingStandPositions()){
     var stand=(BrewingStandBlockEntity)level.getBlockEntity(pos);detail.append(" stand ").append(pos).append(" native=").append(stand.saveWithoutMetadata(level.registryAccess())).append("\n");
    }
    for(var request:alchemist.getOpenRequestsOfCitizenOrBuilding(workers.get(0).getId(),r->true))detail.append(" request=").append(request.getLongDisplayString().getString()).append(" state=").append(request.getState()).append("\n");
    Files.writeString(output.resolve("magic-diagnostics.txt"),detail,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(brewed&&visited){h.succeed();return;}check(ticks<60000,"Magic timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("magic_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyMagic",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
