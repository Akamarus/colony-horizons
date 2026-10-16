// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SurveyorBuilderPreparationTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=400)
 public static void builder(GameTestHelper h){var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(5,2,5)));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(chunk.x+x,chunk.z+z,true);h.runAfterDelay(40,()->{
  var rows=new ArrayList<ProbeResult>();var l=h.getLevel();var center=h.absolutePos(new BlockPos(5,14,5));var p=center.offset(3,0,0);
  for(int x=-4;x<=6;x++)for(int z=-4;z<=5;z++)l.setBlock(center.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);
  l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.AIR.defaultBlockState(),2);l.setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);
  var plan=SitePreparationPlan.review(l,List.of(new BoundingBox(p.above())));
  var f=OutpostProbeFixture.create(l,center,UUID.randomUUID());var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(f.ownerId(),"TerrainBuilderQA"));
  var block=com.minecolonies.api.blocks.ModBlocks.blockHutBuilder;
  var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,l.registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);
  h.assertTrue(block.setup(owner,l,center,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"Native builder hut");
  var worker=f.createCitizen();var building=f.colony().getServerBuildingManager().getBuilding(center);building.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(worker);
  var entity=worker.getEntity().orElseThrow();entity.setPos(center.getX()+0.5,center.getY(),center.getZ()+1.5);
  worker.getInventory().setStackInSlot(0,new ItemStack(Items.DIRT,2));
  var journal=new AtomicReference<>(SitePreparationJournal.create(UUID.randomUUID(),plan.fingerprint(),plan.operations().size()));
  SupplyTests.run(rows,"distant_builder_cannot_edit",()->{entity.setPos(center.getX()+20,center.getY(),center.getZ());advance(l,owner,worker,plan,journal);SupplyTests.check(l.isEmptyBlock(p)&&journal.get().cursor()==0);});
  SupplyTests.run(rows,"occluded_builder_requests_approach",()->{entity.setPos(center.getX()+0.5,center.getY(),center.getZ()+1.5);entity.getNavigation().stop();var wall=center.offset(2,1,0);l.setBlock(wall,Blocks.DIRT.defaultBlockState(),2);try{var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)entity.getNavigation();advance(l,owner,worker,plan,journal);SupplyTests.check(journal.get().cursor()==0&&l.isEmptyBlock(p)&&nav.getPathResult()!=null&&nav.getPathResult().getJob() instanceof com.minecolonies.core.entity.pathfinding.pathjobs.IDestinationPathJob path&&!path.getDestination().equals(p)&&l.isEmptyBlock(path.getDestination())&&l.isEmptyBlock(path.getDestination().above())&&l.getBlockState(path.getDestination().below()).isFaceSturdy(l,path.getDestination().below(),net.minecraft.core.Direction.UP));}finally{l.setBlock(wall,Blocks.AIR.defaultBlockState(),2);entity.getNavigation().stop();}});
  SupplyTests.run(rows,"builder_steps_out_of_fill",()->{entity.setPos(p.getX()+0.5,p.getY(),p.getZ()+0.5);entity.getNavigation().stop();advance(l,owner,worker,plan,journal);var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)entity.getNavigation();SupplyTests.check(journal.get().cursor()==0&&l.isEmptyBlock(p)&&worker.getInventory().getStackInSlot(0).getCount()==2&&nav.getPathResult()!=null&&nav.getPathResult().getJob() instanceof com.minecolonies.core.entity.pathfinding.pathjobs.IDestinationPathJob path&&!path.getDestination().equals(p));entity.getNavigation().stop();});
  SupplyTests.run(rows,"builder_clears_fill_edge",()->{entity.setPos(p.getX()+1.1,p.getY(),p.getZ()+0.5);entity.getNavigation().stop();advance(l,owner,worker,plan,journal);var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)entity.getNavigation();SupplyTests.check(journal.get().cursor()==0&&l.isEmptyBlock(p)&&nav.getPathResult()!=null&&nav.getPathResult().getJob() instanceof com.minecolonies.core.entity.pathfinding.pathjobs.IDestinationPathJob path&&!path.getDestination().equals(entity.blockPosition()));entity.getNavigation().stop();});
  SupplyTests.run(rows,"native_builder_uses_own_inventory",()->{entity.setPos(center.getX()+0.5,center.getY(),center.getZ()+1.5);advance(l,owner,worker,plan,journal);SupplyTests.check(l.getBlockState(p).is(Blocks.DIRT)&&worker.getInventory().getStackInSlot(0).getCount()==1&&journal.get().finished());});
  SupplyTests.run(rows,"completed_work_does_not_repeat",()->{advance(l,owner,worker,plan,journal);SupplyTests.check(worker.getInventory().getStackInSlot(0).getCount()==1&&journal.get().finished());});
  try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorBuilderPreparation",rows);}catch(Exception e){throw new RuntimeException(e);}h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 });}
 private static void advance(net.minecraft.server.level.ServerLevel l,net.minecraft.server.level.ServerPlayer owner,com.minecolonies.api.colony.ICitizenData worker,SitePreparationPlan plan,AtomicReference<SitePreparationJournal> journal){try{Class.forName("dev.colonyhorizons.remote.SitePreparationService").getMethod("advance",net.minecraft.server.level.ServerLevel.class,net.minecraft.server.level.ServerPlayer.class,com.minecolonies.api.colony.ICitizenData.class,SitePreparationPlan.class,SitePreparationJournal.class,Consumer.class).invoke(null,l,owner,worker,plan,journal.get(),(Consumer<SitePreparationJournal>)journal::set);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Native builder preparation service is missing",e);}}
}
