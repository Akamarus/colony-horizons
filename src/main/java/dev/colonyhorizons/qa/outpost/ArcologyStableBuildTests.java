package dev.colonyhorizons.qa.outpost;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

/** Native builder construction and upgrades; only support buildings are prebuilt. */
@PrefixGameTestTemplate(false)
public final class ArcologyStableBuildTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=260000)
 public static void stable_upgrades(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new ArcologyUpgradeTests.Run(h,List.of("military/stable1.blueprint"),List.of(new BlockPos(512,16,0)),"ArcologyStableBuild").start());
 }
}
