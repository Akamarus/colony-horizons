package dev.colonyhorizons.qa.outpost;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.WorkOrderPlantationField;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
/** Native drying ticks prove the compatibility boundary and cancellation cleanup. */
@PrefixGameTestTemplate(false)
public final class ArcologyCoralConstructionTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2000)
 public static void coral_drying(GameTestHelper h){
  for(int x=29;x<=34;x++)for(int z=-1;z<=2;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{try{
   var l=h.getLevel();var fixture=OutpostProbeFixture.create(l,new BlockPos(480,16,0),UUID.randomUUID());
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(520,16,0),5,l,false);
   String path="infrastructure/plantationfields/seapickle1.blueprint";var bp=StructurePacks.getBlueprintFuture("Arcology Horizons",path,l.registryAccess()).get();
   var order=WorkOrderPlantationField.create(WorkOrderType.BUILD,"Arcology Horizons",path,"Sea pickle field",new BlockPos(520,16,0),RotationMirror.NONE,0);
   order.setBlueprint(bp,l);fixture.colony().getWorkManager().addWorkOrder(order,false);
   var dry=new BlockPos(515,15,4);var wet=new BlockPos(519,15,4);var outside=new BlockPos(530,15,4);
   for(var p:List.of(dry,wet,outside)){l.setBlockAndUpdate(p,Blocks.BRAIN_CORAL_BLOCK.defaultBlockState());l.scheduleTick(p,Blocks.BRAIN_CORAL_BLOCK,60);}
   l.setBlockAndUpdate(wet.above(),Blocks.WATER.defaultBlockState());
   // Contain the one-cell water fixture so it cannot reach either dry control.
   for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)l.setBlockAndUpdate(wet.above().relative(d),Blocks.STONE.defaultBlockState());
   h.runAfterDelay(200,()->{try{
    check(fixture.colony().getWorkManager().getWorkOrders().containsValue(order),"Construction order disappeared");
    check(l.getBlockState(dry).is(Blocks.BRAIN_CORAL_BLOCK),"Coral dried before pending builder water");
    check(l.getBlockState(outside).is(Blocks.DEAD_BRAIN_CORAL_BLOCK),"Unrelated dry coral was protected");
    check(l.getBlockState(wet).is(Blocks.BRAIN_CORAL_BLOCK),"Wet coral died");
    fixture.colony().getWorkManager().removeWorkOrder(order.getID());
    h.runAfterDelay(200,()->{try{
     check(l.getBlockState(dry).is(Blocks.DEAD_BRAIN_CORAL_BLOCK),"Cancelled construction left dry coral protected");
     check(l.getBlockState(wet).is(Blocks.BRAIN_CORAL_BLOCK),"Completed wet coral did not survive");
     ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyCoralConstruction",List.of(new ProbeResult("coral_construction_boundary",ProbeResult.Outcome.PASS,"Native drying ticks preserve coral only within pending Arcology pond work bounds; unrelated dry coral dies. Removing work order restores drying; water-adjacent coral stays alive. Actual builder construction is checked separately.")));h.succeed();
    }catch(Throwable e){h.fail(e.toString());}});
   }catch(Throwable e){h.fail(e.toString());}});
  }catch(Throwable e){h.fail(e.toString());}});
 }
 static void check(boolean b,String message){if(!b)throw new IllegalStateException(message);}
}
