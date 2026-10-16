// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorPreparationRecordTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void records(GameTestHelper h){
  var rows=new ArrayList<ProbeResult>();
  SupplyTests.run(rows,"pending_survives_reload",()->{var j=create();j=call(j,"begin",0);j=reload(j);check(j,"pending",true);check(j,"cursor",0);reject(j,"begin",0);});
  SupplyTests.run(rows,"receipt_advances_once",()->{var j=call(create(),"begin",0);j=call(j,"complete",0);check(j,"cursor",1);check(j,"pending",false);reject(j,"complete",0);reject(j,"begin",0);});
  SupplyTests.run(rows,"cancel_keeps_completed_work",()->{var j=call(call(create(),"begin",0),"complete",0);j=call(j,"cancel");j=reload(j);check(j,"cursor",1);check(j,"cancelled",true);reject(j,"begin",1);});
  SupplyTests.run(rows,"cancel_pending_requires_recovery",()->{var j=call(call(create(),"begin",0),"cancel");check(j,"pending",true);check(j,"cancelled",true);reject(j,"complete",0);});
  SupplyTests.run(rows,"completion_limit",()->{var j=create();for(int i=0;i<3;i++)j=call(call(j,"begin",i),"complete",i);check(j,"finished",true);reject(j,"begin",3);});
  SupplyTests.run(rows,"malformed_journal_rejected",()->{var j=create();var tag=(CompoundTag)call(j,"save");tag.putInt("cursor",4);try{j.getClass().getMethod("load",CompoundTag.class).invoke(null,tag);throw new AssertionError("Invalid cursor loaded");}catch(java.lang.reflect.InvocationTargetException expected){SupplyTests.check(expected.getCause() instanceof IllegalArgumentException);}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorPreparationRecords",rows);}catch(Exception e){throw new RuntimeException(e);}
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 private static Object create(){try{return Class.forName("dev.colonyhorizons.remote.SitePreparationJournal").getMethod("create",UUID.class,String.class,int.class).invoke(null,UUID.randomUUID(),"a".repeat(64),3);}catch(ReflectiveOperationException e){throw new AssertionError("Terrain operation journal is missing",e);}}
 private static Object call(Object j,String method,Object... args){try{return j.getClass().getMethod(method,args.length==0?new Class<?>[0]:new Class<?>[]{int.class}).invoke(j,args);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 private static Object reload(Object j){try{return j.getClass().getMethod("load",CompoundTag.class).invoke(null,call(j,"save"));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 private static void check(Object j,String method,Object value){SupplyTests.check(Objects.equals(call(j,method),value));}
 private static void reject(Object j,String method,int value){try{call(j,method,value);throw new AssertionError("Replayed or stale terrain operation accepted");}catch(IllegalStateException expected){}}
}
