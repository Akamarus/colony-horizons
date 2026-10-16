package dev.colonyhorizons.qa.outpost;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class ForesterSupplyTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=80000)
    public static void finite_native_forestry(GameTestHelper h) {
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)h.getLevel().setChunkForced(x,z,true);
        for(int x=29;x<=35;x++)for(int z=-3;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
        h.runAfterDelay(40,()->new ForesterSupplyFixture(h).start());
    }
}
