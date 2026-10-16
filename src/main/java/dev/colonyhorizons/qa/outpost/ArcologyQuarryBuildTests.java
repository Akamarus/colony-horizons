package dev.colonyhorizons.qa.outpost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
/** Surface construction only; shaft excavation is a separate native worker test. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryBuildTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=120000)
 public static void quarry_construction(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new ArcologyUpgradeTests.Run(h,List.of("fundamentals/simplequarry1.blueprint","fundamentals/mediumquarry1.blueprint"),List.of(new BlockPos(512,16,0),new BlockPos(544,16,0)),"ArcologyQuarryBuild").start());
 }
}
