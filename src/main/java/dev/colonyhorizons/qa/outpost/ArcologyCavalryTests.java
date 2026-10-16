package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.settings.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.colony.jobs.guard.JobCavalry;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;
/** Tethered starter, real stablemaster conversion followed by native cavalry mounting and mounted patrol. */
@PrefixGameTestTemplate(false)
public final class ArcologyCavalryTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=180000)
 public static void mounted_patrol(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=5;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var f=new ArcologyStableTests.Run(h);f.tetherStarter=true;f.afterVerification=r->new Run(r).start();f.start();});
 }
 static final class Run {
  final ArcologyStableTests.Run f;final List<ProbeResult> rows=new ArrayList<>();ICitizenData guard;CavalryHorseEntity horse;UUID guardId,horseId;Vec3 previousGuard,previousHorse;BlockPos outside,inside;boolean mounted,left;int ticks,insideTicks;
  Run(ArcologyStableTests.Run f){this.f=f;}
  void start(){try{
   f.level.setBlock(new BlockPos(539,16,44),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),18);
   horse=f.level.getEntitiesOfClass(CavalryHorseEntity.class,f.tracked,h->h.hasCustomName()&&h.getCustomName().getString().equals(f.horseName)).stream().findFirst().orElseThrow();horseId=horse.getUUID();horse.dropLeash(true,false);
   guard=f.fixture.createCitizen();check(guard!=null&&guard.getEntity().isPresent(),"Cavalry spawn");var e=guard.getEntity().orElseThrow();guardId=e.getUUID();e.setPos(539.5,16,35.5);guard.setSaturation(20);guard.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
   check(f.stable.getModule(BuildingModules.CAVALRY_STABLE_WORK).assignCitizen(guard)&&guard.getJob() instanceof JobCavalry,"Native cavalry assignment");
   check(f.stable.getModule(BuildingModules.STABLEMASTER_WORK).getAssignedCitizen().contains(f.worker),"Cavalry displaced stablemaster");
   for(var item:List.of(ModItems.spear,Items.SHIELD,Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS))check(ItemHandlerHelper.insertItemStacked(f.stable.getItemHandlerCap((Direction)null),new ItemStack(item),false).isEmpty(),"Guard equipment insertion");
   ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());((MinecoloniesAdvancedPathNavigate)horse.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   previousGuard=e.position();previousHorse=horse.position();outside=new BlockPos(539,16,25);inside=new BlockPos(539,16,48);
   f.stable.getModule(BuildingModules.STABLE_SETTINGS).getSetting(AbstractBuildingGuards.GUARD_TASK).set(GuardTaskSetting.PATROL);f.stable.getSetting(AbstractBuildingGuards.PATROL_MODE).set(GuardPatrolModeSetting.MANUAL);f.stable.getSetting(BuildingStable.PATROL_INTERVAL).setValue(0);
   f.stable.resetPatrolTargets();f.stable.addPatrolTarget(outside);f.stable.setTempNextPatrolPoint(outside);f.stable.startPatrolNext();schedule();
  }catch(Throwable e){fail(e);}}
  void schedule(){f.h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;var e=guard.getEntity().orElseThrow();guard.setSaturation(20);check(e.isAlive()&&horse.isAlive()&&e.getUUID().equals(guardId)&&horse.getUUID().equals(horseId)&&guard.getWorkBuilding()==f.stable,"Mounted staff/horse identity lost");
   check(previousGuard.distanceTo(e.position())<=12&&previousHorse.distanceTo(horse.position())<=12,"Mounted recovery teleport");previousGuard=e.position();previousHorse=horse.position();
   boolean riding=e.getVehicle()==horse;
   if(!mounted&&riding){check(((JobCavalry)guard.getJob()).getMount().equals(horseId)&&horse.getAnimalData().getHomeBuilding()==f.stable,"Native mount/home link missing");mounted=true;pass("cavalry_mount","Native cavalry assigned alongside the same paused stablemaster chose and mounted the horse that worker actually converted and made combat-ready. No mount/reservation/horse-home injection; starter vanilla horse initially tied to a temporary fence, untied and removed after native conversion and readiness; initial guard equipment/food supplied, recovery disabled.");}
   if(mounted)check(riding,"Guard dismounted during daytime patrol");
   if(mounted&&!left&&horse.blockPosition().distSqr(outside)<=25&&!f.stable.isInBuilding(horse.blockPosition())){left=true;pass("cavalry_exit","Same mounted guard/horse left the stable and reached the configured exterior patrol point using native patrol/navigation, recovery disabled.");f.stable.resetPatrolTargets();f.stable.addPatrolTarget(inside);f.stable.setTempNextPatrolPoint(inside);f.stable.startPatrolNext();}
   if(left&&riding&&f.stable.isInBuilding(horse.blockPosition())&&horse.blockPosition().distSqr(inside)<=25)insideTicks+=20;else insideTicks=0;
   if(insideTicks>=100){pass("cavalry_return","Same mounted pair returned from the exterior patrol point into the stable's central aisle and remained within native interaction range for100 sampled ticks. Native player-configurable patrol interval0/manual points; clear weather/daytime and initial equipment supplied. No recovery, post-spawn repositioning or mounting calls; not combat, all stalls, breeding or restart.");guard.setPaused(true);f.h.succeed();return;}
   if(ticks%400==0){String text="ticks="+ticks+" guard="+e.position()+" horse="+horse.position()+" mounted="+mounted+" riding="+riding+" left="+left+" ai="+guard.getJob().getWorkerAI().getState()+" target="+f.stable.getNextPatrolTarget(false)+"\n";for(var r:f.stable.getOpenRequestsOfCitizenOrBuilding(guard.getId(),q->true))text+=r.getLongDisplayString().getString()+" "+r.getState()+"\n";Files.writeString(f.output.resolve("cavalry-diagnostics.txt"),text,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   check(ticks<60000,"Mounted patrol timeout; see diagnostics");schedule();
  }
  void pass(String id,String evidence)throws Exception{rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence));ProbeReport.write(f.output,"ArcologyCavalry",rows);}
  void fail(Throwable e){try{rows.add(new ProbeResult("cavalry_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,"ArcologyCavalry",rows);}catch(Exception ignored){}f.h.fail(e.toString());}
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 }
}
