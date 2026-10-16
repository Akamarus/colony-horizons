package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class FreeFounderRecordTests {
 static CompoundTag seed(GameTestHelper h,boolean free,int count){
  var data=new RemoteDevelopmentData(Level.OVERWORLD);data.create(RemoteRecordTests.sample(UUID.randomUUID(),512));
  var tag=data.save(new CompoundTag(),h.getLevel().registryAccess());var t=entry(tag);
  if(count==5)t.putIntArray("citizens",new int[]{513,514,515,516,517});
  if(free){t.putString("foundingPolicy","FREE_FOUR_V1");t.putIntArray("citizens",new int[0]);t.putInt("mayor",0);}
  return tag;
 }
 static CompoundTag entry(CompoundTag t){return t.getList("developments",10).getCompound(0);}
 static CompoundTag round(GameTestHelper h,CompoundTag t){return RemoteDevelopmentData.load(t,h.getLevel().registryAccess(),Level.OVERWORLD).save(new CompoundTag(),h.getLevel().registryAccess());}
 static ListTag roster(){var list=new ListTag();int i=20;for(String role:List.of("MAYOR","BUILDER","COURIER","FORESTER")){var t=new CompoundTag();t.putString("role",role);t.putInt("citizen",i++);t.putUUID("entity",UUID.randomUUID());list.add(t);}return list;}
 static CompoundTag binding(){var t=new CompoundTag();t.putInt("version",1);t.putUUID("survey",UUID.randomUUID());t.putLong("source",net.minecraft.core.BlockPos.ZERO.asLong());t.put("profile",ProductionProfile.forTarget(net.minecraft.resources.ResourceLocation.withDefaultNamespace("kelp")).save());return t;}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300)
 public static void records(GameTestHelper h){var rows=new ArrayList<ProbeResult>();
  for(int count:List.of(4,5))SupplyTests.run(rows,count==4?"legacy_four_roundtrip":"legacy_five_roundtrip",()->{var t=seed(h,false,count);var r=entry(round(h,t));SupplyTests.check(Arrays.equals(r.getIntArray("citizens"),entry(t).getIntArray("citizens"))&&r.getString("fingerprint").equals(entry(t).getString("fingerprint"))&&r.getString("phase").equals("PROPOSED"));});
  SupplyTests.run(rows,"free_intent_without_ids",()->{var t=entry(round(h,seed(h,true,4)));SupplyTests.check(t.getIntArray("citizens").length==0&&t.getInt("mayor")==0&&t.getString("foundingPolicy").equals("FREE_FOUR_V1"));});
  SupplyTests.run(rows,"free_roster_roundtrip",()->{var t=seed(h,true,4);var r=roster();entry(t).put("founderRoster",r);SupplyTests.check(entry(round(h,t)).getList("founderRoster",10).equals(r));});
  SupplyTests.run(rows,"duplicate_identity_rejected",()->{var t=seed(h,true,4);var r=roster();r.getCompound(1).putUUID("entity",r.getCompound(0).getUUID("entity"));entry(t).put("founderRoster",r);SupplyTests.reject(()->round(h,t));});
  SupplyTests.run(rows,"unknown_policy_rejected",()->{var t=seed(h,false,4);entry(t).putString("foundingPolicy","FUTURE_POLICY");SupplyTests.reject(()->round(h,t));});
  SupplyTests.run(rows,"reused_id_wrong_uuid",()->{var t=seed(h,true,4);var r=roster();entry(t).put("founderRoster",r);var parties=new ListTag();for(var v:r){var n=((CompoundTag)v).copy();n.remove("role");parties.add(n);}parties.getCompound(0).putUUID("entity",UUID.randomUUID());entry(t).put("approvedParty",parties);SupplyTests.reject(()->round(h,t));});
  SupplyTests.run(rows,"distinct_uuid_reused_id_activation",()->{var a=seed(h,true,4);var other=new RemoteDevelopmentData(Level.OVERWORLD);other.create(RemoteRecordTests.sample(UUID.randomUUID(),2048));var b=other.save(new CompoundTag(),h.getLevel().registryAccess());var second=entry(b);second.putString("foundingPolicy","FREE_FOUR_V1");second.putIntArray("citizens",new int[0]);second.putInt("mayor",0);var combined=a.getList("developments",10);var ra=roster();var rb=roster();entry(a).put("founderRoster",ra);second.put("founderRoster",rb);entry(a).putString("phase","OPERATING");second.putString("phase","OPERATING");combined.add(second);var restored=RemoteDevelopmentData.load(a,h.getLevel().registryAccess(),Level.OVERWORLD);SupplyTests.check(restored.all().size()==2);rb.getCompound(0).putUUID("entity",ra.getCompound(0).getUUID("entity"));second.put("founderRoster",rb);SupplyTests.reject(()->RemoteDevelopmentData.load(a,h.getLevel().registryAccess(),Level.OVERWORLD));});
  SupplyTests.run(rows,"survey_binding_roundtrip",()->{var t=seed(h,true,4);var binding=binding();entry(t).putString("foundingPolicy","RESOURCE_FOUR_V2");entry(t).put("surveyOrigin",binding);SupplyTests.check(entry(round(h,t)).getCompound("surveyOrigin").equals(binding));});
  SupplyTests.run(rows,"survey_binding_legacy_rejected",()->{var t=seed(h,false,4);entry(t).put("surveyOrigin",binding());SupplyTests.reject(()->round(h,t));});
  SupplyTests.run(rows,"survey_binding_invalid_rejected",()->{var t=seed(h,true,4);var binding=binding();binding.putInt("version",999);entry(t).put("surveyOrigin",binding);SupplyTests.reject(()->round(h,t));});
  SupplyTests.run(rows,"resource_intent_requires_survey",()->{var t=seed(h,true,4);entry(t).putString("foundingPolicy","RESOURCE_FOUR_V2");SupplyTests.reject(()->round(h,t));});
  SupplyTests.run(rows,"resource_roster_roundtrip",()->{var t=seed(h,true,4);entry(t).putString("foundingPolicy","RESOURCE_FOUR_V2");entry(t).put("surveyOrigin",binding());var r=roster();r.getCompound(3).putString("role","PRODUCER");entry(t).put("founderRoster",r);SupplyTests.check(entry(round(h,t)).getList("founderRoster",10).equals(r));});
  SupplyTests.run(rows,"resource_roster_wrong_role_rejected",()->{var t=seed(h,true,4);entry(t).putString("foundingPolicy","RESOURCE_FOUR_V2");entry(t).put("surveyOrigin",binding());entry(t).put("founderRoster",roster());SupplyTests.reject(()->round(h,t));});
  SupplyTests.run(rows,"legacy_roster_producer_rejected",()->{var t=seed(h,true,4);var r=roster();r.getCompound(3).putString("role","PRODUCER");entry(t).put("founderRoster",r);SupplyTests.reject(()->round(h,t));});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FreeFounderRecords",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
