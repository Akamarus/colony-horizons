// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;

@PrefixGameTestTemplate(false)
public final class SurveyorTerrainTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100)
 public static void terrain(GameTestHelper h) {
  var rows=new ArrayList<ProbeResult>();var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,12,4));
  Runnable reset=()->{for(int y=-10;y<=10;y++)level.setBlock(p.offset(0,y,0),Blocks.AIR.defaultBlockState(),2);level.setBlock(p.below(4),Blocks.STONE.defaultBlockState(),2);};
  SupplyTests.run(rows,"supported_dirt_fill",()->{reset.run();var plan=review(h,new BoundingBox(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY()+2,p.getZ()));check(plan,"approved",true);check(plan,"dirtRequired",3);SupplyTests.check(level.getBlockState(p.below()).isAir());});
  SupplyTests.run(rows,"trees_and_hills_are_costed",()->{reset.run();level.setBlock(p.below(),Blocks.DIRT.defaultBlockState(),2);level.setBlock(p,Blocks.DIRT.defaultBlockState(),2);level.setBlock(p.above(),Blocks.OAK_LOG.defaultBlockState(),2);var plan=review(h,box(p,2));check(plan,"approved",true);check(plan,"clearRequired",2);SupplyTests.check(level.getBlockState(p.above()).is(Blocks.OAK_LOG));});
  SupplyTests.run(rows,"deep_foundation_rejected",()->{reset.run();level.setBlock(p.below(4),Blocks.AIR.defaultBlockState(),2);level.setBlock(p.below(10),Blocks.STONE.defaultBlockState(),2);check(review(h,box(p,2)),"approved",false);});
  SupplyTests.run(rows,"lava_rejected",()->{reset.run();level.setBlock(p.below(2),Blocks.LAVA.defaultBlockState(),2);check(review(h,box(p,2)),"approved",false);});
  SupplyTests.run(rows,"container_rejected",()->{reset.run();level.setBlock(p,Blocks.CHEST.defaultBlockState(),2);check(review(h,box(p,2)),"approved",false);});
  SupplyTests.run(rows,"constructed_blocks_rejected",()->{reset.run();level.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),2);check(review(h,box(p,2)),"approved",false);});
  SupplyTests.run(rows,"excess_cut_rejected",()->{reset.run();for(int y=0;y<9;y++)level.setBlock(p.above(y),Blocks.STONE.defaultBlockState(),2);check(review(h,box(p,9)),"approved",false);});
  SupplyTests.run(rows,"overlap_not_double_charged",()->{reset.run();var box=box(p,2);var plan=review(h,box,box);check(plan,"approved",true);check(plan,"dirtRequired",3);});
  SupplyTests.run(rows,"changed_site_detected",()->{reset.run();var plan=review(h,box(p,2));level.setBlock(p,Blocks.CHEST.defaultBlockState(),2);check(plan,"matches",false,level);});
  SupplyTests.run(rows,"bounded_envelope",()->{reset.run();check(review(h,new BoundingBox(p.getX(),p.getY(),p.getZ(),p.getX()+10000,p.getY()+10,p.getZ()+10000)),"approved",false);});
  SupplyTests.run(rows,"clear_columns_top_down",()->{reset.run();for(int x=0;x<2;x++){level.setBlock(p.offset(x,-1,0),Blocks.DIRT.defaultBlockState(),2);for(int y=0;y<3;y++)level.setBlock(p.offset(x,y,0),Blocks.DIRT.defaultBlockState(),2);}var plan=dev.colonyhorizons.remote.SitePreparationPlan.review(level,List.of(new BoundingBox(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+2,p.getZ())));SupplyTests.check(plan.approved()&&plan.operations().size()==6&&plan.operations().get(0).pos().equals(p.above(2))&&plan.operations().get(1).pos().equals(p.above())&&plan.operations().get(2).pos().equals(p));});
  SupplyTests.run(rows,"plan_round_trip",()->{reset.run();var plan=review(h,box(p,2));try{var tag=(net.minecraft.nbt.CompoundTag)plan.getClass().getMethod("save").invoke(plan);var restored=plan.getClass().getMethod("load",net.minecraft.nbt.CompoundTag.class).invoke(null,tag);check(restored,"approved",true);check(restored,"dirtRequired",3);check(restored,"matches",true,level);SupplyTests.check(plan.getClass().getMethod("save").invoke(restored).equals(tag));}catch(ReflectiveOperationException e){throw new AssertionError("Approved terrain plans must survive saving",e);}});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorTerrain",rows);}catch(Exception e){throw new RuntimeException(e);}
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 private static BoundingBox box(BlockPos p,int height){return new BoundingBox(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY()+height,p.getZ());}
 private static Object review(GameTestHelper h,BoundingBox... boxes){try{return Class.forName("dev.colonyhorizons.remote.SitePreparationPlan").getMethod("review",net.minecraft.server.level.ServerLevel.class,List.class).invoke(null,h.getLevel(),List.of(boxes));}catch(ClassNotFoundException e){throw new AssertionError("Automatic terrain preparation planner is missing",e);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 private static void check(Object plan,String method,Object expected,Object... args){try{var type=args.length==0?new Class<?>[0]:new Class<?>[]{net.minecraft.server.level.ServerLevel.class};SupplyTests.check(Objects.equals(expected,plan.getClass().getMethod(method,type).invoke(plan,args)));}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
