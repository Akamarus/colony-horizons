package dev.colonyhorizons.qa.outpost;
import com.minecolonies.core.colony.buildings.modules.QuarryModule;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
/** Independently checks native inside-building volume, including below-ground extent. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryBoundsTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1000)
 public static void quarry_bounds(GameTestHelper h){
  for(int x=29;x<=37;x++)for(int z=-2;z<=3;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{
   var rows=new ArrayList<ProbeResult>();var output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
   try{
    var level=h.getLevel();var profile=new GameProfile(UUID.randomUUID(),"QuarryBoundsProbe");level.getServer().getProfileCache().add(profile);
    var fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
    var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
    setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;
    setup.owner.setPos(536,32,0);
    fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
    com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(536,32,0),6,level,false);
    for(int i=0;i<2;i++){
     var name=i==0?"simplequarry":"mediumquarry";int depth=i==0?32:64;var anchor=new BlockPos(512+48*i,32,0);
     var quarry=setup.place("fundamentals/"+name+"1.blueprint",anchor,true);var corners=quarry.getCorners();var extension=quarry.getFirstModuleOccurance(QuarryModule.class).getAdditionalCorners();
     h.assertTrue(extension.getA().equals(new BlockPos(0,-depth,0))&&extension.getB().equals(BlockPos.ZERO),"Quarry lower-corner extension must point downward: "+name+" actual="+extension.getA());
     h.assertTrue(quarry.isInBuilding(anchor),"Quarry excludes its own hut: "+name);
     var bottom=new BlockPos(anchor.getX(),corners.getA().getY()-depth,anchor.getZ());
     h.assertTrue(quarry.isInBuilding(bottom)&&quarry.isInBuilding(bottom.above(depth/2)),"Quarry excludes shaft depth: "+name);
     h.assertTrue(!quarry.isInBuilding(bottom.below(2)),"Quarry extends beyond native depth plus margin");
     h.assertTrue(!quarry.isInBuilding(new BlockPos(anchor.getX(),corners.getB().getY()+2,anchor.getZ())),"Quarry extends above roof plus margin");
     h.assertTrue(!quarry.isInBuilding(new BlockPos(corners.getA().getX()-2,anchor.getY(),anchor.getZ()))&&!quarry.isInBuilding(new BlockPos(corners.getB().getX()+2,anchor.getY(),anchor.getZ())),"Quarry widens horizontal bounds");
     rows.add(new ProbeResult(name+"_bounds",ProbeResult.Outcome.PASS,"Native hut and descending shaft are inside the volume; below-depth, above-roof and outside-side points are rejected with native one-block margin. Lower extension="+extension.getA()+". No claim of protection permissions or excavation completion."));
    }
    ProbeReport.write(output,"ArcologyQuarryBounds",rows);h.succeed();
   }catch(Throwable e){try{rows.add(new ProbeResult("quarry_bounds_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyQuarryBounds",rows);}catch(Exception ignored){}h.fail(e.toString());}
  });
 }
}
