package dev.colonyhorizons.qa.outpost;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingConcreteMixer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
/** Native fluid settling and replenishment at every real blueprint level. */
@PrefixGameTestTemplate(false)
public final class ArcologyMixerWaterTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=5000)
 public static void channels(GameTestHelper h){for(int x=28;x<=44;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
 final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
 final List<BuildingConcreteMixer> huts=new ArrayList<>();final List<BlockPos> targets=new ArrayList<>();final List<ProbeResult> rows=new ArrayList<>();
 Run(GameTestHelper h){this.h=h;level=h.getLevel();}
 void start(){try{
 level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
 for(int x=468;x<=660;x++)for(int z=-16;z<=32;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
 var profile=new GameProfile(UUID.randomUUID(),"MixerWaterProbe");level.getServer().getProfileCache().add(profile);
 var fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;
 var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(560,16,0);
 fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
 com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(560,16,0),8,level,false);
 setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
 for(int n=1;n<=5;n++){var anchor=new BlockPos(480+n*32,16,0);var hut=(BuildingConcreteMixer)setup.place("craftsmanship/luxury/concretemixer"+n+".blueprint",anchor,true);check(hut.getBuildingLevel()==n,"Actual level mismatch "+n);huts.add(hut);targets.add(anchor.offset(-2,0,1));check(hut.getMaxConcretePlaced()==1,"Expected one registered shallow cell L"+n);
 // Fixture placement suppresses neighbor updates: explicitly schedule ordinary fluid ticks.
 for(int z=-1;z<=1;z++){var p=anchor.offset(-2,0,z);level.scheduleTick(p,level.getFluidState(p).getType(),1);}level.scheduleTick(anchor.offset(-3,0,-1),Fluids.WATER,1);}
 h.runAfterDelay(300,()->settled());
 }catch(Throwable e){fail(e);}}
 void settled(){try{for(int i=0;i<5;i++){assertChannel(i);var target=targets.get(i);level.setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());check(target.equals(huts.get(i).getBlockToMine()),"Native mixer did not observe obstruction");}h.runAfterDelay(40,()->unblock());}catch(Throwable e){fail(e);}}
 void unblock(){try{for(var target:targets)check(level.destroyBlock(target,false),"Obstruction removal failed");h.runAfterDelay(300,()->finish());}catch(Throwable e){fail(e);}}
 void assertChannel(int i){var hut=huts.get(i);var target=targets.get(i);check(hut.getMaxConcretePlaced()==1&&target.equals(hut.getBlockToPlace()),"Work position lost L"+(i+1));for(int z=1;z<=3;z++){var p=target.offset(0,0,z-3);var state=level.getBlockState(p);var fluid=state.getFluidState();check(state.is(Blocks.WATER)&&fluid.getType()==Fluids.FLOWING_WATER&&fluid.getAmount()==8-z,"Unstable depth L"+(i+1)+" at "+p+": "+state);}check(level.getFluidState(target).getAmount()<=5,"Work water too deep");check(hut.getBlockToMine()==null,"Obstruction remains");}
 void finish(){try{for(int i=0;i<5;i++){assertChannel(i);rows.add(new ProbeResult("mixer_water_"+(i+1),ProbeResult.Outcome.PASS,"Actual Arcology level "+(i+1)+" registered one work cell. Native fluid ticks retained amounts 7,6,5 after 300 ticks, then replenished amount5 after ordinary stone placement/removal and another 300 ticks. No water correction or fake registration. Prebuilt fixture; worker crafting and survival construction not certified."));}ProbeReport.write(output,"ArcologyMixerWater",rows);h.succeed();}catch(Throwable e){fail(e);}}
 void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
 void fail(Throwable e){try{rows.add(new ProbeResult("mixer_water_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyMixerWater",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
