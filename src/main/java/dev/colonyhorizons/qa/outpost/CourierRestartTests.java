package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.supply.NativeCourierDistricts;
import dev.colonyhorizons.supply.NativeCourierGuard;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class CourierRestartTests {
    private static Path file(GameTestHelper h){return h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("courier-expected.dat");}
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1600)
    public static void courier_restart(GameTestHelper h)throws Exception {
        if(System.getProperty("colonyhorizons.probePhase").equals("seed")){CourierNativeTests.native_selection(h);return;}
        var expected=NbtIo.readCompressed(file(h),NbtAccounter.unlimitedHeap());
        var chunk=new ChunkPos(BlockPos.of(expected.getLong("work")));
        for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(chunk.x+x,chunk.z+z,true);
        h.runAfterDelay(80,()->{try{verify(h,expected);}catch(Exception failure){throw new RuntimeException(failure);}});
    }
    static void seed(GameTestHelper h,OutpostProbeFixture fixture,JobDeliveryman job,IBuilding warehouse,IBuilding from,IBuilding to)throws Exception {
        var citizen=job.getCitizen();var entity=citizen.getEntity().orElseThrow();
        check(NativeCourierGuard.checkWork(entity).allowed(),"Initial native work valid");
        check(ItemHandlerHelper.insertItemStacked(from.getItemHandlerCap(),new ItemStack(Items.OAK_LOG,4),false).isEmpty(),"Seed source capacity");
        var ai=new EntityAIWorkDeliveryman(job);
        check(ai.gatherIfInTileEntity(h.getLevel().getBlockEntity(from.getPosition()),new ItemStack(Items.OAK_LOG,4)),"Native cargo pickup");
        var token=job.getTaskQueue().getFirst();job.addConcurrentDelivery(token);
        check(ArcologyOperationsFixture.count(citizen.getInventory(),new ItemStack(Items.OAK_LOG))==4,"Carried logs");
        var refs=new HashMap<BlockPos,OutpostRecord.BuildingRef>();
        for(var b:List.of(citizen.getWorkBuilding(),warehouse,from,to)){
            var p=b.getPosition();refs.put(p,new OutpostRecord.BuildingRef(p,BuiltInRegistries.BLOCK.getKey(h.getLevel().getBlockState(p).getBlock()),"Arcology Horizons",b.getBlueprintPath(),new BoundingBox(p)));
        }
        var chunk=new ChunkPos(warehouse.getPosition());
        var district=new OutpostRecord(UUID.randomUUID(),fixture.colony().getID(),h.getLevel().dimension(),"Paused local district",Set.of(chunk),
            new BoundingBox(chunk.getMinBlockX(),-64,chunk.getMinBlockZ(),chunk.getMaxBlockX(),320,chunk.getMaxBlockZ()),new BoundingBox(from.getPosition()),refs,Set.of(citizen.getId()),Set.of(citizen.getId()),Optional.empty(),OutpostRecord.State.PAUSED,List.of());
        OutpostSavedData.get(h.getLevel()).register(district);
        check(!NativeCourierGuard.checkWork(entity).allowed(),"Boundary change stops held work");
        var expected=new CompoundTag();expected.putInt("colony",fixture.colony().getID());expected.putInt("citizen",citizen.getId());expected.putUUID("entity",entity.getUUID());expected.putUUID("district",district.id());
        expected.putLong("work",citizen.getWorkBuilding().getPosition().asLong());expected.putLong("target",to.getPosition().asLong());expected.putString("request",token.getIdentifier().toString());
        NbtIo.writeCompressed(expected,file(h));report(h,"cargo_seed","Native pickup retained 4 logs and request while district paused");
    }
    private static void verify(GameTestHelper h,CompoundTag expected)throws Exception {
        var colony=IColonyManager.getInstance().getColonyByDimension(expected.getInt("colony"),h.getLevel().dimension());
        check(colony!=null,"Native colony restored");var citizen=colony.getCitizenManager().getCivilian(expected.getInt("citizen"));
        check(citizen!=null && citizen.getEntity().isPresent(),"Native citizen restored");var entity=citizen.getEntity().orElseThrow();
        check(entity.getUUID().equals(expected.getUUID("entity")),"Citizen identity preserved");var job=(JobDeliveryman)citizen.getJob();
        var target=colony.getServerBuildingManager().getBuilding(BlockPos.of(expected.getLong("target")));
        String phase=System.getProperty("colonyhorizons.probePhase");
        var world=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var index=world.resolve("data/colonyhorizons_outposts.dat").normalize();
        check(index.startsWith(world) && world.getFileName().toString().startsWith("foundation-"),"Only disposable QA data may be faulted");
        if(phase.equals("verify_missing") || phase.equals("verify_corrupt")) {
            check(colony instanceof dev.colonyhorizons.supply.ManagedCourierColony marker && marker.colonyHorizons$managedCouriers(),"Independent native marker survived restart");
            byte[] before=Files.exists(index)?Files.readAllBytes(index):null;
            check(!NativeCourierGuard.checkWork(entity).allowed(),"Lost index cannot silently become unmanaged");
            check(before==null?!Files.exists(index):Arrays.equals(before,Files.readAllBytes(index)),"Guard preserves missing/corrupt evidence");
            if(phase.equals("verify_missing"))Files.write(index,new byte[]{1,2,3,4});
            report(h,"cargo_"+phase,"Native marker blocked logistics after "+phase+" without regenerating the index");return;
        }
        if(System.getProperty("colonyhorizons.probePhase").equals("verify_final")){
            check(ArcologyOperationsFixture.count(target.getItemHandlerCap(),new ItemStack(Items.OAK_LOG))==4,"Exactly one persisted delivery");
            check(ArcologyOperationsFixture.count(citizen.getInventory(),new ItemStack(Items.OAK_LOG))==0,"No duplicate held cargo");
            var backup=world.resolve("data/courier-index-proof-backup.dat").normalize();
            check(backup.startsWith(world),"Backup stays in disposable world");
            Files.move(index,backup);
            report(h,"cargo_verify_final","Second restart retained one delivery of four logs");return;
        }
        check(job.getTaskQueue().stream().anyMatch(t->t.getIdentifier().toString().equals(expected.getString("request"))),"Request token survived restart");
        check(ArcologyOperationsFixture.count(citizen.getInventory(),new ItemStack(Items.OAK_LOG))==4,"Held cargo survived restart");
        check(!NativeCourierGuard.checkWork(entity).allowed(),"Paused membership remains blocked after restart");
        NativeCourierDistricts.activate(h.getLevel(),expected.getUUID("district"));
        check(NativeCourierGuard.checkWork(entity).allowed(),"Restored valid membership resumes original custody");
        var pos=target.getPosition();entity.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+1.5); // Disclosed positioning: this suite tests custody, not travel.
        var profile=new com.mojang.authlib.GameProfile(colony.getPermissions().getOwner(),"CourierRestart");
        var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),profile);
        var owner=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        owner.connection=fake.connection;owner.setPos(pos.getX(),pos.getY(),pos.getZ());
        colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);
        citizen.setSaturation(20);h.getLevel().setDayTime(6000);
        scheduleDelivery(h,citizen,target);
    }
    private static void scheduleDelivery(GameTestHelper h,ICitizenData citizen,IBuilding target) {
        h.runAfterDelay(20,()->{try {
            if(ArcologyOperationsFixture.count(target.getItemHandlerCap(),new ItemStack(Items.OAK_LOG))<4) {
                scheduleDelivery(h,citizen,target);
            } else {
                check(ArcologyOperationsFixture.count(citizen.getInventory(),new ItemStack(Items.OAK_LOG))==0,"Cargo transferred once");
                report(h,"cargo_verify","Native delivery resumed after clean restart and district activation");
            }
        }catch(Exception failure){h.fail(failure.toString());}});
    }
    private static void report(GameTestHelper h,String id,String evidence)throws Exception {ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"CourierRestart",List.of(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence)));h.succeed();}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
