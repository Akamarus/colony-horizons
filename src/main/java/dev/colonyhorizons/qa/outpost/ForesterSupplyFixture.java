package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLumberjack;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.authlib.GameProfile;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.supply.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.items.*;
import java.nio.file.*;
import java.util.*;

/** Disclosed prebuilt QA settlement; all production, consumption and shipments run through native code. */
final class ForesterSupplyFixture {
    private static final BlockPos HOME=new BlockPos(0,-59,0), WOODS=new BlockPos(512,-59,0);
    private final GameTestHelper h;
    private final ServerLevel level;
    private final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
    private final List<ProbeResult> rows=new ArrayList<>();
    private final List<BlockPos> trees=new ArrayList<>();
    private final Set<BlockPos> harvested=new HashSet<>(),replanted=new HashSet<>();
    private OutpostProbeFixture colony;
    private IBuilding home,house;
    private BuildingLumberjack forester;
    private ICitizenData worker;
    private NativeSupplyService service;
    private UUID export,workerIdentity;
    private int ticks,pauseAt=-1;
    private long pauseDelivered;
    private boolean foodConsumed,axeUsed,logsDeposited,resumed,restocked,replacementUsed,suppliedSaplingUsed;
    private int shortageAt=-1,shortageLogs;
    private ItemStack suppliedSapling;
    private int recoveryPhase,recoveryAt;private long recoveryDelivered;
    private final List<ItemStack> savedHome=new ArrayList<>();
    private final boolean localCouriers="ForesterWithCouriers".equals(System.getProperty("colonyhorizons.foundationSuite"));
    private ICitizenData courier;private UUID courierIdentity;private final List<IBuilding> localLogistics=new ArrayList<>();
    ForesterSupplyFixture(GameTestHelper h){this.h=h;level=h.getLevel();}
    void start(){try {
        // This fixture owns endpoint chunk tickets so it can test real unload/reload deterministically.
        com.minecolonies.core.MineColonies.getConfig().getServer().forceLoadColony.set(false);
        level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,level.getServer());
        level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
        for(int x=480;x<=566;x++)for(int z=-32;z<=56;z++){
            level.setBlock(new BlockPos(x,-60,z),Blocks.GRASS_BLOCK.defaultBlockState(),18);
            for(int y=-59;y<=-43;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),18);
        }
        var profile=new GameProfile(UUID.randomUUID(),"ForesterSupply");level.getServer().getProfileCache().add(profile);
        colony=OutpostProbeFixture.create(level,HOME,profile.getId());
        ChunkDataHelper.staticClaimInRange((Colony)colony.colony(),true,WOODS,4,level,false);
        var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=colony;
        var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
        setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,-59,0);
        colony.colony().getPackageManager().addImportantColonyPlayer(setup.owner);colony.colony().getPackageManager().addCloseSubscriber(setup.owner);
        home=setup.place("fundamentals/townhall1.blueprint",HOME,true);
        forester=(BuildingLumberjack)setup.place("fundamentals/lumberjack1.blueprint",WOODS,true);
        house=setup.place("fundamentals/citizen1.blueprint",WOODS.offset(0,0,32),true);
        if(localCouriers){
            var warehouse=setup.place("craftsmanship/storage/warehouse1.blueprint",WOODS.offset(-32,0,0),true);
            var courierHut=setup.place("craftsmanship/storage/deliveryman1.blueprint",WOODS.offset(-32,0,32),true);
            localLogistics.add(warehouse);localLogistics.add(courierHut);
            courier=colony.createCitizen();check(courierHut.getModule(BuildingModules.COURIER_WORK).assignCitizen(courier),"Local courier work assignment");
            check(warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS).assignCitizen(courier),"Native local warehouse assignment");
            var courierEntity=courier.getEntity().orElseThrow();courierIdentity=courierEntity.getUUID();courierEntity.setPos(482.5,-59,28.5);courier.setSaturation(20);
            // Player-accessible Pickup: Never leaves produced logs in the protected export buffer.
            forester.alterPickUpPriority(-forester.getPickUpPriority());
        }
        worker=colony.createCitizen();check(worker.getEntity().isPresent(),"Native worker spawn");
        check(forester.getModule(BuildingModules.FORESTER_WORK).assignCitizen(worker),"Native forester assignment");
        check(house.getModule(BuildingModules.LIVING).assignCitizen(worker),"Native housing assignment");
        var entity=worker.getEntity().orElseThrow();workerIdentity=entity.getUUID();entity.setPos(514.5,-59,-4.5);
        ((MinecoloniesAdvancedPathNavigate)entity.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
        for(int slot=0;slot<worker.getInventory().getSlots();slot++)worker.getInventory().setStackInSlot(slot,ItemStack.EMPTY);
        worker.setSaturation(4); // One initial hunger condition; never reset during the run.
        worker.getInventory().setStackInSlot(4,new ItemStack(Items.OAK_SAPLING)); // Disclosed native first-tree bootstrap.
        forester.setRestrictedArea(new BlockPos(530,-60,-28),new BlockPos(565,-44,12));
        for(int x=536;x<=560;x+=8)for(int z=-24;z<=0;z+=8){
            var tree=new BlockPos(x,-59,z);trees.add(tree);
            for(int y=0;y<5;y++)level.setBlockAndUpdate(tree.above(y),Blocks.OAK_LOG.defaultBlockState());
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=3;y<=5;y++)if(dx!=0||dz!=0||y==5)level.setBlockAndUpdate(tree.offset(dx,y,dz),Blocks.OAK_LEAVES.defaultBlockState());
        }
        var chunks=new HashSet<ChunkPos>();for(int x=localCouriers?29:30;x<=35;x++)for(int z=-2;z<=3;z++)chunks.add(new ChunkPos(x,z));
        var refs=new HashMap<BlockPos,OutpostRecord.BuildingRef>();
        localLogistics.add(forester);localLogistics.add(house);
        for(var b:localLogistics){var p=b.getPosition();refs.put(p,new OutpostRecord.BuildingRef(p,BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()),"Arcology Horizons",b.getBlueprintPath(),new BoundingBox(p)));}
        var people=localCouriers?Set.of(worker.getId(),courier.getId()):Set.of(worker.getId());
        var record=new OutpostRecord(UUID.randomUUID(),colony.colony().getID(),level.dimension(),"Forestry",chunks,new BoundingBox(localCouriers?464:480,-63,-32,575,-32,63),new BoundingBox(530,-60,-28,565,-44,12),refs,people,people,Optional.empty(),OutpostRecord.State.PLANNED,List.of());
        OutpostSavedData.get(level).register(record);NativeCourierDistricts.activate(level,record.id());
        var inventory=((TileEntityColonyBuilding)level.getBlockEntity(HOME)).getInventory();
        for(int slot=0;slot<inventory.getSlots();slot++)inventory.setStackInSlot(slot,ItemStack.EMPTY);
        var firstAxe=new ItemStack(Items.STONE_AXE);firstAxe.setDamageValue(firstAxe.getMaxDamage()-1);
        suppliedSapling=new ItemStack(Items.OAK_SAPLING);suppliedSapling.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("QA supply sapling"));
        for(var stack:List.of(new ItemStack(Items.BREAD,32),firstAxe,new ItemStack(Items.STONE_HOE),suppliedSapling.copyWithCount(32)))check(ItemHandlerHelper.insertItemStacked(inventory,stack,false).isEmpty(),"Home input capacity");
        service=NativeSupplyService.get(level);service.bind(profile.getId(),HOME,"home",false);service.bind(profile.getId(),WOODS,"woods",true);
        service.link(profile.getId(),"home","woods",new ItemStack(Items.BREAD),8,0,8);
        service.link(profile.getId(),"home","woods",firstAxe,1,0,1);
        service.link(profile.getId(),"home","woods",new ItemStack(Items.STONE_AXE),1,0,1);
        service.link(profile.getId(),"home","woods",new ItemStack(Items.STONE_HOE),1,0,1);
        service.link(profile.getId(),"home","woods",suppliedSapling,8,0,8);
        export=service.link(profile.getId(),"woods","home",new ItemStack(Items.OAK_LOG),64,4,8);
        check(count(home.getItemHandlerCap(),Items.OAK_LOG)+count(forester.getItemHandlerCap(),Items.OAK_LOG)+count(worker.getInventory(),Items.OAK_LOG)==0,"No seeded output logs");
        Files.writeString(output.resolve("forester-setup.txt"),"QA: prebuilt Arcology level-1 town hall, forester and home; native setup prerequisites supplied; one parent colony, claimed test endpoints, forced endpoint chunks only. 16 planted test trees. Finite home input: 32 bread, 1 stone axe with one durability left, 1 stone hoe, 32 named oak saplings. One additional normal stone axe is added at home after a witnessed remote tool shortage. One initial worker sapling and saturation 4. No further worker inventory injection, harvesting or stock transfer by fixture. No couriers in this production fixture; courier behavior has separate native suites.\n");
        SupplyTiming.begin();schedule();
    } catch(Throwable failure){fail(failure);}}
    private void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable failure){fail(failure);}});}
    private void tick()throws Exception {
        ticks+=20;check(ticks<75000,"Forester supply timed out; inspect diagnostics");check(service.problem().isEmpty(),service.problem());
        var entity=worker.getEntity().orElseThrow();check(entity.isAlive()&&entity.getUUID().equals(workerIdentity),"Forester identity/life");
        check(entity.distanceToSqr(WOODS.getX(),WOODS.getY(),WOODS.getZ())<10000,"Forester left local district");
        if(localCouriers){var c=courier.getEntity().orElseThrow();check(c.isAlive()&&c.getUUID().equals(courierIdentity),"Local courier survived");check(c.distanceToSqr(WOODS.getX(),WOODS.getY(),WOODS.getZ())<10000,"Local courier left settlement");}
        if(recoveryPhase>=4){if(recoverDestination())complete();else schedule();return;}
        for(var tree:trees){if(!level.getBlockState(tree.above()).is(Blocks.OAK_LOG))harvested.add(tree);if(level.getBlockState(tree).is(Blocks.OAK_SAPLING))replanted.add(tree);}
        int bread=count(home.getItemHandlerCap(),Items.BREAD)+count(forester.getItemHandlerCap(),Items.BREAD)+count(worker.getInventory(),Items.BREAD);
        for(var route:service.routes(colony.ownerId()))if(route.cargo().is(Items.BREAD))bread+=route.cargo().getCount();
        foodConsumed|=bread<32 && worker.getSaturation()>4;
        for(int slot=0;slot<worker.getInventory().getSlots();slot++){var stack=worker.getInventory().getStackInSlot(slot);axeUsed|=stack.is(Items.STONE_AXE)&&stack.getDamageValue()>0;}
        logsDeposited|=count(forester.getItemHandlerCap(),Items.OAK_LOG)>0 || service.route(export).delivered()>0 || !service.route(export).cargo().isEmpty();
                int axes=count(home.getItemHandlerCap(),Items.STONE_AXE)+count(forester.getItemHandlerCap(),Items.STONE_AXE)+count(worker.getInventory(),Items.STONE_AXE);
        for(var route:service.routes(colony.ownerId()))if(route.cargo().is(Items.STONE_AXE))axes+=route.cargo().getCount();
        int remainingLogs=0;for(var tree:trees)for(int y=0;y<5;y++)if(level.getBlockState(tree.above(y)).is(Blocks.OAK_LOG))remainingLogs++;
        if(!restocked && axes==0){
            if(shortageAt<0){shortageAt=ticks;shortageLogs=remainingLogs;}
            if(ticks-shortageAt>=400){
                check(remainingLogs==shortageLogs,"Forester cut more logs without an axe during shortage");
                check(ItemHandlerHelper.insertItemStacked(((TileEntityColonyBuilding)level.getBlockEntity(HOME)).getInventory(),new ItemStack(Items.STONE_AXE),false).isEmpty(),"Replacement capacity at home");
                restocked=true;
            }
        }
        for(int slot=0;slot<worker.getInventory().getSlots();slot++){
            var stack=worker.getInventory().getStackInSlot(slot);
            replacementUsed|=restocked&&stack.is(Items.STONE_AXE)&&stack.getDamageValue()>0&&stack.getDamageValue()<stack.getMaxDamage()-1;
        }
        int marked=ArcologyOperationsFixture.count(home.getItemHandlerCap(),suppliedSapling)+ArcologyOperationsFixture.count(forester.getItemHandlerCap(),suppliedSapling)+ArcologyOperationsFixture.count(worker.getInventory(),suppliedSapling);
        for(var route:service.routes(colony.ownerId()))if(ItemStack.isSameItemSameComponents(route.cargo(),suppliedSapling))marked+=route.cargo().getCount();
        for(var dropped:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-32,-64,-64,600,0,96)))if(ItemStack.isSameItemSameComponents(dropped.getItem(),suppliedSapling))marked+=dropped.getItem().getCount();
        suppliedSaplingUsed|=marked<32&&!replanted.isEmpty();
        if(pauseAt<0 && service.route(export).delivered()>0){pauseAt=ticks;pauseDelivered=service.route(export).delivered();service.pause(colony.ownerId(),export,true);}
        if(pauseAt>=0 && !resumed){check(service.route(export).delivered()==pauseDelivered,"Paused export moved goods");if(ticks-pauseAt>=400 && replanted.size()>=2){service.pause(colony.ownerId(),export,false);resumed=true;}}
        if(ticks%200==0)Files.writeString(output.resolve("forester-diagnostics.txt"),"tick="+ticks+" pos="+entity.blockPosition()+" active="+colony.colony().isActive()+" state="+worker.getJob().getWorkerAI().getState()+" saturation="+worker.getSaturation()+" bread="+bread+" harvest="+harvested.size()+" replant="+replanted.size()+" workerLogs="+count(worker.getInventory(),Items.OAK_LOG)+" hutLogs="+count(forester.getItemHandlerCap(),Items.OAK_LOG)+" delivered="+service.route(export).delivered()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        if(foodConsumed&&axeUsed&&logsDeposited&&replanted.size()>=2&&resumed&&replacementUsed&&suppliedSaplingUsed&&service.route(export).delivered()>pauseDelivered && recoverDestination()){
            complete();return;
        }
        schedule();
    }
    private void complete()throws Exception {
            pass("replacement_axe_used","Axe broke, remote stock exhausted, a finite replacement shipped from home and acquired wear");pass("shortage_then_recovery","No more logs cut during a 400-tick tool shortage; restocked only at home");pass("sapling_restock_used","Named home-supplied saplings consumed during native replanting");
            pass("finite_home_resupply","Native worker received finite home supplies over protected routes");
            pass("first_harvest_replant","Native harvest and replant observed");pass("native_log_deposit","Native output entered export buffer");
            pass("export_home_receipt","Home received "+service.route(export).delivered()+" native-produced logs");pass("food_consumed","Bread accounting decreased and native saturation increased");
            pass("second_cycle_pause_resume","At least two native replants; export held for 400+ ticks then resumed");pass("forester_identity_and_bounds","Same worker remained alive/local");
            check(count(home.getItemHandlerCap(),Items.OAK_LOG)==service.route(export).delivered(),"Actual home stock must equal delivered native production");
            if(localCouriers)pass("local_warehouse_export_buffer","Native local warehouse and courier coexist with Pickup: Never on forester; real production exported without manual transfer");
            SupplyTiming.finish(output);ProbeReport.write(output,localCouriers?"ForesterWithCouriers":"ForesterSupply",rows);h.succeed();return;
    }
    /** Fault only the disclosed QA destination. No output is created; existing stock is held aside and restored exactly. */
    private boolean recoverDestination() {
        var route=service.route(export);check(route.cargo().getCount()<=8,"Bounded cargo during destination recovery");
        if(recoveryPhase==0){
            recoveryDelivered=route.delivered();var inv=((TileEntityColonyBuilding)level.getBlockEntity(HOME)).getInventory();
            for(int i=0;i<inv.getSlots();i++){savedHome.add(inv.getStackInSlot(i).copy());inv.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));}
            level.getBlockEntity(HOME).setChanged();recoveryPhase=1;recoveryAt=ticks;
        } else if(recoveryPhase==1 && route.reason()==SupplyNetwork.Reason.DESTINATION_FULL){
            check(route.delivered()==recoveryDelivered && !route.cargo().isEmpty(),"Full destination must retain cargo");
            if(ticks-recoveryAt<400)return false;
            var inv=((TileEntityColonyBuilding)level.getBlockEntity(HOME)).getInventory();
            for(int i=0;i<inv.getSlots();i++)inv.setStackInSlot(i,savedHome.get(i));
            level.getBlockEntity(HOME).setChanged();recoveryPhase=2;
        } else if(recoveryPhase==2 && route.delivered()>recoveryDelivered){
            check(count(home.getItemHandlerCap(),Items.OAK_LOG)==route.delivered(),"Full-destination recovery conserved all real logs");
            pass("full_export_destination","Real harvested cargo held at a full destination, then received exactly once");recoveryPhase=3;
        } else if(recoveryPhase==3 && !route.cargo().isEmpty()){
            recoveryDelivered=route.delivered();recoveryAt=ticks;
            for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)level.setChunkForced(x,z,false);
            recoveryPhase=4;
        } else if(recoveryPhase==4){
            if(!level.hasChunkAt(HOME)){
                check(route.delivered()==recoveryDelivered && !route.cargo().isEmpty(),"Unloaded target retains real cargo");
                recoveryAt=ticks;recoveryPhase=5;
            } else check(ticks-recoveryAt<3000,"QA home chunk did not unload: "+level.getChunkSource().getChunkDebugData(new ChunkPos(HOME)));
        } else if(recoveryPhase==5){
            check(!level.hasChunkAt(HOME),"Supply service must not force-load endpoint");
            check(route.delivered()==recoveryDelivered && !route.cargo().isEmpty(),"Unloaded destination preserved shipment");
            if(ticks-recoveryAt>=400){for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)level.setChunkForced(x,z,true);recoveryPhase=6;}
        } else if(recoveryPhase==6 && route.delivered()>recoveryDelivered){
            check(count(home.getItemHandlerCap(),Items.OAK_LOG)==route.delivered(),"Reloaded destination exact inventory accounting");
            pass("unloaded_export_destination","Actual endpoint chunk unloaded for 400 ticks; cargo resumed after reload without service chunk tickets");return true;
        }
        return false;
    }
    private static int count(IItemHandler inventory,Item item){int total=0;for(int slot=0;slot<inventory.getSlots();slot++)if(inventory.getStackInSlot(slot).is(item))total+=inventory.getStackInSlot(slot).getCount();return total;}
    private void pass(String id,String evidence){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence));}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private void fail(Throwable failure){try{var trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));Files.writeString(output.resolve("forester-failure-stack.txt"),trace.toString());rows.add(new ProbeResult("forester_failure",ProbeResult.Outcome.FAIL,failure.toString()));ProbeReport.write(output,"ForesterSupply",rows);}catch(Exception writing){failure.addSuppressed(writing);}h.fail(failure.toString());}
}
