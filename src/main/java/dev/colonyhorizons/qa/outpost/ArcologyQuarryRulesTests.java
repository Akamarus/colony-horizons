package dev.colonyhorizons.qa.outpost;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.BlueprintPositionInfo;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
/** Focused native rule checks; full excavation/navigation are separate tests. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryRulesTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=12000)
 public static void quarry_rules(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var f=new ArcologyQuarryTests.Run(h);f.afterSetup=ArcologyQuarryRulesTests::verify;f.start();});
 }
 static void verify(ArcologyQuarryTests.Run f){var rows=new ArrayList<ProbeResult>();try{
  var predicate=EntityAIQuarrier.class.getDeclaredMethod("skipBuilding",BlueprintPositionInfo.class,BlockPos.class,IStructureHandler.class);predicate.setAccessible(true);
  var footing=AbstractEntityAIStructure.class.getDeclaredField("workFrom");footing.setAccessible(true);
  for(int i=0;i<2;i++){
   var citizen=f.workers.get(i);citizen.setPaused(true);var ai=(EntityAIQuarrier)citizen.getJob().getWorkerAI();var mine=f.mines.get(i);var quarry=f.quarries.get(i);
   String path="infrastructure/mineshafts/"+f.names[i]+"shaft1.blueprint";
   var blueprint=StructurePacks.getBlueprintFuture("Arcology Horizons",path,f.level.registryAccess()).get();
   var order=new WorkOrderMiner("Arcology Horizons",path,f.names[i],RotationMirror.NONE,quarry.getPosition().below(2),false,mine.getPosition());order.setBlueprint(blueprint,f.level);order.setClaimedBy(mine.getPosition());f.fixture.colony().getWorkManager().addWorkOrder(order,false);mine.setWorkOrder(order);check(mine.getWorkOrder()==order,"Native fixture order registration");
   var handler=new BuildingStructureHandler<>(f.level,order,ai,new BuildingProgressStage[]{BuildingProgressStage.BUILD_SOLID,BuildingProgressStage.DECORATE,BuildingProgressStage.CLEAR});
   var info=blueprint.getBlockInfoAsList().stream().filter(b->b.getState().is(Blocks.LADDER)&&b.getPos().getY()==blueprint.getSizeY()-2).findFirst().orElseThrow();
   var pos=handler.getProgressPosInWorld(info.getPos());var input=new BlueprintPositionInfo(info.getPos(),info,new CompoundTag[0]);var back=pos.relative(info.getState().getValue(LadderBlock.FACING).getOpposite());
   f.level.setBlock(back,Blocks.COBBLESTONE.defaultBlockState(),18);f.level.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
   check(info.getState().canSurvive(f.level,pos),"Fixture ladder has no backing");
   var decorate=AbstractEntityAIStructure.class.getDeclaredMethod("skipDecorate",BlueprintPositionInfo.class,BlockPos.class,IStructureHandler.class);decorate.setAccessible(true);
   var air=blueprint.getBlockInfoAsList().stream().filter(b->b.getState().isAir()).findFirst().orElseThrow();
   var airInput=new BlueprintPositionInfo(air.getPos(),air,new CompoundTag[0]);
   check((boolean)decorate.invoke(ai,airInput,handler.getProgressPosInWorld(air.getPos()),handler),"Decoration must not erase ore/stone at blueprint air cells before mining");
   check(!(boolean)decorate.invoke(ai,input,pos,handler),"Decoration skipped supported access ladder");

   handler.setStage(BuildingProgressStage.DECORATE);
   var placer=new com.ldtteam.structurize.placement.StructurePlacer(handler);
   f.level.setBlock(pos,Blocks.COAL_ORE.defaultBlockState(),18);
   var blocked=placer.handleBlockPlacement(f.level,pos,null,info);
   check(blocked.getResult()==com.ldtteam.structurize.placement.BlockPlacementResult.Result.BREAK_BLOCK&&f.level.getBlockState(pos).is(Blocks.COAL_ORE),"Access decoration must mine existing ore before placing a ladder: "+blocked.getResult());
   f.level.setBlock(pos,info.getState(),18);
   check(placer.handleBlockPlacement(f.level,pos,null,info).getResult()==com.ldtteam.structurize.placement.BlockPlacementResult.Result.SUCCESS,"Matching existing ladder must not be mined again");
   var torch=blueprint.getBlockInfoAsList().stream().filter(b->b.getState().is(Blocks.WALL_TORCH)).findFirst().orElseThrow();var torchPos=handler.getProgressPosInWorld(torch.getPos());f.level.setBlock(torchPos,Blocks.COAL_ORE.defaultBlockState(),18);
   check(placer.handleBlockPlacement(f.level,torchPos,null,torch).getResult()==com.ldtteam.structurize.placement.BlockPlacementResult.Result.BREAK_BLOCK,"Wall-torch access must also mine first");
   f.level.setBlock(pos,Blocks.COAL_ORE.defaultBlockState(),18);blueprint.setPackName("Unrelated QA pack");
   check(placer.handleBlockPlacement(f.level,pos,null,info).getResult()!=com.ldtteam.structurize.placement.BlockPlacementResult.Result.BREAK_BLOCK,"Access mining changed another pack");blueprint.setPackName("Arcology Horizons");
   handler.setStage(BuildingProgressStage.BUILD_SOLID);
   check(placer.handleBlockPlacement(f.level,pos,null,info).getResult()!=com.ldtteam.structurize.placement.BlockPlacementResult.Result.BREAK_BLOCK,"Access mining changed solid stage");

   ai.loadStructure(order,order.getLocation(),false);
   var structure=AbstractEntityAIStructure.class.getDeclaredField("structurePlacer");structure.setAccessible(true);
   var pair=(com.minecolonies.api.util.Tuple<?,?>)structure.get(ai);
   var active=(BuildingStructureHandler<?,?>)pair.getB();
   var iterator=(com.minecolonies.core.entity.ai.workers.util.LayerBlueprintIterator)((com.ldtteam.structurize.placement.StructurePlacer)pair.getA()).getIterator();
   var advance=EntityAIQuarrier.class.getDeclaredMethod("goToNextStage",com.ldtteam.structurize.placement.StructurePhasePlacementResult.class);advance.setAccessible(true);
   var result=new com.ldtteam.structurize.placement.StructurePhasePlacementResult(BlockPos.ZERO,new com.ldtteam.structurize.placement.BlockPlacementResult(order.getLocation(),com.ldtteam.structurize.placement.BlockPlacementResult.Result.FINISHED));
   for(int y=blueprint.getSizeY()-1;y>=0;y--){
    check(iterator.getLayer()==y&&active.getStage()==BuildingProgressStage.BUILD_SOLID,"Wrong starting shaft layer");
    check((boolean)advance.invoke(ai,result)&&active.getStage()==BuildingProgressStage.DECORATE&&iterator.getLayer()==y,"Access ladders must decorate the current completed liner before clearing: layer="+y+" stage="+active.getStage()+" iterator="+iterator.getLayer());
    check((boolean)advance.invoke(ai,result)&&active.getStage()==BuildingProgressStage.CLEAR&&iterator.getLayer()==y,"Clear moved away from decorated layer");
    check((boolean)advance.invoke(ai,result)==(y>0),"Wrong shaft completion boundary");
   }
   active.setStage(BuildingProgressStage.DECORATE);iterator.setLayer(1);
   var edge=new com.ldtteam.structurize.placement.StructurePhasePlacementResult(BlockPos.ZERO,new com.ldtteam.structurize.placement.BlockPlacementResult(new BlockPos(pos.getX(),f.level.getMinBuildHeight(),pos.getZ()),com.ldtteam.structurize.placement.BlockPlacementResult.Result.FINISHED));
   check(!(boolean)advance.invoke(ai,edge),"World-minimum stop bypassed");
   var foreign=new WorkOrderMiner("Unrelated QA pack",path,f.names[i],RotationMirror.NONE,quarry.getPosition().below(2),false,mine.getPosition());foreign.setBlueprint(blueprint,f.level);foreign.setClaimedBy(mine.getPosition());f.fixture.colony().getWorkManager().addWorkOrder(foreign,false);mine.setWorkOrder(foreign);ai.loadStructure(foreign,foreign.getLocation(),false);
   var foreignPair=(com.minecolonies.api.util.Tuple<?,?>)structure.get(ai);var foreignHandler=(BuildingStructureHandler<?,?>)foreignPair.getB();
   check((boolean)advance.invoke(ai,result)&&foreignHandler.getStage()==BuildingProgressStage.CLEAR,"Other pack's native top-layer stage sequence changed");
   mine.setWorkOrder(order);
   f.level.setBlock(pos,Blocks.BEDROCK.defaultBlockState(),18);check((boolean)predicate.invoke(ai,input,pos,handler),"Bedrock guard bypassed");
   f.level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);f.level.setBlock(back,Blocks.AIR.defaultBlockState(),18);check((boolean)predicate.invoke(ai,input,pos,handler),"Unsupported ladder forced");
   f.level.setBlock(back,Blocks.COBBLESTONE.defaultBlockState(),18);blueprint.setPackName("Unrelated QA pack");check((boolean)predicate.invoke(ai,input,pos,handler),"Other pack altered");blueprint.setPackName("Arcology Horizons");
   var stale=mine.getPosition().above(30);footing.set(ai,stale);ai.walkToConstructionSite(quarry.getPosition().below(12));check(!stale.equals(footing.get(ai)),"Excavated floating work position retained");
   var safe=quarry.getPosition().north(4);f.level.setBlock(safe.below(),Blocks.STONE.defaultBlockState(),18);footing.set(ai,safe);ai.walkToConstructionSite(quarry.getPosition().below(12));check(safe.equals(footing.get(ai)),"Supported work position discarded");
   rows.add(new ProbeResult(f.names[i]+"_rules",ProbeResult.Outcome.PASS,"Native decoration skips blueprint air, requests native mining before ladder/torch placement over ore, preserves already-matching ladders and unrelated-pack/solid-stage placement behavior; layer stages complete liner, current-layer decorations, then excavation for every32/64 layers; world-minimum stop, other-pack stage sequence, bedrock and unsupported solid predicates preserved. Native walkToConstructionSite discards a floating stale work position and retains supported footing. Focused rule calls, not full excavation or travel acceptance."));
  }
  ProbeReport.write(f.output,"ArcologyQuarryRules",rows);f.h.succeed();
 }catch(Throwable e){try{rows.add(new ProbeResult("quarry_rules_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,"ArcologyQuarryRules",rows);}catch(Exception ignored){}f.h.fail(e.toString());}}
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
