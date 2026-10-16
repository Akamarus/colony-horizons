package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.menu.*;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.supply.*;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.api.RotationMirror;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class OutpostMenuTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=600)
 public static void district_menu(GameTestHelper h){h.runAfterDelay(40,()->{try{run(h);}catch(Exception e){throw new RuntimeException(e);}});}
 static IBuilding place(GameTestHelper h,OutpostProbeFixture f,BlockPos p)throws Exception{
  return place(h,f,p,ModBlocks.blockHutLumberjack,"fundamentals/lumberjack1.blueprint");
 }
 static IBuilding place(GameTestHelper h,OutpostProbeFixture f,BlockPos p,com.minecolonies.api.blocks.AbstractBlockHut<?> block,String path)throws Exception{
  h.getLevel().getChunkAt(p);
  var bp=new Blueprint((short)1,(short)1,(short)1,h.getLevel().registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);
  String pack=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
  var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.ownerId(),"MenuOwner"));
  SupplyTests.check(block.setup(actor,h.getLevel(),p,bp,RotationMirror.NONE,true,pack,path));
  var b=f.colony().getServerBuildingManager().getBuilding(p);b.setBuildingLevel(1);b.setCorners(p,p);return b;
 }
 private static void run(GameTestHelper h)throws Exception{
  var rows=new ArrayList<ProbeResult>();var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
  var p=f.center();var a=place(h,f,p.offset(2,0,2));
  // GameTest's origin can straddle a chunk edge; this case requires an enclosed hut.
  var b=place(h,f,a.getPosition().offset((a.getPosition().getX()&15)<14?2:-2,0,0));
  var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.ownerId(),"MenuOwner"));int colony=f.colony().getID();
  SupplyTests.run(rows,"multi_hut_draft",()->{var d=OutpostMenuPlanner.review(actor,colony,"Woods",Set.of(a.getPosition(),b.getPosition()));SupplyTests.eq(d.record().buildings().size(),2);});
  SupplyTests.run(rows,"enclosed_hut_included",()->SupplyTests.eq(OutpostMenuPlanner.review(actor,colony,"Woods",Set.of(a.getPosition())).record().buildings().size(),2));
  SupplyTests.run(rows,"limits_rejected",()->{var old=a.getCorners();a.setCorners(a.getPosition(),a.getPosition().offset(1024,0,1024));try{SupplyTests.reject(()->OutpostMenuPlanner.review(actor,colony,"Huge",Set.of(a.getPosition())));}finally{a.setCorners(old.getA(),old.getB());}});
  SupplyTests.run(rows,"missing_blueprint_rejected",()->{String old=a.getBlueprintPath();a.setBlueprintPath("");try{SupplyTests.reject(()->OutpostMenuPlanner.review(actor,colony,"Woods",Set.of(a.getPosition())));}finally{a.setBlueprintPath(old);}});
  SupplyTests.run(rows,"foreign_owner_rejected",()->SupplyTests.reject(()->OutpostMenuPlanner.review(FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Other")),colony,"Woods",Set.of(a.getPosition()))));
  SupplyTests.run(rows,"stale_review_rejected",()->{var d=OutpostMenuPlanner.review(actor,colony,"Woods",Set.of(a.getPosition()));a.setBuildingLevel(2);try{SupplyTests.reject(()->OutpostMenuPlanner.activate(actor,colony,d));SupplyTests.check(OutpostSavedData.get(h.getLevel()).all().isEmpty());SupplyTests.check(!((ManagedCourierColony)f.colony()).colonyHorizons$managedCouriers());}finally{a.setBuildingLevel(1);}});
  SupplyTests.run(rows,"missing_claim_rejected",()->{var claim=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(h.getLevel().dimension(),new net.minecraft.world.level.ChunkPos(a.getPosition()));claim.setOwningColony(99999,h.getLevel().getChunkAt(a.getPosition()));try{SupplyTests.reject(()->OutpostMenuPlanner.review(actor,colony,"Woods",Set.of(a.getPosition())));}finally{claim.setOwningColony(colony,h.getLevel().getChunkAt(a.getPosition()));}});
  SupplyTests.run(rows,"unloaded_rejected",()->{var far=new BlockPos(1000000,64,1000000);SupplyTests.check(!h.getLevel().hasChunkAt(far));SupplyTests.reject(()->OutpostMenuPlanner.review(actor,colony,"Far",Set.of(far)));SupplyTests.check(!h.getLevel().hasChunkAt(far));});
  var outside=place(h,f,p.offset(34,0,2));
  SupplyTests.run(rows,"cross_assignment_rejected",()->{var citizen=f.createCitizen();a.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.FORESTER_WORK).assignCitizen(citizen);citizen.setHomeBuilding(outside);try{SupplyTests.reject(()->OutpostMenuPlanner.review(actor,colony,"Split",Set.of(a.getPosition())));}finally{citizen.setHomeBuilding(null);}});

  var warehouse=place(h,f,p.offset(50,0,2),ModBlocks.blockHutWareHouse,"craftsmanship/storage/warehouse1.blueprint");
  SupplyTests.run(rows,"rack_extends_boundary",()->{var rack=warehouse.getPosition().offset(16,0,0);warehouse.addContainerPosition(rack);try{var d=OutpostMenuPlanner.review(actor,colony,"Warehouse",Set.of(warehouse.getPosition()));SupplyTests.check(d.record().approvedChunks().contains(new net.minecraft.world.level.ChunkPos(rack)));}finally{warehouse.removeContainerPosition(rack);}});
  var hall=place(h,f,p.offset(-34,0,2),ModBlocks.blockHutTownHall,"infrastructure/townhall1.blueprint");
  SupplyTests.run(rows,"townhall_rejected",()->SupplyTests.reject(()->OutpostMenuPlanner.review(actor,colony,"Hall",Set.of(hall.getPosition()))));
  SupplyTests.run(rows,"activate_and_pause",()->{var id=OutpostMenuPlanner.activate(actor,colony,OutpostMenuPlanner.review(actor,colony,"Woods",Set.of(a.getPosition())));SupplyTests.check(OutpostSavedData.get(h.getLevel()).find(id).orElseThrow().state()==OutpostRecord.State.ACTIVE);SupplyMenuService.pauseOutpost(actor,colony,id,true);SupplyTests.check(OutpostSavedData.get(h.getLevel()).find(id).orElseThrow().state()==OutpostRecord.State.PAUSED);SupplyMenuService.pauseOutpost(actor,colony,id,false);});
  SupplyTests.run(rows,"overlap_rejected",()->SupplyTests.reject(()->OutpostMenuPlanner.review(actor,colony,"Other",Set.of(a.getPosition()))));
  SupplyTests.run(rows,"stale_hut_rejected",()->{var d=OutpostMenuPlanner.review(actor,colony,"Remote",Set.of(outside.getPosition()));var old=h.getLevel().getBlockState(outside.getPosition());h.getLevel().setBlockAndUpdate(outside.getPosition(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());try{SupplyTests.reject(()->OutpostMenuPlanner.activate(actor,colony,d));}finally{h.getLevel().setBlockAndUpdate(outside.getPosition(),old);}});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"MenuDistricts",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
