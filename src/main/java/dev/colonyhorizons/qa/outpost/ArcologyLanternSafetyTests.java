package dev.colonyhorizons.qa.outpost;

import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

/** Real builder mining operations; optional Amendments falling-entity/fire control, no full work order. */
@PrefixGameTestTemplate(false)
public final class ArcologyLanternSafetyTests {
 static final class BuilderProbe extends EntityAIStructureBuilder {
  BuilderProbe(JobBuilder job){super(job);}
  boolean mine(BlockPos p,BlockPos stand){return mineBlock(p,stand);}
 }
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2000)
 public static void lantern_support_removal(GameTestHelper h){
  for(int x=29;x<=31;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{try{
   boolean integration="ArcologyAmendments".equals(System.getProperty("colonyhorizons.foundationSuite"));
   if(integration){
    check(net.neoforged.fml.ModList.get().isLoaded("amendments"),"Amendments not loaded");
    var mode=(java.util.function.Supplier<?>)Class.forName("net.mehvahdjukaar.amendments.configs.CommonConfigs").getField("FALLING_LANTERNS").get(null);
    check("ON".equals(mode.get().toString()),"Amendments falling lantern fire must be ON");
   }
   var level=h.getLevel();var center=new BlockPos(480,16,0);
   var fixture=OutpostProbeFixture.create(level,center,UUID.randomUUID());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;
   var builder=(BuildingBuilder)setup.place("fundamentals/builder5.blueprint",center,true);
   var citizen=fixture.createCitizen();check(citizen!=null,"Citizen creation");
   check(builder.getModule(BuildingModules.BUILDER_WORK).assignCitizen(citizen),"Builder assignment");citizen.setPaused(true);
   var entity=citizen.getEntity().orElseThrow();var stand=center.offset(0,0,-4);entity.setPos(stand.getX()+0.5,stand.getY(),stand.getZ()+0.5);
   var inventory=citizen.getInventory();inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND_PICKAXE));inventory.setStackInSlot(1,new ItemStack(Items.DIAMOND_AXE));
   var rows=new ArrayList<ProbeResult>();var support=stand.offset(1,9,0);
   for(boolean hanging:new boolean[]{true,false})for(var lamp:List.of(Blocks.LANTERN,Blocks.SOUL_LANTERN)){
    var pos=hanging?support.below():support.above();
    level.setBlockAndUpdate(support,Blocks.OAK_PLANKS.defaultBlockState());
    level.setBlockAndUpdate(pos,lamp.defaultBlockState().setValue(LanternBlock.HANGING,hanging));
    check(level.getBlockState(pos).canSurvive(level,pos),"Invalid supported lamp fixture");
    int before=count(inventory,lamp.asItem());var ai=new BuilderProbe((JobBuilder)citizen.getJob());
    for(int i=0;i<12&&!level.getBlockState(pos).isAir();i++){
     check(!ai.mine(support,stand),"Builder reported support removed before collecting its lantern");
     check(level.getBlockState(support).is(Blocks.OAK_PLANKS),"Builder removed support while lantern was attached");
    }
    check(level.getBlockState(pos).isAir(),"Lantern was not collected");
    check(count(inventory,lamp.asItem())==before+1,"Lantern not salvaged exactly once into builder inventory");
    boolean done=false;for(int i=0;i<12&&!done;i++)done=ai.mine(support,stand);
    check(done&&level.getBlockState(support).isAir(),"Support mining did not resume");
    rows.add(new ProbeResult((hanging?"hanging_":"standing_")+(lamp==Blocks.LANTERN?"lantern":"soul_lantern"),ProbeResult.Outcome.PASS,"Real builder mining collects exactly one lamp before touching its support, then resumes support removal. Direct native mining operation fixture; no full work-order traversal assertion."));
   }
   if(integration){
    check(level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(470,14,-10,490,36,10),e->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("amendments:falling_lantern")).isEmpty(),"Builder spawned falling lantern");
    var control=center.offset(12,0,0);
    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(control.offset(x,-1,z),Blocks.OAK_PLANKS.defaultBlockState());
    var ceiling=control.above(10);level.setBlockAndUpdate(ceiling,Blocks.OAK_PLANKS.defaultBlockState());
    level.setBlockAndUpdate(ceiling.below(),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
    level.removeBlock(ceiling,false);
    check(!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(control).inflate(3,13,3),e->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("amendments:falling_lantern")).isEmpty(),"Control failed to spawn actual Amendments falling lantern");
    h.runAfterDelay(100,()->{try{
     boolean fire=false;for(var pos:BlockPos.betweenClosed(control.offset(-3,0,-3),control.offset(3,2,3)))fire|=level.getBlockState(pos).getBlock() instanceof BaseFireBlock;
     check(fire,"Dropped control lantern did not ignite a fire");
     for(var pos:BlockPos.betweenClosed(center.offset(-6,0,-8),center.offset(6,12,6)))check(!(level.getBlockState(pos).getBlock() instanceof BaseFireBlock),"Builder area caught fire");
     rows.add(new ProbeResult("ordinary_support_physics",ProbeResult.Outcome.PASS,"Unprotected support removal spawned an actual Amendments falling lantern."));
     rows.add(new ProbeResult("amendments_fire_control",ProbeResult.Outcome.PASS,"With falling_lanterns=ON a real lantern fell nine blocks and ignited the control floor; no fire or falling entity injected."));
     rows.add(new ProbeResult("amendments_builder_no_fire",ProbeResult.Outcome.PASS,"All four native builder removals salvaged the lantern before support removal with Amendments active; no falling lantern spawned and no fire appeared in builder area after 100 ticks."));
     ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyAmendments",rows);h.succeed();
    }catch(Throwable e){h.fail(e.toString());}});return;
   }
   // Ordinary world/player changes must retain vanilla support physics.
   level.setBlockAndUpdate(support,Blocks.OAK_PLANKS.defaultBlockState());
   level.setBlockAndUpdate(support.below(),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
   level.removeBlock(support,false);check(level.getBlockState(support.below()).isAir(),"Non-builder physics changed");
   rows.add(new ProbeResult("ordinary_support_physics",ProbeResult.Outcome.PASS,"Removing support outside builder AI still removes unsupported lantern normally."));
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyLanternSafety",rows);h.succeed();
  }catch(Throwable e){h.fail(e.toString());}});
 }
 static int count(net.neoforged.neoforge.items.IItemHandler inventory,Item item){int total=0;for(int i=0;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).is(item))total+=inventory.getStackInSlot(i).getCount();return total;}
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
