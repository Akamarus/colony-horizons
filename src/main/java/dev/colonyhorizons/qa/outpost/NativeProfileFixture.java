package dev.colonyhorizons.qa.outpost;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import java.nio.file.Path;
/** GameTest server only: MineColonies needs a profile cache before native colony reload. */
@EventBusSubscriber(modid="colonyhorizons")
public final class NativeProfileFixture {
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void beforeLoad(ServerAboutToStartEvent event){
  if(!Boolean.getBoolean("colonyhorizons.foundation"))return;
  var server=event.getServer();if(server.getProfileCache()!=null)return;
  try{
   var field=net.minecraft.server.MinecraftServer.class.getDeclaredField("services");field.setAccessible(true);
   var old=(net.minecraft.server.Services)field.get(server);
   var cache=new net.minecraft.server.players.GameProfileCache(old.profileRepository(),Path.of(System.getProperty("colonyhorizons.probeOutput")).resolveSibling("profiles.json").toFile());
   field.set(server,new net.minecraft.server.Services(old.sessionService(),old.servicesKeySet(),old.profileRepository(),cache));
   System.out.println("FOUNDATION_QA_PROFILE_CACHE_INSTALLED");
  }catch(Exception e){throw new IllegalStateException("GameTest profile fixture failed",e);}
 }
}
