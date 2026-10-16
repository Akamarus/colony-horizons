package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native registration at five levels and stablemaster conversion/lead delivery at level five. */
@PrefixGameTestTemplate(false)
public final class ArcologyStableTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=105000)
 public static void stable(GameTestHelper h){for(int x=28;x<=39;x++)for(int z=-2;z<=5;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ProbeResult> rows=new ArrayList<>();final Set<UUID> navigationChecked=new HashSet<>();final Map<UUID,Vec3> horsePrevious=new HashMap<>();
  final String horseName="Arcology-native-conversion-"+UUID.randomUUID();final AABB tracked=new AABB(448,0,-32,624,80,96);
  OutpostProbeFixture fixture;BuildingStable stable;ICitizenData worker;UUID workerId,sourceId;Horse source;Vec3 previous,spawn;
  UUID roundupId;Vec3 roundupSpawn;int roundupBaseline;
  boolean leashed,delivered,trainedState,conversionPassed,roundupPassed;int ticks;double initialXp;
  java.util.function.Consumer<Run> afterSetup,afterVerification; boolean tetherStarter;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(110000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=608;x++)for(int z=-16;z<=80;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"StableProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(544,16,40);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(536,16,24),7,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(576,16,0),new BlockPos(512,16,40),new BlockPos(544,16,40)};
   for(int i=1;i<=5;i++){
    var b=setup.place("military/stable"+i+".blueprint",anchors[i-1],true);check(b instanceof BuildingStable,"Wrong native stable type L"+i);var s=(BuildingStable)b;
    var origin=anchors[i-1].offset(-12,-1,-1);check(s.getBuildingLevel()==i&&s.getStructurePack().equals("Arcology Horizons"),"Stable identity/level L"+i);
    Set<BlockPos> expected=new HashSet<>();for(int pair=0;pair<i;pair++){expected.add(origin.offset(2,1,5+3*pair));expected.add(origin.offset(11,1,5+3*pair));}
    check(s.stallPositions().size()==2*i&&new HashSet<>(s.stallPositions()).equals(expected),"Exact native stall registration L"+i+": "+s.stallPositions());
    int[] bedX={1,2,3,4,10,11};Set<BlockPos> beds=new HashSet<>();for(int j=0;j<=i;j++){var head=origin.offset(bedX[j],1,1);beds.add(head);var hs=level.getBlockState(head);var fs=level.getBlockState(head.south());check(hs.getBlock() instanceof BedBlock&&hs.getValue(BedBlock.PART)==BedPart.HEAD&&hs.getValue(BedBlock.FACING)==Direction.NORTH&&fs.getBlock()==hs.getBlock()&&fs.getValue(BedBlock.PART)==BedPart.FOOT&&fs.getValue(BedBlock.FACING)==Direction.NORTH,"Incomplete bed L"+i);}
    check(new HashSet<>(s.getModule(BuildingModules.BED).getRegisteredBlocks()).equals(beds),"Exact registered bed heads L"+i);
    for(var pos:expected)for(int y=0;y<4;y++)check(level.getBlockState(pos.above(y)).getCollisionShape(level,pos.above(y)).isEmpty(),"Obstructed native stall "+pos);
    check(s.getLocationsFromTag("patrol_point").equals(List.of(origin.offset(7,1,1))),"Patrol tag L"+i);
    pass("stable_layout_"+i,"Prebuilt generated stable L"+i+" registered exact "+(2*i)+" native stall destinations and "+(i+1)+" complete north-facing bed heads, correct level/style and patrol destination. Stall columns clear. This placement check is not native construction, upgrade, rotation or mounted path acceptance.");
    if(i==5)stable=s;
   }
   worker=fixture.createCitizen();check(worker!=null&&worker.getEntity().isPresent(),"Stablemaster spawn");var e=worker.getEntity().orElseThrow();e.setPos(540.5,16,42.5);workerId=e.getUUID();previous=e.position();worker.setSaturation(20);worker.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
   check(stable.getModule(BuildingModules.STABLEMASTER_WORK).assignCitizen(worker),"Native stablemaster assignment");((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());initialXp=worker.getCitizenSkillHandler().getTotalXP();
   for(var item:new Item[]{Items.LEAD,Items.IRON_AXE,Items.GOLDEN_APPLE,Items.WHEAT,Items.LEATHER,Items.LEATHER_HORSE_ARMOR})check(ItemHandlerHelper.insertItemStacked(stable.getItemHandlerCap((Direction)null),new ItemStack(item,item==Items.IRON_AXE?1:16),false).isEmpty(),"Supply insertion "+item);
   check(level.getEntitiesOfClass(CavalryHorseEntity.class,tracked).isEmpty()&&stat("horses_trained")==0&&stat("roundups_completed")==0,"No supplied outputs or preexisting stats");
   // Test input containment only: open Arcology sides are not animal pens.
   // Two fence blocks prevent stepping over from the one-block corner framing. No AI/output injection.
   if(!tetherStarter)for(int x=531;x<=547;x++)for(int z=38;z<=59;z++)if(x==531||x==547||z==38||z==59)
    for(int y=16;y<=17;y++)level.setBlock(new BlockPos(x,y,z),Blocks.OAK_FENCE.defaultBlockState(),3);
   source=EntityType.HORSE.create(level);check(source!=null,"Vanilla horse creation");source.setPos(539.5,16,40.5);source.setPersistenceRequired();source.setCustomName(Component.literal(horseName));sourceId=source.getUUID();spawn=source.position();check(!source.isBaby()&&!source.isTamed()&&!source.isInLove()&&!source.isVehicle()&&!source.isNoAi(),"Ordinary adult horse prerequisites");check(stable.isInBuilding(source.blockPosition())&&level.addFreshEntity(source),"Horse in native stable bounds");
   if(tetherStarter){var tether=new BlockPos(539,16,44);level.setBlock(tether,Blocks.OAK_FENCE.defaultBlockState(),18);source.setLeashedTo(net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(level,tether),true);}
   if(afterSetup!=null)afterSetup.accept(this);else schedule();
  }catch(Throwable e){fail(e);}}
  int stat(String key){return stable.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal(key);}
  void pass(String id,String detail)throws Exception{rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,detail));ProbeReport.write(output,"ArcologyStable",rows);}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=worker.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(workerId)&&worker.getWorkBuilding()==stable,"Stablemaster identity/assignment lost");check(e.position().distanceTo(previous)<=12,"Worker recovery teleport");previous=e.position();worker.setSaturation(20);
   var ai=worker.getJob().getWorkerAI();trainedState|=ai!=null&&ai.getState()==com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.HERDER_TRAIN;
   CavalryHorseEntity converted=null;
   for(var horse:level.getEntitiesOfClass(AbstractHorse.class,tracked,a->a.hasCustomName()&&(a.getCustomName().getString().equals(horseName)||a.getUUID().equals(roundupId)))){
    check(horse.isAlive()&&!horse.isNoAi(),"Horse died or AI disabled");var old=horsePrevious.put(horse.getUUID(),horse.position());if(old!=null)check(old.distanceTo(horse.position())<=12,"Horse recovery teleport");
    boolean workerLeash=horse.getUUID().equals(roundupId)&&horse.getLeashHolder()==e;leashed|=workerLeash;
    if(workerLeash&&horse.position().distanceTo(roundupSpawn)>3&&stable.stallPositions().stream().anyMatch(p->horse.distanceToSqr(p.getX()+0.5,p.getY(),p.getZ()+0.5)<=6.25))delivered=true;
    if(horse instanceof CavalryHorseEntity cav){converted=cav;if(navigationChecked.add(cav.getUUID()))((MinecoloniesAdvancedPathNavigate)cav.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());}
   }
   if(!conversionPassed&&converted!=null&&stat("horses_trained")>0){check(!source.isAlive()||source.isRemoved(),"Original vanilla horse remains after conversion");check(!converted.getUUID().equals(sourceId)&&converted.getAnimalData()!=null&&converted.getAnimalData().getHomeBuilding()==stable,"Native conversion/home association missing");check(trainedState&&worker.getCitizenSkillHandler().getTotalXP()>initialXp,"Training state/XP missing");conversionPassed=true;pass("stablemaster_conversion","Native stablemaster converted the sole supplied ordinary untamed adult vanilla horse; unique name transferred, original removed, distinct cavalry entity registered with this stable as home, horses_trained statistic and worker XP increased. Supplies/food/daytime were fixtures; "+(tetherStarter?"starter tethered to a fixture fence for mounted-test preparation":"external two-block-high fixture fence retained the untethered source within stable bounds")+". No conversion, output, worker AI or home injection. Same worker; navigation recovery disabled. Breeding, maintenance, mounted patrol and return are not certified.");}
   if(!roundupPassed&&leashed&&delivered&&stat("roundups_completed")>roundupBaseline&&level.getEntity(roundupId) instanceof AbstractHorse returned&&!returned.isLeashed()){roundupPassed=true;pass("stablemaster_stall_delivery","Native worker held the separately supplied ordinary donkey's leash, donkey moved over three blocks from its front-aisle spawn to within 2.5 blocks of an exact registered stall while worker-leashed, and native roundups_completed increased beyond its value at donkey spawn; the donkey was detached after observed delivery. A separate unconverted input avoids the native exclusion of home cavalry horses from roundup; no AI state, lead attachment or post-spawn position was injected. This establishes one real reachable stall delivery; outdoor collection, every stall, mounted travel and long-term containment are not certified.");}
   if(conversionPassed&&!tetherStarter&&roundupId==null){
    // Separate ordinary input: home cavalry horses are intentionally excluded from native roundup.
    // A donkey stays unconverted, allowing collection to run independently of training selection.
    var donkey=EntityType.DONKEY.create(level);check(donkey!=null,"Roundup donkey creation");
    donkey.setPos(539.5,16,40.5);donkey.setPersistenceRequired();donkey.setCustomName(Component.literal("Arcology-roundup-input"));
    check(!donkey.isTamed()&&!donkey.isLeashed()&&!donkey.isNoAi()&&level.addFreshEntity(donkey),"Ordinary untethered roundup input");
    roundupId=donkey.getUUID();roundupSpawn=donkey.position();roundupBaseline=stat("roundups_completed");
   }
   if(conversionPassed&&(roundupPassed||(tetherStarter&&converted!=null&&converted.isReadyForCombat()))){worker.setPaused(true);if(afterVerification!=null)afterVerification.accept(this);else h.succeed();return;}
   if(ticks%400==0){var text=new StringBuilder("tick="+ticks+" worker="+e.position()+" AI="+(ai==null?"none":ai.getState())+" trained="+stat("horses_trained")+" roundups="+stat("roundups_completed")+" leashed="+leashed+" delivered="+delivered+" source="+source.getUUID()+" removed="+source.isRemoved()+"\n");for(var horse:level.getEntitiesOfClass(AbstractHorse.class,tracked))text.append("horse=").append(horse.getUUID()).append(" type=").append(horse.getType()).append(" pos=").append(horse.position()).append(" leash=").append(horse.getLeashHolder()).append(" home=").append(horse instanceof CavalryHorseEntity cav&&cav.getAnimalData()!=null?cav.getAnimalData().getHomeBuilding():null).append('\n');for(var r:stable.getOpenRequestsOfCitizenOrBuilding(worker.getId(),r->true))text.append(r.getLongDisplayString().getString()).append(' ').append(r.getState()).append('\n');Files.writeString(output.resolve("stable-diagnostics.txt"),text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   check(ticks<100000,"Stable timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("stable_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyStable",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
