package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.api.RotationMirror;
import dev.colonyhorizons.outpost.OutpostSavedData;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RemoteRecordTests {
    static Development sample(UUID owner, int x) {
        var id = UUID.randomUUID();
        var anchor = new BlockPos(x, 100, 0);
        var named = new ItemStack(Items.OAK_LOG, 24);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved timber"));
        var budget = new Budget(Map.of(new ItemKey(named), 24), Map.of(new ItemKey(named), 16));
        var request = new SiteRequest(1, "Remote grove", anchor,
            new BoundingBox(x-48, 80, -48, x+63, 160, 63), BlockPos.ZERO, List.of(x+1,x+2,x+3,x+4), x+1, 5, budget);
        var building = new BuildingPlan(UUID.randomUUID(), "builder", anchor, "Arcology Horizons",
            "fundamentals/builder1.blueprint", "a".repeat(64), RotationMirror.NONE,
            new BoundingBox(x,100,0,x+8,120,8),1);
        return new Development(id,owner,new SiteDraft(id,request,Set.of(new ChunkPos(anchor)),List.of(building),
            "b".repeat(64),List.of()),Mode.APPROVAL,Phase.PROPOSED,0);
    }
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=600)
    public static void remote_records(GameTestHelper h) {
        var rows = new ArrayList<ProbeResult>();
        try {
            var registry=h.getLevel().registryAccess();
            var owner=UUID.randomUUID(); var record=sample(owner,512);
            var data=new RemoteDevelopmentData(Level.OVERWORLD);
            var legacy=new OutpostSavedData(Level.OVERWORLD);
            var old=OutpostRecordTests.sample(UUID.randomUUID(),1,0,Set.of(1,2)); legacy.register(old);
            var oldCopy=OutpostSavedData.load(legacy.save(new CompoundTag(),registry),registry,Level.OVERWORLD);
            h.assertTrue(oldCopy.find(old.id()).isPresent() && data.find(old.id()).isEmpty(),"Legacy district gains no remote authority"); pass(rows,"legacy_no_authority");
            data.create(record); reject(()->data.create(record),h);
            var claiming=data.transition(record.id(),0,Phase.CLAIMING);
            reject(()->data.transition(record.id(),0,Phase.STAGING),h);
            h.assertTrue(data.all().size()==1 && data.find(record.id()).orElseThrow().revision()==1 && claiming.phase()==Phase.CLAIMING,"Rejected write is atomic"); pass(rows,"duplicate_and_stale_atomic");
            var saved=data.save(new CompoundTag(),registry);
            var loaded=RemoteDevelopmentData.load(saved,registry,Level.OVERWORLD);
            var restored=loaded.find(record.id()).orElseThrow();
            h.assertTrue(restored.revision()==1 && restored.owner().equals(owner) && restored.draft().buildings().getFirst().transform()==RotationMirror.NONE,"Remote identity roundtrip");
            var key=restored.draft().request().budget().allowances().keySet().iterator().next();
            h.assertTrue(key.stack().getCount()==1 && key.stack().getHoverName().getString().equals("Reserved timber") && restored.draft().request().budget().allowances().get(key)==24,"Component budget roundtrip");
            h.assertTrue(!key.equals(new ItemKey(new ItemStack(Items.OAK_LOG))),"Components separate item identities"); pass(rows,"component_budget_roundtrip");
            key.stack().set(DataComponents.CUSTOM_NAME,Component.literal("Mutated"));
            record.draft().request().allowedArea().move(1000,0,0);
            record.draft().buildings().getFirst().upgradeVolume().move(1000,0,0);
            h.assertTrue(key.stack().getHoverName().getString().equals("Reserved timber") && record.draft().request().allowedArea().minX()==464 && record.draft().buildings().getFirst().upgradeVolume().minX()==512,"No mutable escape"); pass(rows,"defensive_copies");
            var bad=saved.copy(); bad.putInt("schema",99); reject(()->RemoteDevelopmentData.load(bad,registry,Level.OVERWORLD),h);
            reject(()->RemoteDevelopmentData.load(saved,registry,Level.NETHER),h);
            for(String field:List.of("schema","dimension","developments")) {var malformed=saved.copy(); malformed.putString(field,"invalid");reject(()->RemoteDevelopmentData.load(malformed,registry,Level.OVERWORLD),h);}
            var duplicate=saved.copy(); duplicate.getList("developments",Tag.TAG_COMPOUND).add(duplicate.getList("developments",Tag.TAG_COMPOUND).getCompound(0).copy()); reject(()->RemoteDevelopmentData.load(duplicate,registry,Level.OVERWORLD),h);
            pass(rows,"strict_schema");
            var second=sample(owner,768); data.create(second); reject(()->data.transition(second.id(),0,Phase.CLAIMING),h);
            data.transition(record.id(),1,Phase.OPERATING); data.transition(second.id(),0,Phase.CLAIMING); data.transition(second.id(),1,Phase.OPERATING);
            var third=sample(owner,1024); data.create(third); reject(()->data.transition(third.id(),0,Phase.CLAIMING),h);
            h.assertTrue(data.find(third.id()).orElseThrow().phase()==Phase.PROPOSED,"Third remains unapproved");pass(rows,"owner_limits");
            var defaults=RemoteLimits.forServer(h.getLevel().getServer());
            h.assertTrue(defaults.developing()==1 && defaults.active()==2 && defaults.chunks()==49 && defaults.endpointChunks()==16,"Registered defaults");
            var reduced=new RemoteDevelopmentData(Level.OVERWORLD,()->new RemoteLimits(1,1,1,1));
            reduced.create(record); reduced.transition(record.id(),0,Phase.CLAIMING); reduced.transition(record.id(),1,Phase.OPERATING); reduced.create(second);reject(()->reduced.transition(second.id(),0,Phase.CLAIMING),h);
            reject(()->new RemoteLimits(2,3,50,17),h);pass(rows,"reduced_limits");
            var chunks=new HashSet<ChunkPos>(); for(int i=0;i<50;i++) chunks.add(new ChunkPos(32+i,0));
            reject(()->new SiteDraft(record.id(),record.draft().request(),chunks,record.draft().buildings(),"b".repeat(64),List.of()),h);
            reject(()->new Budget(Map.of(key,-1),Map.of()),h);
            reject(()->new Development(UUID.randomUUID(),owner,record.draft(),Mode.APPROVAL,Phase.PROPOSED,0),h);pass(rows,"invalid_authority_rejected");
            var blocked=sample(UUID.randomUUID(),2048);
            var blockedDraft=new SiteDraft(blocked.id(),blocked.draft().request(),blocked.draft().chunks(),blocked.draft().buildings(),blocked.draft().fingerprint(),List.of("Occupied"));
            var blockedData=new RemoteDevelopmentData(Level.OVERWORLD);blockedData.create(new Development(blocked.id(),blocked.owner(),blockedDraft,Mode.APPROVAL,Phase.PROPOSED,0));reject(()->blockedData.transition(blocked.id(),0,Phase.CLAIMING),h);pass(rows,"blocked_draft_rejected");
            reject(()->blockedData.transition(blocked.id(),0,Phase.PAUSED),h);
            reject(()->blockedData.transition(blocked.id(),0,Phase.OPERATING),h);
            var paused=new RemoteDevelopmentData(Level.OVERWORLD);paused.create(record);paused.create(second);paused.transition(record.id(),0,Phase.CLAIMING);paused.transition(record.id(),1,Phase.PAUSED);
            reject(()->paused.transition(second.id(),0,Phase.CLAIMING),h);pass(rows,"pause_cannot_bypass_limits");
            var disk=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(h.getLevel().dimension(),h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/colonyhorizons_remote_development.dat");
            Files.createDirectories(disk.getParent());var outer=new CompoundTag();outer.put("data",bad);NbtIo.writeCompressed(outer,disk);var original=Files.readAllBytes(disk);
            boolean refused=false;try{RemoteDevelopmentData.get(h.getLevel()).create(record);}catch(IllegalStateException e){refused=true;}
            h.getLevel().getDataStorage().save();h.assertTrue(refused && Arrays.equals(original,Files.readAllBytes(disk)),"Corrupt data cannot become empty writable authority");pass(rows,"disk_fail_closed");
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteRecords",rows);h.succeed();
        } catch(Throwable e) {try{rows.add(new ProbeResult("remote_exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteRecords",rows);}catch(Exception ignored){}throw new RuntimeException(e);}
    }
    private static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Verified real saved-data contract"));}
    private static void reject(Runnable action,GameTestHelper h){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Invalid authority must reject");}
}
