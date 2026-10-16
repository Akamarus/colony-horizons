package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.integration.NativeOutpostMembership;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;
public final class NativeMembershipTests {
 static OutpostRecord record(OutpostProbeFixture f,BlockPos p,int citizen,int colony){
  return new OutpostRecord(UUID.randomUUID(),colony,f.colony().getDimension(),"Native",Set.of(new ChunkPos(p)),
   new BoundingBox(p.getX()-2,p.getY()-2,p.getZ()-2,p.getX()+2,p.getY()+2,p.getZ()+2),
   new BoundingBox(p.getX()+20,p.getY(),p.getZ(),p.getX()+30,p.getY()+5,p.getZ()+10),
   Map.of(p,new OutpostRecord.BuildingRef(p,ResourceLocation.parse("minecolonies:blockhutlumberjack"),"Arcology","fundamentals/lumberjack1.blueprint",new BoundingBox(p))),
   Set.of(citizen),Set.of(citizen),Optional.empty(),OutpostRecord.State.PLANNED,List.of());
 }
 public static void verify(GameTestHelper h,List<ProbeResult> rows)throws Exception{
  var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
  var p=f.center().offset(0,0,4);
  var hut=com.minecolonies.api.blocks.ModBlocks.blockHutLumberjack;
  var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,h.getLevel().registryAccess());
  bp.addBlockState(BlockPos.ZERO,hut.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);
  var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(f.ownerId(),"OutpostProbe"));
  String pack=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
  h.assertTrue(hut.setup(player,h.getLevel(),p,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,pack,"fundamentals/lumberjack1.blueprint"),"Native setup");
  var b=f.colony().getServerBuildingManager().getBuilding(p);var c=f.createCitizen();
  b.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.FORESTER_WORK).assignCitizen(c);
  var r=record(f,p,c.getId(),f.colony().getID());
  int count=f.colony().getCitizenManager().getCurrentCitizenCount();
  c.getInventory().setStackInSlot(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPRUCE_LOG,7));
  h.assertTrue(NativeOutpostMembership.inspect(h.getLevel(),r).ready(),"Native references match");pass(rows,"native_identity_matches");
  h.assertTrue(!NativeOutpostMembership.inspect(h.getLevel(),record(f,p,c.getId(),Integer.MAX_VALUE)).ready(),"Foreign colony reject");pass(rows,"foreign_colony_rejected");
    var claims=com.minecolonies.api.colony.IColonyManager.getInstance().getClaimData(h.getLevel().dimension(),new ChunkPos(p));
  claims.setOwningColony(999999,h.getLevel().getChunkAt(p));
  h.assertTrue(!NativeOutpostMembership.inspect(h.getLevel(),r).ready(),"Foreign claim must block even with valid native references");
  claims.setOwningColony(f.colony().getID(),h.getLevel().getChunkAt(p));
  h.getLevel().setBlockAndUpdate(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
  h.assertTrue(!NativeOutpostMembership.inspect(h.getLevel(),r).ready(),"Removed hut reported");pass(rows,"removed_hut_reported");
  h.assertTrue(NativeOutpostMembership.inspect(h.getLevel(),record(f,p,999999,f.colony().getID())).missingCitizens().contains(999999),"Missing citizen report");pass(rows,"missing_citizen_reported");
  var far=p.offset(100000,0,100000);
  h.assertTrue(!h.getLevel().hasChunkAt(far),"Fixture far chunk unloaded");
  var unresolved=NativeOutpostMembership.inspect(h.getLevel(),record(f,far,c.getId(),f.colony().getID()));
  h.assertTrue(unresolved.blockers().stream().anyMatch(s->s.contains("Unloaded")) && !h.getLevel().hasChunkAt(far),"Inspection must not load chunks");pass(rows,"unloaded_reference_deferred");
  h.assertTrue(f.colony().getCitizenManager().getCurrentCitizenCount()==count && c.getInventory().getStackInSlot(0).getCount()==7,"Inspection preserves population/inventory");
 }
 static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native references verified without reconstruction"));}
}
