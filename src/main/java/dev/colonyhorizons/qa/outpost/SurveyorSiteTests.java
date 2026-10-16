// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorSiteTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1800)
 public static void sites(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();var l=h.getLevel();var env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(),Map.of()),0);var data=SurveySavedData.get(l);var r=SurveyRecord.create(UUID.randomUUID(),env.home.colony().getID(),env.owner.getUUID(),env.home.center(),77,UUID.randomUUID(),ResourceLocation.withDefaultNamespace("birch_log"));data.add(r);var searching=r.next(SurveyRecord.State.SEARCHING);data.replace(r,searching);var source=env.draft.request().center().offset(40,0,0);var op=UUID.randomUUID();data.putSamples(r.id(),data.samples(r.id()).prepare(op,source));data.putSamples(r.id(),data.samples(r.id()).collected(op,1));var q=env.draft.request();var request=new SiteRequest(q.colonyId(),q.name(),q.center(),q.allowedArea(),q.homeSupply(),List.of(),0,5,q.budget(),FoundingRoster.Policy.RESOURCE_FOUR_V2,Optional.of(new SurveyOrigin(r.id(),source,ProductionProfile.forTarget(r.target()))));var obstacle=q.center().offset(-30,0,-20);
  SupplyTests.run(rows,"terrain_candidate_read_only",()->{l.setBlockAndUpdate(obstacle,Blocks.DIRT.defaultBlockState());try{var result=inspect(l,env.owner.getUUID(),request);SupplyTests.check(suitable(result)&&l.getBlockState(obstacle).is(Blocks.DIRT)&&RemoteDevelopmentData.get(l).all().size()==1);}finally{l.removeBlock(obstacle,false);}});
  SupplyTests.run(rows,"candidate_not_founding_authority",()->SupplyTests.check(!RemoteSitePlanner.reviewPrepared(l,env.owner.getUUID(),request).blockers().isEmpty()));
  SupplyTests.run(rows,"candidate_wrong_owner_rejected",()->reject(()->inspect(l,UUID.randomUUID(),request)));
  SupplyTests.run(rows,"candidate_wrong_evidence_rejected",()->{var wrong=new SiteRequest(q.colonyId(),q.name(),q.center(),q.allowedArea(),q.homeSupply(),List.of(),0,5,q.budget(),FoundingRoster.Policy.RESOURCE_FOUR_V2,Optional.of(new SurveyOrigin(r.id(),source.offset(1,0,0),ProductionProfile.forTarget(r.target()))));SupplyTests.check(!suitable(inspect(l,env.owner.getUUID(),wrong)));});
  SupplyTests.run(rows,"candidate_preserves_container",()->{l.setBlockAndUpdate(obstacle,Blocks.CHEST.defaultBlockState());try{SupplyTests.check(!suitable(inspect(l,env.owner.getUUID(),request))&&l.getBlockState(obstacle).is(Blocks.CHEST));}finally{l.removeBlock(obstacle,false);}});
  SupplyTests.run(rows,"site_progress_round_trip",()->{var start=SurveySiteSearch.start(source);data.writeSiteSearch(r.id(),start);var checked=start.checked(Optional.empty(),"Too steep");data.writeSiteSearch(r.id(),checked);var restored=SurveySavedData.load(data.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess()),l.registryAccess());SupplyTests.check(restored.siteSearch(r.id()).orElseThrow().equals(checked));invalid(()->data.writeSiteSearch(r.id(),checked));});
  SupplyTests.run(rows,"site_search_bounded",()->{var search=SurveySiteSearch.start(source);var seen=new HashSet<BlockPos>();for(int i=0;i<SurveySiteSearch.LIMIT;i++){SupplyTests.check(seen.add(search.candidate()));search=search.checked(Optional.empty(),"No site");}SupplyTests.check(search.complete());var done=search;invalid(done::candidate);invalid(()->done.checked(Optional.empty(),"Again"));var tag=done.save();tag.putInt("inspected",10);invalid(()->SurveySiteSearch.load(tag));});
  SupplyTests.run(rows,"site_result_matches_inspected_column",()->invalid(()->SurveySiteSearch.start(source).checked(Optional.of(source.offset(32,0,0)),"Wrong candidate")));
  SupplyTests.run(rows,"site_saved_evidence_mismatch",()->{var tag=data.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());var entry=tag.getList("surveys",10).getCompound(0);entry.getCompound("siteSearch").putLong("source",source.offset(1,0,0).asLong());invalid(()->SurveySavedData.load(tag,l.registryAccess()));});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorSiteReview",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(v->v.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 });}
 static Object inspect(ServerLevel l,UUID owner,SiteRequest request){try{return RemoteSitePlanner.class.getMethod("inspectCandidate",ServerLevel.class,UUID.class,SiteRequest.class).invoke(null,l,owner,request);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof SecurityException x)throw x;throw new AssertionError(e.getCause());}catch(Exception e){throw new AssertionError("Candidate inspection unavailable",e);}}
 static boolean suitable(Object result){try{return (boolean)result.getClass().getMethod("suitable").invoke(result);}catch(Exception e){throw new AssertionError(e);}}
 static void invalid(Runnable action){try{action.run();throw new AssertionError("Invalid site progress accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
 static void reject(Runnable action){try{action.run();throw new AssertionError("Unauthorized inspection accepted");}catch(SecurityException expected){}}
}

