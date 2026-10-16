package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class FoundingTravelRestartTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2000)
 public static void restart_original_travellers(GameTestHelper h){h.runAfterDelay(60,()->run(h));}
 private static void run(GameTestHelper h){String phase=System.getProperty("colonyhorizons.probePhase","seed");var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);net.minecraft.server.level.ServerPlayer owner;
  if(phase.equals("seed")){
   var bread=new ItemKey(new ItemStack(Items.BREAD));var env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(bread,1),Map.of()));
   var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());
   h.assertTrue(com.minecolonies.api.blocks.ModBlocks.blockHutBuilder.setup(env.owner,l,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"Home source");
   var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(env.home.center());hut.getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,1));hut.setChanged();
   RemoteClaimService.commit(l,env.draft.id(),0);var r=data.find(env.draft.id()).orElseThrow();RemoteFoundingService.escrow(env.owner,r.id(),r.revision(),Map.of(bread,1));
   for(int id:r.draft().request().citizenIds()){var c=env.home.colony().getCitizenManager().getCivilian(id);var item=new ItemStack(Items.IRON_AXE);item.setDamageValue(17);c.getInventory().setStackInSlot(0,item);}
   owner=RemoteActivityTests.loginAtHome(env);RemoteActivityService.tick(l);FoundingTravel.begin(l,r.id(),r.draft().request().citizenIds());
   var j=data.journey(r.id()).orElseThrow();h.assertTrue(j.state()==FoundingTravel.State.IN_TRANSIT,"Departure is committed");l.setBlockAndUpdate(j.targets().getFirst(),Blocks.STONE.defaultBlockState());
  }else{
   h.assertTrue(data.all().size()==1,"No reseeding on restart");var r=data.all().getFirst();var j=data.journey(r.id()).orElseThrow();
   var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());
   owner=RemoteActivityTests.loginAtHome(l,r.owner());RemoteActivityService.tick(l);
   final var onlineOwner=owner;
   h.runAfterDelay(40,()->{try {
   for(var target:j.targets())h.assertTrue(com.minecolonies.api.util.WorldUtil.isEntityBlockLoaded(l,target),"Production tickets reached native entity readiness");
   if(phase.equals("reload")){
    h.assertTrue(j.state()==FoundingTravel.State.IN_TRANSIT,"Native journey persisted in flight");
    // Controlled test clock exercises persisted cadence; natural cadence is covered by FoundingTravel.
    while(data.journey(r.id()).orElseThrow().remainingTicks()>0){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+20);FoundingTravel.reconcile(l,r.id());}
    for(var m:j.members()){var c=colony.getCitizenManager().getCivilian(m.citizenId());c.updateEntityIfNecessary();h.assertTrue(c.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isEmpty(),"Obstructed original stays in protected travel");}
    l.setBlockAndUpdate(j.targets().getFirst(),Blocks.AIR.defaultBlockState());FoundingTravel.reconcile(l,r.id());
    h.assertTrue(data.journey(r.id()).orElseThrow().state()==FoundingTravel.State.ARRIVED,"Safe original party arrives after restart");
   }else h.assertTrue(phase.equals("paused-reload") && j.state()==FoundingTravel.State.ARRIVED,"Completed arrival persists");
   for(var m:j.members()){var c=colony.getCitizenManager().getCivilian(m.citizenId());h.assertTrue(c!=null&&c.getUUID().equals(m.entityId()),"Original ID/UUID persisted");c.updateEntityIfNecessary();var e=c.getEntity().orElseThrow();h.assertTrue(e.isAlive()&&e.getUUID().equals(m.entityId())&&e.blockPosition().closerThan(r.draft().request().center(),20),"One original remote entity restored id="+m.citizenId()+" actual="+e.blockPosition()+" expectedNear="+r.draft().request().center());h.assertTrue(c.getInventory().getStackInSlot(0).is(Items.IRON_AXE)&&c.getInventory().getStackInSlot(0).getDamageValue()==17,"Original damaged tool persisted");}
   h.assertTrue(colony.getCitizenManager().getCitizens().size()==4,"No replacement logical citizens");
   finish(h,phase,rows,onlineOwner);
   }catch(Throwable e){failure(h,phase,rows,e);}});return;
  }
  finish(h,phase,rows,owner);
 }catch(Throwable e){failure(h,phase,rows,e);}}
 private static void finish(GameTestHelper h,String phase,List<ProbeResult> rows,net.minecraft.server.level.ServerPlayer owner)throws Exception {
  var l=h.getLevel();RemoteActivityTests.connect(owner,false);RemoteActivityService.tick(l);l.removePlayerImmediately(owner,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);RemoteDevelopmentData.get(l).checkpoint(l);
  rows.add(new ProbeResult("travel_"+phase,ProbeResult.Outcome.PASS,"Original party and inventory survive separate native process restarts"));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingTravelRestart",rows);h.succeed();
 }
 private static void failure(GameTestHelper h,String phase,List<ProbeResult> rows,Throwable e){try{rows.add(new ProbeResult("travel_"+phase,ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingTravelRestart",rows);}catch(Exception ignored){}h.fail(e.toString());}
}
