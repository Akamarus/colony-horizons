package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native breeding and animal products in unchanged prebuilt husbandry blueprints. */
@PrefixGameTestTemplate(false)
public final class ArcologyAnimalTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void husbandry(GameTestHelper h){
  for(int x=28;x<=37;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new Run(h).start());
 }
 static final class Run {
  final GameTestHelper h; final ServerLevel level;final boolean containment;final int roleCount;
  final Set<UUID> containmentBirds=new HashSet<>(),verifiedButchered=new HashSet<>();final Map<UUID,Integer> outsideTicks=new HashMap<>();final Map<UUID,Vec3> containmentPrevious=new HashMap<>();
  final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final String[] names={"chickenherder","cowboy","shepherd","rabbithutch","swineherder"};
  final EntityType<?>[] types={EntityType.CHICKEN,EntityType.COW,EntityType.SHEEP,EntityType.RABBIT,EntityType.PIG};
  final List<IBuilding> huts=new ArrayList<>(); final List<ICitizenData> workers=new ArrayList<>();
  final List<UUID> identities=new ArrayList<>(); final List<Vec3> previous=new ArrayList<>();
  final List<Set<UUID>> parents=new ArrayList<>(); final List<Item> feed=new ArrayList<>();
  final List<ProbeResult> rows=new ArrayList<>(); final Set<String> passed=new HashSet<>();
  final Map<UUID,Vec3> chickenPrevious=new HashMap<>();
  final boolean[] moved=new boolean[5]; OutpostProbeFixture fixture;int ticks;boolean observedSheared;
  Run(GameTestHelper h){this(h,false);}
  Run(GameTestHelper h,boolean containment){this.h=h;level=h.getLevel();this.containment=containment;roleCount=containment?1:5;}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
   level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=596;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"AnimalProbe");level.getServer().getProfileCache().add(profile);
   fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;
   var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(528,16,16),5,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(512,16,32),new BlockPos(544,16,32),new BlockPos(576,16,32)};
   for(int i=0;i<roleCount;i++){
    String path="agriculture/husbandry/"+names[i]+"5.blueprint";
    var b=setup.place(path,anchors[i],true);huts.add(b);
    check(b.getSetting(AbstractBuilding.BREEDING).getValue(),"Breeding must be enabled "+names[i]);
    if(i==1){var cow=(BuildingCowboy)b;cow.getSetting(BuildingCowboy.MILK_ITEM).set(Items.MILK_BUCKET.getDescriptionId());cow.getSetting(BuildingCowboy.MILKING_AMOUNT).setValue(1);insert(b,Items.BUCKET,1);}
    if(i==2){check(b.getSetting(BuildingShepherd.SHEARING).getValue(),"Shearing disabled");if(b.getSetting(BuildingShepherd.DYEING).getValue())b.getSetting(BuildingShepherd.DYEING).trigger();insert(b,Items.SHEARS,1);}
    var herd=b.getModulesByType(AnimalHerdingModule.class).get(0);var food=herd.getBreedingItems().get(0).getItem();feed.add(food);insert(b,food,64);insert(b,Items.IRON_AXE,1);
    // Only choose grass actually present in the blueprint, not the external test foundation.
    var bp=setup.blueprint(path);var origin=anchors[i].subtract(bp.getPrimaryBlockOffset());BlockPos pen=null;
    for(var info:bp.getBlockInfoAsList()){
     var floor=origin.offset(info.getPos());var p=floor.above();
     if(bp.getBlockState(info.getPos()).is(Blocks.GRASS_BLOCK)&&level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir()){
      if(pen==null||p.distSqr(anchors[i])<pen.distSqr(anchors[i]))pen=p;
     }
    }
    check(pen!=null,"No clear native blueprint grass for animals "+names[i]);
    Set<UUID> initial=new HashSet<>();parents.add(initial);
    for(int n=0;n<2;n++){
     var animal=(Animal)types[i].create(level);check(animal!=null,"Animal creation");animal.setPos(pen.getX()+0.35+n*0.3,pen.getY(),pen.getZ()+0.5);animal.setPersistenceRequired();
     if(animal instanceof Sheep sheep)sheep.setColor(DyeColor.WHITE);
     check(!animal.isBaby()&&!animal.isInLove(),"Fixture must contain ordinary adult animals");check(level.addFreshEntity(animal),"Animal spawn");initial.add(animal.getUUID());
    }
    check(WorldUtil.getEntitiesWithinBuilding(level,Animal.class,b,a->initial.contains(a.getUUID())).size()==2,"Animals outside native building search "+names[i]);
    var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Worker spawn");workers.add(c);
    c.getEntity().orElseThrow().setPos(anchors[i].getX()+2.5,16,anchors[i].getZ()-4.5);c.setSaturation(20);identities.add(c.getEntity().orElseThrow().getUUID());previous.add(c.getEntity().orElseThrow().position());
    check(b.getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(c),"Native assignment "+names[i]);
    ((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
    c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    check(count(i,Items.EGG)==0&&count(i,Items.MILK_BUCKET)==0&&count(i,Items.WHITE_WOOL)==0,"No fixture products allowed");
    Files.writeString(output.resolve("animal-diagnostics.txt"),names[i]+" anchor="+anchors[i]+" pen="+pen+" feed="+food+" corners="+b.getCorners()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   schedule();
  }catch(Throwable e){fail(e);}}
  void insert(IBuilding b,Item item,int n){check(ItemHandlerHelper.insertItemStacked(b.getItemHandlerCap((Direction)null),new ItemStack(item,n),false).isEmpty(),"Fixture insertion "+item);}
  int count(int i,Item item){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item));}
  boolean inPen(Vec3 p){return p.x>=511&&p.x<514&&p.z>=3&&p.z<7;}
  boolean inHut(Vec3 p){return p.x>=510&&p.x<514&&p.z>=-2&&p.z<7;}
  boolean shearsWorn(int i){
   for(var inventory:List.of(workers.get(i).getInventory(),huts.get(i).getItemHandlerCap((Direction)null)))
    for(int slot=0;slot<inventory.getSlots();slot++){var stack=inventory.getStackInSlot(slot);if(stack.is(Items.SHEARS)&&stack.getDamageValue()>0)return true;}
   return false;
  }
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void pass(String id,String detail)throws Exception{if(containment){passed.add(id);return;}if(passed.add(id)){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,detail+" Prebuilt level5 blueprint, two ordinary adult animals, feed, tools, worker food and daytime supplied as fixtures. Worker identity and movement checked; recovery disabled. Other levels, slaughter, courier delivery and restart not certified."));ProbeReport.write(output,"ArcologyAnimals",rows);}}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<roleCount;i++){
    final int index=i;var c=workers.get(i);var e=c.getEntity().orElseThrow();var b=huts.get(i);
    check(e.isAlive()&&e.getUUID().equals(identities.get(i))&&c.getWorkBuilding()==b,"Worker lost "+names[i]);c.setSaturation(20);
    double distance=e.position().distanceTo(previous.get(i));check(distance<=12,"Recovery teleport "+names[i]);moved[i]|=distance>0.1;previous.set(i,e.position());
    var stats=b.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager();
    var herd=b.getModulesByType(AnimalHerdingModule.class).get(0);var animals=WorldUtil.getEntitiesWithinBuilding(level,Animal.class,b,herd::isCompatible);
    if(!passed.contains(names[i]+"_breed")&&animals.stream().anyMatch(a->a.isBaby()&&!parents.get(index).contains(a.getUUID()))){
     check(moved[i]&&count(i,feed.get(i))<=62&&stats.getStatTotal("breeding_attempts")>=2&&stats.getStatTotal("item_used;"+feed.get(i).getDescriptionId())>=2,"Offspring without native feed/movement/stat evidence "+names[i]);
     pass(names[i]+"_breed","Native worker fetched breeding feed, approached parents, consumed feed and produced a new offspring; native breeding and item-use statistics verified.");
    }
    if(i==0&&!passed.contains("chickenherder_eggs")&&count(i,Items.EGG)>0){check(moved[i],"No worker navigation for egg pickup");pass("chickenherder_eggs","Native worker collected naturally laid eggs into worker/hut inventory; no egg entity/item or laying timer injected.");}
    if(i==1&&!passed.contains("cowboy_milk")&&count(i,Items.MILK_BUCKET)>0){check(moved[i]&&count(i,Items.BUCKET)==0&&stats.getStatTotal("milking_attempts")>=1,"Missing milk input/stat evidence");pass("cowboy_milk","Configured bucket milking: native worker consumed the supplied empty bucket near an adult cow, produced milk and recorded a native milking attempt.");}
    if(i==2){
     observedSheared|=animals.stream().anyMatch(a->a instanceof Sheep sheep&&sheep.isSheared());
     for(var id:parents.get(i)){var parent=level.getEntity(id);if(parent instanceof Sheep sheep)observedSheared|=sheep.isSheared();}
     if(!passed.contains("shepherd_shear")&&count(i,Items.WHITE_WOOL)>0&&moved[i]&&observedSheared&&shearsWorn(i)&&stats.getStatTotal("item_obtained;"+Items.WHITE_WOOL.getDescriptionId())>0)
      pass("shepherd_shear","Configured shearing with dyeing disabled: native worker fetched shears, approached sheep, changed native sheared state (latched observation), wore supplied shears and collected white wool with native item-obtained statistics.");
    }
    if(ticks%1000==0)Files.writeString(output.resolve("animal-diagnostics.txt"),"tick="+ticks+" role="+names[i]+" pos="+e.position()+" state="+(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState())+" feed="+count(i,feed.get(i))+" animals="+animals.size()+" breeds="+stats.getStatTotal("breeding_attempts")+" eggs="+count(i,Items.EGG)+" milk="+count(i,Items.MILK_BUCKET)+" wool="+count(i,Items.WHITE_WOOL)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(containment){containmentTick();return;}
   var chickenWorker=workers.get(0).getEntity().orElseThrow();
   var traceArea=new net.minecraft.world.phys.AABB(494,14,-18,530,22,23);
   for(var bird:level.getEntitiesOfClass(Chicken.class,traceArea)){
    var prior=chickenPrevious.put(bird.getUUID(),bird.position());
    if(prior!=null&&(inPen(prior)!=inPen(bird.position())||inHut(prior)!=inHut(bird.position())))
     Files.writeString(output.resolve("animal-diagnostics.txt"),"chicken-crossing tick="+ticks+" uuid="+bird.getUUID()+" previous="+prior+" current="+bird.position()+" pen="+inPen(bird.position())+" hut="+inHut(bird.position())+" gate="+level.getBlockState(new BlockPos(512,16,2))+" worker="+chickenWorker.position()+" held="+chickenWorker.getMainHandItem()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(ticks%1000==0){
    var chickenHut=huts.get(0);var worker=workers.get(0).getEntity().orElseThrow();
    var corners=chickenHut.getCorners();
    var area=new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(corners.getA()),Vec3.atLowerCornerOf(corners.getB())).inflate(16);
    var detail=new StringBuilder("chicken-detail tick="+ticks+" corners="+corners.getA()+" to "+corners.getB()+" path="+worker.getNavigation().getPath()+" navigationDone="+worker.getNavigation().isDone()+"\n");
    for(var chicken:level.getEntitiesOfClass(Chicken.class,area))detail.append(" chicken uuid=").append(chicken.getUUID()).append(" pos=").append(chicken.position()).append(" age=").append(chicken.getAge()).append(" eggTime=").append(chicken.eggTime).append(" jockey=").append(chicken.isChickenJockey()).append(" alive=").append(chicken.isAlive()).append("\n");
    for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area))detail.append(" ground item=").append(item.getItem()).append(" pos=").append(item.position()).append("\n");
    for(int x=510;x<=514;x++)for(int z=1;z<=4;z++){
     var pos=new BlockPos(x,16,z);detail.append(" block ").append(pos).append(" = ").append(level.getBlockState(pos)).append("\n");
    }
    var path=worker.getNavigation().getPath();
    if(path!=null){detail.append(" path next=").append(path.getNextNodeIndex()).append(" nodes=").append(path.getNodeCount()).append(" target=").append(path.getTarget()).append("\n");for(int n=0;n<path.getNodeCount();n++)detail.append(" node ").append(n).append(" ").append(path.getNode(n)).append("\n");}
    Files.writeString(output.resolve("animal-diagnostics.txt"),detail,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(passed.size()==8){for(var c:workers)c.setPaused(true);h.succeed();return;}
   check(ticks<60000,"Animal timeout; passed="+passed+"; see diagnostics");schedule();
  }
  void containmentTick()throws Exception{
   var hut=huts.get(0);var worker=workers.get(0).getEntity().orElseThrow();var gate=new BlockPos(512,16,2);
   var loadedArea=new net.minecraft.world.phys.AABB(448,-64,-32,608,128,80);
   containmentBirds.addAll(parents.get(0));
   for(var bird:level.getEntitiesOfClass(Chicken.class,loadedArea))containmentBirds.add(bird.getUUID());
   var trace=new StringBuilder("tick="+ticks+" barrier="+level.getBlockState(gate)+" crossingTop="+level.getBlockState(gate.above())+" worker="+worker.position()+" held="+worker.getMainHandItem()+" workerAI="+(workers.get(0).getJob().getWorkerAI()==null?"none":workers.get(0).getJob().getWorkerAI().getState())+"\n");
   String failure=null;int livingAdults=0;
   for(var id:containmentBirds){
    if(verifiedButchered.contains(id))continue;
    var entity=level.getEntity(id);
    if(!(entity instanceof Chicken bird)){trace.append("missing uuid=").append(id).append(" previous=").append(containmentPrevious.get(id)).append('\n');failure="Tracked chicken disappeared without verified native slaughter: "+id;continue;}
    if(!bird.isAlive()){
     if(bird.getLastHurtByMob()==worker&&bird.getLastDamageSource()!=null&&bird.getLastDamageSource().getEntity()==worker&&hut.isInBuilding(bird.blockPosition())&&bird.getZ()>=2.5){verifiedButchered.add(id);trace.append("verified-native-slaughter uuid=").append(id).append(" pos=").append(bird.position()).append(" worker=").append(worker.getUUID()).append('\n');continue;}
     trace.append("unattributed-or-outside-pen-death uuid=").append(id).append(" pos=").append(bird.position()).append('\n');failure="Tracked chicken died without inside-pen worker attribution: "+id;continue;
    }
    if(!bird.isBaby())livingAdults++;
    var pos=bird.position();var prior=containmentPrevious.put(id,pos);boolean inside=hut.isInBuilding(bird.blockPosition());
    int outside=inside?0:outsideTicks.getOrDefault(id,0)+20;outsideTicks.put(id,outside);
    boolean planeCrossed=false;double crossingX=Double.NaN;
    if(prior!=null&&(prior.z-2.5)*(pos.z-2.5)<0){crossingX=prior.x+(pos.x-prior.x)*(2.5-prior.z)/(pos.z-prior.z);planeCrossed=crossingX>=512&&crossingX<=513;}
    boolean hutCrossed=prior!=null&&hut.isInBuilding(BlockPos.containing(prior))!=inside;
    trace.append("bird uuid=").append(id).append(" prior=").append(prior).append(" current=").append(pos).append(" age=").append(bird.getAge()).append(" hut=").append(inside).append(" outsideTicks=").append(outside).append(" gatePlaneCrossed=").append(planeCrossed).append(" crossingX=").append(crossingX).append(" hutBoundaryCrossed=").append(hutCrossed).append('\n');
    if(outside>200)failure="Chicken outside native hut bounds for "+outside+" ticks: "+id+" at "+pos;
    if(!loadedArea.contains(pos)||!level.hasChunkAt(bird.blockPosition()))failure="Chicken left loaded tracking bounds: "+id+" at "+pos;
   }
   if(livingAdults<2)failure="Fewer than two living adult chickens remain: "+livingAdults;
   Files.writeString(output.resolve("chicken-containment-trace.txt"),trace,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(failure==null,failure);
   if(ticks>=24000){
    check(passed.contains("chickenherder_breed")&&passed.contains("chickenherder_eggs"),"Containment soak lacked native breeding/egg collection: "+passed);
    ProbeReport.write(output,"ArcologyChickenContainment",List.of(new ProbeResult("chicken_containment",ProbeResult.Outcome.PASS,"Generated level5 chickenherder blueprint observed for 24000 ticks with two supplied ordinary adult chickens and native offspring, breeding and egg collection. Tracked chickens remained loaded with at least two living adults; only explicitly observed worker-attributed deaths inside the pen were exempted as native slaughter. None stayed outside native hut bounds for more than 200 ticks. Per-20-tick trajectories, actual gate-plane crossings, native hut-boundary transitions, barrier and crossing-top states and worker held items recorded. No runtime-added barriers, animal/worker AI changes, movement correction or egg-timer edits; worker recovery disabled. This bounded soak does not establish permanent containment or certify other levels.")));h.succeed();return;
   }
   schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult(containment?"chicken_containment":"animal_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,containment?"ArcologyChickenContainment":"ArcologyAnimals",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
