package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner;
import com.minecolonies.core.util.WorkerUtil;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** Focused native shaft action with natural water ticks. Scheduler is paused; no simulated excavation. */
@PrefixGameTestTemplate(false)
public final class ArcologyMinerInflowTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=5000)
 public static void dirt_plug_under_water(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{
   var f=new ArcologyUpgradeTests.Run(h,List.of("fundamentals/miner1.blueprint"),List.of(new BlockPos(512,16,0)),"ArcologyMinerInflow");
   f.afterSetup=r->new Monitor(r).start();f.start();
  });
 }
 static final class Monitor {
  final ArcologyUpgradeTests.Run f;ICitizenData c;Object ai;Method action;
  BlockPos target,source;BuildingMiner mine;int ticks,placed,removed,drySince,phase,phaseStart,pickBefore;boolean wasDirt;
  final List<ProbeResult> rows=new ArrayList<>();
  Monitor(ArcologyUpgradeTests.Run f){this.f=f;}
  void start(){try{
   Files.createDirectories(f.output);
   f.worker.setPaused(true);
   mine=(BuildingMiner)f.place("fundamentals/miner5.blueprint",new BlockPos(512,16,0),true);
   mine.setStructurePack("Arcology Horizons");
   check("Arcology Horizons".equals(mine.getStructurePack()),"Test mine must retain Arcology Horizons style");
   mine.getSetting(BuildingMiner.FILL_BLOCK).setValue((BlockItem)Items.DIRT);
   c=f.fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Citizen fixture");
   check(mine.getModule(BuildingModules.MINER_WORK).assignCitizen(c),"Miner assignment");
   c.setPaused(true);c.setSaturation(20);
   c.getInventory().setStackInSlot(0,new ItemStack(Items.DIAMOND_SHOVEL));
   c.getInventory().setStackInSlot(1,new ItemStack(Items.DIAMOND_PICKAXE));
   c.getInventory().setStackInSlot(2,new ItemStack(Items.DIRT,64));
   ai=c.getJob().getWorkerAI();check(ai instanceof EntityAIStructureMiner,"Regular Mine AI required");
   action=EntityAIStructureMiner.class.getDeclaredMethod("doShaftMining");action.setAccessible(true);
   var ladder=mine.getLadderLocation();var ladderState=f.level.getBlockState(ladder);
   check(ladderState.is(Blocks.LADDER),"Actual ladder marker must contain ladder");
   for(int depth=1;depth<=12;depth++){
    f.level.setBlock(mine.getCobbleLocation().below(depth),Blocks.COBBLESTONE.defaultBlockState(),3);
    f.level.setBlock(ladder.below(depth),ladderState,3);
   }
   int y=WorkerUtil.getLastLadder(ladder,f.level);
   var vector=ladder.subtract(mine.getCobbleLocation());
   var center=new BlockPos(ladder.getX()+3*vector.getX(),y,ladder.getZ()+3*vector.getZ());
   // Dry excavation plane except one inflow cell. A stone roof confines an overhead source.
   for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
    var p=center.offset(x,0,z);if((p.getX()==ladder.getX()&&p.getZ()==ladder.getZ())||(p.getX()==mine.getCobbleLocation().getX()&&p.getZ()==mine.getCobbleLocation().getZ()))continue;
    f.level.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);
    f.level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
    f.level.setBlock(p.above(),Blocks.AIR.defaultBlockState(),3);
    f.level.setBlock(p.above(2),Blocks.STONE.defaultBlockState(),3);
   }
   target=center;source=target.above();
   for(var d:Direction.Plane.HORIZONTAL)f.level.setBlock(source.relative(d),Blocks.STONE.defaultBlockState(),3);
   f.level.setBlock(source,Blocks.WATER.defaultBlockState(),3);
   f.level.setBlock(target,Blocks.WATER.defaultBlockState(),3);
   c.getEntity().orElseThrow().setPos(target.getX()+1.5,y,target.getZ()+0.5);
   schedule();
  }catch(Throwable e){fail(e);}}
  void schedule(){f.h.runAfterDelay(1,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks++;action.invoke(ai);
   if(phase==1){
    if(f.level.getBlockState(target).isAir()){
     check(f.level.getBlockState(source).is(Blocks.DIRT)&&f.level.getBlockState(target.below()).is(Blocks.DIRT),"Restocked seals changed");
     pass("restocked_two_sides","Two supplied dirt seal both fluid neighbors before native mining resumes");
     protectedContainerAndBoundary();
     f.level.setBlock(source,Blocks.STONE.defaultBlockState(),3);
     f.level.setBlock(target,Blocks.STONE.defaultBlockState(),3);
     pickBefore=c.getInventory().getStackInSlot(1).getDamageValue();phase=2;phaseStart=ticks;
    }
    check(ticks-phaseStart<200,"Restocked mining failed to resume");schedule();return;
   }
   if(phase==2){
    if(f.level.getBlockState(target).isAir()){
     check(c.getInventory().getStackInSlot(1).getDamageValue()>pickBefore,"Dry stone must be mined with native tool wear");
     pass("dry_mining","Native pickaxe excavation still works after the wet cell is contained");
     // A broad overhead pool reproduces inflow beyond the original single-cell test.
     for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
      var p=target.offset(x,0,z);if(p.getX()==mine.getLadderLocation().getX()&&p.getZ()==mine.getLadderLocation().getZ())continue;
      f.level.setBlock(p.above(),Blocks.WATER.defaultBlockState(),3);
      f.level.setBlock(p,Blocks.WATER.defaultBlockState(),3);
     }
     c.getInventory().setStackInSlot(2,new ItemStack(Items.DIRT,64));
     c.getInventory().setStackInSlot(3,new ItemStack(Items.DIRT,64));
     c.getInventory().setStackInSlot(4,new ItemStack(Items.DIRT,64));
     phase=3;phaseStart=ticks;schedule();return;
    }
    check(ticks-phaseStart<200,"Dry mining blocked");schedule();return;
   }
   if(phase==3){
    int wet=0,plugs=0;
    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
     var p=target.offset(x,0,z);if(p.getX()==mine.getLadderLocation().getX()&&p.getZ()==mine.getLadderLocation().getZ())continue;
     if(!f.level.getFluidState(p).isEmpty())wet++;
     if(f.level.getBlockState(p).is(Blocks.DIRT))plugs++;
    }
    if(wet==0&&plugs==0){pass("broad_overhead_inflow","Entire 7x7 excavation plane dry and all temporary dirt plugs removed after "+(ticks-phaseStart)+" actions");
     target=target.above(4);source=target.above();
     f.level.setBlock(target.below(),Blocks.STONE.defaultBlockState(),3);
     for(var d:Direction.Plane.HORIZONTAL){f.level.setBlock(source.relative(d),Blocks.STONE.defaultBlockState(),3);f.level.setBlock(target.relative(d),Blocks.AIR.defaultBlockState(),3);}
     f.level.setBlock(source.above(),Blocks.STONE.defaultBlockState(),3);
     f.level.setBlock(source,Blocks.WATER.defaultBlockState(),3);f.level.setBlock(target,Blocks.WATER.defaultBlockState(),3);
     c.getInventory().setStackInSlot(2,new ItemStack(Items.DIRT,64));
     var blockField=com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure.class.getDeclaredField("blockToMine");blockField.setAccessible(true);blockField.set(ai,target);
     var workField=com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure.class.getDeclaredField("workFrom");workField.setAccessible(true);workField.set(ai,target.east());
     c.getEntity().orElseThrow().setPos(target.getX()+1.5,target.getY(),target.getZ()+0.5);
     action=EntityAIStructureMiner.class.getDeclaredMethod("doMining");action.setAccessible(true);phase=4;phaseStart=ticks;drySince=0;schedule();return;}
    check(ticks-phaseStart<1600,"Broad pool stalled: wet="+wet+" plugs="+plugs+" "+detail());
    schedule();return;
   }
   if(phase==4){
    boolean dry=f.level.getBlockState(target).isAir()&&f.level.getFluidState(source).isEmpty();
    drySince=dry?(drySince==0?ticks:drySince):0;
    if(drySince>0&&ticks-drySince>=40){pass("shaft_construction_inflow","Native structure doMining contained inflow, mined the plug and stayed dry for 40 ticks");ProbeReport.write(f.output,"ArcologyMinerInflow",rows);f.h.succeed();return;}
    check(ticks-phaseStart<300,"Shaft construction water stalled: "+detail());schedule();return;
   }
   boolean dirt=f.level.getBlockState(target).is(Blocks.DIRT);
   if(dirt&&!wasDirt)placed++;if(!dirt&&wasDirt)removed++;wasDirt=dirt;
   boolean dry=f.level.getBlockState(target).isAir()&&f.level.getFluidState(source).isEmpty();
   drySince=dry?(drySince==0?ticks:drySince):0;
   if(ticks%100==0)Files.writeString(f.output.resolve("miner-inflow-diagnostics.txt"),detail()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   if(drySince>0&&ticks-drySince>=40){
    check(removed>0,"Native mining must remove a dirt plug");
    pass("dirt_overhead_inflow",detail());shortage();phase=1;phaseStart=ticks;schedule();return;
   }
   check(ticks<1200,"Miner did not contain overhead inflow: "+detail());schedule();
  }
  void shortage()throws Exception{
   f.level.setBlock(target,Blocks.DIRT.defaultBlockState(),3);
   f.level.setBlock(source,Blocks.WATER.defaultBlockState(),3);
   f.level.setBlock(target.below(),Blocks.WATER.defaultBlockState(),3);
   var working=EntityAIStructureMiner.class.getDeclaredField("minerWorkingLocation");working.setAccessible(true);working.set(ai,target);
   for(int i=0;i<c.getInventory().getSlots();i++)if(c.getInventory().getStackInSlot(i).is(Items.DIRT))c.getInventory().setStackInSlot(i,ItemStack.EMPTY);
   int damage=c.getInventory().getStackInSlot(0).getDamageValue();
   for(int supply=0;supply<=1;supply++){
    c.getInventory().setStackInSlot(2,supply==0?ItemStack.EMPTY:new ItemStack(Items.DIRT,supply));
    for(int n=0;n<10;n++)action.invoke(ai);
    check(f.level.getBlockState(target).is(Blocks.DIRT)&&f.level.getFluidState(source).isSource()&&f.level.getFluidState(target.below()).isSource(),"Shortage must leave barrier and fluids intact");
    check(c.getInventory().getStackInSlot(2).getCount()==supply&&c.getInventory().getStackInSlot(0).getDamageValue()==damage,"Shortage consumed dirt or tool durability");
   }
   check(!mine.getOpenRequestsOfCitizenOrBuilding(c.getId(),r->r.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable d&&d.matches(new ItemStack(Items.DIRT))).isEmpty(),"Shortage must create native dirt request");
   pass("fill_shortage","Zero and one dirt cannot seal two fluid neighbors: barrier, fluids, stock and shovel preserved; native dirt request exists");
   c.getInventory().setStackInSlot(2,new ItemStack(Items.DIRT,2));action.invoke(ai);
   check(f.level.getBlockState(source).is(Blocks.DIRT)&&f.level.getBlockState(target.below()).is(Blocks.DIRT),"Two fill blocks must seal both neighbors");
  }
  void protectedContainerAndBoundary()throws Exception{
   f.level.setBlock(target,Blocks.DIRT.defaultBlockState(),3);
   f.level.setBlock(source,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED,true),3);
   var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)f.level.getBlockEntity(source);
   chest.setItem(0,new ItemStack(Items.DIAMOND,7));
   c.getInventory().setStackInSlot(2,new ItemStack(Items.DIRT,8));
   var working=EntityAIStructureMiner.class.getDeclaredField("minerWorkingLocation");working.setAccessible(true);working.set(ai,target);
   int damage=c.getInventory().getStackInSlot(0).getDamageValue();action.invoke(ai);
   check(f.level.getBlockState(target).is(Blocks.DIRT)&&f.level.getBlockEntity(source)==chest&&chest.getItem(0).getCount()==7&&chest.getItem(0).is(Items.DIAMOND),"Waterlogged container or barrier destroyed");
   check(c.getInventory().getStackInSlot(2).getCount()==8&&c.getInventory().getStackInSlot(0).getDamageValue()==damage,"Protected container consumed supplies or durability");
   pass("waterlogged_container","Waterlogged chest and contents remain intact; worker does not excavate its protective plug");
   f.level.setBlock(source,Blocks.WATER.defaultBlockState(),3);
   mine.setStructurePack("Medieval Oak");action.invoke(ai);
   check(f.level.getFluidState(source).isSource(),"Patch affected an unrelated building style");
   mine.setStructurePack("Arcology Horizons");
   pass("style_boundary","Unrelated Medieval Oak mine retains native fluid behavior");
  }
  void pass(String id,String detail){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,detail));}
  String detail(){return "ticks="+ticks+" placed="+placed+" removed="+removed+" targetPos="+target+" ladder="+mine.getLadderLocation()+" lastLadder="+WorkerUtil.getLastLadder(mine.getLadderLocation(),f.level)+" target="+f.level.getBlockState(target)+" source="+f.level.getBlockState(source)+" shovelDamage="+c.getInventory().getStackInSlot(0).getDamageValue()+" dirtRemaining="+c.getInventory().getStackInSlot(2).getCount();}
  void fail(Throwable e){while(e.getCause()!=null)e=e.getCause();try{ProbeReport.write(f.output,"ArcologyMinerInflow",List.of(new ProbeResult("dirt_overhead_inflow",ProbeResult.Outcome.FAIL,e.toString())));}catch(Exception ignored){}f.h.fail(e.toString());}
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 }
}
