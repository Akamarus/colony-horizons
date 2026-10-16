package dev.colonyhorizons.qa.outpost;

import com.minecolonies.core.colony.jobs.JobQuarrier;
import com.minecolonies.core.colony.buildings.modules.QuarryModule;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Actual excavation must seal enclosed fluid pockets and continue below them. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryHazardTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300000)
 public static void quarry_hazards(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var f=new ArcologyQuarryTests.Run(h);f.afterSetup=r->new Monitor(r,System.getProperty("colonyhorizons.foundationSuite","").equals("ArcologyQuarryPools")).start();f.start();});
 }
 static final class Monitor {
  final ArcologyQuarryTests.Run f;final boolean pools;final boolean restock=System.getProperty("colonyhorizons.foundationSuite","").equals("ArcologyQuarryRestock");final String report;
  final int[] shortageAt=new int[2];final boolean[] waited=new boolean[2],resupplied=new boolean[2];final BlockPos[] barrier=new BlockPos[2];final net.minecraft.world.level.block.state.BlockState[] barrierState=new net.minecraft.world.level.block.state.BlockState[2];java.lang.reflect.Field miningTarget;final BlockPos[][] pockets;
  final boolean[][] sealed;final int[][] sealTick;
  final List<List<BlockPos>> lowerCells=new ArrayList<>(),afterSeals=new ArrayList<>();final List<ProbeResult> rows=new ArrayList<>();int ticks;
  Monitor(ArcologyQuarryTests.Run f,boolean pools){this.f=f;this.pools=pools;report=restock?"ArcologyQuarryRestock":pools?"ArcologyQuarryPools":"ArcologyQuarryHazards";int size=pools?9:2;pockets=new BlockPos[2][size];sealed=new boolean[2][size];sealTick=new int[2][size];}
  boolean allSealed(int i){for(boolean value:sealed[i])if(!value)return false;return true;}
  void start(){try{
   if(restock){miningTarget=com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure.class.getDeclaredField("blockToMine");miningTarget.setAccessible(true);}
   for(int i=0;i<2;i++){
    lowerCells.add(f.completeShafts.get(i).entrySet().stream().filter(e->e.getKey().getY()==11&&e.getValue().isAir()).map(Map.Entry::getKey).toList());
    f.check(lowerCells.get(i).size()>=12&&lowerCells.get(i).stream().allMatch(pos->f.level.getBlockState(pos).is(Blocks.STONE)),"Lower layer must start solid");afterSeals.add(null);
    var candidates=f.completeShafts.get(i).entrySet().stream().filter(e->e.getKey().getY()==13&&e.getValue().isAir())
     .map(Map.Entry::getKey).filter(p->Arrays.stream(Direction.values()).allMatch(d->f.level.getBlockState(p.relative(d)).is(Blocks.STONE)))
     .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).toList();
    if(pools){
     var corner=candidates.getFirst().offset(1,0,1);
     for(int x=0;x<3;x++)for(int z=0;z<3;z++)pockets[i][x*3+z]=corner.offset(x,0,z);
    }else{
     pockets[i][0]=candidates.getFirst();var first=pockets[i][0];
     pockets[i][1]=candidates.stream().filter(p->p.distManhattan(first)>=5).findFirst().orElseThrow();
    }
    for(int j=0;j<pockets[i].length;j++){
     var pos=pockets[i][j];f.check(f.level.getBlockState(pos).is(Blocks.STONE),"Hazard terrain already excavated");
     f.check(f.completeShafts.get(i).get(pos).isAir(),"Pool must be inside shaft interior");
     if(pools)f.level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
     f.level.setBlock(pos,((pools?i:j)==0?Blocks.WATER:Blocks.LAVA).defaultBlockState(),3);
     f.check(f.level.getFluidState(pos).isSource(),"Missing source fluid");
    }
   }
   schedule();
  }catch(Throwable e){fail(e);}}
  void schedule(){f.h.runAfterDelay(1,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks++;boolean finished=true;
   for(int i=0;i<2;i++){
    var c=f.workers.get(i);var e=c.getEntity().orElseThrow();var mine=f.mines.get(i);var quarry=f.quarries.get(i);
    f.check(e.isAlive()&&e.getUUID().equals(f.ids.get(i))&&c.getWorkBuilding()==mine&&c.getJob() instanceof JobQuarrier
     &&((JobQuarrier)c.getJob()).findQuarry()==quarry&&quarry.getFirstModuleOccurance(QuarryModule.class).hasAssignedCitizen(c),"Hazard worker/assignment lost");
    f.check(f.previous.get(i).distanceTo(e.position())<=12,"Hazard recovery teleport");f.previous.set(i,e.position());
    f.check(!e.isOnFire()&&!e.isInLava(),"Worker burned during fluid encounter");c.setSaturation(20);
    if(f.done[i])continue;
    var order=mine.getWorkOrder();if(order!=null){f.check(order instanceof WorkOrderMiner&&order.getStructurePack().equals("Arcology Horizons")&&order.getClaimedBy().equals(mine.getPosition()),"Wrong hazard work order");f.orderSeen[i]=true;}
    if(restock)handleShortage(i);
    if(ticks%20==0){
     if(!restock||shortageAt[i]==0||resupplied[i])f.supply(i);
     for(var tool:List.of(Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL)){
      var stack=new ItemStack(tool);if(ArcologyOperationsFixture.count(c.getInventory(),stack)+ArcologyOperationsFixture.count(mine.getItemHandlerCap((Direction)null),stack)<2)
       ItemHandlerHelper.insertItemStacked(mine.getItemHandlerCap((Direction)null),stack,false);
     }
    }
    for(int j=0;j<pockets[i].length;j++)if(!sealed[i][j]){
     var state=f.level.getBlockState(pockets[i][j]);
     if(state.is(Blocks.COBBLESTONE)){sealed[i][j]=true;sealTick[i][j]=ticks;}
     else f.check(!state.isAir(),"Fluid disappeared without observed native fill: "+pockets[i][j]);
    }

    if(allSealed(i)&&afterSeals.get(i)==null){
     var solid=lowerCells.get(i).stream().filter(pos->f.level.getBlockState(pos).is(Blocks.STONE)).toList();
     f.check(solid.size()>=12,"Too little untouched lower terrain remains after sealing");afterSeals.set(i,solid);
    }
    int lower=0;if(afterSeals.get(i)!=null)for(var pos:afterSeals.get(i))if(f.level.getBlockState(pos).isAir())lower++;
    if(allSealed(i)&&lower>=12&&f.stat(i)>0&&f.orderSeen[i]){
     f.check(!restock||(waited[i]&&resupplied[i]),"Live shortage/resupply was not observed");
     for(int j=0;j<pockets[i].length;j++){
      f.check(f.level.getFluidState(pockets[i][j]).isEmpty(),"Hazard fluid returned");
      if((!pools&&!restock)||j==0)rows.add(new ProbeResult(f.names[i]+"_"+(restock?"restock":pools?(i==0?"water_pool":"lava_pool"):(j==0?"water":"lava")),ProbeResult.Outcome.PASS,
       (restock?"Live stock withdrawal at tick "+shortageAt[i]+": native NEEDS_ITEM observed and original barrier retained for200 ticks, then fill supplied to the Mine hut only; both fluid pockets subsequently sealed":pools?"Nine connected source blocks in a3x3 pool with initial open headspace were each observed as native cobblestone fill, seal ticks="+Arrays.toString(sealTick[i]):"Enclosed source pocket at "+pockets[i][j]+" became native cobblestone fill at tick "+sealTick[i][j])+
       "; same native quarrier then cleared "+lower+" interior cells two layers below it. Native coal statistic="+f.stat(i)+
       ". Same worker and Mine/quarry assignment, recovery disabled, no fire/lava contact observed. Requested materials/tools and food supplied; no post-start terrain edits or health restoration. Bounded fixture; not an unbounded aquifer, lava lake or autonomous external resupply. ticks="+ticks));
     }
     f.done[i]=true;c.setPaused(true);ProbeReport.write(f.output,report,rows);
    }
    finished&=f.done[i];
    if(ticks%1000==0)Files.writeString(f.output.resolve("quarry-hazard-diagnostics.txt"),"tick="+ticks+" shaft="+i+" pos="+e.position()+" ai="+c.getJob().getWorkerAI().getState()+" sealed="+Arrays.toString(sealed[i])+" lower="+lower+" coal="+f.stat(i)+" target="+((com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier)c.getJob().getWorkerAI()).getCurrentBuildingPosition()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(finished){f.h.succeed();return;}f.check(ticks<280000,"Fluid encounter timeout");schedule();
  }
  void handleShortage(int i)throws Exception{
   var c=f.workers.get(i);var ai=c.getJob().getWorkerAI();
   if(shortageAt[i]==0){
    var target=(BlockPos)miningTarget.get(ai);
    if(target==null||f.level.getBlockState(target).isAir()||Arrays.stream(Direction.values()).noneMatch(d->!f.level.getFluidState(target.relative(d)).isEmpty()))return;
    barrier[i]=target.immutable();barrierState[i]=f.level.getBlockState(target);shortageAt[i]=ticks;
    drain(c.getInventory());drain(f.mines.get(i).getItemHandlerCap((Direction)null));
    f.check(ArcologyOperationsFixture.count(c.getInventory(),new ItemStack(Items.COBBLESTONE))==0&&ArcologyOperationsFixture.count(f.mines.get(i).getItemHandlerCap((Direction)null),new ItemStack(Items.COBBLESTONE))==0,"Shortage fixture failed to withdraw fill");
   }
   if(resupplied[i])return;
   waited[i]|=ai.getState()==com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.NEEDS_ITEM;
   f.check(f.level.getBlockState(barrier[i]).equals(barrierState[i]),"Barrier changed before resupply");
   f.check(Arrays.stream(Direction.values()).anyMatch(d->!f.level.getFluidState(barrier[i].relative(d)).isEmpty()),"Hazard disappeared during shortage");
   if(ticks-shortageAt[i]>=200){
    f.check(waited[i],"Native missing-item state never observed");
    var left=ItemHandlerHelper.insertItemStacked(f.mines.get(i).getItemHandlerCap((Direction)null),new ItemStack(Items.COBBLESTONE,64),false);
    f.check(left.isEmpty(),"Hut resupply did not fit");resupplied[i]=true;
   }
  }
  void drain(net.neoforged.neoforge.items.IItemHandler inventory){
   for(int slot=0;slot<inventory.getSlots();slot++)if(inventory.getStackInSlot(slot).is(Items.COBBLESTONE))inventory.extractItem(slot,inventory.getStackInSlot(slot).getCount(),false);
  }
  void fail(Throwable e){try{rows.add(new ProbeResult("quarry_hazard_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,report,rows);}catch(Exception ignored){}f.h.fail(e.toString());}
 }
}
