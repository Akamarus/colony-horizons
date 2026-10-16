package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.menu.*;
import dev.colonyhorizons.network.SupplyMenuPayloads.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class SupplyMenuProtocolTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300)
 public static void protocol(GameTestHelper h)throws Exception{
  var rows=new ArrayList<ProbeResult>();var f=OutpostProbeFixture.create(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),UUID.randomUUID());
  var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.ownerId(),"MenuOwner"));
  var req=new MenuRequest(UUID.randomUUID(),1,Action.REVIEW_OUTPOST,f.colony().getID(),0,0,0,"Woods",List.of(new BlockPos(1,2,3)),new UUID(0,0),64,0,8);
  SupplyTests.run(rows,"round_trip",()->{var buf=new FriendlyByteBuf(Unpooled.buffer());try{MenuRequest.CODEC.encode(buf,req);SupplyTests.check(req.equals(MenuRequest.CODEC.decode(buf)));}finally{buf.release();}});
  SupplyTests.run(rows,"oversized_decode_rejected",()->{boolean rejected=false;var buf=new FriendlyByteBuf(Unpooled.buffer());try{buf.writeZero(65537);try{MenuRequest.CODEC.decode(buf);}catch(RuntimeException expected){rejected=true;}SupplyTests.check(rejected);}finally{buf.release();}});
  SupplyTests.run(rows,"unknown_session_rejected",()->SupplyTests.check(!SupplyMenuSessions.handle(actor,req).ok()));
  var opened=SupplyMenuSessions.open(actor,f.colony().getID());
  SupplyTests.run(rows,"session_actor_bound",()->{var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Other"));SupplyTests.check(!SupplyMenuSessions.handle(stranger,req.withSession(opened.session())).ok());});
  SupplyTests.run(rows,"duplicate_request_cached",()->{var refresh=new MenuRequest(opened.session(),1,Action.REFRESH,f.colony().getID(),0,0,0,"",List.of(),new UUID(0,0),64,0,8);var a=SupplyMenuSessions.handle(actor,refresh);var b=SupplyMenuSessions.handle(actor,refresh);SupplyTests.check(a.ok()&&a.equals(b));});
  SupplyTests.run(rows,"reordered_request_rejected",()->SupplyTests.check(!SupplyMenuSessions.handle(actor,new MenuRequest(opened.session(),0,Action.REFRESH,f.colony().getID(),0,0,0,"",List.of(),new UUID(0,0),64,0,8)).ok()));
  SupplyTests.run(rows,"changed_owner_rejected",()->{var replacement=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Replacement"));f.colony().getPermissions().setOwner(replacement);try{SupplyTests.check(!SupplyMenuSessions.handle(actor,req.withSession(opened.session())).ok());}finally{f.colony().getPermissions().setOwner(actor);}});
  SupplyTests.run(rows,"rate_limit",()->{var open=SupplyMenuSessions.open(actor,f.colony().getID());for(int j=1;j<=4;j++)SupplyMenuSessions.handle(actor,new MenuRequest(open.session(),j,Action.REVIEW_OUTPOST,f.colony().getID(),0,0,0,"",List.of(),new UUID(0,0),64,0,8));var result=SupplyMenuSessions.handle(actor,new MenuRequest(open.session(),5,Action.REVIEW_OUTPOST,f.colony().getID(),0,0,0,"",List.of(),new UUID(0,0),64,0,8));SupplyTests.check(!result.ok()&&result.message().contains("wait"));});
  SupplyTests.run(rows,"expired_session_rejected",()->{var open=SupplyMenuSessions.open(actor,f.colony().getID());try{var field=SupplyMenuSessions.class.getDeclaredField("SESSIONS");field.setAccessible(true);var session=((java.util.Map<?,?>)field.get(null)).get(actor.getUUID());var tick=session.getClass().getDeclaredField("lastTick");tick.setAccessible(true);tick.setLong(session,h.getLevel().getGameTime()-12001);SupplyTests.check(!SupplyMenuSessions.handle(actor,req.withSession(open.session())).ok());}catch(ReflectiveOperationException e){throw new AssertionError(e);}});
  SupplyTests.run(rows,"close_bypasses_view",()->{var open=SupplyMenuSessions.open(actor,f.colony().getID());var close=new MenuRequest(open.session(),1,Action.CLOSE,f.colony().getID(),999,999,999,"",List.of(),new UUID(0,0),64,0,8);SupplyTests.check(SupplyMenuSessions.handle(actor,close).ok());SupplyTests.check(!SupplyMenuSessions.handle(actor,req.withSession(open.session())).ok());});
  SupplyMenuSessions.close(actor.getUUID());
  ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"MenuProtocol",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
}
