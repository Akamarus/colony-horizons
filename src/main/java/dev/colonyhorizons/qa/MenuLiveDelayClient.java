package dev.colonyhorizons.qa;

import dev.colonyhorizons.network.SupplyMenuNetworking;
import dev.colonyhorizons.network.SupplyMenuPayloads.MenuReply;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in disposable-client reply delay; absent from distributable jars. */
@EventBusSubscriber(modid="colonyhorizons",value=Dist.CLIENT)
public final class MenuLiveDelayClient {
 private record Delayed(long due, MenuReply reply) {}
 private static final ArrayDeque<Delayed> replies=new ArrayDeque<>();
 private static Consumer<MenuReply> receiver;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
  long delay=Long.getLong("colonyhorizons.menuLiveDelayMillis",0);
  if(!Boolean.getBoolean("colonyhorizons.menuLive")||delay<=0)return;
  if(receiver==null){
   receiver=SupplyMenuNetworking.clientReceiver;
   SupplyMenuNetworking.clientReceiver=reply->{
    if(reply.number()==0)receiver.accept(reply);
    else replies.addLast(new Delayed(System.currentTimeMillis()+Math.min(delay,15000),reply));
   };
   System.out.println("MENU_LIVE_DELAY installed: "+delay+"ms for action replies");
  }
  while(!replies.isEmpty()&&replies.peekFirst().due()<=System.currentTimeMillis())receiver.accept(replies.removeFirst().reply());
 }
}
