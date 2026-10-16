package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.supply.*;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class SupplyBridgeTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
 public static void bridge_contract(GameTestHelper h)throws Exception {
  var rows=new ArrayList<ProbeResult>();
  SupplyTests.run(rows,"transient_snapshot_lock_retried",()->{
   int[] attempts={0};boolean[] written={false};
   try {SupplyFileWriter.write(()->{if(++attempts[0]<3)throw new java.nio.file.AccessDeniedException("QA locked snapshot");written[0]=true;});}
   catch(java.io.IOException e){throw new AssertionError(e);}
   SupplyTests.check(written[0]);SupplyTests.eq(attempts[0],3);
  });
  SupplyTests.run(rows,"permanent_snapshot_failure_not_retried",()->{
   int[] attempts={0};boolean failed=false;
   try {SupplyFileWriter.write(()->{attempts[0]++;throw new java.io.IOException("QA disk failure");});}
   catch(java.io.IOException e){failed=true;}
   SupplyTests.check(failed);SupplyTests.eq(attempts[0],1);
  });
  SupplyTests.run(rows,"snapshot_lock_retry_bounded",()->{
   int[] attempts={0};boolean failed=false;
   try {SupplyFileWriter.write(()->{attempts[0]++;throw new java.nio.file.AccessDeniedException("QA persistent lock");});}
   catch(java.io.IOException e){failed=true;}
   SupplyTests.check(failed);SupplyTests.eq(attempts[0],4);
  });
  SupplyTests.run(rows,"external_stock_reserve_and_delivery",()->{
   var n=new SupplyNetwork();var owner=UUID.randomUUID();var a=n.addDepot(owner,1);var b=n.addDepot(owner,1);
   var from=new ItemStackHandler(3);var to=new ItemStackHandler(3);
   from.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,40));
   var route=n.addRoute(owner,a,b,new ItemStack(Items.SPRUCE_LOG),24,16,32,20);
   var batch=new SupplyInventoryBatch(Map.of(a,from,b,to));
   n.tick(20,Set.of(a,b),batch);
   SupplyTests.eq(from.getStackInSlot(0).getCount(),40); // staging must not change the real hut
   SupplyTests.eq(n.route(route).cargo().getCount(),24);
   batch.apply();SupplyTests.eq(from.getStackInSlot(0).getCount(),16);
   batch=new SupplyInventoryBatch(Map.of(a,from,b,to));n.tick(20,Set.of(a,b),batch);batch.apply();
   SupplyTests.eq(to.getStackInSlot(0).getCount(),24);SupplyTests.eq(n.stock(b,new ItemStack(Items.SPRUCE_LOG)),0);
   batch=new SupplyInventoryBatch(Map.of(a,from,b,to));n.tick(20,Set.of(a,b),batch);
   SupplyTests.check(!batch.changed());SupplyTests.check(n.route(route).reason()==SupplyNetwork.Reason.TARGET_MET);
  });
  SupplyTests.run(rows,"slot_rules_and_changed_inventory",()->{
   var id=UUID.randomUUID();var handler=new ItemStackHandler(2){
    @Override public boolean isItemValid(int slot,ItemStack stack){return slot==1;}
    @Override public int getSlotLimit(int slot){return 4;}
   };
   var batch=new SupplyInventoryBatch(Map.of(id,handler));
   SupplyTests.eq(batch.insert(id,new ItemStack(Items.BREAD,10)).getCount(),6);
   batch.apply();SupplyTests.check(handler.getStackInSlot(0).isEmpty());SupplyTests.eq(handler.getStackInSlot(1).getCount(),4);
   batch=new SupplyInventoryBatch(Map.of(id,handler));batch.extract(id,new ItemStack(Items.BREAD),2);
   handler.setStackInSlot(1,new ItemStack(Items.BREAD,3));
   boolean rejected=false;try{batch.apply();}catch(IllegalStateException e){rejected=true;}
   SupplyTests.check(rejected);SupplyTests.eq(handler.getStackInSlot(1).getCount(),3);
  });
  SupplyTests.run(rows,"shared_source_reserve",()->{
   var n=new SupplyNetwork();var owner=UUID.randomUUID();
   var a=n.addDepot(owner,1);var b=n.addDepot(owner,1);var c=n.addDepot(owner,1);
   var from=new ItemStackHandler(1);var left=new ItemStackHandler(1);var right=new ItemStackHandler(1);
   from.setStackInSlot(0,new ItemStack(Items.SPRUCE_LOG,64));
   var first=n.addRoute(owner,a,b,new ItemStack(Items.SPRUCE_LOG),64,8,32,20);
   var second=n.addRoute(owner,a,c,new ItemStack(Items.SPRUCE_LOG),64,24,32,20);
   var handlers=Map.of(a,from,b,left,c,right);var batch=new SupplyInventoryBatch(handlers);
   n.tick(20,handlers.keySet(),batch);SupplyTests.eq(from.getStackInSlot(0).getCount(),64);
   SupplyTests.eq(n.route(first).cargo().getCount()+n.route(second).cargo().getCount(),40);
   batch.apply();SupplyTests.eq(from.getStackInSlot(0).getCount(),24);
   batch=new SupplyInventoryBatch(handlers);n.tick(20,handlers.keySet(),batch);batch.apply();
   SupplyTests.eq(left.getStackInSlot(0).getCount()+right.getStackInSlot(0).getCount(),40);
   SupplyTests.eq(from.getStackInSlot(0).getCount(),24);
  });
  SupplyTests.run(rows,"shared_destination_target",()->{
   var n=new SupplyNetwork();var owner=UUID.randomUUID();
   var a=n.addDepot(owner,1);var b=n.addDepot(owner,1);var c=n.addDepot(owner,1);
   var left=new ItemStackHandler(1);var right=new ItemStackHandler(1);var target=new ItemStackHandler(1);
   left.setStackInSlot(0,new ItemStack(Items.BREAD,32));right.setStackInSlot(0,new ItemStack(Items.BREAD,32));
   target.setStackInSlot(0,new ItemStack(Items.BREAD,8));
   var first=n.addRoute(owner,a,c,new ItemStack(Items.BREAD),40,0,32,20);
   var second=n.addRoute(owner,b,c,new ItemStack(Items.BREAD),40,0,32,20);
   var handlers=Map.of(a,left,b,right,c,target);var batch=new SupplyInventoryBatch(handlers);
   n.tick(20,handlers.keySet(),batch);
   SupplyTests.eq(n.route(first).cargo().getCount()+n.route(second).cargo().getCount(),32);
   batch.apply();batch=new SupplyInventoryBatch(handlers);n.tick(20,handlers.keySet(),batch);batch.apply();
   SupplyTests.eq(target.getStackInSlot(0).getCount(),40);
   SupplyTests.eq(left.getStackInSlot(0).getCount()+right.getStackInSlot(0).getCount(),32);
  });
  SupplyTests.run(rows,"changed_endpoint_prevents_all_writes",()->{
   var a=UUID.randomUUID();var b=UUID.randomUUID();
   var from=new ItemStackHandler(1);var to=new ItemStackHandler(1);
   from.setStackInSlot(0,new ItemStack(Items.BREAD,16));
   var handlers=new LinkedHashMap<UUID,ItemStackHandler>();handlers.put(a,from);handlers.put(b,to);
   var batch=new SupplyInventoryBatch(handlers);var cargo=batch.extract(a,new ItemStack(Items.BREAD),8);
   SupplyTests.check(batch.insert(b,cargo).isEmpty());
   to.setStackInSlot(0,new ItemStack(Items.APPLE,1));
   boolean rejected=false;try{batch.apply();}catch(IllegalStateException expected){rejected=true;}
   SupplyTests.check(rejected);SupplyTests.eq(from.getStackInSlot(0).getCount(),16);
   SupplyTests.check(to.getStackInSlot(0).is(Items.APPLE));SupplyTests.eq(to.getStackInSlot(0).getCount(),1);
  });
  SupplyTests.run(rows,"durable_handoff_order",()->{
   var order=new ArrayList<String>();var before=new CompoundTag();before.putInt("stock",32);var after=new CompoundTag();after.putInt("stock",16);
   var cp=new SupplyCheckpoint(tag->order.add(tag.contains("pending")?"prepare":"commit"));
   cp.commit(before,after,()->order.add("inventory"),()->order.add("flush"));
   SupplyTests.check(order.equals(List.of("prepare","inventory","flush","commit")));
  });
  SupplyTests.run(rows,"handoff_failure_quarantined",()->{
   for(int fail=0;fail<4;fail++){
    int at=fail;int[] step={0};var saved=new CompoundTag[]{null};boolean[] changed={false};
    Runnable boundary=()->{if(step[0]++==at)throw new IllegalStateException("Injected fault");};
    var cp=new SupplyCheckpoint(tag->{boundary.run();saved[0]=tag.copy();});
    boolean rejected=false;try{cp.commit(new CompoundTag(),new CompoundTag(),()->{boundary.run();changed[0]=true;},boundary);}catch(IllegalStateException e){rejected=true;}
    SupplyTests.check(rejected);SupplyTests.check(cp.blocked());
    if(at==0)SupplyTests.check(!changed[0]);else SupplyTests.check(saved[0].contains("pending"));
    boolean retry=false;try{cp.commit(new CompoundTag(),new CompoundTag(),()->{},()->{});}catch(IllegalStateException e){retry=true;}
    SupplyTests.check(retry);
   }
  });
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SupplyBridge",rows);
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Bridge failed: "+rows);h.succeed();
 }
}
