package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.supply.SupplyNetwork;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class SupplyTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
 public static void protected_supply_contract(GameTestHelper h)throws Exception {
  var rows=new ArrayList<ProbeResult>();
  run(rows,"two_way_delivery",()->{
   var f=new Fixture();f.put(f.home,Items.BREAD,32);f.put(f.outpost,Items.SPRUCE_LOG,40);
   var logs=f.route(f.outpost,f.home,Items.SPRUCE_LOG,20,8,20,5);
   f.route(f.home,f.outpost,Items.BREAD,16,8,16,5);
   f.n.tick(1,f.all);eq(f.n.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG)),20);eq(f.n.route(logs).cargo().getCount(),20);
   f.n.tick(4,f.all);eq(f.n.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),0);
   f.n.tick(1,f.all);eq(f.n.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),20);eq(f.n.stock(f.outpost,new ItemStack(Items.BREAD)),16);
   f.n.tick(20,f.all);eq(f.n.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),20);
  });
  run(rows,"full_destination",()->{
   var f=new Fixture();f.put(f.home,Items.SPRUCE_LOG,20);var r=f.route(f.home,f.outpost,Items.SPRUCE_LOG,20,0,20,1);
   f.n.tick(1,f.all);for(int i=0;i<9;i++)f.put(f.outpost,Items.STONE,64);
   f.n.tick(1,f.all);eq(f.n.route(r).cargo().getCount(),20);check(f.n.route(r).reason()==SupplyNetwork.Reason.DESTINATION_FULL);
   f.n.withdraw(f.owner,f.outpost,new ItemStack(Items.STONE),64);f.put(f.outpost,Items.SPRUCE_LOG,57);
   f.n.tick(1,f.all);eq(f.n.route(r).cargo().getCount(),13);
   f.n.withdraw(f.owner,f.outpost,new ItemStack(Items.SPRUCE_LOG),64);f.n.tick(1,f.all);eq(f.n.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG)),13);check(f.n.route(r).cargo().isEmpty());
  });
  run(rows,"unavailable_endpoints",()->{
   var f=new Fixture();f.put(f.home,Items.SPRUCE_LOG,20);var r=f.route(f.home,f.outpost,Items.SPRUCE_LOG,20,0,20,2);
   f.n.tick(1,Set.of(f.outpost));eq(f.n.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),20);
   f.n.tick(1,f.all);f.n.tick(2,Set.of(f.home));eq(f.n.route(r).cargo().getCount(),20);check(f.n.route(r).reason()==SupplyNetwork.Reason.DESTINATION_UNAVAILABLE);
   f.n.tick(1,f.all);eq(f.n.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG)),20);
  });
  run(rows,"reserves_and_incoming",()->{
   var f=new Fixture();f.put(f.home,Items.SPRUCE_LOG,40);
   f.route(f.home,f.outpost,Items.SPRUCE_LOG,30,5,20,5);f.route(f.home,f.outpost,Items.SPRUCE_LOG,30,16,20,5);
   f.n.tick(1,f.all);eq(f.n.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),16);
   f.n.tick(5,f.all);eq(f.n.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG)),24);
   f.put(f.home,Items.SPRUCE_LOG,20);f.n.tick(1,f.all);f.n.tick(5,f.all);eq(f.n.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG)),30);
  });
  run(rows,"pause_and_return",()->{
   var f=new Fixture();f.put(f.home,Items.SPRUCE_LOG,20);var r=f.route(f.home,f.outpost,Items.SPRUCE_LOG,20,0,20,5);
   f.n.tick(1,f.all);f.n.pause(f.owner,r,true);f.n.tick(20,f.all);eq(f.n.route(r).remainingTicks(),5);
   f.n.cancel(f.owner,r);for(int i=0;i<9;i++)f.put(f.home,Items.STONE,64);
   f.n.tick(20,f.all);eq(f.n.route(r).cargo().getCount(),20);
   f.n.withdraw(f.owner,f.home,new ItemStack(Items.STONE),64);f.n.tick(20,f.all);
   eq(f.n.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),20);check(f.n.route(r).cargo().isEmpty());f.n.tick(20,f.all);eq(f.n.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),20);
  });
  run(rows,"components_and_copies",()->{
   var f=new Fixture();var axe=new ItemStack(Items.IRON_AXE);axe.setDamageValue(17);axe.set(DataComponents.CUSTOM_NAME,Component.literal("Outpost axe"));
   check(f.n.deposit(f.owner,f.home,axe).isEmpty());axe.setDamageValue(30);
   var wanted=new ItemStack(Items.IRON_AXE);wanted.setDamageValue(17);wanted.set(DataComponents.CUSTOM_NAME,Component.literal("Outpost axe"));
   var r=f.n.addRoute(f.owner,f.home,f.outpost,wanted,1,0,1,1);f.n.tick(1,f.all);f.n.route(r).cargo().setDamageValue(99);f.n.tick(1,f.all);
   check(ItemStack.matches(f.n.withdraw(f.owner,f.outpost,wanted,1),wanted));
  });
  run(rows,"authority_and_bounds",()->{
   var f=new Fixture();var foreign=f.n.addDepot(UUID.randomUUID(),2);var otherColony=f.n.addDepot(f.owner,2);
   reject(()->f.n.deposit(UUID.randomUUID(),f.home,new ItemStack(Items.STONE)));
   reject(()->f.n.addRoute(f.owner,f.home,foreign,new ItemStack(Items.STONE),1,0,1,1));
   reject(()->f.n.addRoute(f.owner,f.home,otherColony,new ItemStack(Items.STONE),1,0,1,1));
   reject(()->f.route(f.home,f.home,Items.STONE,1,0,1,1));reject(()->f.route(f.home,f.outpost,Items.STONE,0,0,1,1));reject(()->f.n.tick(-1,f.all));
  });
  run(rows,"snapshot_conservation",()->{
   var f=new Fixture();f.put(f.home,Items.SPRUCE_LOG,32);var r=f.route(f.home,f.outpost,Items.SPRUCE_LOG,20,8,20,10);f.n.tick(1,f.all);
   var copy=SupplyNetwork.load(f.n.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
   eq(copy.stock(f.home,new ItemStack(Items.SPRUCE_LOG))+copy.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG))+copy.route(r).cargo().getCount(),32);
   eq(copy.route(r).remainingTicks(),10);copy.tick(10,f.all);eq(copy.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG)),20);
   copy=SupplyNetwork.load(copy.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());copy.tick(20,f.all);eq(copy.stock(f.outpost,new ItemStack(Items.SPRUCE_LOG)),20);
  });
  run(rows,"returning_stock_counts_as_incoming",()->{
   var f=new Fixture();var third=f.n.addDepot(f.owner,1);var all=Set.of(f.home,f.outpost,third);
   f.put(f.home,Items.SPRUCE_LOG,20);f.put(third,Items.SPRUCE_LOG,20);
   var outgoing=f.route(f.home,f.outpost,Items.SPRUCE_LOG,20,0,20,10);f.n.tick(1,all);f.n.cancel(f.owner,outgoing);
   var replacement=f.route(third,f.home,Items.SPRUCE_LOG,20,0,20,2);f.n.tick(1,all);
   eq(f.n.stock(third,new ItemStack(Items.SPRUCE_LOG)),20);check(f.n.route(replacement).cargo().isEmpty());
   var restored=SupplyNetwork.load(f.n.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());restored.tick(10,all);
   eq(restored.stock(f.home,new ItemStack(Items.SPRUCE_LOG)),20);eq(restored.stock(third,new ItemStack(Items.SPRUCE_LOG)),20);
  });
  run(rows,"malformed_custody_rejected",()->{
   var f=new Fixture();f.put(f.home,Items.SPRUCE_LOG,20);f.route(f.home,f.outpost,Items.SPRUCE_LOG,20,0,20,2);f.n.tick(1,f.all);
   var bad=f.n.save(h.getLevel().registryAccess());bad.getList("routes",10).getCompound(0).putBoolean("disabled",true);
   reject(()->SupplyNetwork.load(bad,h.getLevel().registryAccess()));
   var duplicate=f.n.save(h.getLevel().registryAccess());var list=duplicate.getList("depots",10);list.add(list.getCompound(0).copy());reject(()->SupplyNetwork.load(duplicate,h.getLevel().registryAccess()));
   var wrong=f.n.save(h.getLevel().registryAccess());wrong.put("routes",new net.minecraft.nbt.ListTag());wrong.getList("routes",8).add(net.minecraft.nbt.StringTag.valueOf("bad"));reject(()->SupplyNetwork.load(wrong,h.getLevel().registryAccess()));
   var missing=f.n.save(h.getLevel().registryAccess());missing.getList("routes",10).getCompound(0).remove("remaining");reject(()->SupplyNetwork.load(missing,h.getLevel().registryAccess()));
   var foreign=f.n.save(h.getLevel().registryAccess());foreign.getList("depots",10).getCompound(0).putUUID("owner",UUID.randomUUID());reject(()->SupplyNetwork.load(foreign,h.getLevel().registryAccess()));
  });
  run(rows,"failed_snapshot_write_preserves_custody",()->{
   try {
    var f=new Fixture();f.put(f.home,Items.SPRUCE_LOG,32);var id=f.route(f.home,f.outpost,Items.SPRUCE_LOG,20,8,20,5);f.n.tick(1,f.all);
    var path=Path.of(System.getProperty("colonyhorizons.probeOutput"),"supply-write-fault.dat");
    net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(f.n.save(h.getLevel().registryAccess()),path);
    boolean failed=false;try{net.neoforged.neoforge.common.IOUtilities.atomicWrite(path,out->{out.write(new byte[]{1,2,3});throw new java.io.IOException("Injected before publication");});}catch(java.io.IOException expected){failed=true;}
    check(failed);var restored=SupplyNetwork.load(net.minecraft.nbt.NbtIo.readCompressed(path,net.minecraft.nbt.NbtAccounter.unlimitedHeap()),h.getLevel().registryAccess());
    eq(restored.stock(f.home,new ItemStack(Items.SPRUCE_LOG))+restored.route(id).cargo().getCount(),32);eq(restored.route(id).remainingTicks(),5);
   }catch(java.io.IOException e){throw new RuntimeException(e);}
  });
  run(rows,"invalid_saved_data",()->{
   var f=new Fixture();var tag=f.n.save(h.getLevel().registryAccess());tag.putInt("schema",99);reject(()->SupplyNetwork.load(tag,h.getLevel().registryAccess()));
   tag.putInt("schema",1);tag.remove("depots");reject(()->SupplyNetwork.load(tag,h.getLevel().registryAccess()));
  });
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Supply",rows);
  h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Supply cases failed: "+rows);h.succeed();
 }
 static void run(List<ProbeResult> rows,String id,Runnable test){try{test.run();rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Assertions passed"));}catch(Throwable t){rows.add(new ProbeResult(id,ProbeResult.Outcome.FAIL,t.toString()));}}
 static void check(boolean b){if(!b)throw new AssertionError("Condition failed");}
 static void eq(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
 static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException|SecurityException expected){return;}throw new AssertionError("Must reject");}
 static final class Fixture {
  final SupplyNetwork n=new SupplyNetwork();final UUID owner=UUID.randomUUID(),home=n.addDepot(owner,1),outpost=n.addDepot(owner,1);final Set<UUID> all=Set.of(home,outpost);
  void put(UUID depot,Item item,int count){check(n.deposit(owner,depot,new ItemStack(item,count)).isEmpty());}
  UUID route(UUID src,UUID dst,Item item,int target,int reserve,int batch,int travel){return n.addRoute(owner,src,dst,new ItemStack(item),target,reserve,batch,travel);}
 }
}
