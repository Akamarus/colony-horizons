package dev.colonyhorizons.qa.outpost;
import java.util.List;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class ArcologyBuilderRecallTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=400000)
 public static void recalled_builder_exits(GameTestHelper h)throws Exception {
  new ArcologyNavigationTests.Run(h,List.of("fundamentals/builder1.blueprint","fundamentals/builder2.blueprint","fundamentals/builder3.blueprint","fundamentals/builder4.blueprint","fundamentals/builder5.blueprint"),"ArcologyBuilderRecall",true).start();
 }
}
