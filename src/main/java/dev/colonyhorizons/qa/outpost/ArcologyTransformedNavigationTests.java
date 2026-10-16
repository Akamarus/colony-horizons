package dev.colonyhorizons.qa.outpost;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
/** Every new surface/field level through all eight native rotations and mirrors. */
@PrefixGameTestTemplate(false)
public final class ArcologyTransformedNavigationTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=400000)
 public static void transformed_access(GameTestHelper h)throws Exception{
  new ArcologyNavigationTests.Run(h,ArcologyExtensionNavigationTests.PATHS,"ArcologyTransformedNavigation",true).start();
 }
}
