package dev.colonyhorizons.supply;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.*;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import dev.colonyhorizons.supply.CourierDistrictPolicy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Live validation shared by assignment and work hooks; never completes or cancels requests. */
public final class NativeCourierGuard {
    private NativeCourierGuard() {}
    public static Decision checkDestination(com.minecolonies.api.entity.citizen.AbstractEntityCitizen courier,BlockPos destination) {
        var decision=checkWork(courier);if(!decision.allowed())return decision;
        var job=(JobDeliveryman)courier.getCitizenData().getJob();var level=(ServerLevel)courier.level();
        var endpoint=NativeCourierDistricts.resolve(level,job.getColony().getID(),job.getCitizen().getWorkBuilding().getPosition());
        if(endpoint.status()==Status.UNMANAGED)return decision;
        var records=dev.colonyhorizons.outpost.OutpostSavedData.get(level).all();
        if(destination==null || endpoint.district().isEmpty() || !within(level,endpoint.district().get(),records,courier.blockPosition())
            || !within(level,endpoint.district().get(),records,destination))return new Decision(false,"Courier destination is outside its district");
        return decision;
    }
    public static Decision checkWork(com.minecolonies.api.entity.citizen.AbstractEntityCitizen courier) {
        if(courier.getCitizenData()==null || !(courier.getCitizenData().getJob() instanceof JobDeliveryman job))return new Decision(true,"");
        if(!(courier.level() instanceof ServerLevel level))return new Decision(false,"Server unavailable");
        var work=job.getCitizen().getWorkBuilding();
        var district=NativeCourierDistricts.resolve(level,job.getColony().getID(),work==null?null:work.getPosition());
        if(district.status()==Status.UNMANAGED)return new Decision(true,"");
        var warehouse=job.findWareHouse();
        var store=NativeCourierDistricts.resolve(level,job.getColony().getID(),warehouse==null?null:warehouse.getPosition());
        var decision=CourierDistrictPolicy.check(district,store,district,store);
        if(!decision.allowed())return decision;
        for(var token:job.getTaskQueue()) {
            decision=checkRequest(job,job.getColony().getRequestManager().getRequestForToken(token));
            if(!decision.allowed())return decision;
        }
        return NativeCourierCustody.validate(courier,job,level);
    }
    public static Decision checkPath(com.minecolonies.api.entity.citizen.AbstractEntityCitizen courier,net.minecraft.world.level.pathfinder.Path path) {
        var workDecision=checkWork(courier);
        if(!workDecision.allowed())return workDecision;
        if(courier.getCitizenData()==null || !(courier.getCitizenData().getJob() instanceof JobDeliveryman job))return workDecision;
        var level=(ServerLevel)courier.level();
        var work=job.getCitizen().getWorkBuilding();
        var endpoint=NativeCourierDistricts.resolve(level,job.getColony().getID(),work==null?null:work.getPosition());
        if(endpoint.status()==Status.UNMANAGED || path==null)return workDecision;
        if(endpoint.district().isEmpty())return new Decision(false,"Courier district unavailable");
        var district=endpoint.district().get();
        var records=dev.colonyhorizons.outpost.OutpostSavedData.get(level).all();
        var previous=courier.position();
        if(path.getNodeCount()>4096)return new Decision(false,"Courier path exceeds local validation limit");
        for(int index=path.getNextNodeIndex();index<path.getNodeCount();index++) {
            var node=path.getNode(index);
            var next=new net.minecraft.world.phys.Vec3(node.x+0.5,node.y,node.z+0.5);
            int steps=(int)Math.ceil(previous.distanceTo(next)*4);
            if(steps>4096)return new Decision(false,"Courier path segment exceeds local validation limit");
            for(int step=0;step<=steps;step++) {
                var point=previous.lerp(next,steps==0?0:(double)step/steps);
                double radius=courier.getBbWidth()/2.0;
                for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1}) {
                    var pos=BlockPos.containing(point.x+dx*radius,point.y,point.z+dz*radius);
                    if(!within(level,district,records,pos))return new Decision(false,"Courier path leaves its district");
                }
            }
            previous=next;
        }
        return workDecision;
    }
    private static boolean within(ServerLevel level,District district,List<dev.colonyhorizons.outpost.OutpostRecord> records,BlockPos position) {
        if(!level.hasChunkAt(position))return false;
        var chunk=new net.minecraft.world.level.ChunkPos(position);
        var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
        if(claim==null || claim.getOwningColony()!=district.colonyId())return false;
        try{
            var remote=NativeCourierDistricts.remoteRecords(level,district.colonyId());
            var containingRemote=remote.stream().filter(r->r.draft().chunks().contains(chunk)).findFirst();
            if(containingRemote.isPresent()){var r=containingRemote.orElseThrow();return district.outpostId().filter(r.id()::equals).isPresent()&&Set.of(dev.colonyhorizons.remote.RemoteModels.Phase.STAGING,dev.colonyhorizons.remote.RemoteModels.Phase.BUILDING,dev.colonyhorizons.remote.RemoteModels.Phase.OPERATING).contains(r.phase());}
            if(district.outpostId().isPresent()&&remote.stream().anyMatch(r->r.id().equals(district.outpostId().orElseThrow())))return false;
        }catch(RuntimeException failure){return false;}
        var containing=records.stream().filter(r->r.approvedChunks().contains(chunk)).findFirst();
        return district.outpostId().isEmpty()?containing.isEmpty():containing.isPresent() && containing.get().id().equals(district.outpostId().get())
            && containing.get().state()==dev.colonyhorizons.outpost.OutpostRecord.State.ACTIVE;
    }
    public static Decision checkRequest(ServerLevel level,int colonyId,BlockPos workplace,BlockPos warehouse,IRequest<?> request) {
        var work=NativeCourierDistricts.resolve(level,colonyId,workplace);
        if(work.status()==Status.UNMANAGED)return new Decision(true,"");
        if(request==null)return new Decision(false,"Courier request unavailable");
        var store=NativeCourierDistricts.resolve(level,colonyId,warehouse);
        if(request.getRequest() instanceof Delivery delivery) {
            return CourierDistrictPolicy.check(work,store,resolve(level,colonyId,delivery.getStart()),resolve(level,colonyId,delivery.getTarget()));
        }
        if(request.getRequest() instanceof Pickup) {
            return CourierDistrictPolicy.check(work,store,resolve(level,colonyId,request.getRequester().getLocation()),store);
        }
        return new Decision(false,"Unsupported managed courier request");
    }
    private static Endpoint resolve(ServerLevel level,int colonyId,ILocation location) {
        if(location==null || !level.dimension().equals(location.getDimension()))
            return new Endpoint(Status.BLOCKED,Optional.empty(),"Courier request crosses dimensions");
        return NativeCourierDistricts.resolve(level,colonyId,location.getInDimensionLocation());
    }
    public static Decision checkRequest(JobDeliveryman job,IRequest<?> request) {
        if(!(job.getColony().getWorld() instanceof ServerLevel level))return new Decision(false,"Server unavailable");
        var work=job.getCitizen().getWorkBuilding();var warehouse=job.findWareHouse();
        return checkRequest(level,job.getColony().getID(),work==null?null:work.getPosition(),warehouse==null?null:warehouse.getPosition(),request);
    }
    /** Reject remote warehouse stock before native resolution creates an undeliverable child request. */
    public static boolean warehouseStockAllowed(IRequestManager manager,BlockPos warehouse,IRequest<?> request){
        if(!(manager.getColony().getWorld() instanceof ServerLevel level)||request==null)return false;
        var source=NativeCourierDistricts.resolve(level,manager.getColony().getID(),warehouse);
        var destination=resolve(level,manager.getColony().getID(),request.getRequester().getLocation());
        return CourierDistrictPolicy.check(source,source,source,destination).allowed();
    }
    public static boolean eligible(IRequestManager manager,BlockPos warehouse,IRequest<?> request) {
        if(!(manager.getColony().getWorld() instanceof ServerLevel level))return false;
        var endpoint=NativeCourierDistricts.resolve(level,manager.getColony().getID(),warehouse);
        if(endpoint.status()==Status.UNMANAGED)return true;
        if(endpoint.status()!=Status.ACTIVE)return false;
        var building=manager.getColony().getServerBuildingManager().getBuilding(warehouse);
        if(building==null || !building.hasModule(BuildingModules.WAREHOUSE_COURIERS))return false;
        for(var citizen:building.getModule(BuildingModules.WAREHOUSE_COURIERS).getAssignedCitizen()) {
            var work=citizen.getWorkBuilding();
            if(work!=null && checkRequest(level,manager.getColony().getID(),work.getPosition(),warehouse,request).allowed())return true;
        }
        return false;
    }
    /** Snapshot for native selection and batching; only native's final removals touch the actual queue. */
    public static List<IToken<?>> selectionQueue(JobDeliveryman job,WarehouseRequestQueueModule module) {
        var backing=module.getMutableRequestList();
        var selected=new ArrayList<IToken<?>>();
        for(var token:backing) {
            var request=job.getColony().getRequestManager().getRequestForToken(token);
            if(request==null || checkRequest(job,request).allowed())selected.add(token);
        }
        return new ArrayList<IToken<?>>(selected) {
            @Override public boolean removeAll(Collection<?> tokens) {
                // Bound removals to entries that were visible through this snapshot.
                var removals=new ArrayList<>(this);removals.retainAll(tokens);
                boolean changed=backing.removeAll(removals);super.removeAll(removals);return changed;
            }
        };
    }
}
