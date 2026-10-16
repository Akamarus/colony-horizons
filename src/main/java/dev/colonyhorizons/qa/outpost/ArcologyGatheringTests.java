package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native gathering workers. Environmental fixtures never supply harvested output or worker AI states. */
@PrefixGameTestTemplate(false)
public final class ArcologyGatheringTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=105000)
 public static void gathering(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h; final ServerLevel level;
  final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final String[] names={"farmer","beekeeper","fisherman","florist"};
  final List<IBuilding> huts=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();
  final List<UUID> ids=new ArrayList<>();final List<net.minecraft.world.phys.Vec3> previous=new ArrayList<>();
  final List<ProbeResult> rows=new ArrayList<>();final boolean[] done=new boolean[4];
  final Set<BlockPos> matured=new HashSet<>();final Set<UUID> adultBees=new HashSet<>();
  OutpostProbeFixture fixture;int ticks;boolean composted,flowerGrown,hookObserved;BlockPos fieldPos=new BlockPos(512,16,21);
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,level.getServer());
   level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=88;z++)for(int y=12;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"GatherProbe");level.getServer().getProfileCache().add(profile);
   fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;
   var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(528,16,32),6,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   String[] paths={"agriculture/horticulture/farmer5.blueprint","agriculture/husbandry/beekeeper5.blueprint","agriculture/husbandry/fisherman5.blueprint","agriculture/horticulture/florist5.blueprint"};
   BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(512,16,48),new BlockPos(544,16,48)};
   for(int i=0;i<4;i++){
    var b=setup.place(paths[i],anchors[i],true);huts.add(b);
    var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Worker spawn "+names[i]);workers.add(c);
    c.getEntity().orElseThrow().setPos(anchors[i].getX()+2.5,16,anchors[i].getZ()-4.5);ids.add(c.getEntity().orElseThrow().getUUID());previous.add(c.getEntity().orElseThrow().position());
    boolean assigned=switch(i){case 0->b.getModule(BuildingModules.FARMER_CRAFT).assignCitizen(c);case 1->b.getModule(BuildingModules.BEEKEEPER_WORK).assignCitizen(c);case 2->b.getModule(BuildingModules.FISHER_WORK).assignCitizen(c);default->b.getModule(BuildingModules.FLORIST_WORK).assignCitizen(c);};
    check(assigned,"Native assignment "+names[i]);c.setSaturation(20);c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    ((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   }
   // A player-configured scarecrow, initially unplanted ground, and native field assignment.
   level.setBlockAndUpdate(fieldPos,com.minecolonies.api.blocks.ModBlocks.blockScarecrow.defaultBlockState());
   level.setBlockAndUpdate(fieldPos.above(),com.minecolonies.api.blocks.ModBlocks.blockScarecrow.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
   var field=FarmField.create(fieldPos,level);field.setSeed(new ItemStack(Items.WHEAT_SEEDS));
   for(var d:Direction.Plane.HORIZONTAL)field.setRadius(d,2);
   fixture.colony().getServerBuildingManager().addBuildingExtension(field);
   check(huts.get(0).getModule(BuildingModules.FARMER_FIELDS).assignExtension(field),"Farm field assignment");
   supply(0,Items.BONE_MEAL,64);supply(0,Items.WHEAT_SEEDS,64);supply(0,Items.IRON_HOE,1);supply(0,Items.IRON_AXE,1);
   // Wet boundary keeps the native hoed field hydrated without supplying crops.
   for(int x=509;x<=515;x++)for(int z=18;z<=24;z++)if(x==509||x==515||z==18||z==24)level.setBlock(new BlockPos(x,15,z),Blocks.WATER.defaultBlockState(),18);
   var bees=(BuildingBeekeeper)huts.get(1);var hive=new BlockPos(564,16,-8);level.setBlockAndUpdate(hive,Blocks.BEEHIVE.defaultBlockState());bees.addHive(hive);
   bees.getModuleMatching(ItemListModule.class,m->m.getId().equals(com.minecolonies.api.util.constant.BuildingConstants.BUILDING_FLOWER_LIST)).addItem(new ItemStorage(Items.POPPY));
   check(bees.getSetting(BuildingBeekeeper.BREEDING).getValue(),"Bee breeding must be enabled");
   supply(1,Items.POPPY,64);supply(1,Items.SHEARS,1);supply(1,Items.GLASS_BOTTLE,16);
   // Accessible glass bee enclosure: bees keep their native movement/breeding AI, but cannot disperse.
   for(int x=561;x<=567;x++)for(int z=-11;z<=-5;z++)for(int y=16;y<=19;y++)if(x==561||x==567||z==-11||z==-5||y==19)level.setBlock(new BlockPos(x,y,z),Blocks.GLASS.defaultBlockState(),18);
   level.setBlockAndUpdate(new BlockPos(564,16,-5),Blocks.OAK_FENCE_GATE.defaultBlockState());
   level.setBlockAndUpdate(new BlockPos(564,17,-5),Blocks.AIR.defaultBlockState());
   for(int n=0;n<2;n++){var bee=EntityType.BEE.create(level);check(bee!=null,"Bee spawn");bee.setPos(563.5+n*2,16,-7.5);bee.setPersistenceRequired();adultBees.add(bee.getUUID());level.addFreshEntity(bee);}
   // Large open-water pond is environmental terrain; fisherman must discover it himself.
   for(int x=501;x<=523;x++)for(int z=63;z<=85;z++)for(int y=12;y<=15;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),18);
   supply(2,Items.FISHING_ROD,1);
   var florist=(BuildingFlorist)huts.get(3);check(!florist.getPlantGround().isEmpty(),"Blueprint florist has no registered beds");
   for(var p:florist.getPlantGround()){check(level.isEmptyBlock(p.above()),"Florist must start without flowers");check(level.getBlockEntity(p) instanceof com.minecolonies.core.tileentities.TileEntityCompostedDirt,"Florist bed missing");}
   // The flower configuration is a deny-list: retain only poppies for an exact output assertion.
   var flowers=florist.getModuleMatching(ItemListModule.class,m->m.getId().equals(com.minecolonies.api.util.constant.BuildingConstants.BUILDING_FLOWER_LIST));
   for(var plant:BuildingFlorist.getPlantablesForBuildingLevel(5))if(plant.getItem()!=Items.POPPY)flowers.addItem(plant);
   supply(3,com.minecolonies.api.items.ModItems.compost,64);supply(3,Items.SHEARS,1);
   check(count(0,Items.WHEAT)==0&&count(2,Items.COD)==0&&count(3,Items.POPPY)==0,"Initial output present");schedule();
  }catch(Throwable e){fail(e);}}
  void supply(int i,Item item,int n){check(ItemHandlerHelper.insertItemStacked(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item,n),false).isEmpty(),"Supply "+item);}
  int count(int i,Item item){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item));}
  int stat(int i,String key){return huts.get(i).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal(key);}
  void pass(int i,String evidence)throws Exception{done[i]=true;workers.get(i).setPaused(true);rows.add(new ProbeResult(names[i]+"_cycle",ProbeResult.Outcome.PASS,evidence+" Prebuilt level5 Arcology hut, terrain, inputs, tools and food supplied. Natural day/night enabled for native field scheduling. Same worker and assignment; recovery disabled. Other levels, delivery, restart and other modes not certified."));ProbeReport.write(output,"ArcologyGathering",rows);}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<4;i++){var c=workers.get(i);var e=c.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(ids.get(i))&&c.getWorkBuilding()==huts.get(i),"Identity/assignment lost "+names[i]);check(e.position().distanceTo(previous.get(i))<=12,"Recovery teleport "+names[i]);previous.set(i,e.position());c.setSaturation(20);}
   if(!done[0]){
    for(int x=510;x<=514;x++)for(int z=19;z<=23;z++){var p=new BlockPos(x,16,z);var state=level.getBlockState(p);if(state.getBlock() instanceof CropBlock crop&&matured.add(p)){level.setBlockAndUpdate(p,crop.getStateForAge(crop.getMaxAge()));}}
    if(count(0,Items.WHEAT)>0&&stat(0,"item_obtained;"+Items.WHEAT.getDescriptionId())>0){check(!matured.isEmpty(),"No native planting observed");pass(0,"Native farmer tilled and planted supplied seeds, then harvested wheat with native item-obtained statistics. QA matured only crops observed after native planting; crop growth time is not certified.");}
   }
   if(!done[1]){var babies=level.getEntitiesOfClass(Bee.class,new AABB(540,10,-24,580,40,24),b->b.isBaby()&&!adultBees.contains(b.getUUID()));if(!babies.isEmpty()&&count(1,Items.POPPY)<64&&stat(1,"breeding_attempts")>0)pass(1,"Native beekeeper fetched flowers and bred the two supplied adult bees in an accessible glass enclosure; new baby UUID, consumed flowers and native breeding-attempt statistics observed. Honey/honeycomb harvesting is not certified.");}
   if(!done[2]){
    hookObserved|=!level.getEntitiesOfClass(com.minecolonies.core.entity.other.NewBobberEntity.class,new AABB(490,10,30,530,45,90)).isEmpty();
    int fish=count(2,Items.COD)+count(2,Items.SALMON)+count(2,Items.PUFFERFISH)+count(2,Items.TROPICAL_FISH);
    if(fish>0){check(hookObserved,"Fish without native bobber observation");pass(2,"Native fisherman discovered supplied open water, cast a native bobber and collected "+fish+" fish into worker/hut inventory. No pond/job/bobber/loot state injection.");}
   }
   if(!done[3]){var florist=(BuildingFlorist)huts.get(3);for(var p:florist.getPlantGround()){var tile=(com.minecolonies.core.tileentities.TileEntityCompostedDirt)level.getBlockEntity(p);composted|=tile!=null&&tile.isComposted();flowerGrown|=level.getBlockState(p.above()).is(Blocks.POPPY);}if(count(3,Items.POPPY)>0&&stat(3,"flowers_picked;"+Items.POPPY.getDescriptionId())>0){check(composted&&flowerGrown&&count(3,com.minecolonies.api.items.ModItems.compost)<64,"Missing compost/growth cycle");pass(3,"Native florist fetched and consumed compost on registered blueprint beds, waited for native flower growth, and harvested poppies with native flowers-picked statistics. No flower/timer injection.");}}
   if(rows.size()==4){h.succeed();return;}
   if(ticks%400==0){var s=new StringBuilder("tick="+ticks+" day="+fixture.colony().getDay()+" daylight="+level.getDayTime()+" matured="+matured.size()+" composted="+composted+" grown="+flowerGrown+" hook="+hookObserved+"\n");for(int i=0;i<4;i++){var c=workers.get(i);s.append("citizenAI=").append(((com.minecolonies.core.entity.citizen.EntityCitizen)c.getEntity().orElseThrow()).getCitizenAI().getState()).append(" jobStatus=").append(c.getJobStatus()).append(" seeds=").append(count(0,Items.WHEAT_SEEDS)).append(" field=").append(huts.get(0).getModule(BuildingModules.FARMER_FIELDS).getOwnedExtensions().stream().map(f->((FarmField)f).getFieldStage().toString()).toList()).append(" ");s.append(names[i]).append(" done=").append(done[i]).append(" pos=").append(c.getEntity().orElseThrow().position()).append(" AI=").append(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState()).append('\n');for(var r:((com.minecolonies.core.colony.buildings.AbstractBuilding)huts.get(i)).getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(r.getLongDisplayString().getString()).append(' ').append(r.getState()).append('\n');}Files.writeString(output.resolve("gathering-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   check(ticks<100000,"Gathering timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("gathering_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyGathering",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
