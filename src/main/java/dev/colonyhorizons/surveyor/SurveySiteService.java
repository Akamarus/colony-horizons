// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Uses the expedition's existing lease and sample evidence; never places or claims anything. */
public final class SurveySiteService {
 private SurveySiteService(){}
 public static boolean advance(ServerLevel level,SurveyRecord record){
  var data=SurveySavedData.get(level);var existing=data.siteSearch(record.id());
  if(existing.isEmpty()){SurveyTickets.release(level,record.id());data.writeSiteSearch(record.id(),SurveySiteSearch.start(data.samples(record.id()).source()));data.checkpoint(level);}
  var search=data.siteSearch(record.id()).orElseThrow();if(search.complete())return true;
  ProductionProfile profile;
  try{profile=ProductionProfile.forTarget(record.target());}catch(IllegalArgumentException unsupported){return rejected(level,record,search,"Samples available; renewable production is not supported for this item");}
  var rough=search.candidate();var chunk=new ChunkPos(rough);var chunks=new HashSet<ChunkPos>();
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var c=new ChunkPos(chunk.x+x,chunk.z+z);if(!level.getWorldBorder().isWithinBounds(c.getMinBlockX(),c.getMinBlockZ())||!level.getWorldBorder().isWithinBounds(c.getMaxBlockX(),c.getMaxBlockZ()))return rejected(level,record,search,"Candidate crosses the world border");chunks.add(c);}
  if(!SurveyTickets.isOpen(level,record.id())&&SurveyTickets.active(level)>=2)return false;
  SurveyTickets.open(level,record.id(),chunks);SurveyTickets.advance(level,record.id());if(!SurveyTickets.ready(level,record.id()))return false;
  // A median of local surface heights avoids choosing the top of a single tree or hole.
  var heights=new ArrayList<Integer>();for(int x=-24;x<=24;x+=12)for(int z=-24;z<=24;z+=12)heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,rough.getX()+x,rough.getZ()+z));
  Collections.sort(heights);var center=new BlockPos(rough.getX(),heights.get(heights.size()/2),rough.getZ());
  var area=new BoundingBox(chunk.getMinBlockX()-48,level.getMinBuildHeight(),chunk.getMinBlockZ()-48,chunk.getMinBlockX()+63,level.getMaxBuildHeight()-1,chunk.getMinBlockZ()+63);
  var request=new SiteRequest(record.colony(),"Survey candidate",center,area,record.hut(),List.of(),0,5,new Budget(Map.of(),Map.of()),FoundingRoster.Policy.RESOURCE_FOUR_V2,Optional.of(new SurveyOrigin(record.id(),search.source(),profile)));
  var review=RemoteSitePlanner.inspectCandidate(level,record.owner(),request);
  data.writeSiteSearch(record.id(),search.checked(review.suitable()?Optional.of(center):Optional.empty(),review.suitable()?"Terrain suitable; founding requires a fresh review":String.join("; ",review.blockers())));data.checkpoint(level);SurveyTickets.release(level,record.id());
  return data.siteSearch(record.id()).orElseThrow().complete();
 }
 private static boolean rejected(ServerLevel level,SurveyRecord record,SurveySiteSearch search,String why){var data=SurveySavedData.get(level);data.writeSiteSearch(record.id(),search.checked(Optional.empty(),why));data.checkpoint(level);SurveyTickets.release(level,record.id());return data.siteSearch(record.id()).orElseThrow().complete();}
}
