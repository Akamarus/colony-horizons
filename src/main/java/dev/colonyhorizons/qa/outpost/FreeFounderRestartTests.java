package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;

/** QA-only durable boundary injection; never shipped in the production artifact. */
@PrefixGameTestTemplate(false)
public final class FreeFounderRestartTests {
 static String phase(){return System.getProperty("colonyhorizons.probePhase");}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=4000)
 public static void restart(GameTestHelper h){if(phase().startsWith("seed-")){FreeFounderGrantTests.grant(h);return;}h.runAfterDelay(60,()->reload(h));}
 static void put(RemoteDevelopmentData data,UUID id,FoundingGrantJournal g)throws Exception{var method=RemoteDevelopmentData.class.getDeclaredMethod("putGrant",UUID.class,FoundingGrantJournal.class);method.setAccessible(true);method.invoke(data,id,g);}
 static void seed(GameTestHelper h,RemoteTestEnvironment env,UUID id,ServerPlayer owner)throws Exception{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);var g=data.grant(id).orElseThrow();String fault=phase().substring(5);int slots=switch(fault){case "one"->1;case "two"->2;case "three"->3;case "four","return"->4;default->0;};
  for(int i=0;i<slots;i++){g=g.begin();put(data,id,g);data.checkpoint(l);var c=NativeFounderFactory.register(env.home.colony());g=g.receipt(new FoundingRoster.Member(g.intent().orElseThrow(),c.getId(),c.getUUID()));put(data,id,g);data.checkpoint(l);NativeFoundingCheckpoint.save(l,env.home.colony());}
  if(slots==0){g=g.begin();put(data,id,g);data.checkpoint(l);if(fault.equals("native"))NativeFounderFactory.register(env.home.colony());if(fault.equals("missing")){g=g.receipt(new FoundingRoster.Member(FoundingRoster.Role.MAYOR,999999,UUID.randomUUID()));put(data,id,g);data.checkpoint(l);}}
  if(fault.equals("return")){
   FoundingGrantService.advance(l,id);FoundingTravel.beginGranted(l,id);
   // Persist cancellation after the first completed return while the outbound journey remains in flight.
   var targets=List.of(env.home.center().offset(-3,0,-3),env.home.center().offset(0,0,-3),env.home.center().offset(3,0,-3),env.home.center().offset(0,0,3));
   g=g.returning(targets);put(data,id,g);data.transition(id,data.find(id).orElseThrow().revision(),Phase.RECOVERY_NEEDED);data.checkpoint(l);
   var c=env.home.colony().getCitizenManager().getCivilian(g.receipts().getFirst().citizenId());var method=FoundingTravel.class.getDeclaredMethod("returnGranted",ServerLevel.class,com.minecolonies.api.colony.ICitizenData.class,net.minecraft.core.BlockPos.class);method.setAccessible(true);method.invoke(null,l,c,targets.getFirst());NativeFoundingCheckpoint.save(l,env.home.colony());put(data,id,g.returnedOne());
  }
  NativeFoundingCheckpoint.save(l,env.home.colony());finish(h,owner,"Seeded durable boundary "+fault);
 }
 static void reload(GameTestHelper h){try{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);h.assertTrue(data.all().size()==1,"One existing record; no seed on reload");var r=data.all().getFirst();var id=r.id();var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());var before=data.grant(id).orElseThrow().receipts();var owner=RemoteActivityTests.loginAtHome(l,r.owner());RemoteActivityService.tick(l);
  h.runAfterDelay(40,()->{try{
   String fault=phase().substring(7);
   if(fault.equals("return")){
    for(int i=0;i<4;i++)FoundingGrantService.class.getMethod("resumeReturns",ServerLevel.class,UUID.class).invoke(null,l,id);
    h.assertTrue(data.find(id).orElseThrow().phase()==Phase.CANCELLED,"Interrupted return completes automatically");
    for(var m:before){var c=colony.getCitizenManager().getCivilian(m.citizenId());h.assertTrue(!FoundingTravel.blocksSpawn(c),"Returned originals can respawn Home");c.updateEntityIfNecessary();h.assertTrue(c.getEntity().orElseThrow().blockPosition().closerThan(r.draft().request().homeSupply(),16),"Original returned Home");}
   }else if(Set.of("intent","native","missing").contains(fault)){
    for(int i=0;i<3;i++)RemoteFoundingService.advance(l,id);
    h.assertTrue(data.find(id).orElseThrow().phase()==Phase.RECOVERY_NEEDED&&data.grant(id).orElseThrow().consumed(),"Ambiguous/missing native receipt remains quarantined");h.assertTrue(colony.getCitizenManager().getCurrentCitizenCount()==(fault.equals("native")?1:0),"No duplicate or replacement created");
   }else{
    for(int i=0;i<4;i++)RemoteFoundingService.advance(l,id);
    var roster=FoundingRoster.resolve(data,id);h.assertTrue(roster.size()==4&&colony.getCitizenManager().getCurrentCitizenCount()==4,"Only remaining slots created");h.assertTrue(roster.subList(0,before.size()).equals(before),"Every durable identity preserved");
    while(data.journey(id).orElseThrow().remainingTicks()>0){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+20);FoundingTravel.reconcile(l,id);}h.assertTrue(data.journey(id).orElseThrow().state()==FoundingTravel.State.ARRIVED,"Originals arrive after process restart");
   }
   finish(h,owner,"Separate process reconciled "+fault);
  }catch(Throwable e){FreeFounderGrantTests.fail(h,new ArrayList<>(),e);}});
 }catch(Throwable e){FreeFounderGrantTests.fail(h,new ArrayList<>(),e);}}
 static void finish(GameTestHelper h,ServerPlayer owner,String detail)throws Exception{var l=h.getLevel();RemoteActivityTests.connect(owner,false);RemoteActivityService.tick(l);l.removePlayerImmediately(owner,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);RemoteDevelopmentData.get(l).checkpoint(l);ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FreeFounderRestart",List.of(new ProbeResult("founders_"+phase(),ProbeResult.Outcome.PASS,detail)));h.succeed();}
}

