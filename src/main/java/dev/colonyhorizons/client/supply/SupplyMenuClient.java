package dev.colonyhorizons.client.supply;

import com.ldtteam.blockui.views.BOWindow;

import dev.colonyhorizons.network.*;

import dev.colonyhorizons.network.SupplyMenuPayloads.*;

import net.minecraft.core.BlockPos;

import net.minecraft.nbt.CompoundTag;

import net.neoforged.api.distmarker.Dist;

import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.fml.common.EventBusSubscriber;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

@EventBusSubscriber(modid="colonyhorizons",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)

public final class SupplyMenuClient {

 static UUID session=new UUID(0,0);static long number;static int colony;static CompoundTag data=new CompoundTag();static boolean pending;static BOWindow parent;static WindowSupplyBase window;static long sentAt;static UUID opening;static CompoundTag priorPageData;

 private SupplyMenuClient(){}

 @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->SupplyMenuNetworking.clientReceiver=SupplyMenuClient::receive);}

 public static void open(int id,BOWindow back){parent=back;colony=id;opening=UUID.randomUUID();pending=true;sentAt=System.currentTimeMillis();PacketDistributor.sendToServer(new MenuRequest(opening,0,Action.OPEN,id,0,0,0,"",List.of(),new UUID(0,0),64,0,8));}
 public static void openSurvey(int id,BlockPos hut,BOWindow back){parent=back;colony=id;opening=UUID.randomUUID();pending=true;sentAt=System.currentTimeMillis();var form=new CompoundTag();form.putLong("surveyHut",hut.asLong());PacketDistributor.sendToServer(new MenuRequest(opening,0,Action.OPEN,id,0,0,0,"",List.of(),new UUID(0,0),64,0,8,form));}

 public static void receive(MenuReply reply){

  if(reply.number()==0&&!reply.ok()){if(!reply.session().equals(opening))return;pending=false;if(net.minecraft.client.Minecraft.getInstance().player!=null)net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal(reply.message()),false);return;}

  if(reply.number()==0&&reply.ok()){
   if(reply.data().hasUUID("opening")&&(!reply.data().getUUID("opening").equals(opening)||parent==null||net.minecraft.client.Minecraft.getInstance().screen!=parent.getScreen())){
    PacketDistributor.sendToServer(new MenuRequest(reply.session(),1,Action.CLOSE,reply.data().getInt("colony"),0,0,0,"",List.of(),new UUID(0,0),64,0,8));pending=false;return;
   }
   if(!reply.data().hasUUID("opening"))parent=null;opening=null;session=reply.session();number=0;data=reply.data();colony=data.getInt("colony");pending=false;window=data.contains("survey")?new WindowSurveys():new WindowOutpostsSupplies();window.open();return;}

  if(!reply.session().equals(session)||reply.number()!=number)return;

  pending=false;if(!reply.ok()&&priorPageData!=null)data=priorPageData;priorPageData=null;if(reply.ok()&&!reply.data().isEmpty())data=reply.data();

  if(window!=null)window.reply(reply);

 }

 static void send(Action action,String name,List<BlockPos> huts,UUID selected,int target,int reserve,int batch){

  if(pending)return;pending=true;sentAt=System.currentTimeMillis();

  PacketDistributor.sendToServer(new MenuRequest(session,++number,action,colony,data.getInt("hp"),data.getInt("dp"),data.getInt("rp"),name,huts,selected,target,reserve,batch));

 }

 static void remote(Action action,CompoundTag form,UUID id){if(pending)return;pending=true;sentAt=System.currentTimeMillis();PacketDistributor.sendToServer(new MenuRequest(session,++number,action,colony,data.getInt("hp"),data.getInt("dp"),data.getInt("rp"),"",List.of(),id,0,0,0,form));}
 static void send(Action action,UUID id){send(action,"",List.of(),id,64,0,8);}

 static void page(String key,int delta){if(pending)return;priorPageData=data.copy();data.putInt(key,Math.max(0,data.getInt(key)+delta));send(Action.REFRESH,new UUID(0,0));}

 static void show(WindowSupplyBase next){window=next;next.open();}

 static void finish(){pending=false;send(Action.CLOSE,new UUID(0,0));window=null;session=new UUID(0,0);pending=false;if(parent!=null){var p=parent;parent=null;p.open();}else net.minecraft.client.Minecraft.getInstance().setScreen(null);}

}

