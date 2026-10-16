// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import dev.colonyhorizons.menu.*;
import dev.colonyhorizons.network.SupplyMenuPayloads.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorMenuTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void menus(GameTestHelper h){var rows=new ArrayList<ProbeResult>();var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());var pos=f.center().offset(0,0,4);
  var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(f.ownerId(),"SurveyMenuQA"));actor.setPos(f.center().getX(),f.center().getY(),f.center().getZ());
  var block=SurveyorRegistration.HUT.get();var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,h.getLevel().registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);h.assertTrue(block.setup(actor,h.getLevel(),pos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/surveyor1.blueprint"),"Surveyor setup");
  var hut=(BuildingSurveyor)f.colony().getServerBuildingManager().getBuilding(pos);hut.setBuildingLevel(1);
  SupplyTests.run(rows,"native_survey_tab",()->SupplyTests.check(hut.getFirstModuleOccurance(SurveyorMenuModule.class)!=null));
  SupplyTests.run(rows,"hut_snapshot",()->{var view=SurveyMenuService.view(actor,f.colony().getID(),pos);SupplyTests.check(view.getList("resources",10).size()>=30&&view.getString("status").contains("Assign"));});
  SupplyTests.run(rows,"townhall_open_rejected",()->{SupplyMenuSessions.clear();var r=new MenuRequest(UUID.randomUUID(),0,Action.OPEN,f.colony().getID(),0,0,0,"",List.of(),UUID.randomUUID(),0,0,0);SupplyTests.check(!SupplyMenuSessions.handle(actor,r).ok());});
  SupplyTests.run(rows,"surveyor_open_allowed",()->{SupplyMenuSessions.clear();var form=new CompoundTag();form.putLong("surveyHut",pos.asLong());var r=new MenuRequest(UUID.randomUUID(),0,Action.OPEN,f.colony().getID(),0,0,0,"",List.of(),UUID.randomUUID(),0,0,0,form);SupplyTests.check(SupplyMenuSessions.handle(actor,r).ok());});
  SupplyTests.run(rows,"remote_access_rejected",()->{actor.setPos(pos.getX()+100,pos.getY(),pos.getZ());try{SurveyMenuService.view(actor,f.colony().getID(),pos);throw new AssertionError("Remote access allowed");}catch(IllegalArgumentException expected){}finally{actor.setPos(f.center().getX(),f.center().getY(),f.center().getZ());}});
  var oldSource=pos.offset(300,0,300);var saved=SurveySavedData.get(h.getLevel());var finding=completed(saved,f.colony().getID(),actor.getUUID(),oldSource,"kelp",3);completed(saved,f.colony().getID(),UUID.randomUUID(),oldSource,"vine",2);completed(saved,f.colony().getID()+100,actor.getUUID(),oldSource,"poppy",2);completed(saved,f.colony().getID(),actor.getUUID(),oldSource,"wheat_seeds",0);
  SupplyTests.run(rows,"findings_survive_original_hut",()->{var view=SurveyMenuService.view(actor,f.colony().getID(),pos);var found=view.getList("findings",10);SupplyTests.check(found.size()==2&&found.stream().map(t->(CompoundTag)t).anyMatch(t->t.hasUUID("id")&&t.getUUID("id").equals(finding)&&t.getInt("collected")==3&&t.getString("target").equals("minecraft:kelp")));});
  SupplyTests.run(rows,"findings_do_not_claim_production",()->{var found=SurveyMenuService.view(actor,f.colony().getID(),pos).getList("findings",10);SupplyTests.check(found.size()==2&&found.stream().map(t->(CompoundTag)t).allMatch(t->!t.getBoolean("renewable")&&!t.getBoolean("readyToFound")&&t.getString("production").contains("Rediscover")));});
  SupplyTests.run(rows,"empty_survey_has_no_resource_location",()->{var found=SurveyMenuService.view(actor,f.colony().getID(),pos).getList("findings",10);SupplyTests.check(found.stream().map(t->(CompoundTag)t).anyMatch(t->t.getInt("collected")==0&&!t.contains("source")&&t.getString("evidence").contains("No samples")));});
  SupplyTests.run(rows,"legacy_rediscover",()->{var found=SurveyMenuService.view(actor,f.colony().getID(),pos).getList("findings",10);var row=found.stream().map(t->(CompoundTag)t).filter(t->t.getUUID("id").equals(finding)).findFirst().orElseThrow();SupplyTests.check(!row.contains("site")&&row.getString("production").contains("Rediscover")&&!row.getBoolean("readyToFound"));});
  SupplyTests.run(rows,"missing_hut_rejected",()->{h.getLevel().removeBlock(pos,false);try{SurveyMenuService.view(actor,f.colony().getID(),pos);throw new AssertionError("Destroyed hut opened");}catch(IllegalArgumentException expected){}});
  SupplyMenuSessions.clear();try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorMenus",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 private static UUID completed(SurveySavedData data,int colony,UUID owner,BlockPos hut,String target,int count){var r=SurveyRecord.create(UUID.randomUUID(),colony,owner,hut,10,UUID.randomUUID(),net.minecraft.resources.ResourceLocation.withDefaultNamespace(target));data.add(r);var next=r.next(SurveyRecord.State.SEARCHING);data.replace(r,next);r=next;if(count>0){var op=UUID.randomUUID();var pending=data.samples(r.id()).prepare(op,hut.offset(20,0,20));data.putSamples(r.id(),pending);data.putSamples(r.id(),pending.collected(op,count));if(target.equals("kelp")){var site=SurveySiteSearch.start(hut.offset(20,0,20));data.writeSiteSearch(r.id(),site);data.writeSiteSearch(r.id(),site.checked(Optional.of(site.source()),"Terrain suitable; founding requires a fresh review"));}}next=r.next(SurveyRecord.State.RETURNING);data.replace(r,next);r=next;next=r.next(SurveyRecord.State.COMPLETE);data.replace(r,next);return r.id();}

}
