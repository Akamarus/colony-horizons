package dev.colonyhorizons.qa.outpost;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class ArcologyOperationsTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100000)
 public static void native_arcology_operations(GameTestHelper h){
  for(int x=29;x<=35;x++)for(int z=-3;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.getLevel().setChunkForced(0,0,true);
  h.runAfterDelay(40,()->new ArcologyOperationsFixture(h).start());
 }
}
