package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.supply.*;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class SupplyRestartTests {
 static final String KEY="colonyhorizons_supply_expected";
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
 public static void persistent_supply(GameTestHelper h)throws Exception {
  String phase=System.getProperty("colonyhorizons.probePhase","seed");var rows=new ArrayList<ProbeResult>();
  try {
   var storage=h.getLevel().getDataStorage();var expected=storage.get(FACTORY,KEY);
   var n=SupplySavedData.get(h.getLevel()).network();
   if(phase.equals("seed")){
    h.assertTrue(expected==null && n.depotIds().isEmpty(),"Seed world must be empty");expected=new Expected();
    expected.owner=UUID.randomUUID();expected.home=n.addDepot(expected.owner,1);expected.outpost=n.addDepot(expected.owner,1);
    h.assertTrue(n.deposit(expected.owner,expected.outpost,new ItemStack(Items.SPRUCE_LOG,32)).isEmpty(),"Seed logs fit");
    h.assertTrue(n.deposit(expected.owner,expected.home,food(32)).isEmpty(),"Seed food fits");
    expected.logs=n.addRoute(expected.owner,expected.outpost,expected.home,new ItemStack(Items.SPRUCE_LOG),20,8,20,10);
    expected.food=n.addRoute(expected.owner,expected.home,expected.outpost,food(1),8,8,8,10);
    n.tick(1,Set.of(expected.home,expected.outpost));n.tick(3,Set.of(expected.home,expected.outpost));n.pause(expected.owner,expected.food,true);
    storage.set(KEY,expected);expected.setDirty();
   }else{
    h.assertTrue(expected!=null,"Reload must not reseed");
    h.assertTrue(n.depotIds().equals(Set.of(expected.home,expected.outpost)) && n.routeIds().equals(Set.of(expected.logs,expected.food)),"Identities survive actual restart");
    if(phase.equals("verify")){
     h.assertTrue(n.route(expected.logs).remainingTicks()==7 && n.route(expected.food).remainingTicks()==7,"No offline travel");
     h.assertTrue(n.route(expected.logs).cargo().getCount()==20,"Cargo persisted, no extraction replay");
     n.tick(7,Set.of(expected.home,expected.outpost));
     h.assertTrue(n.route(expected.food).cargo().getCount()==8 && n.route(expected.food).remainingTicks()==7,"Paused cargo/progress retained");
    }else if(phase.equals("verify_paused")){
     h.assertTrue(n.route(expected.food).reason()==SupplyNetwork.Reason.PAUSED && n.route(expected.food).remainingTicks()==7,"Pause survives process restart");
     n.pause(expected.owner,expected.food,false);n.tick(7,Set.of(expected.home));
     h.assertTrue(n.route(expected.food).reason()==SupplyNetwork.Reason.DESTINATION_UNAVAILABLE && n.route(expected.food).cargo().getCount()==8,"Unavailable destination retains cargo");
    }else if(phase.equals("verify_final")){
     h.assertTrue(n.route(expected.food).remainingTicks()==0 && ItemStack.matches(n.route(expected.food).cargo(),food(8)),"Delayed food/components persist");
     n.tick(1,Set.of(expected.home,expected.outpost));n.tick(20,Set.of(expected.home,expected.outpost));
     h.assertTrue(n.stock(expected.home,new ItemStack(Items.SPRUCE_LOG))==20 && n.stock(expected.outpost,food(1))==8,"Two-way arrival exactly once");
     h.assertTrue(n.route(expected.logs).cargo().isEmpty() && n.route(expected.food).cargo().isEmpty(),"No stale delivered cargo");
    }else throw new IllegalArgumentException("Unknown restart phase");
   }
   int logs=n.stock(expected.home,new ItemStack(Items.SPRUCE_LOG))+n.stock(expected.outpost,new ItemStack(Items.SPRUCE_LOG))+n.route(expected.logs).cargo().getCount();
   int bread=n.stock(expected.home,food(1))+n.stock(expected.outpost,food(1))+n.route(expected.food).cargo().getCount();
   h.assertTrue(logs==32 && bread==32,"All stock+cargo conserved across restart");
   rows.add(new ProbeResult("supply_"+phase,ProbeResult.Outcome.PASS,"Actual SavedData process phase; 32 logs and 32 named bread conserved; identities/progress/pause verified"));
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SupplyRestart",rows);h.succeed();
  }catch(Throwable t){rows.add(new ProbeResult("supply_"+phase,ProbeResult.Outcome.FAIL,t.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"SupplyRestart",rows);h.fail(t.toString());}
 }
 static ItemStack food(int count){var s=new ItemStack(Items.BREAD,count);s.set(DataComponents.CUSTOM_NAME,Component.literal("Outpost lunch"));return s;}
 public static final class Expected extends SavedData {
  UUID owner,home,outpost,logs,food;
  static Expected load(CompoundTag tag,HolderLookup.Provider registries){var e=new Expected();e.owner=tag.getUUID("owner");e.home=tag.getUUID("home");e.outpost=tag.getUUID("outpost");e.logs=tag.getUUID("logs");e.food=tag.getUUID("food");return e;}
  @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putUUID("owner",owner);tag.putUUID("home",home);tag.putUUID("outpost",outpost);tag.putUUID("logs",logs);tag.putUUID("food",food);return tag;}
 }
}
