package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.mayor.*;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import java.nio.file.Path;
import java.util.*;

/** Actual process interruption after native blocks change, then paused and completed reloads. */
final class UpgradeInFlightAssertions {
 static boolean enabled(){return System.getProperty("colonyhorizons.foundationSuite").equals("MayorUpgradeInFlight");}
 static Path snapshot(){return Path.of(System.getProperty("colonyhorizons.probeOutput")).getParent().resolve("inflight.nbt");}
 static boolean seed(GameTestHelper h,RemoteTestEnvironment env,net.minecraft.server.level.ServerPlayer owner,UUID proposal)throws Exception{
  if(proposal==null)return false;
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);var plan=env.draft.buildings().getFirst();
  var entry=data.construction(env.draft.id()).values().stream().filter(e->e.building().equals(plan.operationId())&&e.target()==2).findFirst().orElse(null);
  if(entry==null||entry.state()!=ConstructionJournal.State.QUEUED)return false;
  var b=env.home.colony().getServerBuildingManager().getBuilding(plan.anchor());h.assertTrue(b.getBuildingLevel()==1,"Interrupt before native level completes");
  var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint(plan.pack(),plan.blueprint(),l.registryAccess());var origin=plan.anchor().subtract(bp.getPrimaryBlockOffset());
  var target=com.ldtteam.structurize.storage.StructurePacks.getBlueprint(plan.pack(),"fundamentals/builder2.blueprint",l.registryAccess());var targetOrigin=plan.anchor().subtract(target.getPrimaryBlockOffset());
  boolean changed=false;for(var pos:net.minecraft.core.BlockPos.betweenClosed(targetOrigin,targetOrigin.offset(target.getSizeX()-1,target.getSizeY()-1,target.getSizeZ()-1))){var rel=pos.subtract(origin);boolean inside=rel.getX()>=0&&rel.getY()>=0&&rel.getZ()>=0&&rel.getX()<bp.getSizeX()&&rel.getY()<bp.getSizeY()&&rel.getZ()<bp.getSizeZ();var expected=target.getBlockState(pos.subtract(targetOrigin));if(!pos.equals(plan.anchor())&&(!inside||bp.getBlockState(rel).isAir())&&!expected.isAir()&&!expected.getCollisionShape(l,pos).isEmpty()&&l.getBlockState(pos).getBlock()==expected.getBlock()){changed=true;break;}}
  if(!changed)return false;
  var proof=new CompoundTag();proof.putInt("order",entry.nativeOrder());proof.putUUID("proposal",proposal);proof.putUUID("building",plan.operationId());NbtIo.write(proof,snapshot());
  WarehouseRestartAssertions.saveAndLeave(h,owner,env.home.colony());pass(h,"seed","Native level2 order saved after the builder changed existing blocks; level remains1");return true;
 }
 static void reload(GameTestHelper h){try{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);h.assertTrue(data.all().size()==1,"Load the existing settlement without reseeding");var r=data.all().getFirst();var proof=NbtIo.read(snapshot());var phase=System.getProperty("colonyhorizons.probePhase");
  var owner=RemoteActivityTests.loginAtHome(l,r.owner());RemoteActivityService.tick(l);
  h.runAfterDelay(60,()->{try{
   var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());var plan=r.draft().buildings().getFirst();var entry=data.construction(r.id()).values().stream().filter(e->e.building().equals(plan.operationId())&&e.target()==2).findFirst().orElseThrow();
   h.assertTrue(entry.nativeOrder()==proof.getInt("order")&&data.construction(r.id()).size()==11,"Original order identity and exact journal count survive");
   for(var member:data.journey(r.id()).orElseThrow().members())h.assertTrue(colony.getCitizenManager().getCivilian(member.citizenId()).getUUID().equals(member.entityId()),"All original citizens survive");
   if(phase.equals("complete-reload")){h.assertTrue(entry.state()==ConstructionJournal.State.COMPLETE&&colony.getServerBuildingManager().getBuilding(plan.anchor()).getBuildingLevel()==2&&colony.getWorkManager().getWorkOrders().isEmpty(),"Completed upgrade stays complete without a second order");WarehouseRestartAssertions.saveAndLeave(h,owner,colony);pass(h,phase,"Completed native upgrade and identities survive another process");return;}
   var order=colony.getWorkManager().getWorkOrder(entry.nativeOrder());h.assertTrue(entry.state()==ConstructionJournal.State.QUEUED&&order!=null&&order.getTargetLevel()==2&&order.getLocation().equals(plan.anchor())&&colony.getServerBuildingManager().getBuilding(plan.anchor()).getBuildingLevel()==1,"Same unfinished native order restores");
   if(phase.equals("reload")){var current=data.find(r.id()).orElseThrow();dev.colonyhorizons.menu.RemoteMenuService.pause(owner,colony.getID(),r.id(),current.revision(),true);WarehouseRestartAssertions.saveAndLeave(h,owner,colony);pass(h,phase,"Active interrupted order restored and paused using production service");return;}
   h.assertTrue(data.find(r.id()).orElseThrow().phase()==Phase.PAUSED&&!RemoteTickets.snapshot(l).containsKey(r.id()),"Paused upgrade does not acquire tickets");
   h.runAfterDelay(200,()->{try{h.assertTrue(data.construction(r.id()).values().stream().anyMatch(e->e.target()==2&&e.nativeOrder()==proof.getInt("order")&&e.state()==ConstructionJournal.State.QUEUED),"Pause preserves pending work");var current=data.find(r.id()).orElseThrow();dev.colonyhorizons.menu.RemoteMenuService.pause(owner,colony.getID(),r.id(),current.revision(),false);poll(h,owner,r,proof,0);}catch(Throwable e){fail(h,e);}});
  }catch(Throwable e){fail(h,e);}});
 }catch(Throwable e){fail(h,e);}}
 static void poll(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner,Development r,CompoundTag proof,int elapsed){h.runAfterDelay(20,()->{try{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());var entry=data.construction(r.id()).values().stream().filter(e->e.building().equals(proof.getUUID("building"))&&e.target()==2).findFirst().orElseThrow();
  h.assertTrue(entry.nativeOrder()==proof.getInt("order")&&data.construction(r.id()).size()==11,"Resume never creates a replacement order");
  if(entry.state()!=ConstructionJournal.State.COMPLETE||MayorProposals.get(l).entries().get(proof.getUUID("proposal")).state()!=MayorProposals.State.COMPLETE){h.assertTrue(elapsed<220000,"Resumed upgrade did not finish: "+data.find(r.id()).orElseThrow().phase()+" "+entry);poll(h,owner,r,proof,elapsed+20);return;}
  h.assertTrue(colony.getServerBuildingManager().getBuilding(r.draft().buildings().getFirst().anchor()).getBuildingLevel()==2,"Native builder completes level2 without fixture placement or supplies");WarehouseRestartAssertions.saveAndLeave(h,owner,colony);pass(h,"paused-reload","Paused upgrade resumed its original order and completed natively with finite original supplies");
 }catch(Throwable e){fail(h,e);}});}
 static void pass(GameTestHelper h,String phase,String detail)throws Exception{WarehouseRestartAssertions.report(List.of(new ProbeResult("inflight_"+phase,ProbeResult.Outcome.PASS,detail)));h.succeed();}
 static void fail(GameTestHelper h,Throwable e){try{WarehouseRestartAssertions.report(List.of(new ProbeResult("inflight_"+System.getProperty("colonyhorizons.probePhase"),ProbeResult.Outcome.FAIL,e.toString())));}catch(Exception ignored){}h.fail(e.toString());}
}
