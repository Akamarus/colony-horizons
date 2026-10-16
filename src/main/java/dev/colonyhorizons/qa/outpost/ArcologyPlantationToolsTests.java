package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.core.util.WorkerUtil;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
/** Tool-selection contract backed by vanilla loot, not only a UI declaration. */
@PrefixGameTestTemplate(false)
public final class ArcologyPlantationToolsTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void aquatic_tools(GameTestHelper h){
  for(int x=29;x<=39;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var setup=new ArcologyCropExtensionTests.Run(h);setup.afterVerification=f->{
   var rows=new ArrayList<ProbeResult>();
   try{
    var pos=f.origins.get(3).offset(2,1,4);var state=Blocks.SEAGRASS.defaultBlockState();
    int bare=Block.getDrops(state,f.level,pos,null,null,ItemStack.EMPTY).stream().filter(s->s.is(Items.SEAGRASS)).mapToInt(ItemStack::getCount).sum();
    int sheared=Block.getDrops(state,f.level,pos,null,null,new ItemStack(Items.SHEARS)).stream().filter(s->s.is(Items.SEAGRASS)).mapToInt(ItemStack::getCount).sum();
    h.assertTrue(bare==0&&sheared>0,"Vanilla seagrass loot prerequisites changed");
    for(var block:List.of(Blocks.SEAGRASS,Blocks.TALL_SEAGRASS))h.assertTrue(WorkerUtil.getBestToolForBlock(block.defaultBlockState(),0,f.plantation,f.level,pos)==ModEquipmentTypes.shears.get(),"Planter selects no shears for "+block);
    h.assertTrue(f.fields.get(3).getModule().getRequiredTool()==ModEquipmentTypes.shears.get(),"Seagrass recipe display omits required shears");
    h.assertTrue(WorkerUtil.getBestToolForBlock(Blocks.WATER.defaultBlockState(),0,f.plantation,f.level,pos)==ModEquipmentTypes.none.get(),"Unrelated block tool selection changed");
    rows.add(new ProbeResult("seagrass_tool_contract",ProbeResult.Outcome.PASS,"Vanilla loot yields zero seagrass barehanded and positive output with shears. Native planter tool selection requires shears for both short/tall seagrass; displayed recipe tool matches. Water remains tool-free."));
    ProbeReport.write(f.output,"ArcologyPlantationTools",rows);h.succeed();
   }catch(Throwable e){try{rows.add(new ProbeResult("seagrass_tool_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,"ArcologyPlantationTools",rows);}catch(Exception ignored){}h.fail(e.toString());}
  };setup.start();});
 }
}
