package dev.colonyhorizons.network;
import dev.colonyhorizons.menu.SupplyMenuSessions;
import dev.colonyhorizons.network.SupplyMenuPayloads.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
@EventBusSubscriber(modid="colonyhorizons",bus=EventBusSubscriber.Bus.MOD)
public final class SupplyMenuNetworking {
 private SupplyMenuNetworking(){}
 public static java.util.function.Consumer<MenuReply> clientReceiver=reply->{};
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
  var registrar=event.registrar("supply-menu-3");
  registrar.playToServer(MenuRequest.TYPE,MenuRequest.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)PacketDistributor.sendToPlayer(player,SupplyMenuSessions.handle(player,p));}));
  registrar.playToClient(MenuReply.TYPE,MenuReply.CODEC,(p,c)->c.enqueueWork(()->clientReceiver.accept(p)));
 }
}
