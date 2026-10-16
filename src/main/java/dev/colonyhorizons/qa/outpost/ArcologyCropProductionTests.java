package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.*;
import java.nio.file.*;
import java.util.*;

/** Native planter actions and navigation; explicit native chunk growth ticks, no planted/output injection. */
@PrefixGameTestTemplate(false)
public final class ArcologyCropProductionTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=7000000)
 public static void crop_production(GameTestHelper h){
  for(int x=29;x<=39;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var setup=new ArcologyCropExtensionTests.Run(h);setup.afterVerification=r->new Run(r).start();setup.start();});
 }
 static final class Run {
  final ArcologyCropExtensionTests.Run f;final List<ProbeResult> rows=new ArrayList<>();
  ICitizenData worker;UUID id;Vec3 previous;int crop,ticks,baseline,lastCount;boolean produced,visited,grew;
  final String reportName;final int end;
  java.util.function.Consumer<Run> afterProduction;
  Run(ArcologyCropExtensionTests.Run f){this.f=f;String suite=System.getProperty("colonyhorizons.foundationSuite","");boolean twisting=suite.equals("ArcologyTwistingVineProduction"),grass=suite.equals("ArcologySeagrassProduction"),pickle=suite.equals("ArcologySeaPickleProduction"),fungus=suite.equals("ArcologyFungusProduction");crop=twisting?7:grass?3:pickle?4:fungus?8:0;end=twisting?8:grass?4:pickle?5:f.names.length;reportName=twisting||grass||pickle||fungus?suite:"ArcologyCropProduction";}

  void start(){try{
   // No connected players exist in this server fixture. Explicit native chunk
   // ticks exercise normal block growth; never set crop age or generated drops.
   f.level.setWeatherParameters(7000000,0,false,false);
   f.level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,f.level.getServer());
   f.level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(64,f.level.getServer());
   worker=f.fixture.createCitizen();check(worker!=null&&worker.getEntity().isPresent(),"Planter spawn");
   var e=worker.getEntity().orElseThrow();id=e.getUUID();e.setPos(486.5,16,26.5);previous=e.position();worker.setSaturation(20);
   check(f.plantation.getModule(BuildingModules.PLANTATION_WORK).assignCitizen(worker),"Native planter assignment");
   ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   supply(worker.getInventory(),new ItemStack(Items.DIAMOND_AXE));supply(worker.getInventory(),new ItemStack(Items.SHEARS));
   supply(worker.getInventory(),new ItemStack(Items.BREAD,64));
   for(var field:f.fields){
    for(var stack:field.getModule().getRequiredItemsForOperation())supply(f.plantation.getItemHandlerCap((Direction)null),new ItemStack(stack.getItem(),32));
   }
   // Native sea-pickle module advertises bonemeal but also requests starter pickles.
   supply(f.plantation.getItemHandlerCap((Direction)null),new ItemStack(Items.SEA_PICKLE,64));
   for(int i=0;i<8;i++)supply(f.plantation.getItemHandlerCap((Direction)null),new ItemStack(Items.BONE_MEAL,64));
   next();schedule();
  }catch(Throwable e){fail(e);}}
  final Map<net.minecraft.core.BlockPos,Integer> vineHeights=new HashMap<>();boolean vineCut,vineYield;
  int vineHeight(net.minecraft.core.BlockPos base){int height=0;for(int dy=1;dy<=4;dy++){var state=f.level.getBlockState(base.above(dy));if(!state.is(Blocks.TWISTING_VINES)&&!state.is(Blocks.TWISTING_VINES_PLANT))break;height++;}return height;}
  void next(){
   ticks=0;produced=false;visited=false;grew=false;vineHeights.clear();vineCut=false;vineYield=false;
   check(f.plantation.getModule(BuildingModules.PLANTATION_FIELDS).assignExtension(f.fields.get(crop)),"Assign production field "+f.names[crop]);
   baseline=stat();lastCount=count();
  }
  void supply(IItemHandler target,ItemStack stack){check(ItemHandlerHelper.insertItemStacked(target,stack,false).isEmpty(),"Fixture supplies do not fit: "+stack);}
  // The module item is a display icon, not the complete output list for Nether vegetation.
  // Keep the focused fungus suite strict; the combined route test accepts documented roots too.
  List<Item> outputs(){if(!reportName.equals("ArcologyFungusProduction")){if(crop==8)return List.of(Items.CRIMSON_FUNGUS,Items.CRIMSON_ROOTS);if(crop==9)return List.of(Items.WARPED_FUNGUS,Items.WARPED_ROOTS,Items.NETHER_SPROUTS);}return List.of(f.fields.get(crop).getModule().getItem());}
  int stat(){int n=0;for(var item:outputs())n+=f.plantation.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("item_obtained;"+item.getDescriptionId());return n;}
  int count(){int n=0;for(var handler:List.of(worker.getInventory(),f.plantation.getItemHandlerCap((Direction)null)))for(int i=0;i<handler.getSlots();i++){var stack=handler.getStackInSlot(i);if(outputs().contains(stack.getItem()))n+=stack.getCount();}return n;}
  int harvestableHeads(){int count=0;var origin=f.origins.get(crop);for(var q:net.minecraft.core.BlockPos.betweenClosed(origin,origin.offset(9,4,9)))if(f.level.getBlockState(q).is(Blocks.KELP)&&f.level.getBlockState(q.below()).is(Blocks.KELP_PLANT))count++;return count;}
  void schedule(){f.h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=worker.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(id)&&worker.getWorkBuilding()==f.plantation,"Planter identity/assignment lost");
   check(previous.distanceTo(e.position())<=12,"Recovery teleport during crop access");previous=e.position();worker.setSaturation(20);
   var origin=f.origins.get(crop);
   // Observe native removal before accelerated fixture growth can hide it.
   if(crop==7)for(var wp:f.fields.get(crop).getWorkingPositions()){
    int height=vineHeight(wp);int before=vineHeights.getOrDefault(wp,0);
    if(before>=3&&height==1&&e.blockPosition().distSqr(wp.above())<=64)vineCut=true;
   }
   // Vanilla random block ticks are player-proximity gated. Forced-loaded QA
   // chunks alone left all eight cocoa plants at age0 after60000 ticks.
   var growthChunks=new HashSet<net.minecraft.world.level.ChunkPos>();
   for(int dx:new int[]{0,9})for(int dz:new int[]{0,9})growthChunks.add(new net.minecraft.world.level.ChunkPos(origin.offset(dx,0,dz)));
   for(var chunk:growthChunks)f.level.tickChunk(f.level.getChunk(chunk.x,chunk.z),64);
   if(crop==7)for(var wp:f.fields.get(crop).getWorkingPositions())vineHeights.put(wp,vineHeight(wp));
   var p=e.blockPosition();visited|=p.getX()>=origin.getX()&&p.getX()<origin.getX()+10&&p.getZ()>=origin.getZ()&&p.getZ()<origin.getZ()+10;
   var plant=switch(crop){case 0->Blocks.COCOA;case 1->Blocks.VINE;case 2->Blocks.KELP;case 3->Blocks.SEAGRASS;case 4->Blocks.SEA_PICKLE;case 5->Blocks.CAVE_VINES;case 6->Blocks.WEEPING_VINES;case 7->Blocks.TWISTING_VINES;case 8->Blocks.CRIMSON_ROOTS;default->Blocks.WARPED_ROOTS;};
   for(var q:net.minecraft.core.BlockPos.betweenClosed(origin,origin.offset(9,4,9)))grew|=f.level.getBlockState(q).is(plant)||(crop==8&&f.level.getBlockState(q).is(Blocks.CRIMSON_FUNGUS))||(crop==9&&f.level.getBlockState(q).is(Blocks.WARPED_FUNGUS));
   int current=count();vineYield|=crop==7&&current>lastCount;produced|=stat()>baseline&&current>lastCount;produced|=crop==7&&vineCut&&vineYield;lastCount=current;
   if(ticks%400==0){var text="crop="+f.names[crop]+" ticks="+ticks+" colonyDay="+f.fixture.colony().getDay()+" time="+f.level.getDayTime()+" pos="+e.position()+" visited="+visited+" grew="+grew+" outputIncrease="+produced+" stat="+(stat()-baseline)+" count="+current+" harvestableHeads="+harvestableHeads()+" health="+e.getHealth()+" sick="+worker.getCitizenDiseaseHandler().isSick()+" AI="+(worker.getJob().getWorkerAI()==null?"none":worker.getJob().getWorkerAI().getState())+"\n";
    text+="vineCut="+vineCut+" vineYield="+vineYield+"\n";
    text+="workerCrop="+ArcologyOperationsFixture.count(worker.getInventory(),new ItemStack(f.fields.get(crop).getModule().getItem()))+" hutCrop="+ArcologyOperationsFixture.count(f.plantation.getItemHandlerCap((Direction)null),new ItemStack(f.fields.get(crop).getModule().getItem()))+"\n";
    if(crop==7){
     var module=f.fields.get(crop).getModule();var currentField=f.plantation.getModule(BuildingModules.PLANTATION_FIELDS).getCurrentExtension();
     text+="currentField="+(currentField==null?"none":currentField.getPosition())+" expected="+f.fields.get(crop).getPosition()+"\n";
     for(var wp:f.fields.get(crop).getWorkingPositions()){text+="work="+wp;for(int dy=0;dy<=5;dy++)text+=" "+f.level.getBlockState(wp.above(dy));text+="\n";}
    }
    for(var request:f.plantation.getOpenRequestsOfCitizenOrBuilding(worker.getId(),r->true))text+=request.getLongDisplayString().getString()+" "+request.getState()+"\n";
    Files.writeString(f.output.resolve("crop-production-diagnostics.txt"),text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(produced&&visited&&grew){
    rows.add(new ProbeResult(f.names[crop]+"_production",ProbeResult.Outcome.PASS,(crop==7?"Twisting-vine evidence: mature column cut back to retained base near the sole worker="+vineCut+", worker-plus-hut inventory gain="+vineYield+". Direct-drop statistic excludes cascading entity pickups. ":"")+"Same native planter walked into the field with recovery disabled; fresh crop appeared in initially empty field, native harvest statistic delta "+(stat()-baseline)+" and worker-plus-own-hut item count increased for documented outputs "+outputs()+" without output injection. Initial seeds/tools/bonemeal, research, food and clear weather supplied; each field has a28-day upper window for native daily actions/probabilistic harvest; normal day/night cycle. Explicit native chunk ticks (64 random samples every20 test ticks) provide playerless-fixture growth; not normal-speed yield or unattended survival. ticks="+ticks));
    if(afterProduction!=null){afterProduction.accept(this);return;}
    ProbeReport.write(f.output,reportName,rows);f.plantation.getModule(BuildingModules.PLANTATION_FIELDS).freeExtension(f.fields.get(crop));
    if(++crop==end){worker.setPaused(true);f.h.succeed();return;}next();
   }
   check(ticks<672000,"Crop production timeout: "+f.names[crop]+"; see diagnostics");schedule();
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("crop_production_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,reportName,rows);}catch(Exception x){e.addSuppressed(x);}f.h.fail(e.toString());}
 }
}
