package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.supply.SupplyNetwork;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static dev.colonyhorizons.qa.outpost.SupplyTests.*;
@PrefixGameTestTemplate(false)
public final class BudgetedSupplyTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=200)
 public static void finite_supply(GameTestHelper h)throws Exception{
  var rows=new ArrayList<ProbeResult>();
  run(rows,"finite_refill_budget",()->{var f=new Fixture();f.put(f.home,Items.BREAD,64);var id=UUID.randomUUID();f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,new ItemStack(Items.BREAD),8,16,8,1,20);for(int i=0;i<5;i++){f.n.tick(1,f.all);f.n.tick(1,f.all);f.n.withdraw(f.owner,f.outpost,new ItemStack(Items.BREAD),64);}eq(f.n.route(id).delivered(),20);eq(f.n.stock(f.home,new ItemStack(Items.BREAD)),44);eq(f.n.allowanceRemaining(id),0);});
  run(rows,"budget_route_idempotent",()->{var f=new Fixture();var id=UUID.randomUUID();f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,new ItemStack(Items.BREAD),8,16,8,1,20);f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,new ItemStack(Items.BREAD),8,16,8,1,20);eq(f.n.routeIds().size(),1);reject(()->f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,new ItemStack(Items.BREAD),8,16,8,1,21));});
  run(rows,"budget_transit_reload",()->{var f=new Fixture();f.put(f.home,Items.BREAD,40);var id=UUID.randomUUID();f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,new ItemStack(Items.BREAD),24,16,16,5,24);f.n.tick(1,f.all);var n=SupplyNetwork.load(f.n.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());eq(n.allowanceRemaining(id),8);eq(n.route(id).cargo().getCount(),16);n.tick(5,f.all);n.tick(1,f.all);n.tick(5,f.all);eq(n.route(id).delivered(),24);eq(n.stock(f.home,new ItemStack(Items.BREAD)),16);});
  run(rows,"return_does_not_refill_budget",()->{var f=new Fixture();f.put(f.home,Items.BREAD,40);var id=UUID.randomUUID();f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,new ItemStack(Items.BREAD),16,16,16,5,24);f.n.tick(1,f.all);f.n.cancel(f.owner,id);f.n.tick(5,f.all);eq(f.n.stock(f.home,new ItemStack(Items.BREAD)),40);eq(f.n.allowanceRemaining(id),8);});
  run(rows,"budget_components",()->{var f=new Fixture();f.put(f.home,Items.BREAD,40);var named=new ItemStack(Items.BREAD);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Reserved"));var id=UUID.randomUUID();f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,named,8,0,8,1,8);f.n.tick(1,f.all);eq(f.n.allowanceRemaining(id),8);eq(f.n.route(id).cargo().getCount(),0);});
  run(rows,"corrupt_budget_rejected",()->{var f=new Fixture();var id=UUID.randomUUID();f.n.addBudgetedRoute(id,f.owner,f.home,f.outpost,new ItemStack(Items.BREAD),8,0,8,1,8);var tag=f.n.save(h.getLevel().registryAccess());tag.getList("routes",10).getCompound(0).putInt("dispatched",9);reject(()->SupplyNetwork.load(tag,h.getLevel().registryAccess()));tag.getList("routes",10).getCompound(0).remove("allowance");reject(()->SupplyNetwork.load(tag,h.getLevel().registryAccess()));});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"BudgetedSupply",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),"Budget checks: "+rows);h.succeed();
 }
}
