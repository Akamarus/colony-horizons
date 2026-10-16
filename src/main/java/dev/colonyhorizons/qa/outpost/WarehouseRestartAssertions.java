package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.mayor.*;
import dev.colonyhorizons.supply.*;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.nio.file.Path;
/** Three real processes: native completed colony, protected cargo, then paused reload. */
final class WarehouseRestartAssertions {
 static ItemStack cargo(){var item=new ItemStack(Items.FLINT);item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Warehouse restart cargo"));return item;}
 static UUID operation(UUID id){return UUID.nameUUIDFromBytes((id+":warehouse-restart").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static void seed(GameTestHelper h,RemoteTestEnvironment env,ServerPlayer owner)throws Exception{
  var l=h.getLevel();var id=env.draft.id();var service=NativeSupplyService.get(l);var target=MayorLogistics.warehouse(l,id).orElseThrow().getPosition();
  var result=MayorSupplyController.ensure(l,id,operation(id),target,cargo(),5);h.assertTrue(result.progressed(),"Finite restart shipment approved: "+result);
  service.tick(1);var before=service.route(operation(id));h.assertTrue(before.cargo().getCount()==5&&ItemStack.isSameItemSameComponents(before.cargo(),cargo()),"Original named cargo in transit");
  var courier=env.home.colony().getCitizenManager().getCivilian(env.draft.request().citizenIds().getLast());courier.setPaused(true);for(int tick=0;tick<10;tick++)service.tick(1000);h.assertTrue(service.route(operation(id)).cargo().getCount()==before.cargo().getCount()&&service.route(operation(id)).remainingTicks()==before.remainingTicks(),"Unavailable local courier pauses protected custody");courier.setPaused(false);
  var home=service.endpointAt(env.owner.getUUID(),env.home.center()).orElseThrow();var builder=env.draft.buildings().getFirst().anchor();var illegal=service.bind(env.owner.getUUID(),builder,"Not a warehouse",true);boolean blocked=false;try{service.link(env.owner.getUUID(),home.name(),"Not a warehouse",cargo(),5,0,5);}catch(IllegalArgumentException expected){blocked=true;}h.assertTrue(blocked,"Player route cannot bypass warehouse-only logistics");
  saveAndLeave(h,owner,env.home.colony());
 }
 static void reload(GameTestHelper h){var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);h.assertTrue(data.all().size()==1,"Original development loaded without reseeding");var r=data.all().getFirst();var phase=System.getProperty("colonyhorizons.probePhase");boolean upgrade=System.getProperty("colonyhorizons.foundationSuite").equals("MayorUpgradeRestart");
  var owner=RemoteActivityTests.loginAtHome(l,r.owner());RemoteActivityService.tick(l);
  h.runAfterDelay(60,()->{try{
   var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());h.assertTrue(colony.getCitizenManager().getCitizens().size()==5,"Original five citizens only");
   for(var member:data.journey(r.id()).orElseThrow().members()){var citizen=colony.getCitizenManager().getCivilian(member.citizenId());h.assertTrue(citizen.getUUID().equals(member.entityId())&&citizen.getUUID().equals(data.approvedParty(r.id()).get(citizen.getId())),"Original identity survives");citizen.updateEntityIfNecessary();h.assertTrue(citizen.getHomeBuilding()!=null,"Original home assignment survives");}
   for(var plan:r.draft().buildings()){var building=colony.getServerBuildingManager().getBuilding(plan.anchor());h.assertTrue(building.isBuilt()&&building.getBuildingLevel()==(upgrade&&plan.role().equals("builder")?2:1)&&(phase.equals("paused-reload")||FoundingPlacement.isPaidAnchor(l,plan)),"All ten completed original huts survive");}
   h.assertTrue(data.construction(r.id()).size()==(upgrade?11:10)&&!data.bootstrapSpent(r.id()).isEmpty()&&!data.bootstrapPending(r.id()),"Construction and finite kit journals survive");
   if(upgrade)h.assertTrue(MayorProposals.get(l).entries().values().stream().anyMatch(e->e.settlement().equals(r.id())&&e.target()==2&&e.state()==MayorProposals.State.COMPLETE),"Approved completed upgrade survives restart without another order");
   var service=NativeSupplyService.get(l);h.assertTrue(service.problem().isEmpty(),"Supply custody readable");
   if(phase.equals("reload")){
    h.assertTrue(MayorService.available(l,r.id())&&MayorLogistics.ready(l,r.id()),"Original Mayor and warehouse courier restore automatically");
    for(int tick=0;tick<10;tick++)service.tick(1000);for(int tick=0;tick<10;tick++)service.tick(1000);var route=service.route(operation(r.id()));h.assertTrue(route.delivered()==5&&route.cargo().isEmpty()&&service.allowanceRemaining(route.id())==0,"Original cargo delivered once after restart");
    var warehouse=MayorLogistics.warehouse(l,r.id()).orElseThrow();int count=0;var inv=WarehouseInventory.resolve(l,warehouse.getTileEntity()).inventory();for(int i=0;i<inv.getSlots();i++)if(ItemStack.isSameItemSameComponents(inv.getStackInSlot(i),cargo()))count+=inv.getStackInSlot(i).getCount();h.assertTrue(count==5,"Five exact named items in destination warehouse");
    var current=data.find(r.id()).orElseThrow();data.transition(r.id(),current.revision(),Phase.PAUSED);
   }else{h.assertTrue(data.find(r.id()).orElseThrow().phase()==Phase.PAUSED&&!RemoteTickets.snapshot(l).containsKey(r.id()),"Pause survives server restart");var before=service.route(operation(r.id()));for(int tick=0;tick<10;tick++)service.tick(1000);h.assertTrue(service.route(operation(r.id())).delivered()==before.delivered()&&service.route(operation(r.id())).cargo().getCount()==before.cargo().getCount()&&service.route(operation(r.id())).remainingTicks()==before.remainingTicks(),"Paused reload neither replays nor advances cargo");}
   saveAndLeave(h,owner,colony);rows.add(new ProbeResult("warehouse_"+phase,ProbeResult.Outcome.PASS,"Original completed settlement and finite warehouse cargo preserved across server restart"));report(rows);h.succeed();
  }catch(Throwable e){fail(h,rows,e);}});
 }catch(Throwable e){fail(h,rows,e);}}
 static void saveAndLeave(GameTestHelper h,ServerPlayer owner,com.minecolonies.api.colony.IColony colony)throws Exception{RemoteActivityTests.connect(owner,false);RemoteActivityService.tick(h.getLevel());h.getLevel().removePlayerImmediately(owner,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);RemoteDevelopmentData.get(h.getLevel()).checkpoint(h.getLevel());NativeFoundingCheckpoint.save(h.getLevel(),colony);}
 static void report(List<ProbeResult> rows)throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),System.getProperty("colonyhorizons.foundationSuite"),rows);}
 static void fail(GameTestHelper h,List<ProbeResult> rows,Throwable e){try{rows.add(new ProbeResult("warehouse_"+System.getProperty("colonyhorizons.probePhase"),ProbeResult.Outcome.FAIL,e.toString()));report(rows);}catch(Exception ignored){}h.fail(e.toString());}
}
