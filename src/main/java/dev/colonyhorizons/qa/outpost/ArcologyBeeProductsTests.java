package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Natural bee pollination and native beekeeper collection in blueprint apiaries. */
@PrefixGameTestTemplate(false)
public final class ArcologyBeeProductsTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=125000)
 public static void honey_products(GameTestHelper h){for(int x=28;x<=38;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<BuildingBeekeeper> huts=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();final List<UUID> ids=new ArrayList<>();final List<Vec3> previous=new ArrayList<>();final List<ProbeResult> rows=new ArrayList<>();
  final List<Set<UUID>> bees=new ArrayList<>();final Map<BlockPos,Integer> previousHoney=new HashMap<>();
  final Item[] products={Items.HONEY_BOTTLE,Items.HONEYCOMB};final boolean[] done=new boolean[2],moved=new boolean[2],nectar=new boolean[2],occupied=new boolean[2],reset=new boolean[2];final int[] maxHoney=new int[2];
  OutpostProbeFixture fixture;int ticks;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(200000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=600;x++)for(int z=-16;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"BeeProductProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(544,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(544,16,16),6,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   for(int i=0;i<2;i++){
    var anchor=new BlockPos(512+i*64,16,0);String path="agriculture/husbandry/beekeeper5.blueprint";var hut=(BuildingBeekeeper)setup.place(path,anchor,true);huts.add(hut);
    hut.getSetting(BuildingBeekeeper.MODE).set(i==0?BuildingBeekeeper.HONEY:BuildingBeekeeper.HONEYCOMB);if(hut.getSetting(BuildingBeekeeper.BREEDING).getValue())hut.getSetting(BuildingBeekeeper.BREEDING).trigger();
    var bp=setup.blueprint(path);var origin=anchor.subtract(bp.getPrimaryBlockOffset());List<BlockPos> flowers=new ArrayList<>();
    for(var info:bp.getBlockInfoAsList()){
     var pos=origin.offset(info.getPos());var state=level.getBlockState(pos);
     if(state.getBlock() instanceof BeehiveBlock){var hive=(BeehiveBlockEntity)level.getBlockEntity(pos);check(hive!=null&&hive.getOccupantCount()==0&&state.getValue(BeehiveBlock.HONEY_LEVEL)==0,"Hives must start empty with zero honey");hut.addHive(pos);previousHoney.put(pos,0);}
     if(state.is(net.minecraft.tags.BlockTags.FLOWERS))flowers.add(pos);
    }
    check(!hut.getHives().isEmpty()&&hut.getHives().size()<=hut.getMaximumHives()&&!flowers.isEmpty(),"Native apiary hives/flowers unavailable");
    Set<UUID> initial=new HashSet<>();bees.add(initial);
    for(int n=0;n<6;n++){var bee=EntityType.BEE.create(level);check(bee!=null,"Bee creation");var flower=flowers.get(n%flowers.size());bee.setPos(flower.getX()+0.5,flower.getY()+1.2,flower.getZ()+0.5);bee.setPersistenceRequired();check(!bee.hasNectar()&&!bee.isBaby(),"Ordinary adult bees required");check(level.addFreshEntity(bee),"Bee spawn");initial.add(bee.getUUID());}
    var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Worker spawn");workers.add(c);var e=c.getEntity().orElseThrow();e.setPos(anchor.getX()+2.5,16,-4.5);ids.add(e.getUUID());previous.add(e.position());check(hut.getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(c),"Beekeeper assignment");c.setSaturation(20);c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
    var supplies=i==0?new ItemStack(Items.GLASS_BOTTLE,4):new ItemStack(Items.SHEARS);check(ItemHandlerHelper.insertItemStacked(hut.getItemHandlerCap((Direction)null),supplies,false).isEmpty(),"Tool/bottle insertion");check(count(i,products[i])==0,"Initial bee product present");
   }
   schedule();
  }catch(Throwable e){fail(e);}}
  int count(int i,Item item){return ArcologyOperationsFixture.count(workers.get(i).getInventory(),new ItemStack(item))+ArcologyOperationsFixture.count(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item));}
  boolean worn(int i){for(var inventory:List.of(workers.get(i).getInventory(),huts.get(i).getItemHandlerCap((Direction)null)))for(int s=0;s<inventory.getSlots();s++){var stack=inventory.getStackInSlot(s);if(stack.is(Items.SHEARS)&&stack.getDamageValue()>0)return true;}return false;}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<2;i++){
    var hut=huts.get(i);var c=workers.get(i);var e=c.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(ids.get(i))&&c.getWorkBuilding()==hut,"Beekeeper identity/assignment lost");double distance=e.position().distanceTo(previous.get(i));check(distance<=12,"Recovery teleport");moved[i]|=distance>0.1;previous.set(i,e.position());c.setSaturation(20);
    for(var id:bees.get(i)){var entity=level.getEntity(id);if(entity instanceof Bee bee)nectar[i]|=bee.hasNectar();}
    for(var pos:hut.getHives()){
     var state=level.getBlockState(pos);check(state.getBlock() instanceof BeehiveBlock,"Assigned hive lost");var hive=(BeehiveBlockEntity)level.getBlockEntity(pos);occupied[i]|=hive.getOccupantCount()>0;int honey=state.getValue(BeehiveBlock.HONEY_LEVEL);maxHoney[i]=Math.max(maxHoney[i],honey);reset[i]|=previousHoney.get(pos)>0&&honey==0;previousHoney.put(pos,honey);
    }
    int made=count(i,products[i]);var stats=hut.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager();
    if(!done[i]&&made>0&&moved[i]&&nectar[i]&&occupied[i]&&maxHoney[i]>0&&reset[i]&&stats.getStatTotal("items_collected;"+new ItemStack(products[i]).getHoverName().getString())>=made&&(i==0?count(i,Items.GLASS_BOTTLE)+made==4:worn(i))){
     check(i==0||made>=3,"Native comb yield below three");done[i]=true;c.setPaused(true);rows.add(new ProbeResult(i==0?"beekeeper_honey_cycle":"beekeeper_honeycomb_cycle",ProbeResult.Outcome.PASS,"Native bees acquired nectar, occupied initially empty blueprint hives and increased honey from zero. Native beekeeper fetched "+(i==0?"glass bottles":"shears")+" from hut, navigated and harvested "+made+" "+products[i]+"; honey reset, native collection statistic and "+(i==0?"bottle consumption":"shear wear")+" verified. Prebuilt level5 hut, six ordinary adult bees, native hive assignments, harvest-only mode, worker food and tools supplied. Blueprint flowers retained; no honey/nectar/hive timer/output/AI injection. Identity/movement checked, recovery disabled. Breeding, other levels and restart not certified."));ProbeReport.write(output,"ArcologyBeeProducts",rows);
    }
    if(ticks%1000==0){var s=new StringBuilder("tick="+ticks+" mode="+hut.getHarvestTypes()+" pos="+e.position()+" AI="+(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState())+" nectar="+nectar[i]+" occupied="+occupied[i]+" maxHoney="+maxHoney[i]+" reset="+reset[i]+" product="+made+"\n");for(var pos:hut.getHives())s.append(" hive ").append(pos).append(" state=").append(level.getBlockState(pos)).append(" occupants=").append(((BeehiveBlockEntity)level.getBlockEntity(pos)).getOccupantCount()).append("\n");for(var id:bees.get(i)){var entity=level.getEntity(id);if(entity instanceof Bee bee)s.append(" bee ").append(id).append(" pos=").append(bee.position()).append(" nectar=").append(bee.hasNectar()).append("\n");}for(var r:hut.getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(" request=").append(r.getLongDisplayString().getString()).append("\n");Files.writeString(output.resolve("bee-products-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   }
   if(rows.size()==2){h.succeed();return;}check(ticks<120000,"Natural bee-products timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("bee_products_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyBeeProducts",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
