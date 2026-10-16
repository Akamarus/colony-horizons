// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import dev.colonyhorizons.menu.*;
import dev.colonyhorizons.network.SupplyMenuPayloads.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import java.nio.file.Path;
@PrefixGameTestTemplate(false)
public final class ExplorationMenuTests {
 static MenuRequest req(UUID session,long n,Action a,ExplorationFixture f,CompoundTag form,UUID selected){return new MenuRequest(session,n,a,f.colony().colony().getID(),0,0,0,"",List.of(),selected,0,0,0,form);}
 static UUID open(ExplorationFixture f){SupplyMenuSessions.clear();var tag=new CompoundTag();tag.putLong("surveyHut",f.hut().getPosition().asLong());var reply=SupplyMenuSessions.handle(f.owner(),req(UUID.randomUUID(),0,Action.OPEN,f,tag,UUID.randomUUID()));SupplyTests.check(reply.ok());return reply.session();}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300)
 public static void menus(GameTestHelper h){var p=h.absolutePos(new BlockPos(5,2,5));var c=new net.minecraft.world.level.ChunkPos(p);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)h.getLevel().setChunkForced(c.x+x,c.z+z,true);h.runAfterDelay(40,()->{try{run(h,p);}catch(Exception e){throw new RuntimeException(e);}});}
 static void run(GameTestHelper h,BlockPos p)throws Exception{var f=ExplorationFixture.create(h.getLevel(),p);var rows=new ArrayList<ProbeResult>();var view=SurveyMenuService.view(f.owner(),f.colony().colony().getID(),f.hut().getPosition());
  SupplyTests.run(rows,"locked_discover_only",()->{var row=row(view,"minecraft:poppy");SupplyTests.check(!row.getBoolean("unlocked")&&row.getBoolean("gatherable"));var form=new CompoundTag();form.putString("target","minecraft:poppy");SupplyTests.check(!SupplyMenuSessions.handle(f.owner(),req(open(f),1,Action.valueOf("START_GATHERING"),f,form,UUID.randomUUID())).ok());});
  SupplyTests.run(rows,"unlocked_costs_and_yield",()->{var row=row(view,"minecraft:kelp");SupplyTests.check(row.getBoolean("unlocked")&&row.getBoolean("gatherable")&&row.getInt("yield")==16&&row.getInt("bread")==2&&row.getInt("durationTicks")==6000&&row.getString("biome").equals("minecraft:ocean"));});
  SupplyTests.run(rows,"discovery_only_fruit",()->SupplyTests.check(!row(view,"minecraft:pumpkin").getBoolean("gatherable")));
  SupplyTests.run(rows,"native_tabs_preserved",()->SupplyTests.check(f.hut().getFirstModuleOccurance(SurveyorMenuModule.class)!=null&&f.hut().getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.MIN_STOCK)!=null&&f.hut().getMaxBuildingLevel()==5));
  SupplyTests.run(rows,"foreign_hut_denied",()->{var other=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Other"));other.setPos(f.owner().getX(),f.owner().getY(),f.owner().getZ());try{SurveyMenuService.view(other,f.colony().colony().getID(),f.hut().getPosition());throw new AssertionError("Foreign owner accepted");}catch(SecurityException expected){}});
  SupplyTests.run(rows,"outpost_entry_removed",()->{try(var in=dev.colonyhorizons.menu.SurveyMenuService.class.getResourceAsStream("/assets/colonyhorizons/gui/supply/surveys.xml")){SupplyTests.check(in!=null&&!new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("id=\"outposts\""));}catch(java.io.IOException e){throw new RuntimeException(e);}});
  var session=open(f);var form=new CompoundTag();form.putString("target","minecraft:kelp");form.putInt("yield",999);form.putInt("bread",0);var start=req(session,1,Action.valueOf("START_GATHERING"),f,form,UUID.randomUUID());var reply=SupplyMenuSessions.handle(f.owner(),start);
  SupplyTests.run(rows,"forged_yield_ignored",()->SupplyTests.check(reply.ok()&&GatheringSavedData.get(h.getLevel()).active(f.colony().colony().getID()).orElseThrow().offer().count()==16));
  SupplyTests.run(rows,"replayed_start_no_charge",()->SupplyTests.check(SupplyMenuSessions.handle(f.owner(),start).ok()&&ExplorationRestartTests.count(f.hut().getTileEntity().getInventory(),net.minecraft.world.item.Items.BREAD)==2&&GatheringSavedData.get(h.getLevel()).records().size()==1));
  var record=GatheringSavedData.get(h.getLevel()).active(f.colony().colony().getID()).orElseThrow();var recall=new CompoundTag();recall.putLong("revision",record.revision()+100);
  SupplyTests.run(rows,"stale_recall_rejected",()->SupplyTests.check(!SupplyMenuSessions.handle(f.owner(),req(session,2,Action.valueOf("RECALL_GATHERING"),f,recall,record.id())).ok()&&GatheringSavedData.get(h.getLevel()).find(record.id()).orElseThrow().state()==GatheringRecord.State.GATHERING));
  h.getLevel().removeBlock(f.hut().getPosition(),false);SupplyTests.run(rows,"destroyed_hut_denied",()->SupplyTests.check(!SupplyMenuSessions.handle(f.owner(),req(session,3,Action.REFRESH,f,new CompoundTag(),record.id())).ok()));
  RemoteActivityTests.connect(f.owner(),false);SupplyMenuSessions.clear();ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ExplorationMenus",rows);h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
 }
 static CompoundTag row(CompoundTag view,String target){return view.getList("resources",10).stream().map(t->(CompoundTag)t).filter(t->t.getString("target").equals(target)).findFirst().orElseThrow();}
}
