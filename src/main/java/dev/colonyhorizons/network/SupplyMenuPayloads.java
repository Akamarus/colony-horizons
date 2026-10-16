package dev.colonyhorizons.network;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public final class SupplyMenuPayloads {
 private SupplyMenuPayloads(){}
 public enum Action {OPEN,REFRESH,REVIEW_OUTPOST,CREATE_OUTPOST,PAUSE_OUTPOST,RESUME_OUTPOST,CREATE_ROUTE,PAUSE_ROUTE,RESUME_ROUTE,STOP_ROUTE,CLOSE,REMOTE_OPTIONS,REVIEW_REMOTE,APPROVE_REMOTE,VIEW_MAYOR,PAUSE_REMOTE,RESUME_REMOTE,REVIEW_UPGRADE,APPROVE_UPGRADE,REJECT_UPGRADE,REVIEW_EXPANSION,APPROVE_EXPANSION,REJECT_EXPANSION,START_SURVEY,CANCEL_SURVEY,START_GATHERING,RECALL_GATHERING}
 public record MenuRequest(UUID session,long number,Action action,int colony,int hutPage,int districtPage,int routePage,String name,List<BlockPos> huts,UUID selected,int target,int reserve,int batch,CompoundTag remote) implements CustomPacketPayload {
  public MenuRequest(UUID session,long number,Action action,int colony,int hutPage,int districtPage,int routePage,String name,List<BlockPos> huts,UUID selected,int target,int reserve,int batch){this(session,number,action,colony,hutPage,districtPage,routePage,name,huts,selected,target,reserve,batch,new CompoundTag());}
  @Override public CompoundTag remote(){return remote.copy();}
  public MenuRequest{remote=remote.copy();if(remote.getIntArray("citizens").length>32||remote.getList("budget",10).size()>128)throw new IllegalArgumentException("Remote input is too long");huts=List.copyOf(huts);if(huts.size()>32||name.length()>32)throw new IllegalArgumentException("Menu input is too long");}
  public static final Type<MenuRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("colonyhorizons","menu_request"));
  public static final StreamCodec<FriendlyByteBuf,MenuRequest> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.session);b.writeLong(p.number);b.writeEnum(p.action);b.writeInt(p.colony);b.writeInt(p.hutPage);b.writeInt(p.districtPage);b.writeInt(p.routePage);b.writeUtf(p.name,32);b.writeVarInt(p.huts.size());p.huts.forEach(b::writeBlockPos);b.writeUUID(p.selected);b.writeInt(p.target);b.writeInt(p.reserve);b.writeInt(p.batch);b.writeNbt(p.remote);},b->{
   if(b.readableBytes()>65536)throw new IllegalArgumentException("Menu packet too large");var session=b.readUUID();long number=b.readLong();var action=b.readEnum(Action.class);int colony=b.readInt(),hp=b.readInt(),dp=b.readInt(),rp=b.readInt();String name=b.readUtf(32);int count=b.readVarInt();if(count<0||count>32)throw new IllegalArgumentException("Too many huts");var huts=new ArrayList<BlockPos>();for(int i=0;i<count;i++)huts.add(b.readBlockPos());return new MenuRequest(session,number,action,colony,hp,dp,rp,name,huts,b.readUUID(),b.readInt(),b.readInt(),b.readInt(),Objects.requireNonNull(b.readNbt()));});
  public MenuRequest withSession(UUID id){return new MenuRequest(id,number,action,colony,hutPage,districtPage,routePage,name,huts,selected,target,reserve,batch,remote);}
  @Override public Type<MenuRequest> type(){return TYPE;}
 }
 public record MenuReply(UUID session,long number,boolean ok,String message,CompoundTag data) implements CustomPacketPayload {
  public MenuReply{data=data.copy();message=message.length()>256?message.substring(0,256):message;}
  @Override public CompoundTag data(){return data.copy();}
  public static final Type<MenuReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("colonyhorizons","menu_reply"));
  public static final StreamCodec<FriendlyByteBuf,MenuReply> CODEC=StreamCodec.of((b,p)->{int start=b.writerIndex();b.writeUUID(p.session);b.writeLong(p.number);b.writeBoolean(p.ok);b.writeUtf(p.message,256);b.writeNbt(p.data);if(b.writerIndex()-start>65536)throw new IllegalArgumentException("Menu snapshot too large");},b->{if(b.readableBytes()>65536)throw new IllegalArgumentException("Menu snapshot too large");return new MenuReply(b.readUUID(),b.readLong(),b.readBoolean(),b.readUtf(256),Objects.requireNonNull(b.readNbt()));});
  @Override public Type<MenuReply> type(){return TYPE;}
 }
}
