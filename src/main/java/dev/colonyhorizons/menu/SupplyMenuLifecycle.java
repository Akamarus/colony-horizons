package dev.colonyhorizons.menu;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
@EventBusSubscriber(modid="colonyhorizons")
public final class SupplyMenuLifecycle {
 private SupplyMenuLifecycle(){}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){SupplyMenuSessions.close(event.getEntity().getUUID());}
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){SupplyMenuSessions.close(event.getEntity().getUUID());}
}

