package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.mayor.*;
import dev.colonyhorizons.remote.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

final class MayorRoleAssertions {
 static void run(GameTestHelper h,RemoteTestEnvironment env,ServerPlayer owner,List<ProbeResult> rows){
  var id=env.draft.id();var l=h.getLevel();int mayorId=env.draft.request().mayorId();var citizen=env.home.colony().getCitizenManager().getCivilian(mayorId);var original=citizen.getUUID();
  h.assertTrue(MayorService.appoint(owner,id,mayorId).blockers().isEmpty(),"Approved original Mayor appointed");
  h.assertTrue(citizen.getJob() instanceof JobMayor&&citizen.getJob().getJobRegistryEntry().getKey().toString().equals("colonyhorizons:mayor")&&citizen.getUUID().equals(original),"Real native job registry and original citizen");
  citizen.getJob().createAI();h.assertTrue(MayorService.available(l,id),"Native Mayor and management station are available");pass(rows,"native_mayor_job");
  var station=env.home.colony().getServerBuildingManager().getBuilding(env.draft.buildings().getFirst().anchor());var module=station.getFirstModuleOccurance(MayorTerminalModule.class);
  h.assertTrue(module.hasAssignedCitizen(citizen)&&station.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class)!=module&&!station.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).hasAssignedCitizen(),"Mayor has a separate native workplace, builder slot remains free");pass(rows,"separate_mayor_station");
  var home=env.home.colony().getServerBuildingManager().getBuilding(env.home.center()).getFirstModuleOccurance(MayorTerminalModule.class);h.assertTrue(home.getModuleMax()==0&&!home.assignCitizen(citizen),"Ordinary Home huts do not gain Mayor hiring");pass(rows,"unmanaged_hut_unchanged");
  var builderId=env.draft.request().citizenIds().stream().filter(i->i!=mayorId).findFirst().orElseThrow();var builder=env.home.colony().getCitizenManager().getCivilian(builderId);var homeWorker=env.home.colony().getServerBuildingManager().getBuilding(env.home.center()).getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class);
  h.assertTrue(!homeWorker.assignCitizen(builder)&&builder.getJob()==null,"Arrived founders cannot be hired across the gap");pass(rows,"arrived_workers_stay_local");
  var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)lGet(h,station.getPosition());var marker=hut.getPersistentData().getUUID("colonyhorizons:founding_anchor");
  try{hut.getPersistentData().putUUID("colonyhorizons:founding_anchor",UUID.randomUUID());h.assertTrue(!MayorService.available(l,id),"Replaced terminal identity cannot keep management running");}finally{hut.getPersistentData().putUUID("colonyhorizons:founding_anchor",marker);}pass(rows,"replaced_station_stops_mayor");
  var job=citizen.getJob();MayorService.appoint(owner,id,mayorId);h.assertTrue(citizen.getJob()==job&&module.getAssignedCitizen().size()==1,"Repeated appointment inert");pass(rows,"duplicate_mayor_inert");
  try{MayorService.appoint(owner,id,env.draft.request().citizenIds().stream().filter(i->i!=mayorId).findFirst().orElseThrow());throw new AssertionError("Wrong approved Mayor accepted");}catch(IllegalArgumentException expected){}pass(rows,"wrong_mayor_rejected");
  MayorService.vacate(owner,id);h.assertTrue(!MayorService.available(l,id)&&citizen.getJob()==null&&RemoteDevelopmentData.get(l).journey(id).orElseThrow().state()==FoundingTravel.State.ARRIVED,"Vacancy pauses management, preserves settlement and travellers");pass(rows,"mayor_vacancy_preserves_site");
  MayorService.appoint(owner,id,mayorId);h.assertTrue(MayorService.available(l,id)&&citizen.getUUID().equals(original),"Original Mayor can resume");
 }
 private static net.minecraft.world.level.block.entity.BlockEntity lGet(GameTestHelper h,net.minecraft.core.BlockPos p){return h.getLevel().getBlockEntity(p);}
 private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Original native Mayor, distinct workplace and guarded appointment"));}
}
