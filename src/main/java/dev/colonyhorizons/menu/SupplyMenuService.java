package dev.colonyhorizons.menu;

import java.util.*;

import com.minecolonies.api.colony.*;

import dev.colonyhorizons.outpost.*;

import dev.colonyhorizons.supply.*;

import net.minecraft.core.BlockPos;

import net.minecraft.server.level.*;

import net.minecraft.network.chat.Component;

import net.minecraft.world.level.ChunkPos;

import static dev.colonyhorizons.menu.SupplyMenuModels.*;

public final class SupplyMenuService {

 private SupplyMenuService(){}

 public static IColony owned(ServerPlayer actor,int colonyId){

  var colony=IColonyManager.getInstance().getColonyByDimension(colonyId,actor.level().dimension());

  if(colony==null||!actor.getUUID().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only this colony's owner can manage outposts and supplies");

  return colony;

 }

 public static boolean loaded(ServerLevel level,BlockPos pos){return level.hasChunkAt(pos);}

 public static MenuSnapshot snapshot(ServerPlayer actor,int colonyId,int hp,int dp,int rp){

  var colony=owned(actor,colonyId);var level=actor.serverLevel();var data=OutpostSavedData.get(level);

  var records=data.all().stream().filter(r->r.colonyId()==colonyId).toList();
  var remotes=dev.colonyhorizons.remote.RemoteDevelopmentData.get(level).all().stream().filter(r->r.owner().equals(actor.getUUID())&&r.draft().request().colonyId()==colonyId&&r.phase()!=dev.colonyhorizons.remote.RemoteModels.Phase.CANCELLED).toList();

  var huts=new ArrayList<HutRow>();

  for(var b:colony.getServerBuildingManager().getBuildings().values()){

   var district=records.stream().filter(r->r.approvedChunks().contains(new ChunkPos(b.getPosition()))).findFirst();

   boolean ready=true;String availability="Ready";try{NativeMenuBuildings.inspect(level,colonyId,b.getPosition());}catch(IllegalArgumentException invalid){ready=false;availability=invalid.getMessage();}

   huts.add(new HutRow(b.getPosition(),text(b.getCustomName().isBlank()?Component.translatable(b.getBuildingDisplayName()).getString():b.getCustomName()),district.map(OutpostRecord::name).orElseGet(()->remotes.stream().filter(r->r.phase()!=dev.colonyhorizons.remote.RemoteModels.Phase.PROPOSED&&r.draft().chunks().contains(new ChunkPos(b.getPosition()))).map(r->r.draft().request().name()).findFirst().orElse("Home")),text(availability),ready));

  }

  huts.sort(Comparator.comparing(HutRow::district).thenComparing(HutRow::name).thenComparingLong(r->r.position().asLong()));

  var districts=new ArrayList<DistrictRow>();
  remotes.stream().sorted(Comparator.comparing(r->r.draft().request().name())).forEach(r->districts.add(new DistrictRow(r.id(),r.draft().request().name(),r.phase().name().replace('_',' '),r.draft().buildings().size(),r.phase()==dev.colonyhorizons.remote.RemoteModels.Phase.PAUSED)));

  districts.add(new DistrictRow(new UUID(0,0),"Home","Main colony",(int)huts.stream().filter(r->r.district().equals("Home")).count(),false));

  records.stream().sorted(Comparator.comparing(OutpostRecord::name).thenComparing(OutpostRecord::id)).forEach(r->districts.add(new DistrictRow(r.id(),text(r.name()),r.blockers().isEmpty()?switch(r.state()){case ACTIVE->"Active";case PAUSED->"Paused";case PLANNED->"Not active";case RECOVERY_NEEDED->"Recovery needed";}:text(r.blockers().getFirst()),r.buildings().size(),r.state()!=OutpostRecord.State.ACTIVE)));

  var service=NativeSupplyService.get(level);var routes=new ArrayList<RouteRow>();var endpoints=new HashMap<UUID,NativeSupplyService.Endpoint>();service.endpoints(actor.getUUID()).forEach(e->endpoints.put(e.id(),e));

  for(var r:service.routes(actor.getUUID())){

   var from=endpoints.get(r.source());var to=endpoints.get(r.destination());if(from==null||to==null||from.colony()!=colonyId||to.colony()!=colonyId)continue;

   var s=service.settings(r.id());routes.add(new RouteRow(r.id(),hutName(huts,from.position()),hutName(huts,to.position()),s.item(),s.target(),s.reserve(),s.batch(),s.paused(),s.disabled(),status(r.reason()),r.cargo().getCount(),(r.remainingTicks()+19)/20,r.delivered()));

  }

  routes.sort(Comparator.comparing(RouteRow::source).thenComparing(RouteRow::id));

  return new MenuSnapshot(colonyId,text(colony.getName()),page(huts,hp),page(districts,dp),page(routes,rp),service.problem().isEmpty()?"":"Supply recovery needed; ask the server operator to check the log");

 }

 private static String hutName(List<HutRow> huts,BlockPos pos){return huts.stream().filter(h->h.position().equals(pos)).map(h->h.name()+" ("+pos.toShortString()+")").findFirst().orElse("Unavailable hut");}

 public static String status(SupplyNetwork.Reason reason){return switch(reason){case IDLE->"Waiting for shipment";case TRAVELLING->"Delivering";case TARGET_MET->"Destination stocked";case SOURCE_RESERVE->"Keeping source reserve";case SOURCE_UNAVAILABLE->"Source unavailable";case DESTINATION_UNAVAILABLE->"Destination unavailable";case DESTINATION_FULL->"Destination full";case RETURN_FULL->"Return inventory full";case PAUSED->"Paused";case RETURNING->"Returning cargo";case DISABLED->"Stopped";case BUDGET_EXHAUSTED->"Approved allowance used";};}

 public static void pauseOutpost(ServerPlayer actor,int colonyId,UUID id,boolean paused){

  owned(actor,colonyId);var data=OutpostSavedData.get(actor.serverLevel());var record=data.find(id).orElseThrow(()->new IllegalArgumentException("Unknown outpost"));

  if(record.colonyId()!=colonyId)throw new SecurityException("Outpost belongs to another colony");

  if(paused)data.register(record.withState(OutpostRecord.State.PAUSED));else NativeCourierDistricts.activate(actor.serverLevel(),id);

 }

}

