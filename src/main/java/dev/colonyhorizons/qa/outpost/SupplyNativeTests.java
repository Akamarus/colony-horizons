package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.supply.*;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class SupplyNativeTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void native_hut_supply(GameTestHelper h){h.runAfterDelay(40,()->{try{verify(h);}catch(Exception e){throw new RuntimeException(e);}});}
 private static void verify(GameTestHelper h)throws Exception{
  var rows=new ArrayList<ProbeResult>();
  try{
   var level=h.getLevel();var owner=UUID.randomUUID();var f=OutpostProbeFixture.create(level,h.absolutePos(new BlockPos(5,2,5)),owner);
   var a=f.center().offset(-4,0,4);var b=f.center().offset(4,0,4);
   var player=FakePlayerFactory.get(level,new GameProfile(owner,"OutpostProbe"));
   var hut=ModBlocks.blockHutLumberjack;
   String pack=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
   var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint(pack,"fundamentals/lumberjack1.blueprint",level.registryAccess());
   for(var pos:List.of(a,b))h.assertTrue(hut.setup(player,level,pos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,pack,"fundamentals/lumberjack1.blueprint"),"Native hut setup");
   var from=(TileEntityColonyBuilding)level.getBlockEntity(a);var to=(TileEntityColonyBuilding)level.getBlockEntity(b);
   var service=NativeSupplyService.get(level);
   boolean denied=false;try{service.bind(UUID.randomUUID(),a,"stolen",false);}catch(IllegalArgumentException e){denied=true;}SupplyTests.check(denied);
   var dispatcher=level.getServer().getCommands().getDispatcher();var commandSource=player.createCommandSourceStack();
   SupplyTests.eq(dispatcher.execute("chsupply bind home false "+a.getX()+" "+a.getY()+" "+a.getZ(),commandSource),1);
   SupplyTests.eq(dispatcher.execute("chsupply bind woods true "+b.getX()+" "+b.getY()+" "+b.getZ(),commandSource),1);
   SupplyTests.eq(dispatcher.execute("chsupply status",commandSource),1);pass(rows,"registered_owner_commands");
   if(System.getProperty("colonyhorizons.foundationSuite").equals("BudgetedSupplyNative")){budget(h,service,owner,from,to);return;}
   var route=service.link(owner,"home","woods",new ItemStack(Items.BREAD),24,16,32);
   from.getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,40));from.setChanged();
   service.tick(100);SupplyTests.check(service.problem().isEmpty());SupplyTests.eq(from.getInventory().getStackInSlot(0).getCount(),16);SupplyTests.eq(service.route(route).cargo().getCount(),24);
   pass(rows,"native_dispatch_reserve");
   NativeSupplyService.stop();service=NativeSupplyService.get(level);SupplyTests.check(service.problem().isEmpty());
   service.tick(100);SupplyTests.eq(to.getInventory().getStackInSlot(0).getCount(),0);
   service.tick(100);SupplyTests.check(service.problem().isEmpty());SupplyTests.eq(to.getInventory().getStackInSlot(0).getCount(),24);SupplyTests.eq(service.route(route).delivered(),24);
   NativeSupplyService.stop();service=NativeSupplyService.get(level);service.tick(100);SupplyTests.eq(to.getInventory().getStackInSlot(0).getCount(),24);
   pass(rows,"native_delivery_reload_once");
   var back=service.link(owner,"woods","home",new ItemStack(Items.BREAD),32,8,16);service.tick(100);SupplyTests.eq(service.route(back).cargo().getCount(),16);SupplyTests.eq(to.getInventory().getStackInSlot(0).getCount(),8);
   service.cancel(owner,back);service.tick(200);SupplyTests.eq(to.getInventory().getStackInSlot(0).getCount(),24);SupplyTests.check(service.route(back).cargo().isEmpty());
   pass(rows,"native_return_cargo");
   var invalid=service.link(owner,"home","woods",new ItemStack(Items.SPRUCE_LOG),10,0,10);
   from.getInventory().setStackInSlot(1,new ItemStack(Items.SPRUCE_LOG,10));service.tick(100);
   SupplyTests.eq(service.route(invalid).cargo().getCount(),10);
   level.setBlockAndUpdate(b,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(b,hut.defaultBlockState());
   service.tick(200);SupplyTests.eq(service.route(invalid).cargo().getCount(),10);SupplyTests.check(service.route(invalid).reason()==SupplyNetwork.Reason.DESTINATION_UNAVAILABLE);
   pass(rows,"replaced_hut_retains_cargo");pass(rows,"foreign_owner_rejected");
   var far=new NativeSupplyService.Endpoint(UUID.randomUUID(),owner,f.colony().getID(),"far",a.offset(100000,0,100000),true);
   SupplyTests.check(!level.hasChunkAt(far.position()));SupplyTests.check(service.availability(far).startsWith("Unavailable"));SupplyTests.check(!level.hasChunkAt(far.position()));pass(rows,"resolver_does_not_load_chunks");
   var c=a.offset(0,0,-4);
   SupplyTests.check(hut.setup(player,level,c,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,pack,"fundamentals/lumberjack1.blueprint"));
   service.bind(owner,c,"fault",true);service.link(owner,"home","fault",new ItemStack(Items.BREAD),8,0,8);
   from.getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,32));from.setChanged();
   SupplySaveFault.target=new net.minecraft.world.level.ChunkPos(a);
   service.tick(100);h.assertTrue(service.problem().contains("save verification failed"),"Fault must quarantine: "+service.problem());
   var disk=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/colonyhorizons_native_supply.dat");
   var pending=net.minecraft.nbt.NbtIo.readCompressed(disk,net.minecraft.nbt.NbtAccounter.unlimitedHeap());SupplyTests.check(pending.contains("pending"));
   var bytes=java.nio.file.Files.readAllBytes(disk);NativeSupplyService.stop();service=NativeSupplyService.get(level);service.tick(100);
   SupplyTests.check(service.problem().contains("Interrupted inventory handoff"));SupplyTests.check(java.util.Arrays.equals(bytes,java.nio.file.Files.readAllBytes(disk)));
   pass(rows,"failed_native_save_quarantined");
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SupplyNative",rows);h.succeed();
  }catch(Throwable e){rows.add(new ProbeResult("native_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SupplyNative",rows);throw e;}
 }
 private static void budget(GameTestHelper h,NativeSupplyService service,UUID owner,TileEntityColonyBuilding from,TileEntityColonyBuilding to)throws Exception{
  var rows=new ArrayList<ProbeResult>();var home=service.named(owner,"home").id();var woods=service.named(owner,"woods").id();var operation=UUID.randomUUID();
  var route=service.linkBudgeted(owner,operation,home,woods,new ItemStack(Items.BREAD),8,16,8,20);
  SupplyTests.check(route.equals(operation));SupplyTests.check(service.linkBudgeted(owner,operation,home,woods,new ItemStack(Items.BREAD),8,16,8,20).equals(route));
  boolean denied=false;try{service.linkBudgeted(owner,operation,home,woods,new ItemStack(Items.BREAD),8,16,8,40);}catch(IllegalArgumentException e){denied=true;}SupplyTests.check(denied);pass(rows,"native_budget_identity");
  denied=false;try{service.linkBudgeted(UUID.randomUUID(),UUID.randomUUID(),home,woods,new ItemStack(Items.BREAD),8,0,8,20);}catch(SecurityException|IllegalArgumentException e){denied=true;}SupplyTests.check(denied);pass(rows,"native_budget_owner");
  from.getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,64));from.setChanged();service.tick(100);
  SupplyTests.check(service.problem().isEmpty());SupplyTests.eq(service.allowanceRemaining(route),12);SupplyTests.eq(from.getInventory().getStackInSlot(0).getCount(),56);pass(rows,"native_budget_dispatch");
  NativeSupplyService.stop();service=NativeSupplyService.get(h.getLevel());SupplyTests.check(service.problem().isEmpty());SupplyTests.eq(service.allowanceRemaining(route),12);SupplyTests.eq(service.route(route).cargo().getCount(),8);pass(rows,"native_budget_reload");
  int received=0;for(int i=0;i<6;i++){service.tick(200);received+=to.getInventory().getStackInSlot(0).getCount();to.getInventory().setStackInSlot(0,ItemStack.EMPTY);to.setChanged();service.tick(100);}
  SupplyTests.check(service.problem().isEmpty());SupplyTests.eq(received,20);SupplyTests.eq(service.route(route).delivered(),20);SupplyTests.eq(from.getInventory().getStackInSlot(0).getCount(),44);SupplyTests.eq(service.allowanceRemaining(route),0);pass(rows,"native_budget_exhausted");
  NativeSupplyService.stop();service=NativeSupplyService.get(h.getLevel());service.linkBudgeted(owner,operation,home,woods,new ItemStack(Items.BREAD),8,16,8,20);service.tick(200);SupplyTests.eq(from.getInventory().getStackInSlot(0).getCount(),44);SupplyTests.check(service.route(route).cargo().isEmpty());pass(rows,"native_budget_repeat_cannot_refill");
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"BudgetedSupplyNative",rows);h.succeed();
 }
 private static void pass(List<ProbeResult> rows,String name){rows.add(new ProbeResult(name,ProbeResult.Outcome.PASS,"Native hut assertions passed"));}
}

