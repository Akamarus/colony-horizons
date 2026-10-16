package dev.colonyhorizons.qa.outpost;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
/** Independent inventory of twenty new hut/field levels; shafts tested separately. */
@PrefixGameTestTemplate(false)
public final class ArcologyExtensionNavigationTests {
 static final List<String> PATHS=List.of(
   "military/stable1.blueprint",
   "military/stable2.blueprint",
   "military/stable3.blueprint",
   "military/stable4.blueprint",
   "military/stable5.blueprint",
   "military/gatehouse1.blueprint",
   "military/gatehouse2.blueprint",
   "military/gatehouse3.blueprint",
   "fundamentals/simplequarry1.blueprint",
   "fundamentals/mediumquarry1.blueprint",
   "infrastructure/plantationfields/cocoa1.blueprint",
   "infrastructure/plantationfields/vine1.blueprint",
   "infrastructure/plantationfields/kelp1.blueprint",
   "infrastructure/plantationfields/seagrass1.blueprint",
   "infrastructure/plantationfields/seapickle1.blueprint",
   "infrastructure/plantationfields/glowberry1.blueprint",
   "infrastructure/plantationfields/weepingvine1.blueprint",
   "infrastructure/plantationfields/twistingvine1.blueprint",
   "infrastructure/plantationfields/crimson1.blueprint",
   "infrastructure/plantationfields/warped1.blueprint"
  );
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=60000)
 public static void extension_access(GameTestHelper h)throws Exception {
  new ArcologyNavigationTests.Run(h,PATHS,"ArcologyExtensionNavigation").start();
 }
}
