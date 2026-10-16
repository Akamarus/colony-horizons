package dev.colonyhorizons.qa.outpost;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class BarracksNativeUpgradeTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void native_upgrade(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{
   var run=new ArcologyUpgradeTests.Run(h,List.of("military/barracks1.blueprint"),List.of(new BlockPos(512,16,0)),"BarracksNativeUpgrade");
   run.afterSetup=r->{try{
    var parent=(BuildingBarracks)r.place("military/barracks1.blueprint",new BlockPos(512,16,0),true);r.targets.set(0,parent);r.wanted=2;r.expectedCases=1;
    var tower=(BuildingBarracksTower)r.place("military/barrackstower1.blueprint",parent.getPosition().offset(5,0,5),true);
    parent.registerBlockPosition(r.level.getBlockState(tower.getPosition()),tower.getPosition(),r.level);
    var guard=r.fixture.createCitizen();guard.setPaused(true);r.check(tower.getModule(BuildingModules.KNIGHT_BARRACKS_WORK).assignCitizen(guard),"Guard assignment");
    var inventory=tower.getTileEntity().getInventory();inventory.setStackInSlot(0,new ItemStack(Items.EMERALD,7));
    var corners=tower.getCorners();var blocks=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
    for(var pos:BlockPos.betweenClosed(corners.getA(),corners.getB()))blocks.put(pos.immutable(),r.level.getBlockState(pos));
    r.extraCheck=()->{
     for(var e:blocks.entrySet())r.check(r.level.getBlockState(e.getKey()).equals(e.getValue()),"Tower changed during parent upgrade: "+e.getKey());
     r.check(guard.getWorkBuilding()==tower&&guard.getHomeBuilding()==tower,"Guard assignments lost");
     r.check(tower.getBuildingLevel()==1&&parent.getTowers().contains(tower.getPosition()),"Tower membership/level changed");
     r.check(inventory.getStackInSlot(0).is(Items.EMERALD)&&inventory.getStackInSlot(0).getCount()==7,"Tower inventory changed");
    };r.schedule();
   }catch(Exception e){r.fail(e);}};run.start();
  });
 }
}


