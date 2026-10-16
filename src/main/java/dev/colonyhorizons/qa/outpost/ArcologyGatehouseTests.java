package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGateHouse;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.minecolonies.core.entity.pathfinding.pathjobs.IDestinationPathJob;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native gatehouse terminal registration and normal guard movement, without connection/travel injection. */
@PrefixGameTestTemplate(false)
public final class ArcologyGatehouseTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void gatehouse(GameTestHelper h){for(int x=28;x<=38;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ProbeResult> rows=new ArrayList<>();final List<ICitizenData> guards=new ArrayList<>();final List<UUID> ids=new ArrayList<>();final List<Vec3> previous=new ArrayList<>(),starts=new ArrayList<>();
  final BlockPos[] posts=new BlockPos[2];final boolean[] targeted=new boolean[2],done=new boolean[2];final int[] atPost=new int[2];OutpostProbeFixture fixture;BuildingGateHouse gatehouse;int ticks;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=600;x++)for(int z=-20;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"GatehouseProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(544,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(536,16,16),6,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   for(int l=1;l<=3;l++){
    var anchor=new BlockPos(480+32*l,16,0);var b=setup.place("military/gatehouse"+l+".blueprint",anchor,true);check(b instanceof BuildingGateHouse,"Native gatehouse type L"+l);var g=(BuildingGateHouse)b;var origin=anchor.offset(-12,-1,-6);
    check(g.getBuildingLevel()==l&&g.getMaxBuildingLevel()==3&&g.getBuildingLevelEquivalent()==2*l-1&&g.getStructurePack().equals("Arcology Horizons"),"Native gatehouse level/style L"+l);
    checkPosts(g,"knight",List.of(origin.offset(6,1,1),origin.offset(8,1,1)),l);
    checkPosts(g,"archer",List.of(origin.offset(2,1,2),origin.offset(12,1,2)),l);
    check(g.getLocationsFromTag("gate").equals(List.of(origin.offset(7,1,5)))&&g.getLocationsFromTag("patrol_point").equals(List.of(origin.offset(7,1,8)))&&g.getLocationsFromTag("groundlevel").equals(List.of(origin.offset(7,0,5))),"Arrival/patrol/ground tags L"+l);
    Set<BlockPos> beds=new HashSet<>();for(int x:new int[]{1,12}){var head=origin.offset(x,1,8);beds.add(head);var hs=level.getBlockState(head);var fs=level.getBlockState(head.north());check(hs.getBlock() instanceof BedBlock&&hs.getValue(BedBlock.PART)==BedPart.HEAD&&hs.getValue(BedBlock.FACING)==Direction.SOUTH&&fs.getBlock()==hs.getBlock()&&fs.getValue(BedBlock.PART)==BedPart.FOOT&&fs.getValue(BedBlock.FACING)==Direction.SOUTH,"Complete south-facing beds L"+l);}
    check(new HashSet<>(g.getModule(BuildingModules.BED).getRegisteredBlocks()).equals(beds),"Native bed registration L"+l);
    var data=fixture.colony().getConnectionManager().serializeNBT(level.registryAccess());var terminals=data.getList("gatehouses",Tag.TAG_COMPOUND);int count=0;for(var tag:terminals)if(BlockPosUtil.read((CompoundTag)tag,"pos").equals(anchor))count++;check(count==1,"Native connection manager gatehouse registration L"+l);
    for(String tag:List.of("knight","archer","gate","patrol_point"))for(var pos:g.getLocationsFromTag(tag)){check(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()&&level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()&&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP),"Unsupported/obstructed marker "+tag);}
    pass("gatehouse_layout_"+l,"Prebuilt gatehouse L"+l+" loaded native class/equivalent level, exact world-transformed knight/archer/gate/patrol/ground tags (native guard list order is set-derived), two complete registered beds, supported clear destinations and exactly one native connection-manager terminal record created by building callbacks. No fabricated manager record. This does not establish a connection, actual player arrival, persistence, destruction or native construction.");if(l==3)gatehouse=g;
   }
   for(int i=0;i<2;i++){
    var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Guard spawn");guards.add(c);var e=c.getEntity().orElseThrow();e.setPos(570.5+i*2,16,14.5);ids.add(e.getUUID());previous.add(e.position());starts.add(e.position());c.setSaturation(20);c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
    check((i==0?gatehouse.getModule(BuildingModules.KNIGHT_GATE_WORK):gatehouse.getModule(BuildingModules.RANGER_GATE_WORK)).assignCitizen(c),"Native gate guard assignment "+i);posts[i]=gatehouse.getGuardPos(e);check(posts[i].equals(gatehouse.getLocationsFromTag(i==0?"knight":"archer").get(0)),"Native role selects exact first post");
    ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   }
   check(gatehouse.getAllAssignedCitizen().size()==2&&gatehouse.getModule(BuildingModules.KNIGHT_GATE_WORK).isFull()&&gatehouse.getModule(BuildingModules.RANGER_GATE_WORK).isFull(),"Shared native two-guard cap");
   for(var item:new Item[]{Items.IRON_SWORD,Items.BOW,Items.SHIELD,Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS,Items.ARROW})check(ItemHandlerHelper.insertItemStacked(gatehouse.getItemHandlerCap((Direction)null),new ItemStack(item,item==Items.ARROW?64:2),false).isEmpty(),"Guard equipment supply "+item);
   schedule();
  }catch(Throwable e){fail(e);}}
  void checkPosts(BuildingGateHouse g,String role,List<BlockPos> expected,int buildingLevel)throws Exception{
   // Native cache is new ArrayList<>(Set<BlockPos>); blueprint NBT order is not retained.
   var actual=g.getLocationsFromTag(role);
   Files.writeString(output.resolve("gatehouse-diagnostics.txt"),"layout L"+buildingLevel+" role="+role+" expectedPositions="+expected+" actualNativeOrder="+actual+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(actual.size()==2&&new HashSet<>(actual).size()==2&&new HashSet<>(actual).equals(new HashSet<>(expected)),"Exact two unique "+role+" posts L"+buildingLevel+" expected="+expected+" actualNativeOrder="+actual);
  }
  void pass(String id,String detail)throws Exception{rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,detail));ProbeReport.write(output,"ArcologyGatehouse",rows);}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;check(gatehouse.getAllAssignedCitizen().size()==2&&gatehouse.getTask().equals(com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting.GUARD),"Native guard staffing/task changed");
   for(int i=0;i<2;i++){
    var c=guards.get(i);var e=c.getEntity().orElseThrow();var module=i==0?gatehouse.getModule(BuildingModules.KNIGHT_GATE_WORK):gatehouse.getModule(BuildingModules.RANGER_GATE_WORK);
    check(e.isAlive()&&e.getUUID().equals(ids.get(i))&&c.getWorkBuilding()==gatehouse&&module.getAssignedCitizen().contains(c)&&gatehouse.getGuardPos(e).equals(posts[i]),"Guard identity/native assignment/post changed "+i);check(e.position().distanceTo(previous.get(i))<=12,"Guard recovery teleport "+i);previous.set(i,e.position());c.setSaturation(20);
    var nav=(MinecoloniesAdvancedPathNavigate)e.getNavigation();var result=nav.getPathResult();targeted[i]|=result!=null&&result.getJob() instanceof IDestinationPathJob job&&posts[i].equals(job.getDestination());
    var ai=c.getJob().getWorkerAI();boolean guarding=ai!=null&&ai.getState()==CombatAIStates.NO_TARGET;
    boolean inside=gatehouse.isInBuilding(e.blockPosition());
    var body=e.position().add(0,0.9,0);var postBody=new Vec3(posts[i].getX()+0.5,posts[i].getY()+0.9,posts[i].getZ()+0.5);
    boolean clear=level.clip(new net.minecraft.world.level.ClipContext(body,postBody,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,e)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    if(targeted[i]&&guarding&&inside&&clear&&e.blockPosition().distSqr(posts[i])<=25&&e.position().distanceTo(starts.get(i))>6)atPost[i]+=20;
    if(!done[i]&&atPost[i]>=100){done[i]=true;c.setPaused(true);pass(i==0?"gatehouse_knight_post":"gatehouse_archer_post","Same native "+(i==0?"knight":"archer")+" assigned to the shared two-guard L3 gatehouse; normal navigation selected its exact native tagged guard destination, moved more than six blocks from outside, and was observed guarding inside native building bounds within native five-block post range, with an unobstructed body-height collision ray to the post, for 100 sampled ticks. Recovery disabled; no AI/path/position mutation after initial spawn. Equipment, food, claim/research and daytime supplied. Native guards allow a post radius; this does not assert standing on the marker, combat, cavalry passage or player travel.");}
   }
   if(done[0]&&done[1]){h.succeed();return;}
   if(ticks%400==0){var text=new StringBuilder("tick="+ticks+"\n");for(int i=0;i<2;i++){var c=guards.get(i);var e=c.getEntity().orElseThrow();var nav=(MinecoloniesAdvancedPathNavigate)e.getNavigation();text.append("role=").append(i).append(" pos=").append(e.position()).append(" post=").append(posts[i]).append(" targeted=").append(targeted[i]).append(" nearTicks=").append(atPost[i]).append(" AI=").append(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState()).append(" path=").append(nav.getPath()==null?"none":nav.getPath().getTarget()).append('\n');for(var r:gatehouse.getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))text.append(r.getLongDisplayString().getString()).append(' ').append(r.getState()).append('\n');}Files.writeString(output.resolve("gatehouse-diagnostics.txt"),text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   check(ticks<60000,"Gatehouse guard timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("gatehouse_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyGatehouse",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
