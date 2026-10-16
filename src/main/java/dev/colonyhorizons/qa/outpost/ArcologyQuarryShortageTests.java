package dev.colonyhorizons.qa.outpost;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.production.EntityAIQuarrier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.NEEDS_ITEM;

/** Focused native doMining calls: shortage must not open a fluid barrier. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryShortageTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=12000)
 public static void fill_shortage(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var f=new ArcologyQuarryTests.Run(h);f.afterSetup=ArcologyQuarryShortageTests::verify;f.start();});
 }
 static void verify(ArcologyQuarryTests.Run f){var rows=new ArrayList<ProbeResult>();try{
  var targetField=AbstractEntityAIStructure.class.getDeclaredField("blockToMine");targetField.setAccessible(true);
  var footing=AbstractEntityAIStructure.class.getDeclaredField("workFrom");footing.setAccessible(true);
  for(int i=0;i<2;i++){
   var c=f.workers.get(i);c.setPaused(true);var ai=(EntityAIQuarrier)c.getJob().getWorkerAI();var mine=f.mines.get(i);var quarry=f.quarries.get(i);
   String path="infrastructure/mineshafts/"+f.names[i]+"shaft1.blueprint";
   var bp=StructurePacks.getBlueprintFuture("Arcology Horizons",path,f.level.registryAccess()).get();
   var order=new WorkOrderMiner("Arcology Horizons",path,f.names[i],RotationMirror.NONE,quarry.getPosition().below(2),false,mine.getPosition());
   order.setBlueprint(bp,f.level);order.setClaimedBy(mine.getPosition());f.fixture.colony().getWorkManager().addWorkOrder(order,false);mine.setWorkOrder(order);ai.loadStructure(order,order.getLocation(),false);
   var target=quarry.getPosition().offset(0,-1,6);var stand=target.above().south(2);var e=c.getEntity().orElseThrow();e.setPos(stand.getX()+0.5,stand.getY(),stand.getZ()+0.5);footing.set(ai,stand);targetField.set(ai,target);
   f.level.setBlock(target,Blocks.COAL_ORE.defaultBlockState(),18);
   f.level.setBlock(target.east(),Blocks.WATER.defaultBlockState(),18);f.level.setBlock(target.west(),Blocks.LAVA.defaultBlockState(),18);
   var inv=c.getInventory();for(int slot=0;slot<inv.getSlots();slot++)if(inv.getStackInSlot(slot).is(Items.COBBLESTONE))inv.setStackInSlot(slot,ItemStack.EMPTY);
   check(ai.doMining()==NEEDS_ITEM,"Zero fill stock must enter native NEEDS_ITEM before mining");
   check(f.level.getBlockState(target).is(Blocks.COAL_ORE)&&f.level.getFluidState(target.east()).isSource()&&f.level.getFluidState(target.west()).isSource(),"Shortage changed barrier or fluids");
   check(!mine.getOpenRequestsOfCitizenOrBuilding(c.getId(),r->r.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable d && d.matches(new ItemStack(Items.COBBLESTONE))).isEmpty(),"Shortage must create a native material request");
   inv.setStackInSlot(7,new ItemStack(Items.COBBLESTONE,1));
   check(ai.doMining()==NEEDS_ITEM,"One fill block cannot seal two adjacent fluids");
   check(inv.getStackInSlot(7).getCount()==1&&f.level.getBlockState(target).is(Blocks.COAL_ORE),"Insufficient supply must remain available and barrier intact");
   inv.setStackInSlot(7,new ItemStack(Items.COBBLESTONE,2));ai.doMining();
   check(f.level.getBlockState(target.east()).is(Blocks.COBBLESTONE)&&f.level.getBlockState(target.west()).is(Blocks.COBBLESTONE),"Restocking did not seal both adjacent sources");
   check(inv.getStackInSlot(7).isEmpty(),"Fill consumption must be exactly two");
   // Exact-pack boundary: an unrelated work order retains native behavior.
   var foreign=new WorkOrderMiner("Unrelated QA pack",path,f.names[i],RotationMirror.NONE,quarry.getPosition().below(2),false,mine.getPosition());
   foreign.setBlueprint(bp,f.level);foreign.setClaimedBy(mine.getPosition());f.fixture.colony().getWorkManager().addWorkOrder(foreign,false);mine.setWorkOrder(foreign);
   f.level.setBlock(target.east(),Blocks.WATER.defaultBlockState(),18);f.level.setBlock(target.west(),Blocks.LAVA.defaultBlockState(),18);targetField.set(ai,target);
   check(ai.doMining()!=NEEDS_ITEM,"Other pack's shortage behavior changed");
   mine.setWorkOrder(order);f.level.setBlock(target.east(),Blocks.STONE.defaultBlockState(),18);f.level.setBlock(target.west(),Blocks.STONE.defaultBlockState(),18);targetField.set(ai,target);
   check(ai.doMining()!=NEEDS_ITEM,"Dry mining must not require fill supplies");
   rows.add(new ProbeResult(f.names[i]+"_fill_shortage",ProbeResult.Outcome.PASS,"Focused native doMining calls: zero or one fill block waits with ore barrier intact and a native request; two supplied blocks seal both adjacent water/lava cells and consume exactly two. Paused worker and explicit native workorder/target fixture; not natural courier restocking or end-to-end AI resumption."));
  }
  ProbeReport.write(f.output,"ArcologyQuarryShortage",rows);f.h.succeed();
 }catch(Throwable e){try{rows.add(new ProbeResult("shortage_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,"ArcologyQuarryShortage",rows);}catch(Exception ignored){}f.h.fail(e.toString());}}
 static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
