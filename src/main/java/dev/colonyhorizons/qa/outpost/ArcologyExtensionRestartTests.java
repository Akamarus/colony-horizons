package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Real clean restart of new buildings/fields and paused staff; verify never reseeds. */
@PrefixGameTestTemplate(false)
public final class ArcologyExtensionRestartTests {
 static final String KEY="arcology_extension_restart";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=18000)
 public static void extensions_restart(GameTestHelper h){
  for(int x=29;x<=40;x++)for(int z=-2;z<=8;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{
   if(System.getProperty("colonyhorizons.probePhase","seed").equals("seed")){
    var setup=new ArcologyCropExtensionTests.Run(h);setup.afterVerification=r->seed(h,r);setup.start();
   }else verify(h,0);
  });
 }
 static void seed(GameTestHelper h,ArcologyCropExtensionTests.Run fields){try{
  var level=h.getLevel();var colony=fields.fixture.colony();var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fields.fixture;
  setup.owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(fields.fixture.ownerId(),"CropFieldProbe"));
  com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)colony,true,new BlockPos(544,16,48),9,level,false);
  for(int x=495;x<=625;x++)for(int z=50;z<=110;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(),18);
  var anchors=List.of(new BlockPos(512,16,64),new BlockPos(544,16,64),new BlockPos(576,16,64),new BlockPos(608,16,64));
  var paths=List.of("military/stable5.blueprint","military/gatehouse3.blueprint","fundamentals/simplequarry1.blueprint","fundamentals/mediumquarry1.blueprint");
  for(int i=0;i<4;i++)setup.place(paths.get(i),anchors.get(i),true);
  var home=setup.place("fundamentals/citizen5.blueprint",new BlockPos(512,16,96),true);
  var workerIds=new int[2];var entityIds=new UUID[2];
  for(int i=0;i<2;i++){
   var worker=fields.fixture.createCitizen();check(worker!=null&&worker.getEntity().isPresent(),"Paused extension worker spawn");
   var hut=colony.getServerBuildingManager().getBuilding(anchors.get(i));
   check((i==0?hut.getModule(BuildingModules.STABLEMASTER_WORK):hut.getModule(BuildingModules.KNIGHT_GATE_WORK)).assignCitizen(worker),"Native extension staffing");
   if(i==0)check(home.getModule(BuildingModules.LIVING).assignCitizen(worker),"Stablemaster support home assignment");
   else check(worker.getHomeBuilding()==hut,"Native guard must live at the gatehouse");
   worker.setPaused(true);worker.setSaturation(20);worker.getEntity().orElseThrow().setPos(514.5,16,92.5);workerIds[i]=worker.getId();entityIds[i]=worker.getEntity().orElseThrow().getUUID();
  }
  for(int i=0;i<3;i++)check(fields.plantation.getModule(BuildingModules.PLANTATION_FIELDS).assignExtension(fields.fields.get(i)),"Persisted crop assignment");
  var expected=new Expected();expected.owner=fields.fixture.ownerId();expected.colony=colony.getID();expected.population=colony.getCitizenManager().getCurrentCitizenCount();expected.huts=anchors;expected.fields=List.copyOf(fields.anchors);expected.workers=workerIds;expected.entities=entityIds;expected.signature=signature(colony,level,expected);
  level.getDataStorage().set(KEY,expected);expected.setDirty();level.getServer().getProfileCache().save();
  report(h,"seed","Prebuilt Stable5, Gatehouse3, both Quarry1 surfaces and ten native crop fields; first three fields assigned to Plantation5. Native stablemaster and knight paused, housed and identified by citizen ID/entity UUID. Saved exact tags, beds, stall locations, field ownership/working positions and gate terminal records for independent restart verification. Construction/production tested separately.");
 }catch(Throwable e){h.fail(e.toString());}}
 static void verify(GameTestHelper h,int waited){try{
  var level=h.getLevel();var expected=level.getDataStorage().get(FACTORY,KEY);check(expected!=null,"Missing saved fixture; must not reseed");
  var colony=IColonyManager.getInstance().getColonyByDimension(expected.colony,level.dimension());check(colony!=null,"Colony not restored");
  if(waited==0){
   var profile=new GameProfile(expected.owner,"CropFieldProbe");var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   var owner=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(544,16,48);
   colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);
  }
  check(IColonyManager.getInstance().getIColonyByOwner(level,expected.owner)==colony,"Owner changed");check(colony.getCitizenManager().getCurrentCitizenCount()==expected.population,"Population changed");
  check(expected.signature.equals(signature(colony,level,expected)),"Extension metadata/ownership/terminal changed on restart");
  boolean ready=true;
  for(int i=0;i<2;i++){
   var c=colony.getCitizenManager().getCivilian(expected.workers[i]);check(c!=null&&c.isPaused(),"Saved worker/pause lost");
   check(c.getWorkBuilding()==colony.getServerBuildingManager().getBuilding(expected.huts.get(i)),"Saved workplace lost");
   var work=colony.getServerBuildingManager().getBuilding(expected.huts.get(i));
   check((i==0?work.getModule(BuildingModules.STABLEMASTER_WORK):work.getModule(BuildingModules.KNIGHT_GATE_WORK)).getAssignedCitizen().contains(c),"Native worker module membership lost");
   check(c.getHomeBuilding()!=null&&c.getHomeBuilding().getPosition().equals(i==0?new BlockPos(512,16,96):expected.huts.get(1)),"Saved home lost");
   if(c.getEntity().isEmpty())ready=false;else check(c.getEntity().orElseThrow().isAlive()&&c.getEntity().orElseThrow().getUUID().equals(expected.entities[i]),"Original worker entity identity lost");
  }
  if(!ready){check(waited<12000,"Native worker reload timeout");h.runAfterDelay(20,()->verify(h,waited+20));return;}
  report(h,"verify","Real second server process restored original colony/owner, population, Stable5/Gatehouse3/quarry surfaces, all ten field types/tags/positions and first-three field ownership. Exact marker, bed, stall and terminal signature retained; same two paused worker IDs/entity UUIDs, workplaces and home. No rebuilding, reassignment, respawn or field registration in verify. Readiness wait="+waited+" ticks. Does not certify active production, mid-action saves or crash recovery.");
 }catch(Throwable e){h.fail(e.toString());}}
 static String signature(IColony c,ServerLevel level,Expected e){
  var result=new ArrayList<String>();int i=0;for(var p:e.huts){
   var b=c.getServerBuildingManager().getBuilding(p);check(b!=null,"Missing extension hut "+p);int wanted=i==0?5:i==1?3:1;check(b.getBuildingLevel()==wanted&&b.getStructurePack().equals("Arcology Horizons"),"Extension level/style changed");
   result.add(p+":"+b.getClass().getName()+":"+b.getBuildingLevel()+":"+b.getBlueprintPath());
   for(String tag:List.of("knight","archer","gate","patrol_point","groundlevel"))result.add(p+":"+tag+":"+sorted(b.getLocationsFromTag(tag)));
   if(b instanceof BuildingStable stable)result.add("stalls="+sorted(stable.stallPositions()));
   if(i<2)result.add(p+":beds="+sorted(b.getModule(BuildingModules.BED).getRegisteredBlocks()));
   if(i>=2)check(b.isInBuilding(p),"Reloaded quarry excludes its hut");i++;
  }
  var terminals=c.getConnectionManager().serializeNBT(level.registryAccess()).getList("gatehouses",Tag.TAG_COMPOUND);check(terminals.size()==1,"Missing/duplicate gate terminal");result.add("terminal="+terminals.getCompound(0));
  for(var p:e.fields){
   var list=c.getServerBuildingManager().getBuildingExtensions(f->f.getPosition().equals(p));check(list.size()==1&&list.getFirst() instanceof PlantationField,"Missing/duplicate native field "+p);var field=(PlantationField)list.getFirst();
   result.add(p+":"+field.getModule().getFieldTag()+":"+field.getModule().getWorkTag()+":"+field.getBuildingId()+":"+sorted(field.getWorkingPositions()));
  }
  Collections.sort(result);return String.join("\n",result);
 }
 static String sorted(Collection<BlockPos> p){return p.stream().map(BlockPos::toShortString).sorted().toList().toString();}
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 static void report(GameTestHelper h,String phase,String evidence)throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyExtensionRestart",List.of(new ProbeResult("extensions_"+phase,ProbeResult.Outcome.PASS,evidence)));h.succeed();}
 static final class Expected extends SavedData {
  UUID owner;int colony,population;List<BlockPos> huts,fields;int[] workers;UUID[] entities;String signature;
  public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putUUID("owner",owner);t.putInt("colony",colony);t.putInt("population",population);t.putLongArray("huts",huts.stream().mapToLong(BlockPos::asLong).toArray());t.putLongArray("fields",fields.stream().mapToLong(BlockPos::asLong).toArray());t.putIntArray("workers",workers);for(int i=0;i<2;i++)t.putUUID("entity"+i,entities[i]);t.putString("signature",signature);return t;}
  static Expected load(CompoundTag t,HolderLookup.Provider p){var e=new Expected();e.owner=t.getUUID("owner");e.colony=t.getInt("colony");e.population=t.getInt("population");e.huts=Arrays.stream(t.getLongArray("huts")).mapToObj(BlockPos::of).toList();e.fields=Arrays.stream(t.getLongArray("fields")).mapToObj(BlockPos::of).toList();e.workers=t.getIntArray("workers");e.entities=new UUID[]{t.getUUID("entity0"),t.getUUID("entity1")};e.signature=t.getString("signature");return e;}
 }
}
