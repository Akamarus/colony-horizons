package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.blocks.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.locations.StaticLocation;
import com.minecolonies.core.colony.requestsystem.resolvers.DeliveryRequestResolver;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.api.RotationMirror;
import com.mojang.authlib.GameProfile;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.supply.NativeCourierDistricts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Native request selection tests; movement and inventory completion have separate test gates. */
@PrefixGameTestTemplate(false)
public final class CourierNativeTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=600)
    public static void native_selection(GameTestHelper h) {
        var origin=new ChunkPos(h.absolutePos(BlockPos.ZERO));
        for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(origin.x+x,origin.z+z,true);
        h.runAfterDelay(40,()->{try{run(h);}catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void run(GameTestHelper h)throws Exception {
        var rows=new ArrayList<ProbeResult>();
        var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
        var chunk=new ChunkPos(f.center());
        var base=new BlockPos(chunk.getMinBlockX()+2,f.center().getY(),chunk.getMinBlockZ()+2);
        var warehouse=place(h,f,base,ModBlocks.blockHutWareHouse,"craftsmanship/storage/warehouse1.blueprint");
        var hut=place(h,f,base.offset(4,0,0),ModBlocks.blockHutDeliveryman,"craftsmanship/storage/deliveryman1.blueprint");
        boolean travel="CourierTravel".equals(System.getProperty("colonyhorizons.foundationSuite"));
        var from=place(h,f,base.offset(0,0,travel?12:4),ModBlocks.blockHutLumberjack,"fundamentals/lumberjack1.blueprint");
        var to=place(h,f,base.offset(travel?12:4,0,travel?12:4),ModBlocks.blockHutLumberjack,"fundamentals/lumberjack1.blueprint");
        var remote=place(h,f,base.offset(32,0,0),ModBlocks.blockHutLumberjack,"fundamentals/lumberjack1.blueprint");
        var citizen=f.createCitizen();
        SupplyTests.check(hut.getModule(BuildingModules.COURIER_WORK).assignCitizen(citizen));
        SupplyTests.check(warehouse.getModule(BuildingModules.WAREHOUSE_COURIERS).assignCitizen(citizen));
        var job=(JobDeliveryman)citizen.getJob();
        var queue=warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
        var manager=f.colony().getRequestManager();
        var localId=request(from,to);
        var remoteId=request(remote,to);
        var outboundId=request(from,remote);
        var resolver=new DeliveryRequestResolver(new StaticLocation(warehouse.getPosition(),h.getLevel().dimension()),localId);
        @SuppressWarnings("unchecked") var localRequest=(com.minecolonies.api.colony.requestsystem.request.IRequest<? extends Delivery>)(Object)manager.getRequestForToken(localId);
        @SuppressWarnings("unchecked") var remoteRequest=(com.minecolonies.api.colony.requestsystem.request.IRequest<? extends Delivery>)(Object)manager.getRequestForToken(remoteId);
        @SuppressWarnings("unchecked") var outboundRequest=(com.minecolonies.api.colony.requestsystem.request.IRequest<? extends Delivery>)(Object)manager.getRequestForToken(outboundId);
        SupplyTests.run(rows,"unmanaged_selection",()->SupplyTests.check(resolver.canResolveRequest(manager,remoteRequest)));
        var p=remote.getPosition();
        var record=new OutpostRecord(UUID.randomUUID(),f.colony().getID(),h.getLevel().dimension(),"Remote",Set.of(new ChunkPos(p)),
            new BoundingBox(p),new BoundingBox(p),Map.of(p,new OutpostRecord.BuildingRef(p,BuiltInRegistries.BLOCK.getKey(h.getLevel().getBlockState(p).getBlock()),"Arcology Horizons","fundamentals/lumberjack1.blueprint",new BoundingBox(p))),
            Set.of(),Set.of(),Optional.empty(),OutpostRecord.State.PLANNED,List.of());
        OutpostSavedData.get(h.getLevel()).register(record);NativeCourierDistricts.activate(h.getLevel(),record.id());
        if(travel){CourierTravelTests.start(h,f,job,warehouse,from,to);return;}
        if("CourierRestart".equals(System.getProperty("colonyhorizons.foundationSuite"))) {
            queue.getMutableRequestList().clear();manager.assignRequest(localId);
            SupplyTests.check(job.getCurrentTask()!=null && job.getTaskQueue().contains(localId));
            CourierRestartTests.seed(h,f,job,warehouse,from,to);return;
        }
        SupplyTests.run(rows,"local_resolver_allowed",()->SupplyTests.check(resolver.canResolveRequest(manager,localRequest)));
        SupplyTests.run(rows,"remote_source_resolver_rejected",()->SupplyTests.check(!resolver.canResolveRequest(manager,remoteRequest)));
        SupplyTests.run(rows,"remote_target_resolver_rejected",()->SupplyTests.check(!resolver.canResolveRequest(manager,outboundRequest)));
        SupplyTests.run(rows,"remote_attempt_rejected",()->SupplyTests.check(resolver.attemptResolveRequest(manager,remoteRequest)==null));
        SupplyTests.run(rows,"remote_enqueue_rejected",()->{
            queue.getMutableRequestList().clear();resolver.resolveRequest(manager,remoteRequest);
            SupplyTests.check(!queue.getMutableRequestList().contains(remoteId));
        });
        SupplyTests.run(rows,"mixed_batch_selects_only_local",()->{
            queue.getMutableRequestList().clear();queue.addRequest(remoteId);queue.addRequest(localId);
            var task=job.getCurrentTask();
            SupplyTests.check(task!=null && task.getId().equals(localId));
            SupplyTests.check(job.getTaskQueue().equals(List.of(localId)));
            SupplyTests.check(queue.getMutableRequestList().contains(remoteId));
        });
        SupplyTests.run(rows,"changed_claim_blocks_work",()->{
            var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(h.getLevel().dimension(),new ChunkPos(base));
            claim.setOwningColony(999999,h.getLevel().getChunkAt(base));
            try {SupplyTests.check(!dev.colonyhorizons.supply.NativeCourierGuard.checkWork(citizen.getEntity().orElseThrow()).allowed());}
            finally {claim.setOwningColony(f.colony().getID(),h.getLevel().getChunkAt(base));}
        });
        SupplyTests.run(rows,"local_path_allowed",()->{
            var entity=citizen.getEntity().orElseThrow();entity.setPos(base.getX()+4.5,base.getY(),base.getZ()+0.5);
            var nodes=List.of(new net.minecraft.world.level.pathfinder.Node(base.getX()+4,base.getY(),base.getZ()),new net.minecraft.world.level.pathfinder.Node(base.getX()+4,base.getY(),base.getZ()+4));
            SupplyTests.check(dev.colonyhorizons.supply.NativeCourierGuard.checkPath(entity,new net.minecraft.world.level.pathfinder.Path(nodes,to.getPosition(),true)).allowed());
        });
        SupplyTests.run(rows,"remote_detour_rejected",()->{
            var entity=citizen.getEntity().orElseThrow();
            var nodes=List.of(new net.minecraft.world.level.pathfinder.Node(p.getX(),p.getY(),p.getZ()),new net.minecraft.world.level.pathfinder.Node(to.getPosition().getX(),p.getY(),to.getPosition().getZ()));
            SupplyTests.check(!dev.colonyhorizons.supply.NativeCourierGuard.checkPath(entity,new net.minecraft.world.level.pathfinder.Path(nodes,to.getPosition(),true)).allowed());
        });
        SupplyTests.run(rows,"segment_crossing_rejected",()->{
            var entity=citizen.getEntity().orElseThrow();
            // A long segment can cross the remote footprint even when its endpoint is outside it.
            var far=p.offset(32,0,0);
            var nodes=List.of(new net.minecraft.world.level.pathfinder.Node(far.getX(),far.getY(),far.getZ()));
            SupplyTests.check(!dev.colonyhorizons.supply.NativeCourierGuard.checkPath(entity,new net.minecraft.world.level.pathfinder.Path(nodes,far,true)).allowed());
        });
        SupplyTests.run(rows,"native_navigation_rejects_detour",()->{
            var entity=(com.minecolonies.core.entity.citizen.EntityCitizen)citizen.getEntity().orElseThrow();
            new com.minecolonies.core.entity.ai.workers.CitizenAI(entity); // Same-tick fixture: register native transitions before selecting a work state.
            entity.getCitizenAI().transitionToNext(new com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition<>(entity.getCitizenAI().getState(),()->true,()->com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState.WORKING,1));
            var nodes=List.of(new net.minecraft.world.level.pathfinder.Node(p.getX(),p.getY(),p.getZ()),new net.minecraft.world.level.pathfinder.Node(to.getPosition().getX(),p.getY(),to.getPosition().getZ()));
            if(entity.getNavigation().moveTo(new net.minecraft.world.level.pathfinder.Path(nodes,to.getPosition(),true),1.0))throw new AssertionError("Unsafe path accepted: state="+entity.getCitizenAI().getState()+" nav="+entity.getNavigation().getClass()+" job="+entity.getCitizenData().getJob()+" guard="+dev.colonyhorizons.supply.NativeCourierGuard.checkPath(entity,new net.minecraft.world.level.pathfinder.Path(nodes,to.getPosition(),true)));
        });
        SupplyTests.run(rows,"native_blocked_delivery_retains_cargo",()->{
            var entity=citizen.getEntity().orElseThrow();
            var target=to.getPosition();entity.setPos(target.getX()+0.5,target.getY(),target.getZ()+0.5);
            for(int slot=0;slot<citizen.getInventory().getSlots();slot++)citizen.getInventory().setStackInSlot(slot,ItemStack.EMPTY);
            citizen.getInventory().setStackInSlot(0,new ItemStack(Items.OAK_LOG,4));
            var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(h.getLevel().dimension(),new ChunkPos(base));
            claim.setOwningColony(999999,h.getLevel().getChunkAt(base));
            var tokens=List.copyOf(job.getTaskQueue());
            try {
                var ai=new com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman(job);
                var method=ai.getClass().getDeclaredMethod("deliver");method.setAccessible(true);method.invoke(ai);
                SupplyTests.eq(citizen.getInventory().getStackInSlot(0).getCount(),4);
                SupplyTests.check(job.getTaskQueue().equals(tokens));
                SupplyTests.eq(ArcologyOperationsFixture.count(to.getItemHandlerCap(),new ItemStack(Items.OAK_LOG)),0);
            } catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            finally {claim.setOwningColony(f.colony().getID(),h.getLevel().getChunkAt(base));}
        });
        SupplyTests.run(rows,"native_blocked_pickup_preserves_stock",()->{
            var entity=citizen.getEntity().orElseThrow();
            var source=from.getPosition();entity.setPos(source.getX()+0.5,source.getY(),source.getZ()+0.5);
            for(int slot=0;slot<citizen.getInventory().getSlots();slot++)citizen.getInventory().setStackInSlot(slot,ItemStack.EMPTY);
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(from.getItemHandlerCap(),new ItemStack(Items.OAK_LOG,4),false);
            var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(h.getLevel().dimension(),new ChunkPos(base));
            claim.setOwningColony(999999,h.getLevel().getChunkAt(base));
            try {
                var ai=new com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman(job);
                SupplyTests.check(!ai.gatherIfInTileEntity(h.getLevel().getBlockEntity(source),new ItemStack(Items.OAK_LOG,4)));
                SupplyTests.eq(ArcologyOperationsFixture.count(from.getItemHandlerCap(),new ItemStack(Items.OAK_LOG)),4);
                SupplyTests.eq(ArcologyOperationsFixture.count(citizen.getInventory(),new ItemStack(Items.OAK_LOG)),0);
            } finally {claim.setOwningColony(f.colony().getID(),h.getLevel().getChunkAt(base));}
        });
        SupplyTests.run(rows,"owner_change_retains_custody",()->{
            var entity=citizen.getEntity().orElseThrow();
            SupplyTests.check(dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());
            var replacement=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"NewOwner"));
            f.colony().getPermissions().setOwner(replacement);
            try {SupplyTests.check(!dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());}
            finally {f.colony().getPermissions().setOwner(FakePlayerFactory.get(h.getLevel(),new GameProfile(f.ownerId(),"CourierProbe")));}
            SupplyTests.check(dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());
        });
        SupplyTests.run(rows,"warehouse_replacement_retains_custody",()->{
            var entity=citizen.getEntity().orElseThrow();
            SupplyTests.check(dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());
            var tile=h.getLevel().getBlockEntity(base);var tag=tile.saveWithFullMetadata(h.getLevel().registryAccess());
            var state=h.getLevel().getBlockState(base);
            h.getLevel().setBlockAndUpdate(base,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            h.getLevel().setBlockAndUpdate(base,state);
            try {SupplyTests.check(!dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());}
            finally {h.getLevel().getBlockEntity(base).loadWithComponents(tag,h.getLevel().registryAccess());}
            SupplyTests.check(dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());
        });
        SupplyTests.run(rows,"warehouse_rack_source_allowed",()->{
            var rack=base.offset(1,0,1);
            h.getLevel().setBlockAndUpdate(rack,ModBlocks.blockRack.defaultBlockState());
            warehouse.addContainerPosition(rack);
            var token=manager.createRequest(to.getRequester(),new Delivery(new StaticLocation(rack,h.getLevel().dimension()),new StaticLocation(to.getPosition(),h.getLevel().dimension()),new ItemStack(Items.OAK_LOG,4),1));
            var decision=dev.colonyhorizons.supply.NativeCourierGuard.checkRequest(job,manager.getRequestForToken(token));
            if(!decision.allowed())throw new AssertionError("Registered rack rejected: "+decision+" registered="+warehouse.getContainers()+" tile="+h.getLevel().getBlockEntity(rack));
            warehouse.removeContainerPosition(rack);
            SupplyTests.check(!dev.colonyhorizons.supply.NativeCourierGuard.checkRequest(job,manager.getRequestForToken(token)).allowed());
        });
        SupplyTests.run(rows,"resume_ongoing_delivery_without_dump",()->{
            citizen.getInventory().setStackInSlot(0,new ItemStack(Items.OAK_LOG,4));job.addConcurrentDelivery(localId);
            var ai=new com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman(job);
            try {
                var decide=ai.getClass().getDeclaredMethod("decide");decide.setAccessible(true);
                SupplyTests.check(decide.invoke(ai)==com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.PREPARE_DELIVERY);
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            finally {job.removeConcurrentDelivery(localId);}
        });
        SupplyTests.run(rows,"completed_pickup_keeps_custody",()->{
            var entity=citizen.getEntity().orElseThrow();
            SupplyTests.check(dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());
            java.util.List<com.minecolonies.api.colony.requestsystem.token.IToken<?>> nativeQueue;
            try {
                var method=JobDeliveryman.class.getDeclaredMethod("getTaskQueueFromDataStore");method.setAccessible(true);
                nativeQueue=(java.util.List<com.minecolonies.api.colony.requestsystem.token.IToken<?>>)method.invoke(job);
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            var tokens=new ArrayList<>(nativeQueue);nativeQueue.clear();
            var replacement=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"NewOwner"));
            f.colony().getPermissions().setOwner(replacement);
            try {SupplyTests.check(!dev.colonyhorizons.supply.NativeCourierGuard.checkWork(entity).allowed());}
            finally {f.colony().getPermissions().setOwner(FakePlayerFactory.get(h.getLevel(),new GameProfile(f.ownerId(),"CourierProbe")));nativeQueue.addAll(tokens);}
        });
        SupplyTests.run(rows,"managed_stuck_recovery_suppressed",()->{
            var entity=citizen.getEntity().orElseThrow();
            var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)entity.getNavigation();
            var original=nav.getStuckHandler();int[] attempts={0};
            nav.setStuckHandler(new com.minecolonies.api.entity.pathfinding.IStuckHandler<com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate>() {
                public void checkStuck(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate n){attempts[0]++;}
                public void resetGlobalStuckTimers(){} public int getStuckLevel(){return 9;}
            });
            try {for(int i=0;i<30;i++)nav.tick();SupplyTests.eq(attempts[0],0);}
            finally {nav.setStuckHandler(original);}
        });
        SupplyTests.run(rows,"unsafe_path_job_never_teleports",()->{
            var entity=citizen.getEntity().orElseThrow();
            var before=entity.position();var dest=base.offset(1000,0,0);
            var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)entity.getNavigation();
            nav.moveTo(dest.getX(),dest.getY(),dest.getZ(),1.0);
            SupplyTests.check(entity.position().equals(before));
            SupplyTests.check(nav.isDone());
        });
        SupplyTests.run(rows,"indirect_local_path_can_be_computed",()->{
            var entity=citizen.getEntity().orElseThrow();entity.setPos(base.getX()+4.5,base.getY(),base.getZ()+0.5);
            var dest=base.offset(64,0,0);
            var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)entity.getNavigation();
            try {nav.walkTo(dest,1.0);SupplyTests.check(nav.getPathResult()!=null);}
            finally {nav.stop();}
        });
        SupplyTests.run(rows,"warehouse_remote_stock_rejected",()->{
            ((com.minecolonies.core.tileentities.TileEntityColonyBuilding)h.getLevel().getBlockEntity(base)).getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,16));
            var token=manager.createRequest(remote.getRequester(),new com.minecolonies.api.colony.requestsystem.requestable.Stack(new ItemStack(Items.BREAD,4)));
            @SuppressWarnings("unchecked") var wanted=(com.minecolonies.api.colony.requestsystem.request.IRequest<? extends com.minecolonies.api.colony.requestsystem.requestable.IDeliverable>)(Object)manager.getRequestForToken(token);
            var stockResolver=new com.minecolonies.core.colony.requestsystem.resolvers.WarehouseConcreteRequestResolver(new StaticLocation(base,h.getLevel().dimension()),token);
            SupplyTests.check(!stockResolver.canResolveRequest(manager,wanted));
            SupplyTests.check(stockResolver.attemptResolveRequest(manager,wanted)==null);
        });
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"CourierNative",rows);
        h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Native selection failures: "+rows.stream().filter(r->r.outcome()!=ProbeResult.Outcome.PASS).map(ProbeResult::caseId).toList());
        h.succeed();
    }
    private static IToken<?> request(IBuilding from,IBuilding to) {
        var dim=from.getColony().getDimension();
        return from.getColony().getRequestManager().createRequest(to.getRequester(),new Delivery(new StaticLocation(from.getPosition(),dim),new StaticLocation(to.getPosition(),dim),new ItemStack(Items.OAK_LOG,4),1));
    }
    private static IBuilding place(GameTestHelper h,OutpostProbeFixture f,BlockPos p,AbstractBlockHut<?> block,String path)throws Exception {
        var level=h.getLevel();level.getChunkAt(p);
        var pack=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
        var bp=new Blueprint((short)1,(short)1,(short)1,level.registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);
        var player=FakePlayerFactory.get(level,new GameProfile(f.ownerId(),"CourierProbe"));
        SupplyTests.check(block.setup(player,level,p,bp,RotationMirror.NONE,true,pack,path));
        var building=f.colony().getServerBuildingManager().getBuilding(p);building.setBuildingLevel(1);return building;
    }
}
