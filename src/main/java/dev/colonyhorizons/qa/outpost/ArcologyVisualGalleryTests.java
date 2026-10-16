package dev.colonyhorizons.qa.outpost;
import com.ldtteam.structurize.blueprints.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
/** Disposable client visual gallery only. Direct placement is not construction evidence. */
@PrefixGameTestTemplate(false)
public final class ArcologyVisualGalleryTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2000)
 public static void gallery(GameTestHelper h){try{
  var level=h.getLevel();var root=Path.of(System.getProperty("colonyhorizons.arcologyPath"));var output=Path.of(System.getProperty("colonyhorizons.probeOutput"));Files.createDirectories(output);var entries=new LinkedHashMap<String,BlockPos>();
  for(int x=480;x<=650;x++)for(int z=-10;z<=165;z++)level.setBlock(new BlockPos(x,63,z),Blocks.GRASS_BLOCK.defaultBlockState(),18);
  entries.put("fundamentals/citizen1.blueprint",new BlockPos(480,64,130));entries.put("fundamentals/citizen3.blueprint",new BlockPos(595,64,130));
  for(int n=1;n<=5;n++)entries.put("military/stable"+n+".blueprint",new BlockPos(500+25*(n-1),64,0));
  for(int n=1;n<=3;n++)entries.put("military/gatehouse"+n+".blueprint",new BlockPos(500+25*(n-1),64,35));
  entries.put("fundamentals/simplequarry1.blueprint",new BlockPos(580,64,35));entries.put("fundamentals/mediumquarry1.blueprint",new BlockPos(610,64,35));
  int i=0;for(String crop:List.of("cocoa","vine","kelp","seagrass","seapickle","glowberry","weepingvine","twistingvine","crimson","warped")){
   entries.put("infrastructure/plantationfields/"+crop+"1.blueprint",new BlockPos(500+20*(i%5),64,75+20*(i/5)));i++;
  }
  for(var e:entries.entrySet())paste(h,root,e.getKey(),e.getValue());
  paste(h,root,"military/gatehouse1.blueprint",new BlockPos(500,64,130));paste(h,root,"military/stable1.blueprint",new BlockPos(500,64,140));
  paste(h,root,"military/gatehouse3.blueprint",new BlockPos(530,64,130));paste(h,root,"military/stable5.blueprint",new BlockPos(530,64,140));
  paste(h,root,"military/gatehouse3.blueprint",new BlockPos(560,64,130));paste(h,root,"military/stable5.blueprint",new BlockPos(560,64,140));paste(h,root,"military/gatehouse3.blueprint",new BlockPos(560,70,130));paste(h,root,"military/stable5.blueprint",new BlockPos(560,70,140));
  level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
  Files.writeString(output.resolve("gallery-layout.txt"),entries+"\nConnected pairs at x500,530,560 z130/140 floorY64; stacked stone pair Y70. Direct placement, no job/construction claim.\n");h.succeed();
 }catch(Throwable e){h.fail(e.toString());}}
 static void paste(GameTestHelper h,Path root,String path,BlockPos origin)throws Exception{
  var l=h.getLevel();var b=BlueprintUtil.readBlueprintFromNBT(NbtIo.readCompressed(root.resolve(path),NbtAccounter.create(64000000)),l.registryAccess());
  for(var info:b.getBlockInfoAsList()){var p=origin.offset(info.getPos());l.getChunkAt(p);l.setBlock(p,b.getBlockState(info.getPos()),18);var tile=l.getBlockEntity(p);if(tile!=null&&info.getTileEntityData()!=null)tile.loadWithComponents(info.getTileEntityData().copy(),l.registryAccess());}
 }
}

