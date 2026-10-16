package dev.colonyhorizons.network;

import dev.colonyhorizons.recipes.RecipeCatalog;
import dev.colonyhorizons.compat.mixin.RecipeCapacityAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

@EventBusSubscriber(modid="colonyhorizons",bus=EventBusSubscriber.Bus.MOD)
public final class RecipeCatalogNetwork {
 private RecipeCatalogNetwork(){}
 public static java.util.function.Consumer<Reply> receiver=r->{};
 private static final Map<ServerPlayer,Long> LAST=new WeakHashMap<>();
 public record Request(UUID window,int sequence,int colony,BlockPos hut,int module,int page,String query,String selected,int[] choices,boolean learn) implements CustomPacketPayload {
  public Request {choices=choices.clone();if(query.length()>64||selected.length()>384||choices.length>9||page<0||page>10000)throw new IllegalArgumentException("Invalid catalog request");}
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("colonyhorizons","recipe_catalog_request"));
  public static final StreamCodec<FriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.window);b.writeVarInt(p.sequence);b.writeVarInt(p.colony);b.writeBlockPos(p.hut);b.writeVarInt(p.module);b.writeVarInt(p.page);b.writeUtf(p.query,64);b.writeUtf(p.selected,384);b.writeVarIntArray(p.choices);b.writeBoolean(p.learn);},b->new Request(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readUtf(64),b.readUtf(384),b.readVarIntArray(9),b.readBoolean()));
  @Override public Type<Request> type(){return TYPE;}
 }
 public record Reply(UUID window,int sequence,CompoundTag data,String message) implements CustomPacketPayload {
  public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("colonyhorizons","recipe_catalog_reply"));
  public static final StreamCodec<FriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.window);b.writeVarInt(p.sequence);b.writeNbt(p.data);b.writeUtf(p.message,512);},b->new Reply(b.readUUID(),b.readVarInt(),Objects.requireNonNull(b.readNbt()),b.readUtf(512)));
  @Override public Type<Reply> type(){return TYPE;}
 }
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
  var r=event.registrar("recipe-catalog-1");
  r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)PacketDistributor.sendToPlayer(player,handle(player,p));}));
  r.playToClient(Reply.TYPE,Reply.CODEC,(p,c)->c.enqueueWork(()->receiver.accept(p)));
 }
 public static Reply handle(ServerPlayer player,Request request){
  try{
   long tick=player.serverLevel().getGameTime();var previous=LAST.put(player,tick);
   if(previous!=null&&tick-previous<4)throw new IllegalArgumentException("Please wait a moment and try again.");
   var module=RecipeCatalog.require(player,request.colony,request.hut,request.module);
   String message="";
   if(request.learn){RecipeCatalog.learn(player.serverLevel(),module,request.selected,request.choices);message="Recipe learned. Workers still need the crafting ingredients.";}
   var data=new CompoundTag();var access=(RecipeCapacityAccess)module;data.putInt("used",access.colonyHorizons$used());data.putInt("capacity",access.colonyHorizons$capacity());
   var all=RecipeCatalog.entries(player.serverLevel(),module);
   String query=request.query.toLowerCase(Locale.ROOT);
   var filtered=all.stream().filter(e->e.recipe().getPrimaryOutput().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)||String.valueOf(e.recipe().getRecipeId()).toLowerCase(Locale.ROOT).contains(query)).toList();
   int pages=Math.max(1,(filtered.size()+5)/6),page=Math.min(request.page,pages-1);data.putInt("page",page);data.putInt("pages",pages);
   var rows=new ListTag();for(int i=page*6;i<Math.min(filtered.size(),page*6+6);i++){var e=filtered.get(i);var row=new CompoundTag();row.putString("key",e.key());row.putString("name",e.recipe().getPrimaryOutput().getHoverName().getString());row.put("item",e.recipe().getPrimaryOutput().save(player.registryAccess()));rows.add(row);}data.put("rows",rows);
   var selected=all.stream().filter(e->e.key().equals(request.selected)).findFirst();
   if(selected.isPresent()){
    var e=selected.get();var detail=new CompoundTag();detail.putString("name",e.recipe().getPrimaryOutput().getHoverName().getString());detail.putString("note",e.restriction());detail.put("output",e.recipe().getPrimaryOutput().save(player.registryAccess()));
    boolean known=false;if(e.teachable()){var storage=RecipeCatalog.storage(player.serverLevel(),e,request.choices);known=RecipeCatalog.known(module,storage);detail.put("output",storage.getPrimaryOutput().save(player.registryAccess()));detail.putString("name",storage.getPrimaryOutput().getHoverName().getString());}
    detail.putBoolean("learn",e.teachable()&&!known&&access.colonyHorizons$used()<access.colonyHorizons$capacity());
    if(known)detail.putString("note","Already learned; manage it in the normal recipe list.");
    else if(e.teachable()&&access.colonyHorizons$used()>=access.colonyHorizons$capacity())detail.putString("note","Recipe limit reached. Upgrade the hut or free a slot.");
    var ingredients=new ListTag();for(int i=0;i<Math.min(9,e.recipe().getInputs().size());i++){var choices=e.recipe().getInputs().get(i);var item=new CompoundTag();int choice=i<request.choices.length?request.choices[i]:0;if(choice<0||choice>=Math.max(1,choices.size()))throw new IllegalArgumentException("Ingredient options changed; select the recipe again.");item.putInt("options",choices.size());item.putInt("choice",choice);if(!choices.isEmpty())item.put("item",choices.get(choice).save(player.registryAccess()));ingredients.add(item);}detail.put("ingredients",ingredients);data.put("detail",detail);
   }
   return new Reply(request.window,request.sequence,data,message);
  }catch(IllegalArgumentException ex){return new Reply(request.window,request.sequence,new CompoundTag(),ex.getMessage()==null?"Recipe unavailable.":ex.getMessage());}
 }
}
