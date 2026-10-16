package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.FoundingCargo;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class FoundingCargoTests {
    @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
    public static void finite_custody(GameTestHelper h)throws Exception {
        var rows=new ArrayList<ProbeResult>();var provider=h.getLevel().registryAccess();
        var bread=new ItemKey(new ItemStack(Items.BREAD));var logs=new ItemKey(new ItemStack(Items.OAK_LOG));
        SupplyTests.run(rows,"reserve_and_atomic_shortage",()->{
            var inventory=new ItemStackHandler(2);inventory.setStackInSlot(0,new ItemStack(Items.BREAD,40));
            var budget=new Budget(Map.of(bread,24,logs,8),Map.of(bread,16));
            reject(()->FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),inventory,budget,Map.of(bread,25)));
            reject(()->FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),inventory,budget,Map.of(bread,24,logs,1)));
            SupplyTests.eq(inventory.getStackInSlot(0).getCount(),40);
            var transfer=FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),inventory,budget,Map.of(bread,24));
            SupplyTests.eq(inventory.getStackInSlot(0).getCount(),40);
            var writes=new ArrayList<CompoundTag>();boolean[] flushed={false};
            var cargo=transfer.commit(provider,t->{
                if(writes.isEmpty()){SupplyTests.check(t.contains("pending"));SupplyTests.eq(inventory.getStackInSlot(0).getCount(),40);}
                else SupplyTests.check(flushed[0]);writes.add(t.copy());
            },()->{SupplyTests.eq(inventory.getStackInSlot(0).getCount(),16);flushed[0]=true;});
            SupplyTests.eq(cargo.items().get(bread),24);SupplyTests.eq(writes.size(),2);
            SupplyTests.eq(FoundingCargo.load(writes.getLast(),provider).items().get(bread),24);
            reject(()->transfer.commit(provider,t->{throw new AssertionError("Repeated commit writes");},()->{}));
            SupplyTests.eq(inventory.getStackInSlot(0).getCount()+cargo.items().get(bread),40);
        });
        SupplyTests.run(rows,"components_and_extract_rules",()->{
            var named=new ItemStack(Items.BREAD,8);named.set(DataComponents.CUSTOM_NAME,Component.literal("Reserved food"));var key=new ItemKey(named);
            var inventory=new ItemStackHandler(2);inventory.setStackInSlot(0,named);inventory.setStackInSlot(1,new ItemStack(Items.BREAD,8));
            var budget=new Budget(Map.of(key,8),Map.of());
            reject(()->FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),inventory,budget,Map.of(bread,1)));
            var cargo=FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),inventory,budget,Map.of(key,4)).commit(provider,t->{},()->{});
            var roundtrip=FoundingCargo.load(cargo.save(provider),provider);
            SupplyTests.eq(roundtrip.items().get(key),4);SupplyTests.eq(inventory.getStackInSlot(1).getCount(),8);
            var locked=new ItemStackHandler(1){@Override public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}};
            locked.setStackInSlot(0,new ItemStack(Items.BREAD,8));
            reject(()->FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),locked,new Budget(Map.of(bread,8),Map.of()),Map.of(bread,1)));
            SupplyTests.eq(locked.getStackInSlot(0).getCount(),8);
        });
        SupplyTests.run(rows,"handoff_failure_quarantined",()->{
            for(int failure=0;failure<3;failure++) {
                final int point=failure;var inventory=new ItemStackHandler(1);inventory.setStackInSlot(0,new ItemStack(Items.BREAD,8));
                var transfer=FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),inventory,new Budget(Map.of(bread,8),Map.of()),Map.of(bread,4));
                var writes=new ArrayList<CompoundTag>();
                reject(()->transfer.commit(provider,t->{if(point==0 || point==2&&!writes.isEmpty())throw new IllegalStateException("Injected journal failure");writes.add(t.copy());},()->{if(point==1)throw new IllegalStateException("Injected native save failure");}));
                SupplyTests.eq(inventory.getStackInSlot(0).getCount(),point==0?8:4);
                if(!writes.isEmpty())reject(()->FoundingCargo.load(writes.getLast(),provider));
                reject(()->transfer.commit(provider,t->{throw new AssertionError("Uncertain transfer replayed");},()->{}));
            }
        });
        SupplyTests.run(rows,"changed_inventory_rejected",()->{
            var inventory=new ItemStackHandler(1);inventory.setStackInSlot(0,new ItemStack(Items.BREAD,8));
            var transfer=FoundingCargo.prepare(UUID.randomUUID(),UUID.randomUUID(),inventory,new Budget(Map.of(bread,8),Map.of()),Map.of(bread,4));
            inventory.setStackInSlot(0,new ItemStack(Items.BREAD,7));
            reject(()->transfer.commit(provider,t->{},()->{throw new AssertionError("Changed stock flushed");}));
            SupplyTests.eq(inventory.getStackInSlot(0).getCount(),7);
        });
        ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FoundingCargo",rows);
        h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Founding cargo contract");h.succeed();
    }
    private static void reject(Runnable action){boolean failed=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException e){failed=true;}SupplyTests.check(failed);}
}
