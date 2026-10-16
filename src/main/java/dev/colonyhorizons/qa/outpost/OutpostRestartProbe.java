package dev.colonyhorizons.qa.outpost;
import java.util.*;
import java.nio.file.Path;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class OutpostRestartProbe {
    static final String KEY="colonyhorizons_outpost_restart";
    static final BlockPos SOURCE=new BlockPos(0,100,0), TARGET=new BlockPos(2,100,0);
    static final SavedData.Factory<Data> FACTORY=new SavedData.Factory<>(Data::new,Data::load);
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300)
    public static void persisted_depot_transfer(GameTestHelper h) throws Exception {
        String phase=System.getProperty("colonyhorizons.probePhase","seed");
        var results=new ArrayList<ProbeResult>();
        try {
            var level=h.getLevel();level.getChunkAt(SOURCE);level.getChunkAt(TARGET);
            var data=level.getDataStorage().get(FACTORY,KEY);
            if(phase.equals("seed")){
                h.assertTrue(data==null,"Seed must use a fresh world");data=new Data();
                level.setBlockAndUpdate(SOURCE,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(TARGET,Blocks.CHEST.defaultBlockState());
                data.owner=UUID.randomUUID();data.sourceId=UUID.randomUUID();data.targetId=UUID.randomUUID();
                ProbeInventoryAdapter.register(level,new EndpointRef(data.sourceId,level.dimension(),SOURCE,data.owner));
                ProbeInventoryAdapter.register(level,new EndpointRef(data.targetId,level.dimension(),TARGET,data.owner));
                var source=ProbeInventoryAdapter.resolve(level,new EndpointRef(data.sourceId,level.dimension(),SOURCE,data.owner),data.owner).orElseThrow();
                var target=ProbeInventoryAdapter.resolve(level,new EndpointRef(data.targetId,level.dimension(),TARGET,data.owner),data.owner).orElseThrow();
                source.insertItem(0,new ItemStack(Items.SPRUCE_LOG,64),false);
                target.insertItem(0,new ItemStack(Items.SPRUCE_LOG,57),false);
                for(int i=1;i<target.getSlots();i++)target.insertItem(i,new ItemStack(Items.COBBLESTONE,64),false);
                data.shipment=new ProbeShipment();data.shipment.travelTicks=40;
                ProbeTransferEngine.load(data.shipment,source,List.of(new ItemStack(Items.SPRUCE_LOG,20)));
                h.assertTrue(source.getStackInSlot(0).getCount()==44 && data.shipment.count()==20,"Seed moved real chest goods");
                data.phase="seeded";level.getDataStorage().set(KEY,data);
            } else {
                h.assertTrue(data!=null && data.shipment!=null,"Shipment record must survive actual server restart");
                var source=ProbeInventoryAdapter.resolve(level,new EndpointRef(data.sourceId,level.dimension(),SOURCE,data.owner),data.owner).orElseThrow();
                var target=ProbeInventoryAdapter.resolve(level,new EndpointRef(data.targetId,level.dimension(),TARGET,data.owner),data.owner).orElseThrow();
                h.assertTrue(source.getStackInSlot(0).getCount()==44,"Restart must not replay extraction");
                h.assertTrue(data.shipment.travelTicks==40 && data.shipment.policy.equals("PROTECTED"),"Stopped server must not advance saved progress or alter policy");
                switch(phase){
                    case "verify_loaded" -> {
                        h.assertTrue(data.phase.equals("seeded") && data.shipment.count()==20 && target.getStackInSlot(0).getCount()==57,"Loaded source/cargo/destination must agree");
                        ProbeTransferEngine.unload(data.shipment,target);
                        h.assertTrue(data.shipment.count()==13 && data.shipment.delivered()==7 && target.getStackInSlot(0).getCount()==64,"Actual chest accepts seven, retains thirteen");
                        data.phase="partial";
                    }
                    case "verify_partial" -> {
                        h.assertTrue(data.phase.equals("partial") && data.shipment.count()==13 && data.shipment.delivered()==7 && target.getStackInSlot(0).getCount()==64,"Partial receipt persisted without replay");
                        target.extractItem(0,64,false);data.consumed=64;
                        ProbeTransferEngine.unload(data.shipment,target);data.phase="arrived";
                        h.assertTrue(data.shipment.count()==0 && target.getStackInSlot(0).getCount()==13,"Retry inserts only remaining thirteen");
                    }
                    case "verify_arrived" -> {
                        h.assertTrue(data.phase.equals("arrived") && data.shipment.state()==ProbeShipment.State.DELIVERED && data.shipment.delivered()==20,"Delivered state survives restart");
                        ProbeTransferEngine.unload(data.shipment,target);
                        h.assertTrue(target.getStackInSlot(0).getCount()==13 && data.shipment.count()==0,"Delivered retry does not duplicate goods");
                        h.assertTrue(source.getStackInSlot(0).getCount()+target.getStackInSlot(0).getCount()+data.consumed==121,"Conserve initial 64 source +57 destination logs");
                    }
                    default -> throw new IllegalArgumentException("Unknown restart phase");
                }
            }
            data.setDirty();
            results.add(new ProbeResult("restart_"+phase,ProbeResult.Outcome.PASS,"Actual world chests and saved custody verified"));
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Restart",results);h.succeed();
        }catch(Throwable e){
            results.add(new ProbeResult("restart_"+phase,ProbeResult.Outcome.FAIL,e.toString()));
            ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Restart",results);throw e;
        }
    }
    static final class Data extends SavedData {
        String phase="";UUID owner,sourceId,targetId;ProbeShipment shipment;int consumed;
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
            tag.putInt("schema",1);tag.putString("phase",phase);tag.putUUID("owner",owner);
            tag.putUUID("sourceId",sourceId);tag.putUUID("targetId",targetId);tag.putInt("consumed",consumed);
            tag.put("shipment",shipment.save(registries));return tag;
        }
        static Data load(CompoundTag tag,HolderLookup.Provider registries){
            if(tag.getInt("schema")!=1)throw new IllegalArgumentException("Invalid probe data schema");
            var data=new Data();data.phase=tag.getString("phase");data.owner=tag.getUUID("owner");
            data.sourceId=tag.getUUID("sourceId");data.targetId=tag.getUUID("targetId");data.consumed=tag.getInt("consumed");
            data.shipment=ProbeShipment.load(tag.getCompound("shipment"),registries);return data;
        }
    }
}
