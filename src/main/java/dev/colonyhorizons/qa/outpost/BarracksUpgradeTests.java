package dev.colonyhorizons.qa.outpost;
import com.mojang.authlib.GameProfile;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.api.util.CreativeBuildingStructureHandler;
import com.ldtteam.structurize.api.RotationMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class BarracksUpgradeTests {
 private static void check(boolean condition){if(!condition)throw new AssertionError("Barracks check failed at "+StackWalker.getInstance().walk(frames->frames.skip(1).findFirst().orElseThrow()));}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void upgrade(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=3;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();
   SupplyTests.run(rows,"completed_tower_upgrade",()->{try{
    var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),"BarracksUpgrade");var fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
    var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);setup.owner=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());setup.owner.connection=fake.connection;
    var anchor=new BlockPos(512,16,0);var parent=(BuildingBarracks)setup.place("military/barracks1.blueprint",anchor,true);
    var towerPos=anchor.offset(5,0,5);var tower=(BuildingBarracksTower)setup.place("military/barrackstower1.blueprint",towerPos,true);
    parent.registerBlockPosition(level.getBlockState(towerPos),towerPos,level);
    var guard=fixture.createCitizen();check(tower.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.KNIGHT_BARRACKS_WORK).assignCitizen(guard));
    var inventory=tower.getItemHandlerCap((net.minecraft.core.Direction)null);var sentinel=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,7);check(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(inventory,sentinel,false).isEmpty());
    var tileBefore=tower.getTileEntity().saveWithFullMetadata(level.registryAccess());
    int cases=0;
    for(int target=2;target<=5;target++)for(var transform:RotationMirror.values()){
     var next=setup.blueprint("military/barracks"+target+".blueprint").setPackName("Arcology Horizons").setFilePath(java.nio.file.Path.of("military")).setFileName("barracks"+target+".blueprint");next.setRotationMirror(transform,level);
     var sourceBefore=com.ldtteam.structurize.blueprints.v1.BlueprintUtil.writeBlueprintToNBT(next);
     var context=new CreativeBuildingStructureHandler(level,anchor,next,transform,true);var effective=context.getBluePrint();var origin=anchor.subtract(effective.getPrimaryBlockOffset());
     var placer=new com.ldtteam.structurize.placement.StructurePlacer(context);int conflicts=0;int preserved=0;int untouched=0;
     for(var info:next.getBlockInfoAsList()){
      var pos=origin.offset(info.getPos());var corners=tower.getCorners();
      boolean inside=pos.getX()>=Math.min(corners.getA().getX(),corners.getB().getX())&&pos.getX()<=Math.max(corners.getA().getX(),corners.getB().getX())&&pos.getY()>=Math.min(corners.getA().getY(),corners.getB().getY())&&pos.getY()<=Math.max(corners.getA().getY(),corners.getB().getY())&&pos.getZ()>=Math.min(corners.getA().getZ(),corners.getB().getZ())&&pos.getZ()<=Math.max(corners.getA().getZ(),corners.getB().getZ());
      if(!inside){if(!next.getBlockState(info.getPos()).equals(effective.getBlockState(info.getPos())))throw new AssertionError("Outside tower: "+pos+" corners="+corners.getA()+".."+corners.getB()+" state="+next.getBlockState(info.getPos())+" effective="+effective.getBlockState(info.getPos())+" target="+target+" transform="+transform);untouched++;continue;}
      if(next.getBlockState(info.getPos()).isAir()&&!level.getBlockState(pos).isAir())conflicts++;
      check(effective.getBlockState(info.getPos()).getBlock()==com.ldtteam.structurize.blocks.ModBlocks.blockSubstitution.get());
      var before=level.getBlockState(pos);placer.handleBlockPlacement(level,pos,null,effective.getBlockInfoAsMap().get(info.getPos()));check(level.getBlockState(pos).equals(before));preserved++;
     }
     if(!(conflicts>20&&untouched>100&&preserved==150))throw new AssertionError("target="+target+" transform="+transform+" conflicts="+conflicts+" untouched="+untouched+" preserved="+preserved);check(effective!=next&&context.getBluePrint()==effective);check(sourceBefore.equals(com.ldtteam.structurize.blueprints.v1.BlueprintUtil.writeBlueprintToNBT(next)));cases++;
    }
    check(cases==32);check(tileBefore.equals(tower.getTileEntity().saveWithFullMetadata(level.registryAccess())));check(guard.getWorkBuilding()==tower&&guard.getHomeBuilding()==tower);check(parent.getTowers().contains(towerPos));
    // Unbuilt child sites must not be protected; independent tower upgrades must remain untouched.
    var zeroTower=(BuildingBarracksTower)setup.place("military/barrackstower1.blueprint",anchor.offset(-5,0,-5),false);parent.registerBlockPosition(level.getBlockState(zeroTower.getPosition()),zeroTower.getPosition(),level);
    var parentBp=setup.blueprint("military/barracks2.blueprint").setPackName("Arcology Horizons");var masked=dev.colonyhorizons.integration.BarracksTowerPreservation.protect(level,anchor,parentBp);var relative=zeroTower.getPosition().subtract(anchor.subtract(parentBp.getPrimaryBlockOffset()));check(masked.getBlockState(relative).getBlock()==com.minecolonies.api.blocks.ModBlocks.blockHutBarracksTower);
    var cached=new CreativeBuildingStructureHandler(level,anchor,parentBp,RotationMirror.NONE,true);var beforeCompletion=cached.getBluePrint();
    zeroTower.setBuildingLevel(1);zeroTower.onUpgradeComplete(setup.blueprint("military/barrackstower1.blueprint"),1);
    var afterCompletion=cached.getBluePrint();if(beforeCompletion==afterCompletion)throw new AssertionError("Cache did not refresh; child level="+zeroTower.getBuildingLevel()+" towers="+parent.getTowers());if(afterCompletion.getBlockState(relative).getBlock()!=com.ldtteam.structurize.blocks.ModBlocks.blockSubstitution.get())throw new AssertionError("Completed child not masked; corners="+zeroTower.getCorners().getA()+".."+zeroTower.getCorners().getB()+" relative="+relative+" link="+zeroTower.serializeNBT(level.registryAccess()).get("pos"));
    var own=setup.blueprint("military/barrackstower2.blueprint").setPackName("Arcology Horizons");check(dev.colonyhorizons.integration.BarracksTowerPreservation.protect(level,towerPos,own)==own);
   }catch(Exception e){throw new IllegalStateException(e);}});
   try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"BarracksUpgrade",rows);}catch(Exception e){throw new RuntimeException(e);}
   h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
  });
 }
}


