package dev.colonyhorizons.qa;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import dev.colonyhorizons.outpost.OutpostSavedData;
import dev.colonyhorizons.supply.NativeSupplyService;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Opt-in scaffolding for UI testing against a real integrated server, never shipped. */
@EventBusSubscriber(modid="colonyhorizons")
public final class MenuLiveFixture {
 private static boolean prepared;
 @SubscribeEvent public static void joined(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
  if(!Boolean.getBoolean("colonyhorizons.menuLive")||prepared||e.getServer().getPlayerList().getPlayers().isEmpty())return;
  var p=e.getServer().getPlayerList().getPlayers().getFirst();if(p.tickCount<100)return;prepared=true;
  var c=IColonyManager.getInstance().getColonyByPosFromWorld(p.serverLevel(),new BlockPos(0,65,0));
  if(c==null||c.getServerBuildingManager().getBuildings().size()<=1)setup(p);
  else if(c.getName().equals("Menu QA Colony")&&c.getPermissions().getOwner().equals(p.getUUID())){
   var hall=c.getServerBuildingManager().getBuilding(new BlockPos(0,65,0));
   hall.setBlueprintPath("fundamentals/townhall1.blueprint");hall.markDirty();
  }
 }
 @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
  if(!Boolean.getBoolean("colonyhorizons.menuLive"))return;
  e.getDispatcher().register(Commands.literal("chmenuqa").requires(s->s.hasPermission(2))
   .then(Commands.literal("setup").executes(c->{setup(c.getSource().getPlayerOrException());return 1;}))
   .then(Commands.literal("report").executes(c->{report(c.getSource().getPlayerOrException());return 1;})));
 }
 private static void setup(ServerPlayer p) {
  var l=p.serverLevel();var center=new BlockPos(0,65,0);
  var colony=IColonyManager.getInstance().getColonyByPosFromWorld(l,center);
  if(colony!=null&&(!colony.getName().equals("Menu QA Colony")||!colony.getPermissions().getOwner().equals(p.getUUID())||colony.getServerBuildingManager().getBuildings().size()>1))throw new IllegalArgumentException("Fixture already exists");
  if(colony==null)colony=IColonyManager.getInstance().createColony(l,center,p,"Menu QA Colony","Arcology Horizons");
  place(p,new BlockPos(0,65,0),ModBlocks.blockHutTownHall,"fundamentals/townhall1.blueprint","Town Hall");
  place(p,new BlockPos(-4,65,4),dev.colonyhorizons.surveyor.SurveyorRegistration.HUT.get(),"fundamentals/surveyor1.blueprint","Exploration Hut");
  var exploration=(dev.colonyhorizons.surveyor.BuildingSurveyor)colony.getServerBuildingManager().getBuilding(new BlockPos(-4,65,4));
  var worker=colony.getCitizenManager().createAndRegisterCivilianData();colony.getCitizenManager().spawnOrCreateCitizen(worker,l,new BlockPos(-3,65,4));exploration.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(worker);exploration.getTileEntity().getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,32));exploration.getTileEntity().getInventory().setStackInSlot(1,new ItemStack(Items.IRON_AXE));exploration.getTileEntity().getInventory().setStackInSlot(2,new ItemStack(Items.SHEARS));
  place(p,new BlockPos(4,65,4),ModBlocks.blockHutLumberjack,"fundamentals/lumberjack1.blueprint","A Home Forester");
  place(p,new BlockPos(36,65,4),ModBlocks.blockHutLumberjack,"fundamentals/lumberjack1.blueprint","B Woods Forester");
  place(p,new BlockPos(40,65,4),ModBlocks.blockHutBuilder,"fundamentals/builder1.blueprint","C Woods Builder");
  for(int i=0;i<15;i++)place(p,new BlockPos(4+(i%5)*4,65,36+(i/5)*4),ModBlocks.blockHutLumberjack,"fundamentals/lumberjack1.blueprint",String.format("Z Extra Hut %02d",i));
  ((TileEntityColonyBuilding)l.getBlockEntity(new BlockPos(36,65,4))).getInventory().setStackInSlot(0,new ItemStack(Items.OAK_LOG,64));
  p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG,1));
  p.connection.teleport(0.5,65,2.5,180,25);
  p.sendSystemMessage(Component.literal("MENU QA READY: colony "+colony.getID()+"; real huts and 64 export logs. No routes or outposts created."));
 }
 private static void place(ServerPlayer p,BlockPos pos,AbstractBlockHut<?> block,String path,String name) {
  var l=p.serverLevel();l.getChunkAt(pos);
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)l.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.OAK_PLANKS.defaultBlockState());
  var bp=new Blueprint((short)1,(short)1,(short)1,l.registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);
  var existing=IColonyManager.getInstance().getColonyByPosFromWorld(l,pos).getServerBuildingManager().getBuilding(pos);
  if(existing==null&&!block.setup(p,l,pos,bp,RotationMirror.NONE,true,"Arcology Horizons",path))throw new IllegalStateException("Hut setup failed "+pos);
  var c=IColonyManager.getInstance().getColonyByPosFromWorld(l,pos);var b=c.getServerBuildingManager().getBuilding(pos);
  b.setStructurePack("Arcology Horizons");b.setBlueprintPath(path);b.setBuildingLevel(1);b.setCorners(pos,pos);b.setCustomBuildingName(name);b.markDirty();
 }
 private static void report(ServerPlayer p) {
  var service=NativeSupplyService.get(p.serverLevel());
  var message="MENU QA STATE districts="+OutpostSavedData.get(p.serverLevel()).all().size()+" routes="+service.routes(p.getUUID()).size();
  for(var r:service.routes(p.getUUID()))message+=" | "+r.id()+" "+r.reason()+" cargo="+r.cargo().getCount()+" delivered="+r.delivered()+" paused="+service.settings(r.id()).paused()+" stopped="+service.settings(r.id()).disabled();
  com.mojang.logging.LogUtils.getLogger().info(message);p.sendSystemMessage(Component.literal(message));
 }
}
