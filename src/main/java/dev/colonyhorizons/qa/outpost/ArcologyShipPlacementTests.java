package dev.colonyhorizons.qa.outpost;
import java.nio.file.*;
import java.util.*;
import com.ldtteam.structurize.blueprints.v1.*;
import com.minecolonies.core.items.ItemSupplyChestDeployer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
@PrefixGameTestTemplate(false)
public final class ArcologyShipPlacementTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1000)
 public static void raft_waterline(GameTestHelper h)throws Exception {
  var level=h.getLevel();var rows=new ArrayList<ProbeResult>();
  var root=Path.of(System.getProperty("colonyhorizons.arcologyPath"));
  for(var transform:com.ldtteam.structurize.api.RotationMirror.values()){
   var bp=BlueprintUtil.readBlueprintFromNBT(NbtIo.readCompressed(root.resolve("decorations/supplies/supplyship.blueprint"),NbtAccounter.create(64000000)),level.registryAccess());
   bp.setRotationMirror(transform,level);
   var origin=new BlockPos(256,20,256);
   for(int x=-2;x<10;x++)for(int z=-2;z<10;z++)for(int y=-2;y<9;y++)level.setBlock(origin.offset(x,y,z),(y<=0?Blocks.WATER:Blocks.AIR).defaultBlockState(),18);
   var player=FakePlayerFactory.getMinecraft(level);
   boolean surface=ItemSupplyChestDeployer.canShipBePlaced(level,origin.offset(bp.getPrimaryBlockOffset()),bp,new ArrayList<>(),player);
   boolean raised=ItemSupplyChestDeployer.canShipBePlaced(level,origin.above().offset(bp.getPrimaryBlockOffset()),bp,new ArrayList<>(),player);
   for(int x=0;x<bp.getSizeX();x++)for(int z=0;z<bp.getSizeZ();z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),18);
   boolean obstructed=ItemSupplyChestDeployer.canShipBePlaced(level,origin.offset(bp.getPrimaryBlockOffset()),bp,new ArrayList<>(),player);
   // Native supply placement tolerates missing water at fluid placeholders;
   // a raised preview is diagnostic, not a stricter rule introduced by this pack.
   boolean pass=BlueprintTagUtils.getNumberOfGroundLevels(bp,3)==1 && surface && !obstructed;
   rows.add(new ProbeResult("ship:"+transform.name(),pass?ProbeResult.Outcome.PASS:ProbeResult.Outcome.FAIL,"waterLayers="+BlueprintTagUtils.getNumberOfGroundLevels(bp,3)+" surface="+surface+" raised="+raised+" obstructed="+obstructed));
  }
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyShipPlacement",rows);
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Raft placement regression; see report");h.succeed();
 }
}
