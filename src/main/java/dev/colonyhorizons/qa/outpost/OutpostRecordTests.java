package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.outpost.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class OutpostRecordTests {
 public static OutpostRecord sample(UUID id,int colony,int offset,Set<Integer> workers) {
  var a=new BlockPos(offset,100,0);var b=a.offset(0,10,0);
  var type=ResourceLocation.parse("minecolonies:blockhutlumberjack");
  return new OutpostRecord(id,colony,Level.OVERWORLD,"Timber",Set.of(new net.minecraft.world.level.ChunkPos(a)),
   new BoundingBox(offset,90,0,offset+15,130,15),new BoundingBox(offset+16,90,0,offset+31,130,15),
   Map.of(a,new OutpostRecord.BuildingRef(a,type,"Arcology","fundamentals/lumberjack1.blueprint",new BoundingBox(offset,100,0,offset+5,105,5)),
    b,new OutpostRecord.BuildingRef(b,type,"Arcology","fundamentals/lumberjack1.blueprint",new BoundingBox(offset,110,0,offset+5,115,5))),
   Set.of(1,2,3,4),workers,Optional.empty(),OutpostRecord.State.PLANNED,List.of("Missing terminal"));
 }
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=600)
 public static void record_contract(GameTestHelper h) { h.runAfterDelay(40,()->{try{run(h);}catch(Exception e){throw new RuntimeException(e);}}); }
 public static void run(GameTestHelper h)throws Exception {
  var rows=new ArrayList<ProbeResult>();
  try {
   var d=new OutpostSavedData(Level.OVERWORLD);var r=sample(UUID.randomUUID(),1,0,Set.of(1,2));d.register(r);
   var loaded=OutpostSavedData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess(),Level.OVERWORLD);
   h.assertTrue(loaded.find(r.id()).orElseThrow().buildings().size()==2 && loaded.find(r.id()).orElseThrow().workerIds().equals(Set.of(1,2)),"Roundtrip preserves memberships");
   pass(rows,"multiple_huts_roundtrip");
   h.assertTrue(d.find(r.id()).orElseThrow().buildings().size()==2,"Repeated role distinct");pass(rows,"repeated_roles_distinct");
   reject(()->d.register(sample(UUID.randomUUID(),1,64,Set.of(1))),h);pass(rows,"worker_membership_exclusive");
   d.register(sample(UUID.randomUUID(),2,64,Set.of(1)));pass(rows,"different_colonies_same_citizen_id");
   reject(()->new OutpostSavedData(Level.NETHER).register(r),h);pass(rows,"dimension_mismatch_rejected");
   h.assertTrue(d.find(r.id()).isPresent(),"Stacked disjoint boxes valid");pass(rows,"stacked_nonoverlap");
   var refs=new HashMap<>(r.buildings());var anchor=new BlockPos(1,101,1);
   refs.put(anchor,new OutpostRecord.BuildingRef(anchor,ResourceLocation.parse("minecolonies:blockhutbuilder"),"Arcology","fundamentals/builder1.blueprint",new BoundingBox(1,101,1,4,104,4)));
   reject(()->d.register(new OutpostRecord(r.id(),1,r.dimension(),r.name(),r.approvedChunks(),r.development(),r.forestry(),refs,r.residentIds(),r.workerIds(),r.terminal(),r.state(),r.blockers())),h);
   pass(rows,"conflicting_volume_rejected");
   int before=d.all().size();reject(()->d.register(sample(UUID.randomUUID(),1,0,Set.of(3))),h);
   h.assertTrue(d.all().size()==before && d.find(r.id()).orElseThrow().buildings().size()==2,"Reject atomic");pass(rows,"invalid_record_atomic");
   var tag=d.save(new CompoundTag(),h.getLevel().registryAccess());tag.putInt("schema",99);
   reject(()->OutpostSavedData.load(tag,h.getLevel().registryAccess(),Level.OVERWORLD),h);
   pass(rows,"unknown_schema_rejected");
   var valid=d.save(new CompoundTag(),h.getLevel().registryAccess());
   for(String key:List.of("outposts","schema","dimension")){
    var malformed=valid.copy();malformed.put(key,new CompoundTag());
    reject(()->OutpostSavedData.load(malformed,h.getLevel().registryAccess(),Level.OVERWORLD),h);
   }
   for(String key:List.of("buildings","residents","workers","blockers","terminal")){
    var malformed=valid.copy();malformed.getList("outposts",Tag.TAG_COMPOUND).getCompound(0).putString(key,"invalid");
    reject(()->OutpostSavedData.load(malformed,h.getLevel().registryAccess(),Level.OVERWORLD),h);
   }
   var missing=valid.copy();missing.remove("outposts");
   reject(()->OutpostSavedData.load(missing,h.getLevel().registryAccess(),Level.OVERWORLD),h);
   var malformedDisk=valid.copy();malformedDisk.putString("outposts","invalid");
   var disk=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(h.getLevel().dimension(),h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/colonyhorizons_outposts.dat");
   java.nio.file.Files.createDirectories(disk.getParent());
   var outer=new CompoundTag();outer.put("data",malformedDisk);net.minecraft.nbt.NbtIo.writeCompressed(outer,disk);
   var original=java.nio.file.Files.readAllBytes(disk);boolean failedClosed=false;
   try{OutpostSavedData.get(h.getLevel()).register(sample(UUID.randomUUID(),7,256,Set.of(1)));}catch(IllegalStateException e){failedClosed=true;}
   h.getLevel().getDataStorage().save();
   h.assertTrue(failedClosed && java.util.Arrays.equals(original,java.nio.file.Files.readAllBytes(disk)),"Malformed on-disk state must not become an empty writable index");
   pass(rows,"malformed_data_rejected");
   NativeMembershipTests.verify(h,rows);
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Records",rows);h.succeed();
  }catch(Throwable e){rows.add(new ProbeResult("records_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Records",rows);throw e;}
 }
 static void reject(Runnable action,GameTestHelper h){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Invalid input must reject");}
 static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Contract verified"));}
}
