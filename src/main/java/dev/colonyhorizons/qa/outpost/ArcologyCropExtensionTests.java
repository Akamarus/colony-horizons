package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.core.blocks.BlockPlantationField;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import com.minecolonies.core.research.GlobalResearchEffect;
import com.minecolonies.core.tileentities.TileEntityPlantationField;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Native registration/settling/assignment only; no crop production is certified. */
@PrefixGameTestTemplate(false)
public final class ArcologyCropExtensionTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void fields(GameTestHelper h){
  for(int x=29;x<=39;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->new Run(h).start());
 }
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final String[] names={"cocoa","vine","kelp","seagrass","seapickle","glowberry","weepingvine","twistingvine","crimson","warped"};
  final String[] fieldTags={"cocoa_field","vine_field","kelp_field","seagrass_field","seapickle_field","glowb_field","weepv_field","twistv_field","crimsonp_field","warpedp_field"};
  final String[] workTags={"cocoa","vine","kelp","seagrass","seapickle","glowb_vine","weepv_vine","twistv_vine","crimsonp_ground","warpedp_ground"};
  final String[] registry={"plantation_cocoa_beans","plantation_vines","plantation_kelp","plantation_seagrass","plantation_sea_pickles","plantation_glowberries","plantation_weeping_vines","plantation_twisting_vines","plantation_crimson_plants","plantation_warped_plants"};
  final List<BlockPos> origins=new ArrayList<>(),anchors=new ArrayList<>();final List<PlantationField> fields=new ArrayList<>();
  final List<ProbeResult> rows=new ArrayList<>();OutpostProbeFixture fixture;BuildingPlantation plantation;
  java.util.function.Consumer<Run> afterVerification;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   Files.createDirectories(output);level.setDayTime(6000);level.setWeatherParameters(10000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
   level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=624;x++)for(int z=-18;z<=58;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"CropFieldProbe");level.getServer().getProfileCache().add(profile);
   fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;
   var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(544,16,16),7,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   plantation=(BuildingPlantation)setup.place("agriculture/horticulture/plantation5.blueprint",new BlockPos(480,16,32),true);
   var module=plantation.getFirstModuleOccurance(BuildingPlantation.PlantationFieldsModule.class);module.setAssignManually(true);
   check(module.getMaxConcurrentPlants()==3,"Pinned level-five crop cap changed");
   check(module.getOwnedExtensions().isEmpty(),"Fixture unexpectedly owns preexisting legacy fields");
   // Disclosed established-colony research fixture. The native research/capacity
   // checks remain active; no extension ownership or worker AI is injected.
   for(String effect:List.of("plantationjungle","plantationsea","plantationexotic","plantationnether"))
    fixture.colony().getResearchManager().getResearchEffects().applyEffect(new GlobalResearchEffect(ResourceLocation.fromNamespaceAndPath("minecolonies","effects/"+effect),"QA established research","",1,1));
   for(int i=0;i<names.length;i++){
    String path="infrastructure/plantationfields/"+names[i]+"1.blueprint";var bp=setup.blueprint(path);
    BlockPos origin=new BlockPos(504+(i%5)*24,16-bp.getPrimaryBlockOffset().getY(),-2+(i/5)*24);origins.add(origin);
    BlockPos anchor=origin.offset(bp.getPrimaryBlockOffset());anchors.add(anchor);
    check(bp.getSizeX()==10&&bp.getSizeY()==6&&bp.getSizeZ()==10,"Field dimensions "+names[i]);
    for(var info:bp.getBlockInfoAsList()){
     var p=origin.offset(info.getPos());var state=bp.getBlockState(info.getPos());level.setBlock(p,state,18);
     var tile=level.getBlockEntity(p);if(tile!=null&&info.getTileEntityData()!=null)tile.loadWithComponents(info.getTileEntityData().copy(),level.registryAccess());
    }
    var state=level.getBlockState(anchor);check(state.getBlock() instanceof BlockPlantationField,"Wrong field anchor "+names[i]);
    check(level.getBlockEntity(anchor) instanceof TileEntityPlantationField,"Wrong field tile "+names[i]);
    var tile=(TileEntityPlantationField)level.getBlockEntity(anchor);
    check(tile.getPlantationFieldTypes().size()==1,"Ambiguous field category "+names[i]);
    check(tile.getPlantationFieldTypes().iterator().next().getRegistryName().equals(ResourceLocation.fromNamespaceAndPath("minecolonies",registry[i])),"Wrong extension registry "+names[i]);
    check(new HashSet<>(tile.getWorkingPositions(workTags[i])).equals(worldPositions(i,tagged(i))),"Serialized work tags mismatch "+names[i]);
    check(tile.getWorkingPositions(fieldTags[i]).equals(List.of(anchor)),"Field identity marker mismatch "+names[i]);
    // BlockPlantationField is not an AbstractBlockHut and has no hut build-tool
    // hook. This is its real placement registration hook, after full fixture load.
    ((BlockPlantationField)state.getBlock()).setPlacedBy(level,anchor,state,setup.owner,new ItemStack(state.getBlock()));
    var found=fixture.colony().getServerBuildingManager().getBuildingExtensions(f->f.getPosition().equals(anchor));
    check(found.size()==1&&found.getFirst() instanceof PlantationField,"Native placement failed to register "+names[i]);
    var field=(PlantationField)found.getFirst();fields.add(field);
    check(field.getModule().getFieldTag().equals(fieldTags[i])&&field.getModule().getWorkTag().equals(workTags[i]),"Wrong native module "+names[i]);
    check(new HashSet<>(field.getWorkingPositions()).equals(worldPositions(i,derived(i))),"Derived native working positions "+names[i]);
    check(field.isValidPlacement(fixture.colony())&&!field.isTaken(),"Invalid/owned field "+names[i]);
    for(var info:bp.getBlockInfoAsList()){
     var p=origin.offset(info.getPos());level.updateNeighborsAt(p,level.getBlockState(p).getBlock());
     if(level.getBlockState(p).is(Blocks.WATER))level.scheduleTick(p,Fluids.WATER,1);
    }
   }
   h.runAfterDelay(240,()->{try{verify();}catch(Throwable e){fail(e);}});
  }catch(Throwable e){fail(e);}}
  List<BlockPos> tagged(int i){
   List<BlockPos> p=new ArrayList<>();
   if(i==0)return List.of(new BlockPos(3,1,5),new BlockPos(6,1,5));
   if(i==1){for(int y=1;y<=2;y++)for(int z=3;z<=7;z++)p.add(new BlockPos(3,y,z));}
   else if(i==2){for(int x:new int[]{2,6})for(int z=4;z<=6;z++)p.add(new BlockPos(x,0,z));}
   else if(i==3){for(int x:new int[]{2,3,6,7})for(int z=4;z<=6;z++)p.add(new BlockPos(x,0,z));}
   else if(i==4){for(int x:new int[]{2,3,6,7})for(int z:new int[]{4,6})p.add(new BlockPos(x,0,z));}
   else if(i<=7){for(int x:new int[]{3,6})for(int z:new int[]{4,7})p.add(new BlockPos(x,i==7?0:4,z));}
   else{for(int x=3;x<=5;x++)for(int z=4;z<=6;z++)p.add(new BlockPos(x,0,z));}
   return p;
  }
  List<BlockPos> derived(int i){if(i!=0)return tagged(i);Set<BlockPos> result=new HashSet<>();for(var p:tagged(i))for(var d:Direction.Plane.HORIZONTAL)result.add(p.relative(d));return new ArrayList<>(result);}
  Set<BlockPos> worldPositions(int i,List<BlockPos> local){Set<BlockPos> result=new HashSet<>();for(var p:local)result.add(origins.get(i).offset(p));return result;}
  void verify()throws Exception{
   var module=plantation.getFirstModuleOccurance(BuildingPlantation.PlantationFieldsModule.class);
   // Prove the real cap remains enforced: three distinct crops fit, fourth does
   // not. Then release via the same native API used for field reassignment.
   for(int i=0;i<3;i++)check(module.assignExtension(fields.get(i)),"Native capacity fixture assignment "+names[i]);
   check(module.getOwnedExtensions().size()==3&&!module.assignExtension(fields.get(3)),"Native distinct-crop/field cap bypassed");
   for(int i=0;i<3;i++)module.freeExtension(fields.get(i));
   check(module.getOwnedExtensions().isEmpty(),"Native release failed after capacity check");
   for(int i=0;i<names.length;i++){
    var field=fields.get(i);check(field.isValidPlacement(fixture.colony()),"Field disappeared while settling "+names[i]);
    check(new HashSet<>(field.getWorkingPositions()).equals(worldPositions(i,derived(i))),"Working positions changed "+names[i]);
    environment(i);
    check(module.assignExtension(field),"Native researched L5 assignment rejected "+names[i]);
    check(plantation.getPosition().equals(field.getBuildingId())&&module.getOwnedExtensions().size()==1&&module.getOwnedExtensions().contains(field),"Native owner mismatch "+names[i]);
    module.freeExtension(field);check(!field.isTaken()&&module.getOwnedExtensions().isEmpty(),"Native release rejected "+names[i]);
    String evidence="Prebuilt candidate loaded; real BlockPlantationField.setPlacedBy registered "+registry[i]+" with exact field/work tags and "+field.getWorkingPositions().size()+" derived positions. 240 native ticks and scheduled neighbor/fluid updates preserved required substrate, source water and growth space. Prospective crop states passed native canSurvive without placing plants. Native L5 assignExtension/freeExtension succeeded sequentially; three distinct fields accepted and fourth rejected. Established research and prebuilt L5 Plantation are disclosed fixtures. NO worker planting, growth, harvest/output, navigation, builder completion, rotation or restart certified.";
    rows.add(new ProbeResult(names[i]+"_extension_contract",ProbeResult.Outcome.PASS,evidence));ProbeReport.write(output,"ArcologyCropExtensions",rows);
    Files.writeString(output.resolve("crop-extension-diagnostics.txt"),names[i]+" anchor="+anchors.get(i)+" registry="+registry[i]+" work="+field.getWorkingPositions()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   }
   if(afterVerification!=null)afterVerification.accept(this);else h.succeed();
  }
  void environment(int i){
   var origin=origins.get(i);
   if(i>=2&&i<=4)for(int x:new int[]{2,3,6,7})for(int z=4;z<=6;z++){
    var bottom=origin.offset(x,0,z);check(level.getBlockState(bottom).is(i==4?Blocks.BRAIN_CORAL_BLOCK:Blocks.STONE_BRICKS),"Aquatic substrate lost "+names[i]);
    for(int y=1;y<=2;y++){var fluid=level.getFluidState(origin.offset(x,y,z));check(fluid.is(Fluids.WATER)&&fluid.isSource()&&fluid.getAmount()==8,"Water did not settle to source "+names[i]);}
    check(level.getBlockState(origin.offset(x,3,z)).isAir()&&level.getBlockState(origin.offset(x,4,z)).isAir(),"Aquatic surface obstructed");
   }
   for(var local:derived(i)){
    var p=origin.offset(local);BlockPos planted=p;BlockState prospective;
    if(i==0){
     check(level.getBlockState(p).isAir(),"Cocoa derived plot occupied");Direction facing=null;for(var d:Direction.Plane.HORIZONTAL)if(level.getBlockState(p.relative(d)).is(Blocks.JUNGLE_LOG)){facing=d;break;}
     check(facing!=null,"Cocoa jungle log missing");prospective=Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING,facing);
    }else if(i==1){check(level.getBlockState(p).isAir(),"Vine crop cell occupied");prospective=Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST,true);}
    else if(i>=2&&i<=4){planted=p.above();prospective=i==2?Blocks.KELP.defaultBlockState():i==3?Blocks.SEAGRASS.defaultBlockState():Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.WATERLOGGED,true);}
    else if(i==5||i==6){
     check(level.getBlockState(p).is(Blocks.SPRUCE_PLANKS),"Hanging support lost");for(int d=1;d<=3;d++)check(level.getBlockState(p.below(d)).isAir(),"Hanging growth volume obstructed");
     planted=p.below();prospective=(i==5?Blocks.CAVE_VINES:Blocks.WEEPING_VINES).defaultBlockState();
    }else{
     check(level.getBlockState(p).is(i==8?Blocks.CRIMSON_NYLIUM:Blocks.WARPED_NYLIUM),"Nylium substrate decayed "+names[i]);
     for(int d=1;d<=4;d++)check(level.getBlockState(p.above(d)).isAir(),"Upward growth volume obstructed "+names[i]);
     planted=p.above();prospective=(i==7?Blocks.TWISTING_VINES:i==8?Blocks.CRIMSON_FUNGUS:Blocks.WARPED_FUNGUS).defaultBlockState();
    }
    check(prospective.canSurvive(level,planted),"Native proposed plant cannot survive "+names[i]+" at "+planted);
   }
  }
  void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
  void fail(Throwable error){try{rows.add(new ProbeResult("crop_extension_failure",ProbeResult.Outcome.FAIL,error.toString()));ProbeReport.write(output,"ArcologyCropExtensions",rows);}catch(Exception e){error.addSuppressed(e);}h.fail(error.toString());}
 }
}
