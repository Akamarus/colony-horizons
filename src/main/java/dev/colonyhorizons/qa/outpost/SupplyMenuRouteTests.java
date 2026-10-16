package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.menu.*;
import dev.colonyhorizons.supply.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class SupplyMenuRouteTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=600)
 public static void menu_routes(GameTestHelper h){h.runAfterDelay(40,()->{try{run(h);}catch(Exception e){throw new RuntimeException(e);}});}
 private static void run(GameTestHelper h)throws Exception{
  var rows=new ArrayList<ProbeResult>();var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
  var a=OutpostMenuTests.place(h,f,f.center().offset(2,0,2));var b=OutpostMenuTests.place(h,f,f.center().offset(34,0,2));
  var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.ownerId(),"MenuOwner"));int colony=f.colony().getID();
  OutpostMenuPlanner.activate(actor,colony,OutpostMenuPlanner.review(actor,colony,"Woods",Set.of(b.getPosition())));
  actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,12));
  actor.getMainHandItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Trail bread"));
  var service=NativeSupplyService.get(h.getLevel());
  SupplyTests.run(rows,"same_district_rejected",()->SupplyTests.reject(()->SupplyRouteActions.create(actor,colony,a.getPosition(),a.getPosition(),64,0,8)));
  SupplyTests.run(rows,"limits_rejected",()->{SupplyTests.reject(()->SupplyRouteActions.create(actor,colony,a.getPosition(),b.getPosition(),0,0,8));SupplyTests.check(service.endpoints(actor.getUUID()).isEmpty());});
  var id=SupplyRouteActions.create(actor,colony,a.getPosition(),b.getPosition(),64,0,8);
  SupplyTests.run(rows,"home_outpost_pair",()->SupplyTests.eq(service.routes(actor.getUUID()).size(),1));
  SupplyTests.run(rows,"held_components_preserved",()->{SupplyTests.check(ItemStack.isSameItemSameComponents(service.settings(id).item(),actor.getMainHandItem()));SupplyTests.eq(actor.getMainHandItem().getCount(),12);});
  SupplyTests.run(rows,"compatible_binding_reused",()->{SupplyRouteActions.create(actor,colony,b.getPosition(),a.getPosition(),64,0,8);SupplyTests.eq(service.endpoints(actor.getUUID()).size(),2);});
  SupplyTests.run(rows,"empty_hand_rejected",()->{var stack=actor.getMainHandItem();actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);try{SupplyTests.reject(()->SupplyRouteActions.create(actor,colony,a.getPosition(),b.getPosition(),64,0,8));}finally{actor.setItemInHand(InteractionHand.MAIN_HAND,stack);}});
  SupplyTests.run(rows,"pause_resume",()->{SupplyRouteActions.change(actor,colony,id,SupplyRouteActions.RouteAction.PAUSE);SupplyTests.check(service.settings(id).paused());SupplyRouteActions.change(actor,colony,id,SupplyRouteActions.RouteAction.RESUME);SupplyTests.check(!service.settings(id).paused());});
  SupplyTests.run(rows,"foreign_colony_rejected",()->SupplyTests.reject(()->SupplyRouteActions.change(actor,99999,id,SupplyRouteActions.RouteAction.STOP)));
  SupplyTests.run(rows,"invalid_page_no_create",()->{var open=SupplyMenuSessions.open(actor,colony);int before=service.routes(actor.getUUID()).size();var request=new dev.colonyhorizons.network.SupplyMenuPayloads.MenuRequest(open.session(),1,dev.colonyhorizons.network.SupplyMenuPayloads.Action.CREATE_ROUTE,colony,999,0,0,"",List.of(a.getPosition(),b.getPosition()),new UUID(0,0),64,0,8);SupplyTests.check(!SupplyMenuSessions.handle(actor,request).ok());SupplyTests.eq(service.routes(actor.getUUID()).size(),before);SupplyMenuSessions.close(actor.getUUID());});
  SupplyTests.run(rows,"duplicate_create_once",()->{var open=SupplyMenuSessions.open(actor,colony);int before=service.routes(actor.getUUID()).size();var request=new dev.colonyhorizons.network.SupplyMenuPayloads.MenuRequest(open.session(),1,dev.colonyhorizons.network.SupplyMenuPayloads.Action.CREATE_ROUTE,colony,0,0,0,"",List.of(a.getPosition(),b.getPosition()),new UUID(0,0),64,0,8);SupplyTests.check(SupplyMenuSessions.handle(actor,request).ok());SupplyTests.check(SupplyMenuSessions.handle(actor,request).ok());SupplyTests.eq(service.routes(actor.getUUID()).size(),before+1);SupplyMenuSessions.close(actor.getUUID());});
  SupplyTests.run(rows,"stop_with_cargo",()->{var tile=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)h.getLevel().getBlockEntity(a.getPosition());tile.getInventory().setStackInSlot(0,actor.getMainHandItem().copyWithCount(8));service.tick(100);SupplyTests.eq(service.route(id).cargo().getCount(),8);SupplyRouteActions.change(actor,colony,id,SupplyRouteActions.RouteAction.STOP);SupplyTests.eq(service.route(id).cargo().getCount(),8);SupplyTests.check(service.settings(id).disabled());});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"MenuRoutes",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
