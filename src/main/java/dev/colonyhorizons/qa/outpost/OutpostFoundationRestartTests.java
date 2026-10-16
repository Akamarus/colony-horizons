package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.integration.*;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.util.ChunkDataHelper;
import com.ldtteam.structurize.storage.StructurePacks;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.item.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class OutpostFoundationRestartTests {
 static final BlockPos HOME=new BlockPos(0,100,0),REMOTE=new BlockPos(512,100,0);
 static final String KEY="colonyhorizons_foundation_fixture";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void native_settlement_restart(GameTestHelper h){
  var level=h.getLevel();
  // Entity sections load asynchronously after chunk tickets: load endpoints before the fixture delay.
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setChunkForced(x,z,true);
  for(int x=29;x<=35;x++)for(int z=-3;z<=3;z++)level.setChunkForced(x,z,true);
  h.runAfterDelay(40,()->run(h));
 }
 static void run(GameTestHelper h){
  String phase=System.getProperty("colonyhorizons.probePhase","seed");var rows=new ArrayList<ProbeResult>();
  try{
   var level=h.getLevel();level.setDayTime(6000);
   String packName=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
   level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());


   var expected=level.getDataStorage().get(FACTORY,KEY);
   if(phase.equals("seed")){
    h.assertTrue(expected==null,"Seed must be fresh");expected=new Expected();expected.owner=UUID.randomUUID();expected.id=UUID.randomUUID();
    level.getServer().getProfileCache().add(new com.mojang.authlib.GameProfile(expected.owner,"OutpostProbe"));level.getServer().getProfileCache().save();
    var f=OutpostProbeFixture.create(level,HOME,expected.owner);expected.colony=f.colony().getID();
    var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(expected.owner,"OutpostProbe"));
    ChunkDataHelper.staticClaimInRange((Colony)f.colony(),true,REMOTE,3,level,false);
    var paths=List.of("fundamentals/lumberjack1.blueprint","fundamentals/lumberjack1.blueprint","fundamentals/builder1.blueprint","fundamentals/citizen1.blueprint","fundamentals/citizen1.blueprint","fundamentals/citizen1.blueprint");
    var positions=List.of(REMOTE.offset(-24,0,0),REMOTE.offset(-8,0,0),REMOTE.offset(8,0,0),REMOTE.offset(-24,0,24),REMOTE.offset(-8,0,24),REMOTE.offset(8,0,24));
    var refs=new LinkedHashMap<BlockPos,OutpostRecord.BuildingRef>();
    for(int i=0;i<paths.size();i++){
     String path=paths.get(i);var bp=StructurePacks.getBlueprint(packName,path,level.registryAccess());
     h.assertTrue(bp!=null,"Real Arcology blueprint load "+path);
     var anchor=positions.get(i);var origin=anchor.subtract(bp.getPrimaryBlockOffset());
     var volume=new BoundingBox(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+bp.getSizeX()-1,origin.getY()+bp.getSizeY()-1,origin.getZ()+bp.getSizeZ()-1);
     for(int x=volume.minX()-1;x<=volume.maxX()+1;x++)for(int z=volume.minZ()-1;z<=volume.maxZ()+1;z++){
      level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.defaultBlockState());
      for(int y=100;y<=115;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
     }
     var hut=(AbstractBlockHut<?>)bp.getBlockState(bp.getPrimaryBlockOffset()).getBlock();
     h.assertTrue(hut.setup(player,level,anchor,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,packName,path),"Native registration "+path);
     var building=f.colony().getServerBuildingManager().getBuilding(anchor);building.setBuildingLevel(1);
     refs.put(anchor,new OutpostRecord.BuildingRef(anchor,BuiltInRegistries.BLOCK.getKey(hut),packName,path,volume));
    }
    var residents=new LinkedHashSet<Integer>();
    for(int i=0;i<3;i++){
     var c=f.createCitizen();h.assertTrue(c!=null && c.getEntity().isPresent(),"Native citizen creation");
     c.getEntity().orElseThrow().setNoAi(true);c.setSaturation(20);
     c.getEntity().orElseThrow().setPos(positions.get(i).getX()+2.5,100,positions.get(i).getZ()+2.5);
     var work=f.colony().getServerBuildingManager().getBuilding(positions.get(i));
     boolean assigned=i<2?work.getModule(BuildingModules.FORESTER_WORK).assignCitizen(c):work.getModule(BuildingModules.BUILDER_WORK).assignCitizen(c);
     h.assertTrue(assigned,"Native job assignment");
     var house=f.colony().getServerBuildingManager().getBuilding(positions.get(i+3)).getModule(BuildingModules.LIVING);
     h.assertTrue(house.getModuleMax()>=1 && house.assignCitizen(c),"Native level-one housing capacity/assignment");
     c.getInventory().setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,7+i));residents.add(c.getId());
    }
    var chunks=new HashSet<ChunkPos>();for(int x=29;x<=35;x++)for(int z=-3;z<=3;z++)chunks.add(new ChunkPos(x,z));
    var r=new OutpostRecord(expected.id,expected.colony,level.dimension(),"Arcology Timber",chunks,new BoundingBox(464,95,-48,575,130,63),new BoundingBox(600,95,0,650,130,50),refs,residents,residents,Optional.empty(),OutpostRecord.State.PLANNED,List.of("Missing route terminal"));
    OutpostSavedData.get(level).register(r);expected.citizens=residents.stream().mapToInt(i->i).toArray();
    level.getDataStorage().set(KEY,expected);expected.setDirty();
   }else h.assertTrue(expected!=null,"Missing expected state: never reseed");
   var record=OutpostSavedData.get(level).find(expected.id).orElseThrow();
   var colony=IColonyManager.getInstance().getColonyByDimension(expected.colony,level.dimension());
   h.assertTrue(colony!=null,"Primary native colony must reload");
   h.assertTrue(IColonyManager.getInstance().getIColonyByOwner(level,expected.owner).getID()==expected.colony,"Original ownership retained");
   h.assertTrue(record.buildings().size()==6 && record.workerIds().size()==3,"Six huts, three existing workers");
   for(var chunk:record.approvedChunks()){
    var claim=IColonyManager.getInstance().getClaimData(level.dimension(),chunk);
    h.assertTrue(claim!=null && claim.getOwningColony()==expected.colony,"Persisted satellite claim "+chunk);
   }
   h.assertTrue(IColonyManager.getInstance().getIColony(level,new BlockPos(400,100,0))==null,"Gap stays unclaimed");
   h.assertTrue(colony.getCitizenManager().getCurrentCitizenCount()==3,"Native population not duplicated");
   for(int i=0;i<expected.citizens.length;i++){
    var c=colony.getCitizenManager().getCivilian(expected.citizens[i]);
    h.assertTrue(c!=null && c.getHomeBuilding()!=null && record.buildings().containsKey(c.getHomeBuilding().getPosition()),"Native local housing persists");
        var expectedWork=REMOTE.offset(-24+i*16,0,0);
    var expectedHome=REMOTE.offset(-24+i*16,0,24);
    h.assertTrue(c.getHomeBuilding().getPosition().equals(expectedHome),"Exact housing assignment persists");
    h.assertTrue(c.getJob()!=null && c.getWorkBuilding()!=null && c.getWorkBuilding().getPosition().equals(expectedWork),"Exact workplace persists");
    h.assertTrue(i<2?c.getWorkBuilding().getModule(BuildingModules.FORESTER_WORK).hasAssignedCitizen(c):c.getWorkBuilding().getModule(BuildingModules.BUILDER_WORK).hasAssignedCitizen(c),"Exact native job module persists");
    int live=0;
    for(var entity:level.getAllEntities())if(entity instanceof com.minecolonies.api.entity.citizen.AbstractEntityCitizen citizenEntity && citizenEntity.getCitizenData()!=null && citizenEntity.getCitizenData().getColony().getID()==expected.colony && citizenEntity.getCitizenData().getId()==c.getId())live++;
    h.assertTrue(live==1,"Exactly one live representation per citizen, got "+live);
    h.assertTrue(c.getInventory().getStackInSlot(0).is(Items.SPRUCE_LOG) && c.getInventory().getStackInSlot(0).getCount()==7+i,"Native inventory persists");
    if(c.getEntity().isPresent())c.getEntity().get().setNoAi(true);
   }
   var report=NativeOutpostMembership.inspect(level,record);h.assertTrue(report.ready(),"Membership: "+report);
   if(phase.equals("verify"))OutpostSavedData.get(level).register(record.withState(OutpostRecord.State.PAUSED));
   else if(phase.equals("verify_paused"))h.assertTrue(record.state()==OutpostRecord.State.PAUSED,"Pause survives restart");
   else h.assertTrue(phase.equals("seed"),"Known phase only");
   h.assertTrue(NativeOutpostMembership.inspect(level,record).ready(),"Repeated inspection inert");
   rows.add(new ProbeResult("foundation_"+phase,ProbeResult.Outcome.PASS,"Six native Arcology huts, two foresters, one builder, local housing, claims and inventories preserved"));
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"NativeRestart",rows);h.succeed();
  }catch(Throwable e){
   rows.add(new ProbeResult("foundation_"+phase,ProbeResult.Outcome.FAIL,e.toString()));
   try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"NativeRestart",rows);}catch(Exception ignored){}
   h.fail(e.toString());
  }
 }
 static final class Expected extends SavedData {
  UUID id,owner;int colony;int[] citizens=new int[0];
  @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putUUID("id",id);t.putUUID("owner",owner);t.putInt("colony",colony);t.putIntArray("citizens",citizens);return t;}
  static Expected load(CompoundTag t,HolderLookup.Provider p){var e=new Expected();e.id=t.getUUID("id");e.owner=t.getUUID("owner");e.colony=t.getInt("colony");e.citizens=t.getIntArray("citizens");return e;}
 }
}
