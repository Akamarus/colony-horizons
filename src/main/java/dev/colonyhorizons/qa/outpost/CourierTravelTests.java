package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.requestsystem.locations.StaticLocation;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.mojang.authlib.GameProfile;
import dev.colonyhorizons.outpost.*;
import dev.colonyhorizons.supply.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Flat, prebuilt native-hut fixture. After initial positioning only native AI moves the courier. */
@PrefixGameTestTemplate(false)
public final class CourierTravelTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=18000)
    public static void local_travel(GameTestHelper h){CourierNativeTests.native_selection(h);}
    static void start(GameTestHelper h,OutpostProbeFixture f,JobDeliveryman job,IBuilding warehouse,IBuilding from,IBuilding to)throws Exception {
        new Run(h,f,job,warehouse,from,to).start();
    }
    private static final class Run {
        final GameTestHelper h;final OutpostProbeFixture f;final JobDeliveryman job;final IBuilding warehouse,from,to;
        final List<ProbeResult> rows=new ArrayList<>();final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
        int ticks,phase,pauseAt=-1;UUID district;net.minecraft.world.phys.Vec3 previous;double walked;UUID entityId;
        Run(GameTestHelper h,OutpostProbeFixture f,JobDeliveryman job,IBuilding warehouse,IBuilding from,IBuilding to){this.h=h;this.f=f;this.job=job;this.warehouse=warehouse;this.from=from;this.to=to;}
        void start()throws Exception {
            var level=h.getLevel();var p=warehouse.getPosition();
            level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            for(int x=-5;x<22;x++)for(int z=-5;z<22;z++)level.setBlockAndUpdate(p.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
            var profile=new GameProfile(f.ownerId(),"CourierTravel");var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
            var owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(p.getX(),p.getY(),p.getZ());
            f.colony().getPackageManager().addImportantColonyPlayer(owner);f.colony().getPackageManager().addCloseSubscriber(owner);
            var citizen=job.getCitizen();citizen.setSaturation(20);var entity=citizen.getEntity().orElseThrow();entityId=entity.getUUID();
            entity.setPos(p.getX()+4.5,p.getY(),p.getZ()+2.5);previous=entity.position();
            for(int i=0;i<citizen.getInventory().getSlots();i++)citizen.getInventory().setStackInSlot(i,ItemStack.EMPTY);
            warehouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE).getMutableRequestList().clear();
            request();schedule();
        }
        void request(){
            check(ItemHandlerHelper.insertItemStacked(from.getItemHandlerCap(),new ItemStack(Items.OAK_LOG,4),false).isEmpty(),"Source capacity");
            var manager=f.colony().getRequestManager();
            var token=manager.createRequest(to.getRequester(),new Delivery(new StaticLocation(from.getPosition(),h.getLevel().dimension()),new StaticLocation(to.getPosition(),h.getLevel().dimension()),new ItemStack(Items.OAK_LOG,4),1));
            manager.assignRequest(token);
        }
        void schedule(){h.runAfterDelay(1,()->{try{tick();}catch(Throwable e){try{Files.writeString(output.resolve("travel-failure.txt"),e.toString());}catch(Exception ignored){}h.fail(e.toString());}});}
        void tick()throws Exception {
            ticks++;check(ticks<17000,"Native travel timeout");var entity=job.getCitizen().getEntity().orElseThrow();
            check(entity.isAlive()&&entityId.equals(entity.getUUID()),"Courier identity");
            double moved=previous.distanceTo(entity.position());check(moved<2,"Unexpected courier jump");walked+=moved;previous=entity.position();
            int held=ArcologyOperationsFixture.count(job.getCitizen().getInventory(),new ItemStack(Items.OAK_LOG));
            int delivered=ArcologyOperationsFixture.count(to.getItemHandlerCap(),new ItemStack(Items.OAK_LOG));
            if(ticks%200==0)Files.writeString(output.resolve("travel-diagnostics.txt"),"tick="+ticks+" phase="+phase+" pos="+entity.blockPosition()+" state="+job.getWorkerAI().getState()+" active="+f.colony().isActive()+" held="+held+" received="+delivered+" guard="+NativeCourierGuard.checkWork(entity)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            if(phase==0&&delivered==4){
                check(held==0&&walked>3,"Home delivery must walk and empty cargo");rows.add(new ProbeResult("native_home_delivery",ProbeResult.Outcome.PASS,"Native AI collected, walked and delivered four logs"));
                var refs=new HashMap<BlockPos,OutpostRecord.BuildingRef>();
                for(var b:List.of(job.getCitizen().getWorkBuilding(),warehouse,from,to)){var p=b.getPosition();refs.put(p,new OutpostRecord.BuildingRef(p,BuiltInRegistries.BLOCK.getKey(h.getLevel().getBlockState(p).getBlock()),"Arcology Horizons",b.getBlueprintPath(),new BoundingBox(p)));}
                var p=warehouse.getPosition();district=UUID.randomUUID();
                var record=new OutpostRecord(district,f.colony().getID(),h.getLevel().dimension(),"Local courier district",Set.of(new ChunkPos(p)),new BoundingBox(p.getX(),p.getY(),p.getZ(),p.getX()+13,p.getY()+2,p.getZ()+13),new BoundingBox(p),refs,Set.of(job.getCitizen().getId()),Set.of(job.getCitizen().getId()),Optional.empty(),OutpostRecord.State.PLANNED,List.of());
                OutpostSavedData.get(h.getLevel()).register(record);NativeCourierDistricts.activate(h.getLevel(),district);phase=1;request();
            }
            if(phase==1&&held==4){
                var data=OutpostSavedData.get(h.getLevel());data.register(data.find(district).orElseThrow().withState(OutpostRecord.State.PAUSED));pauseAt=ticks;phase=2;
            }
            if(phase==2){check(held==4&&delivered==4,"Paused courier retains cargo");if(ticks-pauseAt>=200){NativeCourierDistricts.activate(h.getLevel(),district);phase=3;}}
            if(phase==3&&delivered==8){
                check(held==0,"Resumed courier empties cargo");
                rows.add(new ProbeResult("native_outpost_delivery",ProbeResult.Outcome.PASS,"Native AI collected and delivered within ACTIVE outpost"));
                rows.add(new ProbeResult("native_pause_resume",ProbeResult.Outcome.PASS,"Held four logs for 200 ticks then native AI resumed delivery"));
                ProbeReport.write(output,"CourierTravel",rows);h.succeed();return;
            }
            schedule();
        }
        void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    }
}
