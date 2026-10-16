package dev.colonyhorizons.qa.outpost;
import java.util.List;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
/** Exact interior destinations, unlike the hut-interaction-range regression. */
@PrefixGameTestTemplate(false)
public final class ArcologyInteriorNavigationTests {
 static final List<String> PATHS=List.of("military/stable1.blueprint","military/stable2.blueprint","military/stable3.blueprint","military/stable4.blueprint","military/stable5.blueprint","military/gatehouse1.blueprint","military/gatehouse2.blueprint","military/gatehouse3.blueprint");
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=400000)
 public static void interior_access(GameTestHelper h)throws Exception {
  new ArcologyNavigationTests.Run(h,PATHS,"ArcologyInteriorNavigation",true,true).start();
 }
}
