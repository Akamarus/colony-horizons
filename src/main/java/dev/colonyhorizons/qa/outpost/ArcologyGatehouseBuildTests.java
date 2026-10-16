package dev.colonyhorizons.qa.outpost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
/** Real builder orders, including native three-level cap and terminal retention. */
@PrefixGameTestTemplate(false)
public final class ArcologyGatehouseBuildTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=160000)
 public static void gatehouse_upgrades(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new ArcologyUpgradeTests.Run(h,List.of("military/gatehouse1.blueprint"),List.of(new BlockPos(512,16,0)),"ArcologyGatehouseBuild").start());
 }
}
