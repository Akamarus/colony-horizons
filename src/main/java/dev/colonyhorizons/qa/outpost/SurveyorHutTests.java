package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.AbstractBlockHut;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class SurveyorHutTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300)
 public static void registration(GameTestHelper h) {
  var rows=new ArrayList<ProbeResult>();var hut=ResourceLocation.fromNamespaceAndPath("colonyhorizons","blockhutsurveyor");var id=ResourceLocation.fromNamespaceAndPath("colonyhorizons","surveyor");
  SupplyTests.run(rows,"native_hut_registered",()->SupplyTests.check(BuiltInRegistries.BLOCK.get(hut) instanceof AbstractBlockHut<?>));
  SupplyTests.run(rows,"hut_item_registered",()->SupplyTests.check(BuiltInRegistries.ITEM.get(hut)!=Items.AIR));
  SupplyTests.run(rows,"building_registered",()->SupplyTests.check(IMinecoloniesAPI.getInstance().getBuildingRegistry().containsKey(id)));
  SupplyTests.run(rows,"job_registered",()->SupplyTests.check(IMinecoloniesAPI.getInstance().getJobRegistry().containsKey(id)));
  SupplyTests.run(rows,"native_florist_unchanged",()->SupplyTests.check(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecolonies","blockhutflorist")) instanceof com.minecolonies.core.blocks.huts.BlockHutFlorist));
  SupplyTests.run(rows,"native_tile_supported",()->SupplyTests.check(com.minecolonies.api.tileentities.MinecoloniesTileEntities.BUILDING.get().isValid(BuiltInRegistries.BLOCK.get(hut).defaultBlockState())));
  SupplyTests.run(rows,"native_assignment_and_save",()->{
   var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new net.minecraft.core.BlockPos(5,2,5)),UUID.randomUUID());
   var block=(AbstractBlockHut<?>)BuiltInRegistries.BLOCK.get(hut);var pos=f.center().offset(0,0,4);
   var blueprint=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,h.getLevel().registryAccess());blueprint.addBlockState(net.minecraft.core.BlockPos.ZERO,block.defaultBlockState());blueprint.setCachePrimaryOffset(net.minecraft.core.BlockPos.ZERO);
   var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(f.ownerId(),"SurveyorProbe"));
   SupplyTests.check(block.setup(player,h.getLevel(),pos,blueprint,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/surveyor1.blueprint"));
   var building=f.colony().getServerBuildingManager().getBuilding(pos);var citizen=f.createCitizen();
   SupplyTests.check(building.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(citizen));
   SupplyTests.check(citizen.getJob().getClass().getSimpleName().equals("JobSurveyor")&&building.getMaxBuildingLevel()==5);
   var saved=building.serializeNBT(h.getLevel().registryAccess());var restored=IMinecoloniesAPI.getInstance().getBuildingRegistry().get(id).produceBuilding(pos,f.colony());restored.deserializeNBT(h.getLevel().registryAccess(),saved);
   SupplyTests.check(restored.getSchematicName().equals("surveyor")&&restored.getBuildingType().getRegistryName().equals(id));
   building.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).removeCitizen(citizen);SupplyTests.check(citizen.getJob()==null);
  });
  try {ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SurveyorHut",rows);}catch(Exception e){throw new RuntimeException(e);}
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
