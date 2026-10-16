// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.SurveyTickets;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorTicketTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void tickets(GameTestHelper h){
  var level=h.getLevel();var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();var rows=new ArrayList<ProbeResult>();var target=new ChunkPos(143,147);
  SupplyTests.run(rows,"bounded_inspections",()->{SurveyTickets.open(level,a,Set.of(target),true);SurveyTickets.open(level,b,Set.of(new ChunkPos(153,157)));try{SurveyTickets.open(level,c,Set.of(new ChunkPos(163,167)));throw new AssertionError("Third inspection admitted");}catch(IllegalStateException expected){}SupplyTests.check(SurveyTickets.active(level)==2);});
  SupplyTests.run(rows,"one_request_per_tick",()->{SurveyTickets.advance(level,a);SurveyTickets.advance(level,b);SupplyTests.check(SurveyTickets.requested(level,a)+SurveyTickets.requested(level,b)==1);});
  h.onEachTick(()->{SurveyTickets.advance(level,a);SurveyTickets.advance(level,b);});
  h.succeedWhen(()->{
   h.assertTrue(SurveyTickets.ready(level,a)&&SurveyTickets.ready(level,b),"Waiting for asynchronous terrain generation");
   h.assertTrue(com.minecolonies.api.util.WorldUtil.isEntityBlockLoaded(level,target.getWorldPosition()),"Waiting for return-area entity ticking");
   SupplyTests.run(rows,"unexplored_chunk_loaded",()->SupplyTests.check(level.getChunkSource().getChunkNow(target.x,target.z)!=null));
   SupplyTests.run(rows,"release_all",()->{SurveyTickets.release(level,a);SurveyTickets.release(level,b);SupplyTests.check(SurveyTickets.active(level)==0);});
   try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorTickets",rows);}catch(Exception e){throw new RuntimeException(e);}
   h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());
  });
 }
}
