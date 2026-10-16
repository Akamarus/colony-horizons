package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
/** Native embedded-tower placement hooks, linking, guard housing and duty movement. */
@PrefixGameTestTemplate(false)
public final class ArcologyBarracksTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=45000)
 public static void barracks(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
 final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));final List<ProbeResult> rows=new ArrayList<>();
 OutpostProbeFixture fixture;BuildingBarracks barracks;BuildingBarracksTower tower;ICitizenData guard;UUID identity;net.minecraft.world.phys.Vec3 previous;int ticks,leg;double walked;BlockPos duty=new BlockPos(532,16,5);
 Run(GameTestHelper h){this.h=h;level=h.getLevel();}
 void start(){try{
 level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
 for(int x=468;x<=580;x++)for(int z=-20;z<=55;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
 var profile=new GameProfile(UUID.randomUUID(),"BarracksProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
 var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
 fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(512,16,16),5,level,false);
 setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
 var anchor=new BlockPos(512,16,0);var bp=setup.blueprint("military/barracks5.blueprint");var origin=anchor.subtract(bp.getPrimaryBlockOffset());
 barracks=(BuildingBarracks)setup.place("military/barracks5.blueprint",anchor,true);check(barracks.getBuildingLevel()==5,"Barracks actual level");
 // Direct fixture placement omits nested hut placement handlers. Exercise the actual native handler
 // and building registration callback used by construction, without editing either link collection.
 var context=new com.minecolonies.api.util.CreativeBuildingStructureHandler(level,anchor,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true);
 var handler=new com.minecolonies.core.placementhandlers.BarracksTowerHandler();Set<BlockPos> expected=new HashSet<>();
 for(var info:bp.getBlockInfoAsList()){var state=bp.getBlockState(info.getPos());if(state.getBlock()!=com.minecolonies.api.blocks.ModBlocks.blockHutBarracksTower)continue;
 var pos=origin.offset(info.getPos());expected.add(pos);check(info.getTileEntityData()!=null,"Missing embedded tower metadata");
 check(fixture.colony().getServerBuildingManager().getBuilding(pos)==null,"Nested tower was already supplied");
 level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);handler.handle(level,pos,state,info.getTileEntityData().copy(),context);
 var registered=fixture.colony().getServerBuildingManager().getBuilding(pos);check(registered instanceof BuildingBarracksTower,"Native tower handler did not register tower");
 barracks.registerBlockPosition(state,pos,level);
 check(com.minecolonies.api.util.NBTUtils.readBlockPos(registered.serializeNBT(level.registryAccess()),"pos").equals(anchor),"Native reverse link missing");
 }
 check(expected.size()==4&&new HashSet<>(barracks.getTowers()).equals(expected),"Four embedded towers must link exactly");
 pass("barracks_tower_links","Four embedded Arcology tower anchors registered through native BarracksTowerHandler and native barracks block-registration callback, with exact forward/reverse links. Prebuilt level5 barracks fixture explicitly invokes construction hooks; no addBarracks or link-map injection. Full builder construction not certified.");
 var towerPos=anchor.offset(5,0,5);tower=(BuildingBarracksTower)setup.place("military/barrackstower5.blueprint",towerPos,true);
 check(tower.getBuildingLevel()==5&&expected.contains(towerPos),"Tower actual level/placement");check(tower.getModule(BuildingModules.BED).getRegisteredBlocks().size()==5,"Expected five native registered tower beds");
 guard=fixture.createCitizen();check(guard!=null&&guard.getEntity().isPresent(),"Guard spawn");var entity=guard.getEntity().orElseThrow();entity.setPos(517.5,16,-8.5);identity=entity.getUUID();previous=entity.position();
 check(guard.getHomeBuilding()==null&&guard.getWorkBuilding()==null,"Guard must start unassigned");
 check(tower.getModule(BuildingModules.KNIGHT_BARRACKS_WORK).assignCitizen(guard),"Native knight assignment");
 check(guard.getHomeBuilding()==tower&&guard.getWorkBuilding()==tower,"Native assignment did not provide tower housing");
 check(tower.getModule(BuildingModules.KNIGHT_BARRACKS_WORK).getModuleMax()==5,"Level5 tower guard capacity");
 int slot=0;for(var item:List.of(Items.IRON_SWORD,Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS,Items.SHIELD))guard.getInventory().setStackInSlot(slot++,new ItemStack(item));
 guard.getInventory().setStackInSlot(8,new ItemStack(Items.GOLDEN_CARROT,64));guard.setSaturation(20);
 ((MinecoloniesAdvancedPathNavigate)entity.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
 tower.getSetting(AbstractBuildingGuards.GUARD_TASK).set(GuardTaskSetting.GUARD);tower.setGuardPos(duty);check(entity.position().distanceTo(duty.getCenter())>10,"Duty must require navigation");
 pass("barrackstower_housing","Native knight assignment set both work and home to the actual level5 tower; five blueprint beds and native capacity5 verified. One guard supplied, no setHomeBuilding call. Bed use, full occupancy and other levels not certified.");
 schedule();
 }catch(Throwable e){fail(e);}}
 void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
 void tick()throws Exception{
 ticks+=20;var entity=guard.getEntity().orElseThrow();check(entity.isAlive()&&entity.getUUID().equals(identity)&&guard.getWorkBuilding()==tower&&guard.getHomeBuilding()==tower,"Guard identity/job/housing lost");
 double step=entity.position().distanceTo(previous);check(step<=12,"Guard recovery teleport");walked+=step;previous=entity.position();guard.setSaturation(20);
 if(entity.position().distanceTo(duty.getCenter())<=5.5&&guard.getJob().getWorkerAI()!=null&&guard.getJob().getWorkerAI().getState()==com.minecolonies.api.entity.ai.combat.CombatAIStates.NO_TARGET){
 if(leg==0){leg=1;duty=new BlockPos(532,16,-12);tower.setGuardPos(duty);check(entity.position().distanceTo(duty.getCenter())>10,"Second duty must require navigation");}
 else{check(walked>20,"Insufficient observed movement");pass("barrackstower_duty","Same housed knight naturally reached two configured guard duty points over10 blocks apart with native no-target guard AI and observed continuous movement. User-facing guard task/positions, equipment and food supplied; no navigation or AI-state injection; recovery disabled. Combat, sleep and restart not certified.");h.succeed();return;}
 }
 if(ticks%1000==0)Files.writeString(output.resolve("barracks-diagnostics.txt"),"tick="+ticks+" position="+entity.position()+" duty="+duty+" leg="+leg+" AI="+(guard.getJob().getWorkerAI()==null?"none":guard.getJob().getWorkerAI().getState())+" walked="+walked+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
 check(ticks<40000,"Barracks duty timeout");schedule();
 }
 void pass(String id,String detail)throws Exception{rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,detail));ProbeReport.write(output,"ArcologyBarracks",rows);}
 void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
 void fail(Throwable e){try{rows.add(new ProbeResult("barracks_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyBarracks",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
