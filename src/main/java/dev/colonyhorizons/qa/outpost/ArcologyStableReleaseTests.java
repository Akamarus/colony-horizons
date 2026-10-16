package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Supplied adult inputs; only native workers feed, breed, train and maintain the herd. */
@PrefixGameTestTemplate(false)
public final class ArcologyStableReleaseTests {
    static final int CAPACITY_TICKS = 12000;
    static final int HERD_TICKS = 36000;
    @GameTest(template="empty", templateNamespace="colonyhorizons", timeoutTicks=90000)
    public static void stable_release(GameTestHelper h) { start(h, System.getProperty("colonyhorizons.foundationSuite", "").equals("ArcologyStableCapacity")); }

    static void start(GameTestHelper h, boolean capacity) {
        for (int x=28;x<=39;x++) for (int z=-2;z<=5;z++) h.getLevel().setChunkForced(x,z,true);
        h.runAfterDelay(40, () -> {
            var fixture = new ArcologyStableTests.Run(h);
            fixture.afterSetup = f -> { try { new Run(f, capacity).start(); } catch (Throwable e) { h.fail(e.toString()); } };
            fixture.start();
        });
    }
    static final class Run {
        final ArcologyStableTests.Run f;
        final boolean capacity;
        final List<Herd> herds = new ArrayList<>();
        final List<ProbeResult> rows = new ArrayList<>();
        int ticks;
        Run(ArcologyStableTests.Run f, boolean capacity) { this.f=f; this.capacity=capacity; }
        String suite() { return capacity ? "ArcologyStableCapacity" : "ArcologyStableCycles"; }
        void start() throws Exception {
            f.worker.setPaused(true);
            f.source.discard();
            BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(576,16,0),new BlockPos(512,16,40),new BlockPos(544,16,40)};
            for (var anchor:anchors) herds.add(new Herd((BuildingStable)f.fixture.colony().getServerBuildingManager().getBuilding(anchor)));
            schedule();
        }
        void schedule() { f.h.runAfterDelay(1, () -> { try { tick(); } catch(Throwable e) { fail(e); } }); }
        void tick() throws Exception {
            ticks++;
            for (var herd:herds) herd.observe();
            if (ticks%1000==0) {
                var message=new StringBuilder("ticks="+ticks+"\n");
                for (var herd:herds) message.append("L").append(herd.tier).append(" births=").append(herd.births.size()).append(" mounts=").append(herd.mounts()).append(" attempts=").append(herd.attempts()).append(" food=").append(herd.food()).append(" AI=").append(herd.worker.getJob().getWorkerAI().getState()).append('\n');
                Files.writeString(f.output.resolve(suite()+"-diagnostics.txt"),message,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }
            if (ticks >= (capacity ? CAPACITY_TICKS : HERD_TICKS)) {
                for (var herd:herds) herd.finish();
                ProbeReport.write(f.output,suite(),rows);
                f.h.succeed();
            } else schedule();
        }
        void fail(Throwable error) {
            try { rows.add(new ProbeResult("stable_release_failure",ProbeResult.Outcome.FAIL,"ticks="+ticks+" "+error)); ProbeReport.write(f.output,suite(),rows); } catch(Exception ignored) { }
            f.h.fail(error.toString());
        }
        final class Herd {
            final BuildingStable stable;
            final ICitizenData worker;
            final UUID workerId;
            final int tier, initialFood;
            final AABB area;
            final Set<UUID> births=new HashSet<>();
            final Set<UUID> inputs=new HashSet<>();
            final Map<UUID,Vec3> previous=new HashMap<>();
            final Set<String> names=new HashSet<>();
            final Map<String,AbstractHorse> offspring=new HashMap<>();
            int offspringConversions;
            int firstBirth=-1, lastBirth=-1;
            boolean sawLove;
            Herd(BuildingStable stable) {
                this.stable=stable; tier=stable.getBuildingLevel();
                var a=stable.getPosition();
                area=new AABB(Vec3.atLowerCornerOf(a.offset(-14,-1,-3)),Vec3.atLowerCornerOf(a.offset(5,8,22)));
                // Every tier has a disclosed external two-high fence. No containment claim for open blueprints.
                for(int x=a.getX()-13;x<=a.getX()+3;x++)for(int z=a.getZ()-2;z<=a.getZ()+19;z++)
                    if(x==a.getX()-13||x==a.getX()+3||z==a.getZ()-2||z==a.getZ()+19)
                        for(int y=16;y<=17;y++)f.level.setBlock(new BlockPos(x,y,z),Blocks.OAK_FENCE.defaultBlockState(),3);
                worker=tier==5?f.worker:f.fixture.createCitizen(); check(worker!=null&&worker.getEntity().isPresent(),"Worker spawn L"+tier);
                var entity=worker.getEntity().orElseThrow(); entity.setPos(a.getX()-3.5,16,a.getZ()+2.5); workerId=entity.getUUID();
                worker.setPaused(false); worker.setSaturation(20); worker.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));
                check((tier==5&&worker.getWorkBuilding()==stable)||stable.getModule(BuildingModules.STABLEMASTER_WORK).assignCitizen(worker),"Worker assignment L"+tier);
                disableRecovery(entity); previous.put(workerId,entity.position());
                // Clear inherited L5 setup stock before supplying this independent measured fixture.
                var stock=stable.getItemHandlerCap((Direction)null);
                for(int slot=0;slot<stock.getSlots();slot++)stock.extractItem(slot,stock.getStackInSlot(slot).getCount(),false);
                for(var item:List.of(Items.LEAD,Items.WOODEN_AXE,Items.GOLDEN_APPLE,Items.WHEAT,Items.LEATHER,Items.LEATHER_HORSE_ARMOR))
                    check(ItemHandlerHelper.insertItemStacked(stable.getItemHandlerCap((Direction)null),new ItemStack(item,item==Items.WOODEN_AXE||item==Items.LEATHER_HORSE_ARMOR?1:64),false).isEmpty(),"Supply insertion L"+tier);
                int count=capacity?2*tier:2;
                for(int i=0;i<count;i++) {
                    var horse=EntityType.HORSE.create(f.level); check(horse!=null,"Input spawn");
                    horse.setPos(a.getX()-8.5+(i%2)*6,16,a.getZ()+4.5+(i/2)*3);
                    horse.setTamed(true); horse.setPersistenceRequired();
                    String name="release-L"+tier+"-parent-"+i; names.add(name); horse.setCustomName(Component.literal(name));
                    check(f.level.addFreshEntity(horse),"Input insertion");
                    AbstractHorse input=horse;
                    // At L1 two home cavalry would already fill the limit: ordinary eligible parents are required.
                    if(capacity||tier>1) {
                        var cav=CavalryHorseEntity.createFromVanilla(f.fixture.colony(),f.level,horse); check(cav!=null,"Registered adult input");
                        cav.getAnimalData().setHomeBuilding(stable); cav.setHealth(cav.getMaxHealth()); input=cav;
                    }
                    inputs.add(input.getUUID()); disableRecovery(input); previous.put(input.getUUID(),input.position());
                    check(!input.isBaby()&&!input.isInLove()&&!input.isNoAi(),"Eligible input contract");
                }
                initialFood=food(); check(attempts()==0&&stable.getSetting(AbstractBuilding.BREEDING).getValue(),"Initial breeding setting/statistics");
            }
            int mounts() { return f.fixture.colony().getAnimalManager().getAnimalsOfClassByHome(CavalryHorseEntity.class,stable).size(); }
            int attempts() { return stable.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("breeding_attempts"); }
            int food() { return ArcologyOperationsFixture.count(worker.getInventory(),new ItemStack(Items.GOLDEN_APPLE))+ArcologyOperationsFixture.count(stable.getItemHandlerCap((Direction)null),new ItemStack(Items.GOLDEN_APPLE)); }
            void observe() {
                var citizen=worker.getEntity().orElseThrow(); worker.setSaturation(20);
                check(citizen.isAlive()&&citizen.getUUID().equals(workerId)&&worker.getWorkBuilding()==stable&&!worker.isPaused(),"Worker lost L"+tier);
                check(previous.put(workerId,citizen.position()).distanceTo(citizen.position())<12,"Worker recovery L"+tier);
                var animals=f.level.getEntitiesOfClass(AbstractHorse.class,area);
                for(String name:names) check(animals.stream().filter(e->e.hasCustomName()&&e.getCustomName().getString().equals(name)).count()==1,"Input lost/duplicated L"+tier+" "+name);
                for(var horse:animals) {
                    check(horse.isAlive()&&!horse.isNoAi()&&stable.isInBuilding(horse.blockPosition()),"Herd died/escaped L"+tier);
                    var old=previous.put(horse.getUUID(),horse.position()); if(old==null)disableRecovery(horse); else check(old.distanceTo(horse.position())<12,"Horse recovery L"+tier);
                    sawLove|=horse.isInLove();
                    if(horse.isBaby()&&!inputs.contains(horse.getUUID())&&births.add(horse.getUUID())) {
                        if(firstBirth<0)firstBirth=ticks; lastBirth=ticks;
                        // Identification only: native conversion explicitly copies custom names.
                        // Do not alter age, AI, health, love, persistence or the horse's position.
                        String name="release-L"+tier+"-offspring-"+horse.getUUID();
                        horse.setCustomName(Component.literal(name)); names.add(name); offspring.put(name,horse);
                    }
                }
                for(var entry:offspring.entrySet()) {
                    var matches=animals.stream().filter(e->e.hasCustomName()&&e.getCustomName().getString().equals(entry.getKey())).toList();
                    check(matches.size()==1,"Offspring lost/duplicated L"+tier+" "+entry.getKey());
                    var current=matches.getFirst(); var prior=entry.getValue();
                    check(current.isAlive()&&!current.isNoAi()&&stable.isInBuilding(current.blockPosition()),"Offspring died/escaped L"+tier);
                    if(!current.getUUID().equals(prior.getUUID())) {
                        check(!(prior instanceof CavalryHorseEntity)&&prior.isRemoved()&&!prior.isBaby(),"Unexpected offspring replacement L"+tier);
                        check(current instanceof CavalryHorseEntity cav&&!cav.isBaby()&&cav.getAnimalData()!=null&&cav.getAnimalData().getHomeBuilding()==stable,"Offspring replacement is not a native adult home cavalry conversion L"+tier);
                        check(previous.get(prior.getUUID()).distanceTo(current.position())<12,"Offspring conversion position jump L"+tier);
                        check(stable.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("horses_trained")>offspringConversions,"Offspring conversion without native training statistic L"+tier);
                        offspringConversions++; entry.setValue(current);
                    }
                }
                check(mounts()<=2*tier,"Native registered mount capacity exceeded L"+tier);
                if(capacity) check(mounts()==2*tier&&attempts()==0&&births.isEmpty()&&!sawLove,"Breeding at native capacity L"+tier);
                // A finite population bound catches runaway output without claiming that the native mount limit caps all foals.
                check(animals.size()<=2*tier+12,"Unbounded herd growth within observation L"+tier);
            }
            void finish() {
                if(capacity) {

                    rows.add(new ProbeResult("stable_capacity_"+tier,ProbeResult.Outcome.PASS,"Exactly "+(2*tier)+" supplied eligible home cavalry; same active native worker, breeding enabled and golden apples available for "+ticks+" ticks. Zero feeding attempts, love or offspring; native registered capacity maintained. Supplied adult inputs, equipment, fixed daytime, saturation and external two-high fence; bounded gate observation only."));
                } else {
                    check(!births.isEmpty()&&sawLove&&attempts()>=2&&initialFood-food()>=2,"No native birth L"+tier);
                    if(tier>1)check(births.size()>=2&&lastBirth-firstBirth>=6000,"No second natural cooldown-separated birth L"+tier);
                    rows.add(new ProbeResult("stable_breeding_"+tier,ProbeResult.Outcome.PASS,"Native feeding/love and "+births.size()+" distinct natural foal UUIDs; attempts="+attempts()+", first/last birth="+firstBirth+"/"+lastBirth+". "+(tier==1?"Two supplied ordinary adult parents; native training may fill L1 capacity after birth.":"Two supplied home cavalry parents; at least two births separated by natural 6000-tick parent cooldown.")));
                    rows.add(new ProbeResult("stable_bounded_herd_"+tier,ProbeResult.Outcome.PASS,"Observed "+ticks+" ticks with same active worker, original named parents and every observed uniquely named offspring retained exactly once (verified native adult conversions="+offspringConversions+"), live AI, no escape, no recovery jumps, registered cavalry <= "+(2*tier)+" and total herd <= "+(2*tier+12)+". Offspring receive identification-only names copied by native conversion; no birth/growth/cooldown injection or acceleration. Food/tools, saturation, daylight and external fence supplied. This finite observation does not establish permanent stability or that foals count toward cavalry capacity."));
                }
            }
        }
    }
    static void disableRecovery(net.minecraft.world.entity.Mob entity) { if(entity.getNavigation() instanceof MinecoloniesAdvancedPathNavigate nav)nav.setStuckHandler(PathingStuckHandler.createStuckHandler()); }
    static void check(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }
}







