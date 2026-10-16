package dev.colonyhorizons.qa.outpost;
import java.util.*;
import java.nio.file.Path;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class OutpostTransferTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
    public static void transfer_contract(GameTestHelper h) throws Exception {
        var results=new ArrayList<ProbeResult>();
        check(results,"load_moves_real_items_once",()->{
            var source=new ItemStackHandler(1);source.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,64));
            var s=new ProbeShipment();var request=List.of(new ItemStack(Items.SPRUCE_LOG,20));
            ProbeTransferEngine.load(s,source,request);
            h.assertTrue(source.getStackInSlot(0).getCount()==44 && s.count()==20,"Loading must move exactly 20 real logs");
            ProbeTransferEngine.load(s,source,request);
            h.assertTrue(source.getStackInSlot(0).getCount()==44 && s.count()==20,"Replayed load must not extract again");
        });
        check(results,"partial_delivery_retains_remainder",()->{
            var source=new ItemStackHandler(1);source.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,20));
            var s=new ProbeShipment();ProbeTransferEngine.load(s,source,List.of(new ItemStack(Items.SPRUCE_LOG,20)));
            var destination=new ItemStackHandler(1);destination.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,57));
            h.assertTrue(ProbeTransferEngine.unload(s,destination).movedItems()==7,"Destination must accept only seven");
            h.assertTrue(s.count()==13 && s.delivered()==7,"Thirteen must remain in custody");
            ProbeTransferEngine.unload(s,destination);h.assertTrue(s.count()==13,"Full retry must preserve remainder");
            destination.extractItem(0,64,false);ProbeTransferEngine.unload(s,destination);
            h.assertTrue(destination.getStackInSlot(0).getCount()==13 && s.count()==0 && s.delivered()==20,"Delivery must account for exactly twenty");
            ProbeTransferEngine.unload(s,destination);h.assertTrue(destination.getStackInSlot(0).getCount()==13,"Delivered replay must be inert");
        });
        check(results,"components_and_tools_survive_transfer",()->{
            var source=new ItemStackHandler(4);var requested=new ArrayList<ItemStack>();
            for(int i=0;i<4;i++){
                var item=new ItemStack(i<2?Items.SPRUCE_LOG:Items.IRON_AXE,i<2?4:1);
                if(i<2)item.set(DataComponents.CUSTOM_NAME,Component.literal(i==0?"North":"South"));else item.setDamageValue(i==2?3:17);
                source.setStackInSlot(i,item.copy());requested.add(item.copy());
            }
            var s=new ProbeShipment();ProbeTransferEngine.load(s,source,requested);
            var copy=s.cargo();if(!copy.isEmpty())copy.getFirst().setCount(99);
            var destination=new ItemStackHandler(4);ProbeTransferEngine.unload(s,destination);
            for(int i=0;i<4;i++)h.assertTrue(ItemStack.matches(requested.get(i),destination.getStackInSlot(i)),"Cargo components/counts must survive slot "+i);
        });
        check(results,"slot_restrictions_are_respected",()->{
            var source=new ItemStackHandler(1);source.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,20));
            var s=new ProbeShipment();ProbeTransferEngine.load(s,source,List.of(new ItemStack(Items.SPRUCE_LOG,20)));
            var target=new ItemStackHandler(2){public boolean isItemValid(int slot,ItemStack stack){return slot==1;}public int getSlotLimit(int slot){return 4;}};
            ProbeTransferEngine.unload(s,target);
            h.assertTrue(target.getStackInSlot(0).isEmpty() && target.getStackInSlot(1).getCount()==4 && s.count()==16,"Real slot rules must constrain unloading");
        });
        check(results,"endpoint_permission_or_identity_change_blocks_transfer",()->{
            var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
            var owner=UUID.randomUUID();var endpoint=new EndpointRef(UUID.randomUUID(),h.getLevel().dimension(),pos,owner);
            ProbeInventoryAdapter.register(h.getLevel(),endpoint);
            h.assertTrue(ProbeInventoryAdapter.resolve(h.getLevel(),endpoint,owner).isPresent(),"Registered owner can resolve actual chest");
            h.assertTrue(ProbeInventoryAdapter.resolve(h.getLevel(),endpoint,UUID.randomUUID()).isEmpty(),"Other actor denied");
            h.assertTrue(ProbeInventoryAdapter.resolve(h.getLevel(),new EndpointRef(UUID.randomUUID(),endpoint.dimension(),pos,owner),owner).isEmpty(),"Wrong endpoint identity denied");
            h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());h.getLevel().setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
            h.assertTrue(ProbeInventoryAdapter.resolve(h.getLevel(),endpoint,owner).isEmpty(),"Replacement chest must not inherit access");
        });
        check(results,"shipment_roundtrip_preserves_components_and_policy",()->{
            var s=new ProbeShipment();var source=new ItemStackHandler(1);var named=new ItemStack(Items.IRON_AXE);named.setDamageValue(19);named.set(DataComponents.CUSTOM_NAME,Component.literal("Remote axe"));source.setStackInSlot(0,named);
            ProbeTransferEngine.load(s,source,List.of(named.copy()));s.travelTicks=73;
            var restored=ProbeShipment.load(s.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.id.equals(s.id) && restored.travelTicks==73 && restored.policy.equals("PROTECTED"),"Shipment identity/progress/policy must persist");
            h.assertTrue(restored.count()==1 && ItemStack.matches(restored.cargo().getFirst(),named),"Saved components must survive");
        });
        check(results,"interrupted_transfer_requires_recovery",()->{
            var s=new ProbeShipment();s.state=ProbeShipment.State.UNLOADING;s.cargo.add(new ItemStack(Items.SPRUCE_LOG,20));
            var restored=ProbeShipment.load(s.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            var target=new ItemStackHandler(1);
            h.assertTrue(restored.state()==ProbeShipment.State.RECOVERY_NEEDED,"Uncertain partial write must quarantine");
            h.assertTrue(ProbeTransferEngine.unload(restored,target).movedItems()==0 && target.getStackInSlot(0).isEmpty(),"Quarantined cargo cannot replay");
            var tag=s.save(h.getLevel().registryAccess());tag.putInt("schema",99);
            boolean rejected=false;try{ProbeShipment.load(tag,h.getLevel().registryAccess());}catch(IllegalArgumentException e){rejected=true;}
            h.assertTrue(rejected,"Unknown schema must be rejected");
        });
        check(results,"interrupted_extraction_preserves_pending_evidence",()->{
            var source=new ItemStackHandler(1){
                @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
                    var extracted=super.extractItem(slot,amount,simulate);
                    if(!simulate && !extracted.isEmpty())throw new InjectedTransferFailure();
                    return extracted;
                }
            };
            source.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,64));
            var s=new ProbeShipment();var request=List.of(new ItemStack(Items.SPRUCE_LOG,20));
            boolean interrupted=false;
            try{ProbeTransferEngine.load(s,source,request);}catch(InjectedTransferFailure expected){interrupted=true;}
            h.assertTrue(interrupted && source.getStackInSlot(0).getCount()==44,"Fault must occur after actual source extraction");
            h.assertTrue(s.state()==ProbeShipment.State.LOADING && s.count()==0,"Unacknowledged extraction must retain intermediate state");
            var restored=ProbeShipment.load(s.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.state()==ProbeShipment.State.RECOVERY_NEEDED && restored.pending.size()==1
                && ItemStack.matches(restored.pending.getFirst(),request.getFirst()),"Extraction quarantine must preserve pending evidence");
            var target=new ItemStackHandler(1);
            h.assertTrue(ProbeTransferEngine.load(restored,source,request).movedItems()==0
                && ProbeTransferEngine.unload(restored,target).movedItems()==0,"Uncertain extraction must not replay");
            h.assertTrue(source.getStackInSlot(0).getCount()==44 && target.getStackInSlot(0).isEmpty()
                && restored.count()==0 && restored.delivered()==0,"Quarantine must not invent replacement cargo");
        });
        check(results,"interrupted_insertion_preserves_pending_evidence",()->{
            var source=new ItemStackHandler(1);source.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,20));
            var s=new ProbeShipment();ProbeTransferEngine.load(s,source,List.of(new ItemStack(Items.SPRUCE_LOG,20)));
            var target=new ItemStackHandler(1){
                @Override public ItemStack insertItem(int slot,ItemStack incoming,boolean simulate){
                    var remainder=super.insertItem(slot,incoming,simulate);
                    if(!simulate && remainder.getCount()<incoming.getCount())throw new InjectedTransferFailure();
                    return remainder;
                }
            };
            target.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,57));
            boolean interrupted=false;
            try{ProbeTransferEngine.unload(s,target);}catch(InjectedTransferFailure expected){interrupted=true;}
            h.assertTrue(interrupted && target.getStackInSlot(0).getCount()==64,"Fault must occur after seven actual insertions");
            h.assertTrue(s.state()==ProbeShipment.State.UNLOADING && s.count()==20 && s.delivered()==0,"Unacknowledged insertion must remain uncertain");
            var restored=ProbeShipment.load(s.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(restored.state()==ProbeShipment.State.RECOVERY_NEEDED && restored.pending.size()==1
                && ItemStack.matches(restored.pending.getFirst(),new ItemStack(Items.SPRUCE_LOG,20)),"Insertion quarantine must preserve pending evidence");
            target.extractItem(0,64,false); // Free capacity so a replay would demonstrably duplicate cargo.
            h.assertTrue(ProbeTransferEngine.unload(restored,target).movedItems()==0
                && target.getStackInSlot(0).isEmpty() && restored.count()==20 && restored.delivered()==0,
                "Uncertain insertion must not replay even when space becomes available");
        });
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Transfers",results);
        h.assertTrue(results.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Transfer contract failed; see report");h.succeed();
    }
    private static final class InjectedTransferFailure extends RuntimeException {}
    static void check(List<ProbeResult> results,String id,Runnable action){
        try{action.run();results.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Assertions passed"));}
        catch(Throwable e){results.add(new ProbeResult(id,ProbeResult.Outcome.FAIL,e.toString()));}
    }
}
