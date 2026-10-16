package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.supply.*;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class SupplyNativeRestartTests {
 private static final BlockPos HOME=new BlockPos(0,100,4),REMOTE=new BlockPos(512,100,4);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void native_restart(GameTestHelper h){
  // QA only: production never loads or claims chunks. Keep the fixture small and repeatable.
  for(var p:List.of(HOME,REMOTE))for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced((p.getX()>>4)+x,(p.getZ()>>4)+z,true);
  h.runAfterDelay(40,()->{try{verify(h);}catch(Exception e){throw new RuntimeException(e);}});
 }
 private static void verify(GameTestHelper h)throws Exception{
  var level=h.getLevel();String phase=System.getProperty("colonyhorizons.probePhase","seed");var rows=new ArrayList<ProbeResult>();
  try{
   var path=level.getServer().getWorldPath(LevelResource.ROOT).resolve("supply-native-expected.dat");
   var service=NativeSupplyService.get(level);CompoundTag expected;
   if(phase.equals("seed")){
    SupplyTests.check(!Files.exists(path));expected=new CompoundTag();var owner=UUID.randomUUID();
    level.getServer().getProfileCache().add(new GameProfile(owner,"SupplyProbe"));
    var f=OutpostProbeFixture.create(level,HOME.offset(0,0,-4),owner);
    ChunkDataHelper.staticClaimInRange((Colony)f.colony(),true,REMOTE,1,level,false);
    var player=FakePlayerFactory.get(level,new GameProfile(owner,"SupplyProbe"));
    String pack=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
    var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint(pack,"fundamentals/lumberjack1.blueprint",level.registryAccess());
    for(var p:List.of(HOME,REMOTE))SupplyTests.check(ModBlocks.blockHutLumberjack.setup(player,level,p,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,pack,"fundamentals/lumberjack1.blueprint"));
    service.bind(owner,HOME,"home",false);service.bind(owner,REMOTE,"woods",true);
    hut(h,REMOTE).getInventory().setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,40));hut(h,REMOTE).setChanged();
    hut(h,HOME).getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,16));hut(h,HOME).setChanged();
    var logs=service.link(owner,"woods","home",new ItemStack(Items.SPRUCE_LOG),24,16,32);
    var bread=service.link(owner,"home","woods",new ItemStack(Items.BREAD),8,8,8);
    service.tick(100);SupplyTests.check(service.problem().isEmpty());
    service.pause(owner,logs,true);service.pause(owner,bread,true);
    expected.putUUID("owner",owner);expected.putUUID("logs",logs);expected.putUUID("bread",bread);
    NbtIo.writeCompressed(expected,path);
    SupplyTests.eq(service.route(logs).cargo().getCount(),24);SupplyTests.eq(service.route(bread).cargo().getCount(),8);
   }else{
    SupplyTests.check(Files.exists(path));expected=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());
    SupplyTests.check(service.problem().isEmpty());
    var logs=expected.getUUID("logs");var bread=expected.getUUID("bread");var owner=expected.getUUID("owner");
    SupplyTests.check(service.endpoints(owner).size()==2);
    for(var e:service.endpoints(owner))SupplyTests.check(service.availability(e).equals("Ready"));
    if(phase.equals("verify")){
     SupplyTests.eq(service.route(logs).cargo().getCount(),24);SupplyTests.eq(service.route(bread).cargo().getCount(),8);
     service.pause(owner,logs,false);service.tick(1200);service.tick(1200);
     SupplyTests.eq(service.route(logs).delivered(),24);SupplyTests.eq(service.route(bread).cargo().getCount(),8);
    }else{
     SupplyTests.check(phase.equals("verify_final"));SupplyTests.eq(service.route(logs).delivered(),24);
     service.pause(owner,bread,false);service.tick(1200);service.tick(1200);service.tick(1200);
     SupplyTests.eq(service.route(bread).delivered(),8);SupplyTests.eq(service.route(logs).delivered(),24);
    }
   }
   SupplyTests.check(service.problem().isEmpty());
   SupplyTests.eq(stock(h,Items.SPRUCE_LOG)+service.route(expected.getUUID("logs")).cargo().getCount(),40);
   SupplyTests.eq(stock(h,Items.BREAD)+service.route(expected.getUUID("bread")).cargo().getCount(),16);
   rows.add(new ProbeResult("native_"+phase,ProbeResult.Outcome.PASS,"Two huts 512 blocks apart; conserved 40 logs and 16 bread across process restart"));
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SupplyNativeRestart",rows);h.succeed();
  }catch(Throwable e){rows.add(new ProbeResult("native_"+phase,ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SupplyNativeRestart",rows);throw e;}
 }
 private static TileEntityColonyBuilding hut(GameTestHelper h,BlockPos pos){return (TileEntityColonyBuilding)Objects.requireNonNull(h.getLevel().getBlockEntity(pos));}
 private static int stock(GameTestHelper h,Item item){int n=0;for(var p:List.of(HOME,REMOTE)){var inv=hut(h,p).getInventory();for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(item))n+=inv.getStackInSlot(i).getCount();}return n;}
}
