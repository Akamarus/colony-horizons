package dev.colonyhorizons.supply;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Protected endpoint transport, including registered native warehouse racks. */
public final class NativeSupplyService {
    private static final String MARKER="colonyhorizons:supply_endpoint";
    private static final Map<ServerLevel,NativeSupplyService> SERVICES=new IdentityHashMap<>();
    public record Endpoint(UUID id,UUID owner,int colony,String name,BlockPos position,boolean outpost){
        public Endpoint{position=position.immutable();}
    }
    private final ServerLevel level;
    private final Path file;
    private final Map<UUID,Endpoint> endpoints=new LinkedHashMap<>();
    private final Map<UUID,UUID> managedRoutes=new LinkedHashMap<>();
    private SupplyNetwork network=new SupplyNetwork();
    private final SupplyCheckpoint checkpoint;
    private String problem="";
    public static NativeSupplyService get(ServerLevel level){return SERVICES.computeIfAbsent(level,NativeSupplyService::new);}
    public static void stop(){SERVICES.clear();}
    private NativeSupplyService(ServerLevel level){
        this.level=level;
        file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data/colonyhorizons_native_supply.dat");
        checkpoint=new SupplyCheckpoint(this::write);
        if(Files.exists(file))try{load(NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()));}
        catch(Exception e){problem="Recovery needed: "+e.getMessage();}
    }
    public SupplyNetwork.RouteSettings settings(UUID id){return network.settings(id);}
    public Optional<Endpoint> endpointAt(UUID actor,BlockPos pos){return endpoints.values().stream().filter(e->e.owner.equals(actor)&&e.position.equals(pos)).findFirst();}
    public String problem(){return problem;}
    public SupplyNetwork.RouteView route(UUID id){return network.route(id);}
    public List<Endpoint> endpoints(UUID actor){return endpoints.values().stream().filter(e->e.owner.equals(actor)).toList();}
    public List<SupplyNetwork.RouteView> routes(UUID actor){return network.routeIds().stream().filter(id->endpoints.get(network.route(id).source()).owner.equals(actor)).map(network::route).toList();}
    public String availability(Endpoint endpoint){return resolve(endpoint)==null?"Unavailable: unloaded, replaced, claim or ownership changed":"Ready";}

    public UUID bind(UUID actor,BlockPos position,String name,boolean outpost){
        healthy();
        if(name.isBlank()||name.length()>32)throw new IllegalArgumentException("Name must contain 1-32 characters");
        if(endpoints.values().stream().anyMatch(e->e.position.equals(position)||e.owner.equals(actor)&&e.name.equals(name)))throw new IllegalArgumentException("Hut or name already bound");
        if(!level.hasChunkAt(position)||!(level.getBlockEntity(position) instanceof TileEntityColonyBuilding hut))throw new IllegalArgumentException("Choose a loaded MineColonies hut block");
        var colony=IColonyManager.getInstance().getColonyByPosFromWorld(level,position);
        if(colony==null||!actor.equals(colony.getPermissions().getOwner())||colony.getServerBuildingManager().getBuilding(position)==null)
            throw new IllegalArgumentException("Only the colony owner can bind its registered huts");
        if(hut.getPersistentData().hasUUID(MARKER))throw new IllegalArgumentException("Hut already has an endpoint identity");
        var id=network.addDepot(actor,colony.getID());var endpoint=new Endpoint(id,actor,colony.getID(),name,position,outpost);
        endpoints.put(id,endpoint);
        // A missing marker after an interrupted binding disables the endpoint; no cargo exists yet.
        persist();hut.getPersistentData().putUUID(MARKER,id);hut.setChanged();
        return id;
    }
    public UUID link(UUID actor,String source,String destination,ItemStack item,int target,int reserve,int batch){
        healthy();var from=named(actor,source);var to=named(actor,destination);
        if(from.outpost==to.outpost)throw new IllegalArgumentException("Link a home hut and an outpost hut");
        if(resolve(from)==null||resolve(to)==null)throw new IllegalArgumentException("Both huts must be loaded and still owned by you");
        if(!warehouseRouteAllowed(from,to))throw new IllegalArgumentException("Remote supplies require completed warehouses and their local courier");
        int travel=(int)Math.min(72000,Math.max(200,Math.ceil(Math.sqrt(from.position.distSqr(to.position))/8.0)*20));
        var id=network.addRoute(actor,from.id,to.id,item,target,reserve,batch,travel);persist();return id;
    }
    /** Coordinator entry point: callers reserve the finite allowance before creating this stable route. */
    public UUID linkBudgeted(UUID actor,UUID operation,UUID source,UUID destination,ItemStack item,int target,int reserve,int batch,int allowance){
        healthy();var from=endpoints.get(source);var to=endpoints.get(destination);
        if(from==null||to==null||!from.owner.equals(actor)||!to.owner.equals(actor))throw new SecurityException("Only the endpoint owner may create a supply route");
        if(from.outpost==to.outpost)throw new IllegalArgumentException("Link a home hut and an outpost hut");
        if(resolve(from)==null||resolve(to)==null)throw new IllegalArgumentException("Both huts must be loaded and still owned by you");
        if(!warehouseRouteAllowed(from,to))throw new IllegalArgumentException("Remote supplies require completed warehouses and their local courier");
        int travel=(int)Math.min(72000,Math.max(200,Math.ceil(Math.sqrt(from.position.distSqr(to.position))/8.0)*20));
        var id=network.addBudgetedRoute(operation,actor,source,destination,item,target,reserve,batch,travel,allowance);persist();return id;
    }
    public int allowanceRemaining(UUID route){return network.allowanceRemaining(route);}
    /** A managed route and its development binding are written in the same custody snapshot. */
    public UUID linkManaged(UUID actor,UUID development,UUID operation,UUID source,UUID destination,ItemStack item,int target,int batch){
        healthy();var ledger=dev.colonyhorizons.mayor.MayorReservations.get(level);var entry=ledger.entries().get(operation);
        if(entry==null||entry.closed()||!entry.owner().equals(actor)||!entry.development().equals(development)||entry.cost().size()!=1)throw new IllegalArgumentException("Missing approved supply reservation");
        var key=new dev.colonyhorizons.remote.RemoteModels.ItemKey(item);var allowance=entry.cost().get(key);
        if(allowance==null||allowance<=0)throw new IllegalArgumentException("Reserved item differs");
        var record=dev.colonyhorizons.remote.RemoteDevelopmentData.get(level).find(development).orElseThrow();var from=endpoints.get(source);var to=endpoints.get(destination);
        if(!record.owner().equals(actor)||from==null||to==null||from.outpost||!to.outpost||!from.position.equals(record.draft().request().homeSupply())||!entry.source().equals(from.position)||record.draft().buildings().stream().noneMatch(b->b.anchor().equals(to.position)))throw new IllegalArgumentException("Supply endpoints differ from approved settlement");
        if(dev.colonyhorizons.remote.FoundingConstructionSupplies.warehouseFirst(record)&&(!(resolve(from)!=null&&resolve(from).getBuilding() instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse)||record.draft().buildings().stream().noneMatch(b->b.role().equals("warehouse")&&b.anchor().equals(to.position))||!dev.colonyhorizons.mayor.MayorLogistics.ready(level,development)))throw new IllegalArgumentException("Managed supply requires ready Home and outpost warehouses and a local courier");
        if(network.routeIds().contains(operation)&&!development.equals(managedRoutes.get(operation)))throw new IllegalArgumentException("Cannot replace a player route");
        if(!network.routeIds().contains(operation)&&entry.dispatched().values().stream().anyMatch(n->n!=0))throw new IllegalArgumentException("Missing previously dispatched route requires recovery");
        ledger.checkpoint(level);boolean fresh=!managedRoutes.containsKey(operation);managedRoutes.put(operation,development);
        try{return linkBudgeted(actor,operation,source,destination,item,target,entry.reserves().getOrDefault(key,0),batch,allowance);}
        catch(RuntimeException e){if(fresh)managedRoutes.remove(operation);throw e;}
    }
    private boolean warehouseRouteAllowed(Endpoint from,Endpoint to){
        if(from==null||to==null)return false;
        for(var r:NativeCourierDistricts.remoteRecords(level,from.colony))if(dev.colonyhorizons.remote.FoundingConstructionSupplies.warehouseFirst(r)&&(r.draft().chunks().contains(new ChunkPos(from.position))||r.draft().chunks().contains(new ChunkPos(to.position)))){
            var remote=r.draft().chunks().contains(new ChunkPos(from.position))?from:to;var home=remote==from?to:from;
            if(!remote.outpost||home.outpost||!home.position.equals(r.draft().request().homeSupply())||r.draft().buildings().stream().noneMatch(b->b.role().equals("warehouse")&&b.anchor().equals(remote.position))||!dev.colonyhorizons.mayor.MayorLogistics.ready(level,r.id()))return false;
            for(var endpoint:List.of(from,to)){var hut=resolve(endpoint);if(hut==null||!(hut.getBuilding() instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse warehouse)||!warehouse.isBuilt()||warehouse.getBuildingLevel()<1)return false;}
        }return true;
    }
    private boolean managedActive(UUID route){
        var view=network.route(route);if(!warehouseRouteAllowed(endpoints.get(view.source()),endpoints.get(view.destination())))return false;
        var id=managedRoutes.get(route);if(id==null)return true;
        var record=dev.colonyhorizons.remote.RemoteDevelopmentData.get(level).find(id).orElseThrow();var entry=dev.colonyhorizons.mayor.MayorReservations.get(level).entries().get(route);
        if(entry==null||entry.closed()||!entry.development().equals(id)||!entry.owner().equals(record.owner()))return false;
        if(dev.colonyhorizons.remote.FoundingConstructionSupplies.warehouseFirst(record)&&!dev.colonyhorizons.mayor.MayorLogistics.ready(level,id))return false;
        var owner=level.getServer().getPlayerList().getPlayer(record.owner());
        return owner!=null&&!owner.isRemoved()&&!(owner instanceof net.neoforged.neoforge.common.util.FakePlayer)&&owner.serverLevel()==level&&dev.colonyhorizons.remote.RemoteTickets.snapshot(level).containsKey(id)&&dev.colonyhorizons.mayor.MayorService.available(level,id);
    }
    private void reconcileReservations(){
        if(managedRoutes.isEmpty())return;var ledger=dev.colonyhorizons.mayor.MayorReservations.get(level);boolean changed=false;
        for(var id:managedRoutes.keySet()){var entry=ledger.entries().get(id);if(entry==null)throw new IllegalStateException("Managed supply reservation missing");var item=new dev.colonyhorizons.remote.RemoteModels.ItemKey(network.settings(id).item());var total=entry.cost().get(item);if(total==null)throw new IllegalStateException("Managed item changed");int spent=total-network.allowanceRemaining(id);if(spent!=entry.dispatched().getOrDefault(item,0)){ledger.recordDispatched(id,Map.of(item,spent));changed=true;}}
        if(changed)ledger.checkpoint(level);
    }
    public void pause(UUID actor,UUID route,boolean value){healthy();authorize(actor,route);network.pause(actor,route,value);persist();}
    public void cancel(UUID actor,UUID route){healthy();authorize(actor,route);network.cancel(actor,route);persist();}
    private void authorize(UUID actor,UUID route){
        var r=network.route(route);
        for(var id:List.of(r.source(),r.destination())){
            var e=endpoints.get(id);var colony=IColonyManager.getInstance().getColonyByDimension(e.colony,level.dimension());
            if(!e.owner.equals(actor)||colony==null||!actor.equals(colony.getPermissions().getOwner()))throw new SecurityException("Colony ownership changed");
        }
    }
    public Endpoint named(UUID actor,String name){return endpoints.values().stream().filter(e->e.owner.equals(actor)&&e.name.equals(name)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown endpoint: "+name));}
    public void tick(int ticks){
        if(!problem.isEmpty()||endpoints.isEmpty())return;
        try{
            var huts=new LinkedHashMap<UUID,TileEntityColonyBuilding>();var views=new LinkedHashMap<UUID,WarehouseInventory.View>();var handlers=new LinkedHashMap<UUID,IItemHandlerModifiable>();
            for(var e:endpoints.values()){var hut=resolve(e);if(hut!=null){try{var view=WarehouseInventory.resolve(level,hut);huts.put(e.id,hut);views.put(e.id,view);handlers.put(e.id,view.inventory());}catch(WarehouseInventory.Unavailable unavailable){/* Preflight only: no custody changed; retry this endpoint next tick. */}}}
            var batch=new SupplyInventoryBatch(handlers);var before=snapshot();
            // Clone the ledger: neither live cargo nor hut inventories change during planning.
            var trial=SupplyNetwork.load(network.save(level.registryAccess()),level.registryAccess());
            reconcileReservations();trial.tick(ticks,handlers.keySet(),batch,this::managedActive);
            var after=before.copy();after.put("network",trial.save(level.registryAccess()));
            if(before.equals(after)&&!batch.changed())return;
            if(batch.changed())checkpoint.commit(before,after,()->{
                for(var e:endpoints.values())if(huts.containsKey(e.id)&&resolve(e)!=huts.get(e.id))throw new IllegalStateException("Endpoint identity changed during handoff");
                views.values().forEach(v->v.validate(level));batch.apply();views.values().forEach(v->v.entities().forEach(net.minecraft.world.level.block.entity.BlockEntity::setChanged));
            },()->NativeSupplyCheckpoint.flushAndVerify(level,views.values().stream().flatMap(v->v.entities().stream()).distinct().toList()));
            else write(after);
            network=trial;reconcileReservations();
        }catch(Exception e){problem="Recovery needed: "+e.getMessage();}
    }
    private TileEntityColonyBuilding resolve(Endpoint e){
        if(!level.hasChunkAt(e.position))return null;
        var colony=IColonyManager.getInstance().getColonyByDimension(e.colony,level.dimension());
        if(colony==null||!e.owner.equals(colony.getPermissions().getOwner())||colony.getServerBuildingManager().getBuilding(e.position)==null)return null;
        var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(e.position));
        if(claim==null||claim.getOwningColony()!=e.colony)return null;
        if(!(level.getBlockEntity(e.position) instanceof TileEntityColonyBuilding hut))return null;
        var tag=hut.getPersistentData();return tag.hasUUID(MARKER)&&tag.getUUID(MARKER).equals(e.id)?hut:null;
    }
    private void healthy(){if(!problem.isEmpty()||checkpoint.blocked())throw new IllegalStateException(problem.isEmpty()?"Recovery needed":problem);}
    private void persist(){try{write(snapshot());}catch(RuntimeException e){problem="Recovery needed: "+e.getMessage();throw e;}}
    private CompoundTag snapshot(){
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putString("dimension",level.dimension().location().toString());tag.put("network",network.save(level.registryAccess()));
        var list=new ListTag();for(var e:endpoints.values()){
            var t=new CompoundTag();t.putUUID("id",e.id);t.putUUID("owner",e.owner);t.putInt("colony",e.colony);t.putString("name",e.name);t.putLong("pos",e.position.asLong());t.putBoolean("outpost",e.outpost);list.add(t);
        }tag.put("endpoints",list);var managed=new ListTag();managedRoutes.forEach((route,development)->{var m=new CompoundTag();m.putUUID("route",route);m.putUUID("development",development);managed.add(m);});tag.put("managed",managed);return tag;
    }
    private void load(CompoundTag tag){
        if(tag.contains("pending"))throw new IllegalStateException("Interrupted inventory handoff; retained before/after snapshots need operator review");
        if(tag.getInt("schema")!=1||!level.dimension().location().toString().equals(tag.getString("dimension"))||!tag.contains("network",Tag.TAG_COMPOUND)||!tag.contains("endpoints",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid native supply save");
        var restored=SupplyNetwork.load(tag.getCompound("network"),level.registryAccess());var loaded=new LinkedHashMap<UUID,Endpoint>();var positions=new HashSet<BlockPos>();var names=new HashSet<String>();
        var raw=(ListTag)tag.get("endpoints");
        if(!raw.isEmpty()&&raw.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid endpoint list");
        var list=tag.getList("endpoints",Tag.TAG_COMPOUND);
        if(list.size()!=restored.depotIds().size())throw new IllegalArgumentException("Endpoint/depot mismatch");
        for(int i=0;i<list.size();i++){
            var t=list.getCompound(i);
            if(!t.hasUUID("id")||!t.hasUUID("owner")||!t.contains("pos",Tag.TAG_LONG)||!t.contains("outpost",Tag.TAG_BYTE)||(t.getByte("outpost")!=0&&t.getByte("outpost")!=1))throw new IllegalArgumentException("Malformed endpoint");
            var e=new Endpoint(t.getUUID("id"),t.getUUID("owner"),t.getInt("colony"),t.getString("name"),BlockPos.of(t.getLong("pos")),t.getBoolean("outpost"));var d=restored.depotView(e.id);
            if(!d.owner().equals(e.owner)||d.colony()!=e.colony||e.name.isBlank()||e.name.length()>32||!positions.add(e.position)||!names.add(e.owner+":"+e.name)||loaded.putIfAbsent(e.id,e)!=null)throw new IllegalArgumentException("Invalid endpoint identity");
        }
        if(tag.contains("managed")){
            if(!tag.contains("managed",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid managed route list");var managed=(ListTag)tag.get("managed");if(managed.size()>256||!managed.isEmpty()&&managed.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid managed routes");
            for(var element:managed){var m=(CompoundTag)element;if(!m.hasUUID("route")||!m.hasUUID("development")||!restored.routeIds().contains(m.getUUID("route"))||restored.allowanceRemaining(m.getUUID("route"))==Integer.MAX_VALUE||managedRoutes.putIfAbsent(m.getUUID("route"),m.getUUID("development"))!=null)throw new IllegalArgumentException("Invalid managed route identity");}
        }
        network=restored;endpoints.putAll(loaded);
    }
    private void write(CompoundTag tag){
        try{Files.createDirectories(file.getParent());SupplyFileWriter.write(()->IOUtilities.writeNbtCompressed(tag,file));}
        catch(IOException e){throw new IllegalStateException("Could not persist supply custody",e);}
    }
}
