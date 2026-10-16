package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class WarehouseSiteTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
    public static void review_real_arcology_site(GameTestHelper h) {
        h.runAfterDelay(40,()->run(h));
    }
    private static void run(GameTestHelper h) {
        var rows=new ArrayList<ProbeResult>();
        try {
            var level=h.getLevel();var owner=UUID.randomUUID();
            var home=OutpostProbeFixture.create(level,new BlockPos(0,-59,0),owner);
            var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(owner,"SiteReviewer"));
            var party=new ArrayList<Integer>();for(int i=0;i<5;i++) party.add(home.createCitizen().getId());
            var center=new BlockPos(1024,-59,0);
            for(int x=976;x<=1087;x++)for(int z=-48;z<=63;z++) {
                for(int y=-63;y<=-60;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState());
                for(int y=-59;y<=-40;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            var request=new SiteRequest(home.colony().getID(),"Grove",center,new BoundingBox(960,-64,-64,1103,150,79),home.center(),party,party.getFirst(),5,new Budget(Map.of(),Map.of()));
            var before=fingerprint(level,center);var draft=RemoteSitePlanner.review(actor,request);
            h.assertTrue(draft.blockers().isEmpty(),"Valid remote site: "+draft.blockers());
            h.assertTrue(draft.chunks().size()==49 && draft.buildings().size()==10 && draft.buildings().stream().filter(b->b.role().equals("home")).count()==5,"Five adults have five level-one homes within 49 chunks");
            h.assertTrue(before==fingerprint(level,center),"Review must not mutate world or claims");pass(rows,"supported_remote_layout");
            h.assertTrue(draft.buildings().stream().filter(b->b.role().equals("warehouse")).count()==1 && draft.buildings().stream().filter(b->b.role().equals("courier")).count()==1,"Warehouse and courier included");
            var access=center.offset(0,0,12);var accessFloor=level.getBlockState(access.below());
            level.setBlockAndUpdate(access.below(),Blocks.AIR.defaultBlockState());
            h.assertTrue(!RemoteSitePlanner.review(actor,request).blockers().isEmpty(),"Access lane must have support");level.setBlockAndUpdate(access.below(),accessFloor);pass(rows,"supported_access");
            for(var a:draft.buildings())for(var b:draft.buildings())if(a!=b)h.assertTrue(!a.upgradeVolume().intersects(b.upgradeVolume()),"Upgrade envelopes overlap");pass(rows,"upgrade_envelopes");
            var floor=new BlockPos(draft.buildings().getFirst().upgradeVolume().minX(),draft.buildings().getFirst().upgradeVolume().minY()-1,draft.buildings().getFirst().upgradeVolume().minZ());
            var old=level.getBlockState(floor);level.setBlockAndUpdate(floor,Blocks.AIR.defaultBlockState());
            h.assertTrue(!RemoteSitePlanner.review(actor,request).blockers().isEmpty(),"Unsupported site rejected");level.setBlockAndUpdate(floor,old);pass(rows,"unsupported_floor");
            var hazard=draft.buildings().getFirst().anchor().offset(1,0,0);var air=level.getBlockState(hazard);
            level.setBlockAndUpdate(hazard,Blocks.WATER.defaultBlockState());h.assertTrue(!RemoteSitePlanner.review(actor,request).blockers().isEmpty(),"Liquid rejected");level.setBlockAndUpdate(hazard,air);pass(rows,"liquid_rejected");
            level.setBlockAndUpdate(hazard,Blocks.CHEST.defaultBlockState());h.assertTrue(!RemoteSitePlanner.review(actor,request).blockers().isEmpty(),"Container rejected");level.setBlockAndUpdate(hazard,air);pass(rows,"container_rejected");
            level.setBlockAndUpdate(hazard,Blocks.STONE.defaultBlockState());var changed=RemoteSitePlanner.review(actor,request);
            h.assertTrue(!draft.fingerprint().equals(changed.fingerprint()),"Changed terrain invalidates approval");level.setBlockAndUpdate(hazard,air);pass(rows,"terrain_fingerprint");
            var packPath=Path.of(System.getProperty("colonyhorizons.arcologyPath"));var higher=packPath.resolve("fundamentals/builder5.blueprint");var bytes=Files.readAllBytes(higher);
            try {Files.write(higher,new byte[]{0},StandardOpenOption.APPEND);h.assertTrue(!draft.fingerprint().equals(RemoteSitePlanner.review(actor,request).fingerprint()),"Changed upgrade file invalidates approval");}finally{Files.write(higher,bytes);}pass(rows,"upgrade_fingerprint");
            var near=new SiteRequest(home.colony().getID(),"Near",new BlockPos(16,-59,0),new BoundingBox(-64,-64,-64,127,150,127),home.center(),party,party.getFirst(),5,request.budget());
            h.assertTrue(!RemoteSitePlanner.review(actor,near).blockers().isEmpty(),"Home district is not remote");pass(rows,"home_radius_rejected");
            var size=level.getWorldBorder().getSize();level.getWorldBorder().setSize(1000);
            h.assertTrue(!RemoteSitePlanner.review(actor,request).blockers().isEmpty(),"World border rejects");level.getWorldBorder().setSize(size);pass(rows,"world_border_rejected");
            var foreign=OutpostProbeFixture.create(level,new BlockPos(2048,-59,0),UUID.randomUUID());
            ChunkDataHelper.staticClaimInRange((Colony)foreign.colony(),true,center,0,level,false);
            h.assertTrue(!RemoteSitePlanner.review(actor,request).blockers().isEmpty(),"Foreign claim rejects");pass(rows,"foreign_claim_rejected");
            var source=Path.of(System.getProperty("colonyhorizons.arcologyPath"));
            var catalog=dev.colonyhorizons.integration.ArcologyCatalog.inspect(source,level.registryAccess());
            h.assertTrue(catalog.packName().equals("Arcology Horizons") && catalog.entries().size()==25,"Installed identity recognized");
            h.assertTrue(!dev.colonyhorizons.integration.ArcologyCatalog.inspect(source.resolve("missing"),level.registryAccess()).diagnostics().isEmpty(),"Missing pack blocked");pass(rows,"pack_identity_and_missing");
            var remoteData=RemoteDevelopmentData.get(level);remoteData.create(new Development(draft.id(),owner,draft,Mode.APPROVAL,Phase.PROPOSED,0));
            int chunksBefore=level.getChunkSource().getLoadedChunksCount();RemoteSurvey.next(level,draft.id());
            h.assertTrue(remoteData.survey(draft.id()).index()==1,"Survey records first candidate");RemoteSurvey.next(level,draft.id());
            h.assertTrue(remoteData.survey(draft.id()).index()==1 && chunksBefore==level.getChunkSource().getLoadedChunksCount(),"Same-tick search neither advances nor loads chunks");
            var reloaded=RemoteDevelopmentData.load(remoteData.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess(),level.dimension());
            h.assertTrue(reloaded.survey(draft.id()).index()==1,"Survey cursor survives reload");pass(rows,"survey_cadence_reload");
            long start=level.getGameTime();for(int i=1;i<=300;i++)reloaded.advanceSurvey(draft.id(),start+20L*i,256);
            h.assertTrue(reloaded.survey(draft.id()).index()==256 && reloaded.advanceSurvey(draft.id(),start+10000,256).isEmpty(),"Survey stops at 256 candidates");pass(rows,"survey_bounded");
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"WarehouseSites",rows);h.succeed();
        }catch(Throwable e){try{rows.add(new ProbeResult("remote_site_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"WarehouseSites",rows);}catch(Exception ignored){}throw new RuntimeException(e);}
    }
    private static long fingerprint(net.minecraft.server.level.ServerLevel l,BlockPos center){long hash=7;for(int x=center.getX()-48;x<=center.getX()+63;x++)for(int z=-48;z<=63;z++)for(int y=-60;y<=-40;y++)hash=31*hash+net.minecraft.world.level.block.Block.getId(l.getBlockState(new BlockPos(x,y,z)));return hash;}
    private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native site review contract verified"));}
}

