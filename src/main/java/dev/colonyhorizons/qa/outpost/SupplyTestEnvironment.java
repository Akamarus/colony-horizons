package dev.colonyhorizons.qa.outpost;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Inventory-only QA needs saved data, not a fresh remote terrain region on every process restart. */
@EventBusSubscriber(modid="colonyhorizons")
public final class SupplyTestEnvironment {
 private static boolean positioned;
 private static boolean enabled(){return Boolean.getBoolean("colonyhorizons.foundation") && System.getProperty("colonyhorizons.foundationSuite","").startsWith("Supply");}
 @SubscribeEvent
 public static void beforeLoad(ServerAboutToStartEvent event){
  if(enabled() && event.getServer() instanceof GameTestServer){
   event.getServer().getWorldData().getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
   positioned=false;
  }
 }
 @SubscribeEvent
 public static void beforeTests(ServerTickEvent.Post event){
  if(enabled() && !positioned && event.getServer() instanceof GameTestServer){
   // GameTestServer selects its test origin immediately after the first ordinary server tick.
   event.getServer().overworld().random.setSeed(739281L);positioned=true;
   System.out.println("SUPPLY_QA_BOUNDED_TEST_WORLD");
  }
 }
}
