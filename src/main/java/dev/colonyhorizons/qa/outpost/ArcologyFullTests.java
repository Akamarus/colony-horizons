package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.blueprints.v1.BlueprintUtil;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.google.gson.JsonParser;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Asset integration checks, deliberately separate from worker-operation evidence. */
@PrefixGameTestTemplate(false)
public final class ArcologyFullTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void every_blueprint_native_load(GameTestHelper h)throws Exception {
  var root=Path.of(System.getProperty("colonyhorizons.arcologyPath"));var rows=new ArrayList<ProbeResult>();
  var fixture=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
  List<Path> files;try(var stream=Files.walk(root)){files=stream.filter(p->p.toString().endsWith(".blueprint")).sorted().toList();}
  h.assertTrue(files.size()>=283,"Original pack coverage lost");  com.google.gson.JsonObject expectedAnchors;
  try(var resource=ArcologyFullTests.class.getResourceAsStream("/qa/arcology-building-anchors.json")){
   h.assertTrue(resource!=null,"Independent building inventory missing");
   expectedAnchors=JsonParser.parseReader(new java.io.InputStreamReader(resource,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  }
  h.assertTrue(expectedAnchors.size()==238,"Original building inventory coverage changed");
  for(var path:expectedAnchors.keySet())h.assertTrue(Files.isRegularFile(root.resolve(path)),"Expected building missing: "+path);
  var manifest=root.resolve("compatibility-manifest.json");
  if(Files.exists(manifest)){
   var json=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
   for(var key:json.getAsJsonObject("source_hashes").keySet())if(key.endsWith(".blueprint"))h.assertTrue(Files.isRegularFile(root.resolve(key)),"Missing source blueprint "+key);
   for(var key:json.getAsJsonArray("new_blueprints"))h.assertTrue(Files.isRegularFile(root.resolve(key.getAsString())),"Missing new blueprint "+key);
  }
  for(var file:files){String relative=root.relativize(file).toString().replace('\\','/');
   try {
    var tag=NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024));
    for(var element:tag.getList("palette",Tag.TAG_COMPOUND)){
     var entry=(CompoundTag)element;var id=ResourceLocation.parse(entry.getString("Name"));
     h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Missing original block "+id);
     var block=BuiltInRegistries.BLOCK.get(id);var properties=entry.getCompound("Properties");
     for(var key:properties.getAllKeys()){
      var property=block.getStateDefinition().getProperty(key);
      h.assertTrue(property!=null && property.getValue(properties.getString(key)).isPresent(),"Invalid block state "+id+" "+key+"="+properties.getString(key));
     }
    }
    for(var mod:tag.getList("required_mods",Tag.TAG_STRING))h.assertTrue(ModList.get().isLoaded(mod.getAsString()),"Missing required mod "+mod.getAsString());
    var bp=BlueprintUtil.readBlueprintFromNBT(tag,h.getLevel().registryAccess());h.assertTrue(bp!=null,"Native decode failed");
    var primary=bp.getPrimaryBlockOffset();h.assertTrue(inBounds(primary,bp.getSizeX(),bp.getSizeY(),bp.getSizeZ()),"Primary outside bounds");
    String anchor=BuiltInRegistries.BLOCK.getKey(bp.getBlockState(primary).getBlock()).toString();
    if(expectedAnchors.has(relative)){
     h.assertTrue(anchor.equals(expectedAnchors.get(relative).getAsString()),"Wrong primary hut for "+relative+": "+anchor);
     h.assertTrue(bp.getBlockState(primary).getBlock() instanceof AbstractBlockHut<?>,"Expected building has no primary hut");
    }
    if(bp.getBlockState(primary).getBlock() instanceof AbstractBlockHut<?> hut){
     var entry=hut.getBuildingEntry();h.assertTrue(entry!=null,"Hut has no registered building");
     var matcher=java.util.regex.Pattern.compile("([0-9]+)[.]blueprint$").matcher(relative);
     h.assertTrue(matcher.find(),"Building blueprint lacks upgrade level");
     int buildingLevel=Integer.parseInt(matcher.group(1));
          var hutPos=fixture.center().offset(3,0,3);
     h.getLevel().setBlockAndUpdate(hutPos,bp.getBlockState(primary));
     var tile=(com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding)h.getLevel().getBlockEntity(hutPos);
     h.assertTrue(tile!=null,"Native hut block entity missing");
     var tileData=bp.getBlockInfoAsMap().get(primary).getTileEntityData();
     h.assertTrue(tileData!=null,"Primary hut data missing");
     tile.loadWithComponents(tileData.copy(),h.getLevel().registryAccess());tile.setColony(fixture.colony());
     var building=entry.produceBuilding(hutPos,fixture.colony());
     h.assertTrue(buildingLevel>=1 && buildingLevel<=building.getMaxBuildingLevel(),"Unsupported native upgrade level "+buildingLevel);
     building.setRotationMirror(RotationMirror.NONE);building.setBuildingLevel(buildingLevel);building.setStructurePack("Arcology Horizons");building.setBlueprintPath(relative);
          if(building instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingArchery archery){
      var stand=archery.getRandomShootingStandPosition(net.minecraft.util.RandomSource.create(0));
      h.assertTrue(stand!=null && BuiltInRegistries.BLOCK.getKey(bp.getBlockState(stand.subtract(hutPos).offset(primary)).getBlock()).toString().equals("structurize:blocktagsubstitution"),"Native archery stand missing or misplaced");
      int targetCount=0;
      for(var info:bp.getBlockInfoAsList())if(bp.getBlockState(info.getPos()).is(net.minecraft.world.level.block.Blocks.TARGET)){
       archery.registerBlockPosition(net.minecraft.world.level.block.Blocks.TARGET,hutPos.subtract(primary).offset(info.getPos()),h.getLevel());targetCount++;
      }
      h.assertTrue(targetCount==(buildingLevel==5?6:buildingLevel),"Archery target capacity mismatch");
      h.assertTrue(archery.getRandomShootingTarget(net.minecraft.util.RandomSource.create(0))!=null,"Native archery has no shooting target");
     }
     if(building instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingCombatAcademy academy){
      var origin=fixture.center().offset(16,2,0);pasteFixture(h,bp,origin);
      try{
       for(var info:bp.getBlockInfoAsList())academy.registerBlockPosition(bp.getBlockState(info.getPos()),origin.offset(info.getPos()),h.getLevel());
       var targets=academy.serializeNBT(h.getLevel().registryAccess()).getList(com.minecolonies.api.util.constant.NbtTagConstants.TAG_COMBAT_TARGET,net.minecraft.nbt.Tag.TAG_COMPOUND);
       h.assertTrue(targets.size()==buildingLevel,"Native Academy dummy capacity mismatch");
       var target=academy.getRandomCombatTarget(net.minecraft.util.RandomSource.create(0));
       h.assertTrue(target!=null&&h.getLevel().getBlockState(target).getBlock() instanceof net.minecraft.world.level.block.HayBlock&&h.getLevel().getBlockState(target.above()).getBlock() instanceof net.minecraft.world.level.block.CarvedPumpkinBlock,"Native Academy dummy missing or misplaced");
      }finally{clearFixture(h,bp,origin);}
     }
     if(building instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner miner){
      h.assertTrue(miner.getLadderLocation()!=null && miner.getCobbleLocation()!=null,"Native miner cannot find its shaft markers");
      h.assertTrue(bp.getBlockState(miner.getLadderLocation().subtract(hutPos).offset(primary)).is(net.minecraft.world.level.block.Blocks.LADDER),"Native ladder marker points at wrong block");
     }
     if(building instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingNetherWorker nether){
      h.assertTrue(nether.getPortalLocation()!=null,"Native Nether Mine cannot find portal");
      h.assertTrue(bp.getBlockState(nether.getPortalLocation().below().subtract(hutPos).offset(primary)).is(net.minecraft.world.level.block.Blocks.OBSIDIAN),"Native portal marker misses frame");      var origin=fixture.center().offset(16,2,0);
      pasteFixture(h,bp,origin);
      var portal=origin.offset(nether.getPortalLocation().subtract(hutPos).offset(primary));
      var shape=net.minecraft.world.level.portal.PortalShape.findEmptyPortalShape(h.getLevel(),portal,Direction.Axis.X);
      h.assertTrue(shape.isPresent(),"Nether Mine portal frame cannot light at tagged location");
      shape.orElseThrow().createPortalBlocks();
      h.assertTrue(h.getLevel().getBlockState(portal).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL),"Portal did not open");
      clearFixture(h,bp,origin);
     }
     if(building instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation plantation){
      plantation.onPlacement();
      var fields=fixture.colony().getServerBuildingManager().getBuildingExtensions(e->e.getPosition().equals(hutPos));      var origin=fixture.center().offset(16,2,0);pasteFixture(h,bp,origin);
      try{
       for(var extension:fields)if(extension instanceof com.minecolonies.core.colony.buildingextensions.PlantationField field){
        var crop=switch(field.getModule().getWorkTag()){
         case "sugar"->net.minecraft.world.level.block.Blocks.SUGAR_CANE;
         case "cactus"->net.minecraft.world.level.block.Blocks.CACTUS;
         case "bamboo"->net.minecraft.world.level.block.Blocks.BAMBOO;
         default->null;
        };
        if(crop!=null)for(var ground:field.getWorkingPositions()){
         var planting=origin.offset(ground.subtract(hutPos).offset(primary)).above();
         h.assertTrue(h.getLevel().getBlockState(planting).isAir() || h.getLevel().getBlockState(planting).is(crop),"Planting space occupied: "+planting);
         int harvestHeight=field.getModule().getWorkTag().equals("bamboo")?6:3;
          for(int dy=1;dy<harvestHeight;dy++)h.assertTrue(h.getLevel().getBlockState(planting.above(dy)).isAir(),"Blocked native harvest height: "+planting.above(dy));
          h.assertTrue(crop.defaultBlockState().canSurvive(h.getLevel(),planting),"Native "+field.getModule().getWorkTag()+" cannot survive at "+ground.subtract(hutPos).offset(primary));
        }
       }
      }finally{clearFixture(h,bp,origin);}
      for(var crop:buildingLevel>=3?List.of("sugar","cactus","bamboo"):buildingLevel>=2?List.of("sugar","cactus"):List.of("sugar"))
       h.assertTrue(fields.stream().anyMatch(e->e instanceof com.minecolonies.core.colony.buildingextensions.PlantationField f && crop.equals(f.getModule().getWorkTag()) && !f.getWorkingPositions().isEmpty()),"Native plantation did not register "+crop);
     }
     var saved=building.serializeNBT(h.getLevel().registryAccess());
     var restored=entry.produceBuilding(building.getPosition(),fixture.colony());restored.deserializeNBT(h.getLevel().registryAccess(),saved);
     h.assertTrue(restored.getBuildingLevel()==buildingLevel && relative.equals(restored.getBlueprintPath()) && "Arcology Horizons".equals(restored.getStructurePack()),"Native building metadata did not survive serialization");
    }
    for(var element:tag.getList("tile_entities",Tag.TAG_COMPOUND)){
     var tile=(CompoundTag)element;var tilePos=new BlockPos(tile.getInt("x"),tile.getInt("y"),tile.getInt("z"));
     for(var raw:tile.getCompound("blueprintDataProvider").getList("posTagMap",Tag.TAG_COMPOUND)){
      var pos=((CompoundTag)raw).getCompound("tagPos");var absolute=tilePos.offset(pos.getInt("x"),pos.getInt("y"),pos.getInt("z"));
      h.assertTrue(inBounds(absolute,bp.getSizeX(),bp.getSizeY(),bp.getSizeZ()),"Work marker outside building: "+absolute);
     }
    }
    // Exercise native block-entity decoding and all native rotations and mirrors, not an invented coordinate transform.
    for(var info:bp.getBlockInfoAsList())if(info.getTileEntityData()!=null)h.assertTrue(bp.getBlockEntity(info.getPos())!=null,"Block entity failed to decode at "+info.getPos()+": "+info.getTileEntityData().getString("id"));
    for(var rotation:RotationMirror.values()){
     var rotated=BlueprintUtil.readBlueprintFromNBT(tag.copy(),h.getLevel().registryAccess());rotated.setRotationMirror(rotation,h.getLevel());
     h.assertTrue(BuiltInRegistries.BLOCK.getKey(rotated.getBlockState(rotated.getPrimaryBlockOffset()).getBlock()).toString().equals(anchor),"Rotation loses primary block: "+rotation);
    }
    rows.add(new ProbeResult("asset:"+relative,ProbeResult.Outcome.PASS,"Native palette/properties/mods/decode/block-entities/rotations; hut registry/level/metadata roundtrip where applicable; worker operation not tested"));
   }catch(Throwable e){rows.add(new ProbeResult("asset:"+relative,ProbeResult.Outcome.FAIL,e.toString()));}finally{h.getLevel().setBlockAndUpdate(fixture.center().offset(3,0,3),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());}
  }
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyFull",rows);
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Full pack native asset failures: "+rows.stream().filter(r->r.outcome()!=ProbeResult.Outcome.PASS).limit(12).toList());h.succeed();
 }
  static void pasteFixture(GameTestHelper h,com.ldtteam.structurize.blueprints.v1.Blueprint bp,BlockPos origin){
  for(var info:bp.getBlockInfoAsList()){
   var state=bp.getBlockState(info.getPos());
   // Native block geometry test only; hut metadata is exercised separately above.
   if(state.getBlock() instanceof AbstractBlockHut<?>)state=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
   h.getLevel().setBlock(origin.offset(info.getPos()),state,18);
  }
 }
 static void clearFixture(GameTestHelper h,com.ldtteam.structurize.blueprints.v1.Blueprint bp,BlockPos origin){
  for(int x=0;x<bp.getSizeX();x++)for(int y=0;y<bp.getSizeY();y++)for(int z=0;z<bp.getSizeZ();z++)h.getLevel().setBlock(origin.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),18);
 }
 static boolean inBounds(BlockPos p,int x,int y,int z){return p.getX()>=0 && p.getY()>=0 && p.getZ()>=0 && p.getX()<x && p.getY()<y && p.getZ()<z;}
}
