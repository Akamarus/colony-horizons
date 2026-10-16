package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.menu.RemoteMenuService;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class RemoteMenuTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void remote_menu(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();var env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(),Map.of()),5);
  var form=new CompoundTag();form.putString("name","Remote Woods");form.putLong("center",env.draft.request().center().asLong());form.putLong("home",env.home.center().asLong());form.putInt("cap",1);form.put("budget",new ListTag());
  SupplyTests.run(rows,"remote_owner_only",()->{var other=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Stranger"));reject(()->RemoteMenuService.options(other,env.home.colony().getID()));});
  SupplyTests.run(rows,"remote_party_limit",()->{var bad=form.copy();bad.putIntArray("citizens",java.util.stream.IntStream.rangeClosed(1,33).toArray());reject(()->RemoteMenuService.review(env.owner,env.home.colony().getID(),bad));});
  SupplyTests.run(rows,"remote_requires_warehouse",()->reject(()->RemoteMenuService.review(env.owner,env.home.colony().getID(),form)));
  var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","craftsmanship/storage/warehouse1.blueprint",l.registryAccess());com.minecolonies.api.blocks.ModBlocks.blockHutWareHouse.setup(env.owner,l,env.home.center(),bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","craftsmanship/storage/warehouse1.blueprint");
  SupplyTests.run(rows,"remote_review_no_effects",()->{int before=RemoteDevelopmentData.get(l).all().size();var draft=RemoteMenuService.review(env.owner,env.home.colony().getID(),form);SupplyTests.check(draft.blockers().isEmpty()&&draft.buildings().size()==8&&RemoteDevelopmentData.get(l).all().size()==before&&!RemoteDevelopmentData.get(l).hasClaimJournal(draft.id()));});
  SupplyTests.run(rows,"remote_invalid_budget",()->{var bad=form.copy();var list=new ListTag();var item=new CompoundTag();item.put("item",new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD).save(l.registryAccess()));item.putInt("allowance",-1);item.putInt("reserve",0);list.add(item);bad.put("budget",list);reject(()->RemoteMenuService.review(env.owner,env.home.colony().getID(),bad));});
  SupplyTests.run(rows,"remote_proposal_cannot_resume",()->reject(()->RemoteMenuService.pause(env.owner,env.home.colony().getID(),env.draft.id(),0,false)));
  SupplyTests.run(rows,"remote_summary_truthful",()->{var summary=RemoteMenuService.mayor(env.owner,env.home.colony().getID(),env.draft.id());SupplyTests.check(summary.getString("phase").equals("PROPOSED")&&summary.getInt("completed")==0&&summary.getInt("total")==10&&summary.getInt("people")==5&&!summary.getBoolean("courierReady"));});
  SupplyTests.run(rows,"remote_options_bounded",()->{var options=RemoteMenuService.options(env.owner,env.home.colony().getID());SupplyTests.check(options.getList("citizens",10).size()==5&&options.getList("warehouses",10).size()==1);});
  SupplyTests.run(rows,"remote_shortage_no_orphan",()->{int before=RemoteDevelopmentData.get(l).all().size();var draft=RemoteMenuService.review(env.owner,env.home.colony().getID(),form);var result=RemoteMenuService.approve(env.owner,env.home.colony().getID(),draft,Mode.APPROVAL);SupplyTests.check(!result.blockers().isEmpty()&&RemoteDevelopmentData.get(l).all().size()==before);});
  SupplyTests.run(rows,"remote_review_replay",()->{var open=dev.colonyhorizons.menu.SupplyMenuSessions.open(env.owner,env.home.colony().getID());var request=new dev.colonyhorizons.network.SupplyMenuPayloads.MenuRequest(open.session(),1,dev.colonyhorizons.network.SupplyMenuPayloads.Action.REVIEW_REMOTE,env.home.colony().getID(),0,0,0,"",List.of(),new UUID(0,0),0,0,0,form);var a=dev.colonyhorizons.menu.SupplyMenuSessions.handle(env.owner,request);var b=dev.colonyhorizons.menu.SupplyMenuSessions.handle(env.owner,request);SupplyTests.check(a.ok()&&a.equals(b)&&a.data().contains("remoteReview"));});
  SupplyTests.run(rows,"remote_wire_copy",()->{var input=form.copy();var request=new dev.colonyhorizons.network.SupplyMenuPayloads.MenuRequest(UUID.randomUUID(),1,dev.colonyhorizons.network.SupplyMenuPayloads.Action.REVIEW_REMOTE,env.home.colony().getID(),0,0,0,"",List.of(),new UUID(0,0),0,0,0,input);input.putString("name","Changed");SupplyTests.check(request.remote().getString("name").equals("Remote Woods"));var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{dev.colonyhorizons.network.SupplyMenuPayloads.MenuRequest.CODEC.encode(buffer,request);SupplyTests.check(request.equals(dev.colonyhorizons.network.SupplyMenuPayloads.MenuRequest.CODEC.decode(buffer)));}finally{buffer.release();}});
  SupplyTests.run(rows,"remote_coordinate_bounds",()->{SupplyTests.check(dev.colonyhorizons.menu.RemoteMenuInput.center("100","64","-100").getY()==64);reject(()->dev.colonyhorizons.menu.RemoteMenuInput.center("100","4160","-100"));reject(()->dev.colonyhorizons.menu.RemoteMenuInput.center("67108864","64","0"));reject(()->dev.colonyhorizons.menu.RemoteMenuInput.center("-2147483648","64","0"));});
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RemoteMenu",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }catch(Throwable e){h.fail(e.toString());}});}
 private static void reject(Runnable work){boolean rejected=false;try{work.run();}catch(IllegalArgumentException|SecurityException expected){rejected=true;}SupplyTests.check(rejected);}
}
