package dev.colonyhorizons.menu;
import dev.colonyhorizons.supply.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import java.util.*;
public final class SupplyRouteActions {
 private SupplyRouteActions(){}
 public enum RouteAction{PAUSE,RESUME,STOP}
 public static UUID create(ServerPlayer actor,int colonyId,BlockPos source,BlockPos destination,int target,int reserve,int batch){
  SupplyMenuService.owned(actor,colonyId);
  if(source.equals(destination)||target<1||target>4096||reserve<0||reserve>4096||batch<1||batch>64)throw new IllegalArgumentException("Choose different huts and valid stock limits (target 1–4096, reserve 0–4096, shipment 1–64)");
  var item=actor.getMainHandItem().copyWithCount(1);if(item.isEmpty())throw new IllegalArgumentException("Hold the item you want to ship");
  var from=NativeCourierDistricts.resolve(actor.serverLevel(),colonyId,source);var to=NativeCourierDistricts.resolve(actor.serverLevel(),colonyId,destination);
  if(from.status()!=CourierDistrictPolicy.Status.ACTIVE||to.status()!=CourierDistrictPolicy.Status.ACTIVE)throw new IllegalArgumentException("Both huts must be loaded and in active districts");
  boolean fromOut=from.district().orElseThrow().outpostId().isPresent(),toOut=to.district().orElseThrow().outpostId().isPresent();
  if(fromOut==toOut)throw new IllegalArgumentException("Choose one Home hut and one active outpost hut");
  var service=NativeSupplyService.get(actor.serverLevel());if(!service.problem().isEmpty())throw new IllegalArgumentException("Supply recovery needed; ask the server operator to check the log");
  validateExisting(service,actor,source,fromOut);validateExisting(service,actor,destination,toOut);
  var a=bind(service,actor,source,fromOut);var b=bind(service,actor,destination,toOut);
  return service.link(actor.getUUID(),a.name(),b.name(),item,target,reserve,batch);
 }
 private static void validateExisting(NativeSupplyService service,ServerPlayer actor,BlockPos pos,boolean outpost){
  service.endpointAt(actor.getUUID(),pos).ifPresent(e->{if(e.outpost()!=outpost||!service.availability(e).equals("Ready"))throw new IllegalArgumentException("A previous supply binding no longer matches this hut; ask the server operator to review it");});
 }
 private static NativeSupplyService.Endpoint bind(NativeSupplyService service,ServerPlayer actor,BlockPos pos,boolean outpost){
  return service.endpointAt(actor.getUUID(),pos).orElseGet(()->{service.bind(actor.getUUID(),pos,"hut-"+UUID.randomUUID().toString().replace("-","").substring(0,28),outpost);return service.endpointAt(actor.getUUID(),pos).orElseThrow();});
 }
 public static void change(ServerPlayer actor,int colonyId,UUID route,RouteAction action){
  SupplyMenuService.owned(actor,colonyId);var service=NativeSupplyService.get(actor.serverLevel());
  var r=service.routes(actor.getUUID()).stream().filter(v->v.id().equals(route)).findFirst().orElseThrow(()->new IllegalArgumentException("Route unavailable"));
  var local=service.endpoints(actor.getUUID()).stream().filter(e->e.colony()==colonyId).map(NativeSupplyService.Endpoint::id).toList();
  if(!local.contains(r.source())||!local.contains(r.destination()))throw new SecurityException("Route belongs to another colony");
  if(action==RouteAction.STOP)service.cancel(actor.getUUID(),route);else {if(service.settings(route).disabled())throw new IllegalArgumentException("Stopped routes cannot resume; create a new route");service.pause(actor.getUUID(),route,action==RouteAction.PAUSE);}
 }
}

