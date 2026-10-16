package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native job AI, with prebuilt level5 huts and explicitly supplied prerequisites. No work-state injection. */
@PrefixGameTestTemplate(false)
public final class ArcologyBehaviorTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200000)
 public static void priority_workers(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new ArcologyUpgradeTests.Run(h,true).start());
 }
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final OutpostProbeFixture fixture;final List<IBuilding> buildings;
  final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ICitizenData> workers=new ArrayList<>();final List<ProbeResult> rows=new ArrayList<>();
  final Set<BlockPos> originalStone=new HashSet<>();final Map<String,PlantationField> fields=new TreeMap<>();
  final Map<String,BlockPos> matured=new HashMap<>();final Map<String,Integer> baseline=new HashMap<>();final Set<String> harvested=new HashSet<>();
  net.minecraft.world.entity.Entity netherEntity;boolean completedLog;final Set<Item> expeditionItems=new HashSet<>();
  String suite="ArcologyBehavior";Runnable completed;boolean reload;long initialTrips;int initialPickDamage;
  final Map<String,Integer> initialHarvest=new HashMap<>();final Map<Item,Integer> initialHutItems=new HashMap<>();
  int ticks;boolean minerDone,netherDone,planterDone,tripStarted,portalOpened,tripInputsProvided;
  Run(GameTestHelper h,OutpostProbeFixture f,List<IBuilding> b){this.h=h;level=h.getLevel();fixture=f;buildings=b;}
  void start(){try{

   for(int i=0;i<3;i++){
    var c=reload?workers.get(i):fixture.createCitizen();check(c!=null && c.getEntity().isPresent(),"Worker spawn/reload");if(!reload)workers.add(c);c.setSaturation(20);
    var a=buildings.get(i).getPosition();if(!reload)c.getEntity().orElseThrow().setPos(a.getX()+2.5,a.getY(),a.getZ()-4.5);
    boolean assigned=reload?c.getWorkBuilding()==buildings.get(i):switch(i){case 0->buildings.get(i).getModule(BuildingModules.MINER_WORK).assignCitizen(c);case 1->buildings.get(i).getModule(BuildingModules.NETHERWORKER_WORK).assignCitizen(c);default->buildings.get(i).getModule(BuildingModules.PLANTATION_WORK).assignCitizen(c);};
    check(assigned,"Native job assignment "+i+" citizen="+c.getId()+" job="+c.getJob()+" current="+c.getWorkBuilding()+" assignees="+buildings.get(i).getAllAssignedCitizen());c.setPaused(false);
    int slot=0;for(var item:List.of(Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_SWORD,Items.SHEARS,Items.FLINT_AND_STEEL)){if(!reload)c.getInventory().setStackInSlot(slot,new ItemStack(item));slot++;}
    if(reload)ItemHandlerHelper.insertItemStacked(c.getInventory(),new ItemStack(Items.BREAD,64),false);
    else c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    ItemHandlerHelper.insertItemStacked(buildings.get(i).getItemHandlerCap((Direction)null),new ItemStack(Items.BREAD,64),false);
    ((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   }
   for(var item:List.of(Items.COBBLESTONE,Items.LADDER,Items.TORCH,Items.OAK_PLANKS,Items.OAK_FENCE)){
    ItemHandlerHelper.insertItemStacked(workers.get(0).getInventory(),new ItemStack(item,64),false);
    ItemHandlerHelper.insertItemStacked(buildings.get(0).getItemHandlerCap((Direction)null),new ItemStack(item,64),false);
   }
   var mine=(BuildingMiner)buildings.get(0);var ladder=mine.getLadderLocation();check(ladder!=null,"Shaft marker");
   for(var p:BlockPos.betweenClosed(ladder.offset(-5,-35,-5),ladder.offset(5,-3,5)))if(level.getBlockState(p).is(Blocks.STONE))originalStone.add(p.immutable());
   check(originalStone.size()>100,"Diggable terrain fixture missing");
   var nether=(BuildingNetherWorker)buildings.get(1);nether.getModule(BuildingModules.NETHERMINER_MENU).addMenuItem(new ItemStack(Items.COOKED_BEEF));
   ItemHandlerHelper.insertItemStacked(workers.get(1).getInventory(),new ItemStack(Items.COOKED_BEEF,64),false);
   for(var armor:List.of(Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS))ItemHandlerHelper.insertItemStacked(workers.get(1).getInventory(),new ItemStack(armor),false);
   var plantation=(BuildingPlantation)buildings.get(2);var module=plantation.getModule(BuildingModules.PLANTATION_FIELDS);module.setAssignManually(true);
   for(var ext:fixture.colony().getServerBuildingManager().getBuildingExtensions(e->e.getPosition().equals(plantation.getPosition())))if(ext instanceof PlantationField field){
    var tag=field.getModule().getWorkTag();fields.put(tag,field);
    var required=field.getModule().getRequiredResearchEffect();if(required!=null)fixture.colony().getResearchManager().getResearchEffects().applyEffect(new com.minecolonies.core.research.GlobalResearchEffect(required,"QA prerequisite","",1,1));
    check(module.getOwnedExtensions().contains(field)||!reload&&module.assignExtension(field),"Field assignment "+tag);
    ItemHandlerHelper.insertItemStacked(workers.get(2).getInventory(),new ItemStack(field.getModule().getItem(),32),false);
   }
   check(fields.keySet().equals(Set.of("sugar","cactus","bamboo")),"Missing legacy crop fields");
   initialTrips=nether.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("trips_completed");
   for(var field:fields.values())initialHarvest.put(field.getModule().getWorkTag(),plantation.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("item_obtained;"+field.getModule().getItem().getDescriptionId()));
   initialPickDamage=pickDamage();schedule();
  }catch(Throwable e){fail(e);}}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   // Lifecycle cases can span multiple days. Medical care/food are supplied QA
   // prerequisites; hospital operation and expedition survival are separate tests.
   if(suite.equals("ArcologyLifecycle"))for(var c:workers){
    c.setSaturation(20);
    if(c.getCitizenDiseaseHandler().isSick())c.getCitizenDiseaseHandler().cure();
    if(c.getEntity().isPresent() && c.getEntity().orElseThrow().getHealth()<6)c.getEntity().orElseThrow().setHealth(20);
   }
   for(int i=0;i<3;i++)check(fixture.colony().getCitizenManager().getCivilian(workers.get(i).getId())==workers.get(i) && workers.get(i).getWorkBuilding()==buildings.get(i),"Worker identity/assignment lost "+i);
   if(!minerDone){
    var m=(BuildingMiner)buildings.get(0);var c=workers.get(0);
    for(var r:new ArrayList<>(m.getNeededResources().values())){
     var stack=r.getItemStack();int have=count(0,stack.getItem());int needed=Math.min(64,r.getAmount()-have);
     if(needed>0)ItemHandlerHelper.insertItemStacked(m.getItemHandlerCap((Direction)null),stack.copyWithCount(needed),false);
    }
    long dug=originalStone.stream().filter(p->!level.getBlockState(p).is(Blocks.STONE)).count();
    boolean worn=false;for(int s=0;s<c.getInventory().getSlots();s++){var tool=c.getInventory().getStackInSlot(s);worn|=tool.is(Items.DIAMOND_PICKAXE)&&tool.getDamageValue()>0;}
    if(dug>=20 && worn && pickDamage()>initialPickDamage){minerDone=true;c.setPaused(true);pass("miner_shaft","Native miner reached shaft, removed "+dug+" natural stone blocks below the blueprint and wore pickaxe. Construction inputs supplied; ore-branch production is not certified by this case.");}
   }
   if(!netherDone){
    var b=(BuildingNetherWorker)buildings.get(1);var c=workers.get(1);var log=b.getModule(BuildingModules.NETHERWORKER_EXPEDITION).getLog();
    var recipe=b.getModule(BuildingModules.NETHERWORKER_CRAFT).getFirstRecipe(ItemStack::isEmpty);
    if(recipe!=null && !tripInputsProvided){for(var input:recipe.getInput())ItemHandlerHelper.insertItemStacked(c.getInventory(),new ItemStack(input.getItem(),input.getAmount()),false);tripInputsProvided=true;}
    portalOpened|=level.getBlockState(b.getPortalLocation()).is(Blocks.NETHER_PORTAL);
    tripStarted|=fixture.colony().getTravellingManager().isTravelling(c);
    check(log.getStatus()!=com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog.Status.KILLED,"Nether expedition worker died");
    if(c.getEntity().isPresent() && c.getEntity().orElseThrow()!=netherEntity){
     netherEntity=c.getEntity().orElseThrow();
     ((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
    }
    // A saved completed log is not evidence of this cycle. Capture only the
    // fresh return, before native AI processes and deposits its expedition loot.
    if(tripStarted && !completedLog && log.getStatus()==com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog.Status.COMPLETED){
     completedLog=true;
     for(var loot:log.getLoot())if(!loot.isEmpty())expeditionItems.add(loot.getItem());
     var inv=b.getItemHandlerCap((Direction)null);
     for(int slot=0;slot<inv.getSlots();slot++){var stack=inv.getStackInSlot(slot);initialHutItems.merge(stack.getItem(),stack.getCount(),Integer::sum);}
    }
    var job=(com.minecolonies.core.colony.jobs.JobNetherWorker)c.getJob();
    long trips=b.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("trips_completed");
    boolean deposited=expeditionItems.stream().anyMatch(item->ArcologyOperationsFixture.count(b.getItemHandlerCap((Direction)null),new ItemStack(item))>initialHutItems.getOrDefault(item,0));
    if(trips>initialTrips && completedLog && !job.isInNether() && c.getEntity().isPresent() && !c.getEntity().orElseThrow().isInvisible() && deposited){
     check(portalOpened && tripStarted && !expeditionItems.isEmpty() && c.getEntity().isPresent(),"Incomplete expedition evidence");
     netherDone=true;c.setPaused(true);pass("nether_expedition","Worker lit tagged portal, entered native travelling system, returned visibly to the hut, recorded a completed trip and deposited expedition loot; gear, food and recipe inputs supplied. Loot items="+expeditionItems);
    }
   }
   if(!planterDone){
    for(var entry:fields.entrySet()){
     var tag=entry.getKey();var field=entry.getValue();var crop=switch(tag){case "sugar"->Blocks.SUGAR_CANE;case "cactus"->Blocks.CACTUS;default->Blocks.BAMBOO;};
     if(!matured.containsKey(tag)){
      for(var ground:field.getWorkingPositions())if(level.getBlockState(ground.above()).is(crop)||tag.equals("bamboo")&&level.getBlockState(ground.above()).is(Blocks.BAMBOO_SAPLING)){
       var base=ground.above();
       // Explicit growth fixture only AFTER native AI planted the original crop.
       // Crop growth timing is not under test; harvesting and item collection are.
       int height=tag.equals("bamboo")?6:3;
       for(int dy=1;dy<height;dy++)check(level.getBlockState(base.above(dy)).isAir(),"Crop cannot reach native harvest height: "+tag+" "+base.above(dy));
       for(int dy=0;dy<height;dy++)level.setBlockAndUpdate(base.above(dy),crop.defaultBlockState());
       matured.put(tag,base);baseline.put(tag,cropBalance(field,crop));break;
      }
     }else if(!level.getBlockState(matured.get(tag).above(2)).is(crop) && cropBalance(field,crop)>baseline.get(tag) && buildings.get(2).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("item_obtained;"+field.getModule().getItem().getDescriptionId())>initialHarvest.get(tag))harvested.add(tag);
    }
    if(harvested.size()==3){planterDone=true;workers.get(2).setPaused(true);pass("plantation_crops",reload ? "Same saved planter resumed harvesting and collecting sugarcane/cactus/bamboo with fresh inventory/stat gains. QA matured an existing stem per crop; fresh planting and natural growth timing after reload not tested." : "Native planter planted sugarcane/cactus/bamboo from supplied seeds, then harvested and collected each crop. QA matured one worker-planted stem per crop; natural growth timing not tested.");}
   }
   if(ticks%200==0)diagnostic();
   if(minerDone && netherDone && planterDone){if(completed!=null)completed.run();else h.succeed();return;}
   check(ticks<180000,"Behavior timeout; see behavior-diagnostics.txt");schedule();
  }
  int pickDamage(){int damage=0;var inv=workers.get(0).getInventory();for(int slot=0;slot<inv.getSlots();slot++)if(inv.getStackInSlot(slot).is(Items.DIAMOND_PICKAXE))damage+=inv.getStackInSlot(slot).getDamageValue();return damage;}
  int cropBalance(PlantationField field,Block crop){return count(2,field.getModule().getItem())+(int)field.getWorkingPositions().stream().filter(p->level.getBlockState(p.above()).is(crop)||crop==Blocks.BAMBOO&&level.getBlockState(p.above()).is(Blocks.BAMBOO_SAPLING)).count();}
  int count(int i,Item item){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(buildings.get(i).getItemHandlerCap((Direction)null),new ItemStack(item));}
  void diagnostic()throws Exception{
   var s=new StringBuilder("tick="+ticks+" worldDayTime="+level.getDayTime()+" colonyDay="+fixture.colony().getDay()+" active="+fixture.colony().isActive()+" subscribers="+fixture.colony().getPackageManager().getCloseSubscribers().size()+" planted="+matured.keySet()+" harvested="+harvested+" miner="+minerDone+" nether="+netherDone+" portal="+portalOpened+" travel="+tripStarted+" inputs="+tripInputsProvided+"\n");
   for(var c:workers){s.append("worker=").append(c.getId()).append(" pos=").append(c.getEntity().map(e->e.position().toString()).orElse("travelling/unloaded")).append(" citizenAI=").append(c.getEntity().map(e->((com.minecolonies.core.entity.citizen.EntityCitizen)e).getCitizenAI().getState().toString()).orElse("away")).append(" leisure=").append(c.getLeisureTime()).append(" AI=").append(c.getJob().getWorkerAI()==null?"travelling/no active AI":c.getJob().getWorkerAI().getState()).append("\n");for(var r:((com.minecolonies.core.colony.buildings.AbstractBuilding)c.getWorkBuilding()).getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(" request=").append(r.getLongDisplayString().getString()).append(" state=").append(r.getState()).append("\n");}
   if(reload){
    var pc=workers.get(2);var nav=(MinecoloniesAdvancedPathNavigate)pc.getEntity().orElseThrow().getNavigation();
    s.append("planter destination=").append(nav.getSafeDestination()).append(" path=").append(nav.getPath()==null?"none":nav.getPath().getTarget()).append("\n");
    for(var entry:fields.entrySet()){var tag=entry.getKey();var crop=switch(tag){case "sugar"->Blocks.SUGAR_CANE;case "cactus"->Blocks.CACTUS;default->Blocks.BAMBOO;};s.append(tag).append(" field=").append(entry.getValue().getPosition()).append(" matured=").append(matured.get(tag)).append(" balance=").append(cropBalance(entry.getValue(),crop)).append(" baseline=").append(baseline.get(tag)).append("\n");}
   }
   s.append("miner dug=").append(originalStone.stream().filter(p->!level.getBlockState(p).is(Blocks.STONE)).count()).append(" pickDamage=").append(pickDamage()).append(" initialPickDamage=").append(initialPickDamage).append("\n");
   var nb=buildings.get(1);var nj=(com.minecolonies.core.colony.jobs.JobNetherWorker)workers.get(1).getJob();
   s.append("netherStatus=").append(nb.getModule(BuildingModules.NETHERWORKER_EXPEDITION).getLog().getStatus()).append(" inNether=").append(nj.isInNether()).append(" crafted=").append(nj.getCraftedResults()).append(" processed=").append(nj.getProcessedResults()).append(" trips=").append(nb.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("trips_completed")).append(" loot=").append(expeditionItems).append("\n");
   s.append("travelData=").append(((com.minecolonies.core.colony.managers.TravellingManager)fixture.colony().getTravellingManager()).serializeNBT(level.registryAccess())).append("\n");
   Files.writeString(output.resolve("behavior-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void pass(String id,String evidence)throws Exception{rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence));ProbeReport.write(output,suite,rows);}
  void fail(Throwable e){try{rows.add(new ProbeResult("behavior_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,suite,rows);}catch(Exception io){e.addSuppressed(io);}h.fail(e.toString());}
 }
}
