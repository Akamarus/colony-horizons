package dev.colonyhorizons.qa;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Opt-in diagnostic observer for disposable world copies only. */
@EventBusSubscriber(modid="colonyhorizons_qa")
public final class CopiedMinerProbe {
 private static int ticks,initialLevels=-1;private static ServerPlayer owner;private static final boolean ENDURANCE=Boolean.getBoolean("colonyhorizons.minerEndurance");
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("colonyhorizons.copiedMiner"))return;
  var server=event.getServer();var level=server.overworld();ticks++;
  if(ticks==1){if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString().contains("miner-copy"))throw new IllegalStateException("Disposable copy required");for(int x=-17;x<=-13;x++)for(int z=-32;z<=-27;z++)level.setChunkForced(x,z,true);level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);}
  if(ticks%100!=0)return;
  var colony=IColonyManager.getInstance().getColonyByDimension(1,level.dimension());if(colony==null){System.out.println("MINER_PROBE no colony");return;}
  if(owner==null){var profile=new GameProfile(colony.getPermissions().getOwner(),"MinerProbe");var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);owner=new ServerPlayer(server,level,profile,ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(-240,63,-453);}
  colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);
  var c=colony.getCitizenManager().getCivilian(7);if(c==null)return;
  var mine=(BuildingMiner)c.getWorkBuilding();
  var levels=mine.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.MinerLevelManagementModule.class);
  if(ENDURANCE&&initialLevels<0){
   initialLevels=levels.getNumberOfLevels();
   for(var item:java.util.List.of(net.minecraft.world.item.Items.DIAMOND_SHOVEL,net.minecraft.world.item.Items.DIAMOND_PICKAXE))
    if(!net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(c.getInventory(),new net.minecraft.world.item.ItemStack(item),false).isEmpty())throw new IllegalStateException("Tool space unavailable");
   var hut=mine.getTileEntity().getInventory();
   for(int i=0;i<4;i++)if(!net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(hut,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT,64),false).isEmpty())throw new IllegalStateException("Dirt space unavailable");
   System.out.println("MINER_ENDURANCE_SETUP initialLevels="+initialLevels+" finite supplies: one diamond shovel, one diamond pickaxe, 256 dirt in hut");
  }
  var ai=c.getJob().getWorkerAI();var text=new StringBuilder("MINER_PROBE tick="+ticks+" active="+colony.isActive()+" pos="+c.getEntity().map(e->e.position().toString())+" state="+(ai==null?"none":ai.getState())+" inv="+c.getInventory().getStackInSlot(3));
  if(ai!=null)for(String name:new String[]{"minerWorkingLocation","currentStandingPosition"}){var field=ai.getClass().getDeclaredField(name);field.setAccessible(true);text.append(" "+name+"="+field.get(ai));}
  if(ai!=null){var f=com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure.class.getDeclaredField("blockToMine");f.setAccessible(true);var p=(BlockPos)f.get(ai);text.append(" structureTarget="+p+(p==null?"":" state="+level.getBlockState(p)));}
  text.append(" ladderBottom="+com.minecolonies.core.util.WorkerUtil.getLastLadder(mine.getLadderLocation(),level));
  for(var r:mine.getOpenRequestsOfCitizenOrBuilding(7,r->true))text.append(" request="+r.getLongDisplayString().getString()+"/"+r.getState());
  System.out.println(text);
  if(ENDURANCE){
   if(levels.getNumberOfLevels()>initialLevels){System.out.println("MINER_ENDURANCE_PASS completed shaft level: "+initialLevels+" -> "+levels.getNumberOfLevels()+" at tick "+ticks);server.halt(false);}
   else if(ticks>=24000){System.out.println("MINER_ENDURANCE_FAIL no completed shaft level after 24000 ticks");server.halt(false);}
  }else if(ticks>=2400)server.halt(false);
 }
}
