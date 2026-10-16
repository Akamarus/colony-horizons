package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.NEEDS_ITEM;

/** Native quarry actions, real fluid ticks and tools; paused scheduler with explicit work targets. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryDirtInflowTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=5000)
 public static void dirt_inflow(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{try{
   Files.createDirectories(java.nio.file.Path.of(System.getProperty("colonyhorizons.probeOutput")));
   var f=new ArcologyQuarryTests.Run(h);f.afterSetup=r->new Monitor(r).start();f.start();
  }catch(Exception e){h.fail(e.toString());}});
 }
 static final class Monitor {
  final ArcologyQuarryTests.Run f;final List<ProbeResult> rows=new ArrayList<>();
  final EntityAIQuarrier[] ai=new EntityAIQuarrier[2];final BlockPos[] target=new BlockPos[2];
  final int[] clearedAt=new int[2],wearAt=new int[2];final boolean[] done=new boolean[2];
  Field targetField;int ticks;
  Monitor(ArcologyQuarryTests.Run f){this.f=f;}
  void start(){try{
   targetField=AbstractEntityAIStructure.class.getDeclaredField("blockToMine");targetField.setAccessible(true);
   var footing=AbstractEntityAIStructure.class.getDeclaredField("workFrom");footing.setAccessible(true);
   for(int i=0;i<2;i++){
    var c=f.workers.get(i);c.setPaused(true);ai[i]=(EntityAIQuarrier)c.getJob().getWorkerAI();
    var mine=f.mines.get(i);var quarry=f.quarries.get(i);mine.getSetting(BuildingMiner.FILL_BLOCK).setValue((BlockItem)Items.DIRT);
    String path="infrastructure/mineshafts/"+f.names[i]+"shaft1.blueprint";
    var bp=StructurePacks.getBlueprintFuture("Arcology Horizons",path,f.level.registryAccess()).get();
    var order=new WorkOrderMiner("Arcology Horizons",path,f.names[i],RotationMirror.NONE,quarry.getPosition().below(2),false,mine.getPosition());
    order.setBlueprint(bp,f.level);order.setClaimedBy(mine.getPosition());f.fixture.colony().getWorkManager().addWorkOrder(order,false);mine.setWorkOrder(order);ai[i].loadStructure(order,order.getLocation(),false);
    var p=quarry.getPosition().offset(0,-1,6);target[i]=p;
    // Confine a retained overhead source. Only the initial fixture edits terrain.
    for(var q:BlockPos.betweenClosed(p.offset(-2,-1,-2),p.offset(2,3,2)))f.level.setBlock(q,Blocks.STONE.defaultBlockState(),3);
    var stand=p.south();f.level.setBlock(stand,Blocks.AIR.defaultBlockState(),3);f.level.setBlock(stand.above(),Blocks.AIR.defaultBlockState(),3);
    var e=c.getEntity().orElseThrow();e.setPos(stand.getX()+0.5,stand.getY(),stand.getZ()+0.5);footing.set(ai[i],stand);targetField.set(ai[i],p);
    f.level.setBlock(p,Blocks.DIRT.defaultBlockState(),3);
    f.level.setBlock(p.above(2),Blocks.WATER.defaultBlockState(),3);
    f.level.setBlock(p.above(),Blocks.WATER.defaultBlockState(),3);
    f.level.setBlock(p.west(),Blocks.WATER.defaultBlockState(),3);
    var inv=c.getInventory();for(int s=0;s<inv.getSlots();s++)if(inv.getStackInSlot(s).is(Items.DIRT))inv.setStackInSlot(s,ItemStack.EMPTY);
    int damage=shovel(i);
    for(int stock=0;stock<=1;stock++){
     inv.setStackInSlot(7,stock==0?ItemStack.EMPTY:new ItemStack(Items.DIRT,stock));
     for(int n=0;n<10;n++)check(ai[i].doMining()==NEEDS_ITEM,"Insufficient dirt must wait");
     check(f.level.getBlockState(p).is(Blocks.DIRT)&&f.level.getFluidState(p.above()).isSource()&&f.level.getFluidState(p.west()).isSource(),"Shortage opened barrier or changed water");
     check(inv.getStackInSlot(7).getCount()==stock&&shovel(i)==damage,"Shortage wasted stock or shovel");
    }
    check(!mine.getOpenRequestsOfCitizenOrBuilding(c.getId(),r->r.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable d&&d.matches(new ItemStack(Items.DIRT))).isEmpty(),"No native dirt request");
    pass(i,"shortage","Zero/one dirt waits for two wet neighbors; barrier, water, supplies and shovel preserved; native dirt request created");
    inv.setStackInSlot(7,new ItemStack(Items.DIRT,2));ai[i].doMining();
    check(f.level.getBlockState(p.above()).is(Blocks.DIRT)&&f.level.getBlockState(p.west()).is(Blocks.DIRT),"Restock did not seal above and side");
    check(inv.getStackInSlot(7).isEmpty(),"Expected exactly two fill items consumed before excavation");
    pass(i,"restock","Exactly two supplied dirt seal overhead and side water through the native quarry action");
   }
   schedule();
  }catch(Throwable e){fail(e);}}
  void schedule(){f.h.runAfterDelay(1,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks++;
   for(int i=0;i<2;i++){
    if(done[i])continue;var p=target[i];ai[i].doMining();
    check(f.level.getBlockState(p.above()).is(Blocks.DIRT)&&f.level.getBlockState(p.west()).is(Blocks.DIRT),"Seal was removed");
    check(f.level.getFluidState(p.above(2)).isSource(),"Retained water source was deleted");
    if(clearedAt[i]==0&&f.level.getBlockState(p).isAir()){
     clearedAt[i]=ticks;wearAt[i]=shovel(i);check(wearAt[i]==1,"Dirt plug must cost exactly one shovel use");
     pass(i,"excavation","Native mining removed the dirt plug once; one shovel durability used");
    }
    if(clearedAt[i]>0){
     check(f.level.getBlockState(p).isAir()&&f.level.getFluidState(p).isEmpty(),"Water re-entered excavation");
     check(shovel(i)==wearAt[i],"Shovel kept wearing after excavation");
     if(ticks-clearedAt[i]>=200){pass(i,"stable_inflow","Dry for 200 real fluid ticks after excavation, with overhead reservoir retained and no further shovel wear");done[i]=true;}
    }
   }
   if(done[0]&&done[1]){ProbeReport.write(f.output,"ArcologyQuarryDirtInflow",rows);f.h.succeed();return;}
   check(ticks<1200,"Quarry did not finish dirt excavation");schedule();
  }
  int shovel(int i){var inv=f.workers.get(i).getInventory();for(int s=0;s<inv.getSlots();s++)if(inv.getStackInSlot(s).is(Items.DIAMOND_SHOVEL))return inv.getStackInSlot(s).getDamageValue();throw new IllegalStateException("Shovel lost");}
  void pass(int i,String id,String evidence){rows.add(new ProbeResult(f.names[i]+"_dirt_"+id,ProbeResult.Outcome.PASS,evidence));}
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable e){try{rows.add(new ProbeResult("failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,"ArcologyQuarryDirtInflow",rows);}catch(Exception ignored){}f.h.fail(e.toString());}
 }
}
