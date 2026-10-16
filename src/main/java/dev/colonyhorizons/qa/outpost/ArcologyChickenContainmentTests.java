package dev.colonyhorizons.qa.outpost;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Long observation of the ordinary native chicken fixture; no containment geometry is injected. */
@PrefixGameTestTemplate(false)
public final class ArcologyChickenContainmentTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=27000)
 public static void containment(GameTestHelper h){
  for(int x=28;x<=37;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new ArcologyAnimalTests.Run(h,true).start());
 }
}
