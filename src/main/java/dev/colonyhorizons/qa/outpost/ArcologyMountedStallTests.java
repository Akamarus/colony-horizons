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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native conversion/readiness/mounting, then command-driven native navigation at exact stalls. */
@PrefixGameTestTemplate(false)
public final class ArcologyMountedStallTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=240000)
    public static void all_registered_stalls(GameTestHelper h) {
        for(int x=28;x<=39;x++)for(int z=-2;z<=5;z++)h.getLevel().setChunkForced(x,z,true);
        h.runAfterDelay(40,()->{
            var fixture=new ArcologyStableTests.Run(h); fixture.tetherStarter=true;
            fixture.afterVerification=f->new Run(f).start(); fixture.start();
        });
    }
    record Stop(int tier, int stall, BlockPos position) { }
    static final class Run {
        final ArcologyStableTests.Run f;
        final List<Stop> route=new ArrayList<>();
        final List<ProbeResult> rows=new ArrayList<>();
        ICitizenData guard; CavalryHorseEntity horse; UUID guardId,horseId;
        Vec3 previousGuard,previousHorse;
        int ticks,legTicks,leg; boolean commanded,finished;
        Run(ArcologyStableTests.Run f) { this.f=f; }
        void start() { try {
            f.level.setBlock(new BlockPos(539,16,44),Blocks.AIR.defaultBlockState(),18);
            horse=f.level.getEntitiesOfClass(CavalryHorseEntity.class,f.tracked,h->h.hasCustomName()&&h.getCustomName().getString().equals(f.horseName)).stream().findFirst().orElseThrow();
            horseId=horse.getUUID(); horse.dropLeash(true,false);
            guard=f.fixture.createCitizen(); check(guard!=null&&guard.getEntity().isPresent(),"Cavalry input spawn");
            var entity=guard.getEntity().orElseThrow(); guardId=entity.getUUID(); entity.setPos(539.5,16,35.5);
            guard.setSaturation(20);guard.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
            check(f.stable.getModule(BuildingModules.CAVALRY_STABLE_WORK).assignCitizen(guard)&&guard.getJob() instanceof JobCavalry,"Native cavalry assignment");
            for(var item:List.of(ModItems.spear,Items.SHIELD,Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS))
                check(ItemHandlerHelper.insertItemStacked(f.stable.getItemHandlerCap((Direction)null),new ItemStack(item),false).isEmpty(),"Equipment supply");
            ArcologyStableReleaseTests.disableRecovery(entity);ArcologyStableReleaseTests.disableRecovery(horse);
            previousGuard=entity.position();previousHorse=horse.position();
            f.stable.getModule(BuildingModules.STABLE_SETTINGS).getSetting(AbstractBuildingGuards.GUARD_TASK).set(GuardTaskSetting.PATROL);
            f.stable.getSetting(AbstractBuildingGuards.PATROL_MODE).set(GuardPatrolModeSetting.MANUAL);
            f.stable.getSetting(BuildingStable.PATROL_INTERVAL).setValue(0);
            f.stable.resetPatrolTargets();f.stable.addPatrolTarget(new BlockPos(539,16,25));f.stable.setTempNextPatrolPoint(new BlockPos(539,16,25));f.stable.startPatrolNext();
            BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(576,16,0),new BlockPos(512,16,40),new BlockPos(544,16,40)};
            for(int tier=1;tier<=5;tier++) {
                var stable=(BuildingStable)f.fixture.colony().getServerBuildingManager().getBuilding(anchors[tier-1]);
                var stalls=new ArrayList<>(stable.stallPositions());stalls.sort(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
                check(stalls.size()==2*tier,"Registered stall count L"+tier);
                route.add(new Stop(tier,-1,anchors[tier-1].offset(-5,0,-5)));
                for(int i=0;i<stalls.size();i++)route.add(new Stop(tier,i,stalls.get(i)));
                route.add(new Stop(tier,-2,anchors[tier-1].offset(-5,0,-5)));
            }
            schedule();
        } catch(Throwable error) { fail(error); } }
        void beginCommands() {
            check(((JobCavalry)guard.getJob()).getMount().equals(horseId)&&horse.getAnimalData().getHomeBuilding()==f.stable,"Native mount ownership");
            commanded=true; legTicks=0;
            var citizen=(com.minecolonies.core.entity.citizen.EntityCitizen)guard.getEntity().orElseThrow();
            // Native command event has priority over patrol, preserving the real mounted pair.
            citizen.getNavigation().stop(); horse.getNavigation().stop();
            // Mounted citizen navigation imports the native mount pathing constraints.
            citizen.getCitizenAI().addTransition(new com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget<com.minecolonies.api.entity.ai.statemachine.states.IState>(() -> {
                if(!finished&&leg<route.size())EntityNavigationUtils.walkToPos(citizen,route.get(leg).position(),0,true);
                return citizen.getCitizenAI().getState();
            }) { @Override public boolean shouldRemove() { return finished; } });
        }
        void schedule() { f.h.runAfterDelay(1,()->{try{tick();}catch(Throwable error){fail(error);}}); }
        void tick() throws Exception {
            ticks++;legTicks++;var citizen=guard.getEntity().orElseThrow();guard.setSaturation(20);
            check(citizen.isAlive()&&horse.isAlive()&&citizen.getUUID().equals(guardId)&&horse.getUUID().equals(horseId)&&guard.getWorkBuilding()==f.stable,"Mounted identity lost");
            check(previousGuard.distanceTo(citizen.position())<12&&previousHorse.distanceTo(horse.position())<12,"Recovery teleport");
            previousGuard=citizen.position();previousHorse=horse.position();
            if(!commanded&&citizen.getVehicle()==horse)beginCommands();
            if(commanded) {
                check(citizen.getVehicle()==horse,"Dismounted during command traversal");
                var stop=route.get(leg);
                if(horse.blockPosition().equals(stop.position())) {
                    if(stop.stall()>=0) rows.add(new ProbeResult("mounted_stall_"+stop.tier()+"_"+(stop.stall()+1),ProbeResult.Outcome.PASS,"Same native-converted/readied horse and natively mounted cavalry reached exact registered stall "+stop.position()+" at tick "+ticks+". Native mounted citizen command-event navigation; recovery disabled, no mounting or position injection during route. Navigation coverage, not autonomous patrol stall selection; unrotated generated blueprint."));
                    horse.getNavigation().stop();leg++;legTicks=0;
                    if(leg==route.size()) {
                        check(rows.size()==30,"Missing stall results"); finished=true;
                        rows.add(new ProbeResult("mounted_stall_roundtrip",ProbeResult.Outcome.PASS,"Same mounted pair traversed all 30 registered stalls across Stable1-5, including an exterior approach and exterior return for each tier. Native worker conversion/readiness and guard mount selection preceded command-driven navigation. Initial starter tether/equipment/daytime disclosed; no post-start teleport or direct mounting. Other rotations and organic all-stall patrol not certified."));
                        ProbeReport.write(f.output,"ArcologyMountedStalls",rows);f.h.succeed();return;
                    }
                }
                check(legTicks<6000,"Exact mounted destination unreachable: "+stop+" horse="+horse.position());
            } else check(ticks<60000,"Native mounting timeout");
            if(ticks%400==0)Files.writeString(f.output.resolve("mounted-stalls-diagnostics.txt"),"ticks="+ticks+" leg="+leg+" commanded="+commanded+" horse="+horse.position()+" guard="+citizen.position()+" AI="+guard.getJob().getWorkerAI().getState()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            schedule();
        }
        void fail(Throwable error) { finished=true;try{rows.add(new ProbeResult("mounted_stall_failure",ProbeResult.Outcome.FAIL,error.toString()));ProbeReport.write(f.output,"ArcologyMountedStalls",rows);}catch(Exception ignored){}f.h.fail(error.toString()); }
    }
    static void check(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}


