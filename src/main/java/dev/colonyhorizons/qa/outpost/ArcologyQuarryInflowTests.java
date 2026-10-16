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

/** A retained external reservoir must stop flowing into the shaft after native containment. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryInflowTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300000)
 public static void outside_inflow(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var f=new ArcologyQuarryTests.Run(h);f.afterSetup=r->new Monitor(r).start();f.start();});
 }
 static final class Monitor {
  final ArcologyQuarryTests.Run f;
  final List<List<BlockPos>> sources=new ArrayList<>(),walls=new ArrayList<>(),inside=new ArrayList<>(),lower=new ArrayList<>(),afterSeal=new ArrayList<>();
  final boolean[] inflow=new boolean[2];final int[] containedAt=new int[2];
  final List<ProbeResult> rows=new ArrayList<>();int ticks;
  Monitor(ArcologyQuarryTests.Run f){this.f=f;}
  void start(){try{
   for(int i=0;i<2;i++){
    var interior=f.completeShafts.get(i).entrySet().stream().filter(e->e.getKey().getY()==13&&e.getValue().isAir()).map(Map.Entry::getKey).toList();
    inside.add(interior);
    int wallX=interior.stream().mapToInt(BlockPos::getX).min().orElseThrow()-1;
    int startZ=interior.stream().mapToInt(BlockPos::getZ).min().orElseThrow()+2;
    sources.add(new ArrayList<>());walls.add(new ArrayList<>());afterSeal.add(null);
    lower.add(f.completeShafts.get(i).entrySet().stream().filter(e->e.getKey().getY()==11&&e.getValue().isAir()).map(Map.Entry::getKey).toList());
    f.check(lower.get(i).size()>=12&&lower.get(i).stream().allMatch(p->f.level.getBlockState(p).is(Blocks.STONE)),"Lower layer must start solid");
    // Open only the initial test cave. Subsequent containment is entirely native.
    for(int z=startZ;z<startZ+3;z++){
     var wall=new BlockPos(wallX,13,z);
     f.check(f.completeShafts.get(i).get(wall).is(Blocks.COBBLESTONE),"Expected shaft liner at cave breach");
     walls.get(i).add(wall);
     for(int x=wallX-1;x<=wallX+1;x++)f.level.setBlock(new BlockPos(x,13,z),Blocks.AIR.defaultBlockState(),3);
     for(int x=wallX-4;x<=wallX-2;x++){
      var source=new BlockPos(x,13,z);sources.get(i).add(source);
      f.check(!interior.contains(source),"External reservoir overlaps shaft interior");
      f.level.setBlock(source,(i==0?Blocks.WATER:Blocks.LAVA).defaultBlockState(),3);
     }
    }
    f.check(sources.get(i).size()==9&&sources.get(i).stream().allMatch(p->f.level.getFluidState(p).isSource()),"Reservoir setup failed");
   }
   schedule();
  }catch(Throwable e){fail(e);}}
  void schedule(){f.h.runAfterDelay(1,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks++;boolean complete=true;
   for(int i=0;i<2;i++){
    var c=f.workers.get(i);var e=c.getEntity().orElseThrow();var mine=f.mines.get(i);var quarry=f.quarries.get(i);
    f.check(e.isAlive()&&e.getUUID().equals(f.ids.get(i))&&c.getWorkBuilding()==mine&&c.getJob() instanceof JobQuarrier
     &&((JobQuarrier)c.getJob()).findQuarry()==quarry&&quarry.getFirstModuleOccurance(QuarryModule.class).hasAssignedCitizen(c),"Inflow worker/assignment changed");
    f.check(f.previous.get(i).distanceTo(e.position())<=12,"Inflow recovery teleport");f.previous.set(i,e.position());
    f.check(!e.isOnFire()&&!e.isInLava(),"Inflow worker burned");c.setSaturation(20);
    if(f.done[i])continue;
    var order=mine.getWorkOrder();if(order!=null){
     f.check(order instanceof WorkOrderMiner&&order.getStructurePack().equals("Arcology Horizons")&&order.getClaimedBy().equals(mine.getPosition()),"Wrong inflow order");f.orderSeen[i]=true;
    }
    if(ticks%20==0){
     f.supply(i);
     for(var item:List.of(Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL)){
      var stack=new ItemStack(item);
      if(ArcologyOperationsFixture.count(c.getInventory(),stack)+ArcologyOperationsFixture.count(mine.getItemHandlerCap((Direction)null),stack)<2)
       ItemHandlerHelper.insertItemStacked(mine.getItemHandlerCap((Direction)null),stack,false);
     }
    }

    boolean wet=inside.get(i).stream().anyMatch(p->!f.level.getFluidState(p).isEmpty());inflow[i]|=wet;
    boolean wallClosed=walls.get(i).stream().allMatch(p->f.level.getBlockState(p).is(Blocks.COBBLESTONE));
    if(inflow[i]&&wallClosed&&!wet&&containedAt[i]==0){
     containedAt[i]=ticks;
     var remaining=lower.get(i).stream().filter(p->f.level.getBlockState(p).is(Blocks.STONE)).toList();
     f.check(remaining.size()>=12,"No untouched lower terrain after containment");afterSeal.set(i,remaining);
    }
    if(containedAt[i]>0)f.check(wallClosed&&!wet,"Fluid re-entered shaft after containment");
    int cleared=0;if(afterSeal.get(i)!=null)for(var pos:afterSeal.get(i))if(f.level.getBlockState(pos).isAir())cleared++;
    long retained=sources.get(i).stream().filter(p->f.level.getFluidState(p).isSource()).count();
    if(containedAt[i]>0&&ticks-containedAt[i]>=200&&cleared>=12&&f.stat(i)>0&&f.orderSeen[i]){
     f.check(retained==9,"External reservoir was removed instead of contained");
     rows.add(new ProbeResult(f.names[i]+"_external_"+(i==0?"water":"lava"),ProbeResult.Outcome.PASS,
      "Natural fluid spread from9 initial external source blocks entered the shaft through a3-wide cave breach. Native worker restored all3 cobblestone liner cells, shaft interior stayed dry for "+(ticks-containedAt[i])+
      " ticks and12 fresh cells two layers below were excavated after containment. All9 external sources remain. Same worker/dual assignment, recovery disabled, no observed burning/lava contact. Supplies/tools/food provided; no terrain/fluid edits after setup. Bounded retained reservoir, not every aquifer or full-depth wet excavation. ticks="+ticks));
     f.done[i]=true;c.setPaused(true);ProbeReport.write(f.output,"ArcologyQuarryInflow",rows);
    }
    complete&=f.done[i];
    if(ticks%1000==0)Files.writeString(f.output.resolve("quarry-inflow-diagnostics.txt"),"tick="+ticks+" shaft="+i+" pos="+e.position()+" ai="+c.getJob().getWorkerAI().getState()+" inflow="+inflow[i]+" wet="+wet+" wallClosed="+wallClosed+" containedAt="+containedAt[i]+" lower="+cleared+" sources="+retained+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(complete){f.h.succeed();return;}f.check(ticks<280000,"External inflow timeout");schedule();
  }
  void fail(Throwable e){try{rows.add(new ProbeResult("inflow_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,"ArcologyQuarryInflow",rows);}catch(Exception ignored){}f.h.fail(e.toString());}
 }
}
