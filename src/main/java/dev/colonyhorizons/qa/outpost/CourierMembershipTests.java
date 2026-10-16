package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.supply.*;
import dev.colonyhorizons.supply.CourierDistrictPolicy.Status;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.api.RotationMirror;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class CourierMembershipTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=600)
    public static void native_membership(GameTestHelper h) {
        h.runAfterDelay(40,()->{try {run(h);} catch(Exception e){throw new RuntimeException(e);}});
    }

    private static void run(GameTestHelper h) throws Exception {
        var rows=new ArrayList<ProbeResult>();
        var level=h.getLevel();
        var fixture=OutpostProbeFixture.create(level,h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
        int colony=fixture.colony().getID();
        var anchor=fixture.center().offset(0,0,4);
        var blueprint=new Blueprint((short)1,(short)1,(short)1,level.registryAccess());
        blueprint.addBlockState(BlockPos.ZERO,ModBlocks.blockHutLumberjack.defaultBlockState());
        blueprint.setCachePrimaryOffset(BlockPos.ZERO);
        String pack=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(Path.of(System.getProperty("colonyhorizons.arcologyPath")).resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
        var player=FakePlayerFactory.get(level,new GameProfile(fixture.ownerId(),"CourierProbe"));
        h.assertTrue(ModBlocks.blockHutLumberjack.setup(player,level,anchor,blueprint,RotationMirror.NONE,true,pack,"fundamentals/lumberjack1.blueprint"),"Native hut setup");
        var worker=fixture.createCitizen();
        fixture.colony().getServerBuildingManager().getBuilding(anchor).getModule(BuildingModules.FORESTER_WORK).assignCitizen(worker);
        var record=NativeMembershipTests.record(fixture,anchor,worker.getId(),colony);
        SupplyTests.run(rows,"native_unmanaged",()->SupplyTests.check(NativeCourierDistricts.resolve(level,colony,anchor).status()==Status.UNMANAGED));
        var data=OutpostSavedData.get(level);data.register(record);
        SupplyTests.run(rows,"marker_on_registration",()->{
            var saved=((Colony)fixture.colony()).write(new CompoundTag(),level.registryAccess());
            SupplyTests.check(saved.getBoolean("colonyhorizons:managed_couriers"));
        });
        SupplyTests.run(rows,"planned_blocked",()->SupplyTests.check(NativeCourierDistricts.resolve(level,colony,anchor).status()==Status.BLOCKED));
        SupplyTests.run(rows,"active_membership",()->{
            NativeCourierDistricts.activate(level,record.id());
            var result=NativeCourierDistricts.resolve(level,colony,anchor);
            SupplyTests.check(result.status()==Status.ACTIVE && result.district().orElseThrow().outpostId().orElseThrow().equals(record.id()));
        });
        SupplyTests.run(rows,"paused_membership_refresh",()->{
            data.register(data.find(record.id()).orElseThrow().withState(OutpostRecord.State.PAUSED));
            SupplyTests.check(NativeCourierDistricts.resolve(level,colony,anchor).status()==Status.BLOCKED);
        });
        SupplyTests.run(rows,"unknown_hut",()->SupplyTests.check(NativeCourierDistricts.resolve(level,colony,anchor.offset(1,0,0)).status()==Status.BLOCKED));
        SupplyTests.run(rows,"foreign_claim",()->{
            var claims=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(anchor));
            claims.setOwningColony(999999,level.getChunkAt(anchor));
            try {
                SupplyTests.check(NativeCourierDistricts.resolve(level,colony,anchor).status()==Status.BLOCKED);
                boolean rejected=false;try{NativeCourierDistricts.activate(level,record.id());}catch(IllegalStateException expected){rejected=true;}
                SupplyTests.check(rejected);
            } finally {claims.setOwningColony(colony,level.getChunkAt(anchor));}
        });
        SupplyTests.run(rows,"unloaded_hut",()->{
            var far=anchor.offset(100000,0,100000);
            SupplyTests.check(!level.hasChunkAt(far));
            SupplyTests.check(NativeCourierDistricts.resolve(level,colony,far).status()==Status.BLOCKED);
            SupplyTests.check(!level.hasChunkAt(far));
        });
        SupplyTests.run(rows,"active_roundtrip",()->{
            NativeCourierDistricts.activate(level,record.id());
            var tag=data.save(new CompoundTag(),level.registryAccess());
            SupplyTests.eq(tag.getInt("schema"),2);
            SupplyTests.check(OutpostSavedData.load(tag,level.registryAccess(),level.dimension()).find(record.id()).orElseThrow().state().name().equals("ACTIVE"));
        });
        SupplyTests.run(rows,"legacy_stays_paused",()->{
            data.register(data.find(record.id()).orElseThrow().withState(OutpostRecord.State.PAUSED));
            var tag=data.save(new CompoundTag(),level.registryAccess());tag.putInt("schema",1);
            SupplyTests.check(OutpostSavedData.load(tag,level.registryAccess(),level.dimension()).find(record.id()).orElseThrow().state()==OutpostRecord.State.PAUSED);
            tag.getList("outposts",Tag.TAG_COMPOUND).getCompound(0).putString("state","ACTIVE");
            boolean rejected=false;try{OutpostSavedData.load(tag,level.registryAccess(),level.dimension());}catch(IllegalArgumentException expected){rejected=true;}
            SupplyTests.check(rejected);
        });
        SupplyTests.run(rows,"native_marker_saved",()->{
            var saved=((Colony)fixture.colony()).write(new CompoundTag(),level.registryAccess());
            SupplyTests.check(saved.getBoolean("colonyhorizons:managed_couriers"));
        });
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"CourierMembership",rows);
        h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Membership failures: "+rows.stream().filter(r->r.outcome()!=ProbeResult.Outcome.PASS).map(ProbeResult::caseId).toList());
        h.succeed();
    }
}
