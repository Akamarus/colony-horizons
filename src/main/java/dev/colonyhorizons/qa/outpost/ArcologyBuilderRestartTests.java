package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Clean server restarts during real construction and upgrade, retaining native orders. */
@PrefixGameTestTemplate(false)
public final class ArcologyBuilderRestartTests {
 static final String KEY="arcology_builder_restart",PATH="military/gatehouse1.blueprint";
 static final BlockPos BUILDER=new BlockPos(480,16,0),TARGET=new BlockPos(512,16,0);
 static final SavedData.Factory<Expected> FACTORY=new SavedData.Factory<>(Expected::new,Expected::load);
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=180000)
 public static void builder_restart(GameTestHelper h){for(int x=28;x<=35;x++)for(int z=-2;z<=4;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->{try{
  if(phase().equals("seed")){var r=run(h);r.afterSetup=s->{try{var stack=sentinel();stack.setCount(17);s.worker.getInventory().setStackInSlot(7,stack);new Monitor(s,null).start();}catch(Throwable e){h.fail(e.toString());}};r.start();}else restore(h,0);
 }catch(Throwable e){h.fail(e.toString());}});}
 static String phase(){return System.getProperty("colonyhorizons.probePhase","seed");}
 static ArcologyUpgradeTests.Run run(GameTestHelper h){return new ArcologyUpgradeTests.Run(h,List.of(PATH),List.of(TARGET),"ArcologyBuilderRestart");}
 static ItemStack sentinel(){var s=new ItemStack(Items.AMETHYST_SHARD);s.set(DataComponents.CUSTOM_NAME,Component.literal("Builder restart conservation"));return s;}
 static void restore(GameTestHelper h,int waited){try{var e=h.getLevel().getDataStorage().get(FACTORY,KEY);check(e!=null,"Missing builder checkpoint");var colony=IColonyManager.getInstance().getColonyByDimension(e.colony,h.getLevel().dimension());check(colony!=null,"Missing colony");var profile=new GameProfile(e.owner,"UpgradeProbe");var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),profile);var owner=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());owner.connection=fake.connection;owner.setPos(512,16,0);colony.getPackageManager().addImportantColonyPlayer(owner);colony.getPackageManager().addCloseSubscriber(owner);
  var worker=colony.getCitizenManager().getCivilian(e.worker);if(worker==null||worker.getEntity().isEmpty()){check(waited<12000,"Builder failed to load");h.runAfterDelay(20,()->restore(h,waited+20));return;}
  var r=run(h);r.fixture=new OutpostProbeFixture(colony,e.owner,BUILDER);r.owner=owner;r.builder=(BuildingBuilder)colony.getServerBuildingManager().getBuilding(BUILDER);r.targets.add(colony.getServerBuildingManager().getBuilding(TARGET));r.worker=worker;r.workerId=e.entity;r.previous=worker.getEntity().orElseThrow().position();((MinecoloniesAdvancedPathNavigate)worker.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());new Monitor(r,e).start();
 }catch(Throwable e){h.fail(e.toString());}}
 static final class Monitor {
  final ArcologyUpgradeTests.Run r;final Expected saved;final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> required=new LinkedHashMap<>();int wanted,ticks,baseline;boolean requested;
  Monitor(ArcologyUpgradeTests.Run r,Expected e){this.r=r;saved=e;wanted=e==null?1:e.wanted;}
  void start()throws Exception{check(r.builder!=null&&r.targets.getFirst()!=null,"Missing huts");setRequired();baseline=matched();if(saved!=null){check(r.targets.getFirst().getBuildingLevel()==wanted-1,"Checkpoint was not in-progress");var o=r.fixture.colony().getWorkManager().getWorkOrder(saved.order);check(o!=null&&o.getTargetLevel()==wanted&&o.getClaimedBy().equals(BUILDER),"Original claimed work order missing");for(long p:saved.placed){var pos=BlockPos.of(p);check(r.level.getBlockState(pos).getBlock()==required.get(pos).getBlock(),"Placed structure reverted on reload "+pos);}requested=true;}schedule();}
  void setRequired()throws Exception{required.clear();var bp=r.blueprint(PATH.replace("1.blueprint",wanted+".blueprint"));var origin=TARGET.subtract(bp.getPrimaryBlockOffset());for(var info:bp.getBlockInfoAsList()){var s=bp.getBlockState(info.getPos());if(s.is(Blocks.OAK_PLANKS)||s.is(Blocks.SPRUCE_PLANKS)||s.is(Blocks.COBBLESTONE)||s.is(Blocks.STONE_BRICKS)||s.is(Blocks.STONE)||s.is(Blocks.MOSSY_STONE_BRICKS)||s.is(Blocks.CRACKED_STONE_BRICKS))required.put(origin.offset(info.getPos()),s);}check(required.size()>24,"Insufficient structural cells");}
  int matched(){return (int)required.entrySet().stream().filter(e->r.level.getBlockState(e.getKey()).getBlock()==e.getValue().getBlock()).count();}
  void schedule(){r.h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){r.h.fail(e.toString());}});}
  void tick()throws Exception{ticks+=20;r.wanted=wanted;var entity=r.worker.getEntity().orElseThrow();check(entity.isAlive()&&entity.getUUID().equals(r.workerId)&&r.worker.getWorkBuilding()==r.builder&&!r.worker.isPaused(),"Builder identity or assignment changed");check(r.previous.distanceTo(entity.position())<=12,"Builder recovery teleport");r.previous=entity.position();r.worker.setSaturation(20);
   int held=ArcologyOperationsFixture.count(r.worker.getInventory(),sentinel())+ArcologyOperationsFixture.count(r.builder.getItemHandlerCap((Direction)null),sentinel());check(held==17,"Sentinel items lost/duplicated: "+held);
   if(!requested){r.targets.getFirst().requestUpgrade(r.owner,BUILDER);requested=true;}
   r.supply();var order=r.builder.getWorkOrder();int count=matched();
   if(saved!=null&&wanted==saved.wanted&&r.targets.getFirst().getBuildingLevel()<wanted&&order!=null)check(order.getID()==saved.order,"Order replaced instead of resumed");
   boolean checkpoint=phase().equals("seed")||phase().equals("verify_build")&&wanted==2;
   if(checkpoint&&order!=null&&order.getTargetLevel()==wanted&&order.getClaimedBy().equals(BUILDER)&&r.builder.getProgress()!=null&&count-baseline>=24&&count<required.size()&&r.targets.getFirst().getBuildingLevel()==wanted-1){var e=new Expected();e.owner=r.fixture.ownerId();e.entity=r.workerId;e.colony=r.fixture.colony().getID();e.worker=r.worker.getId();e.order=order.getID();e.wanted=wanted;e.placed=required.entrySet().stream().filter(p->r.level.getBlockState(p.getKey()).getBlock()==p.getValue().getBlock()).mapToLong(p->p.getKey().asLong()).toArray();r.level.getDataStorage().set(KEY,e);e.setDirty();r.level.getServer().getProfileCache().save();finish("Saved actual partial level "+wanted+" construction, claimed order "+e.order+", "+count+" structural cells and 17 component-bearing sentinel items.");return;}
   if(r.targets.getFirst().getBuildingLevel()==wanted&&r.fixture.colony().getWorkManager().getWorkOrders().isEmpty()){check(count==required.size(),"Completed with missing structure");if(saved!=null&&wanted==saved.wanted)check(count>baseline,"No fresh structural progress after reload");validate();if(phase().equals("verify_build")&&wanted==1){wanted=2;requested=false;setRequired();baseline=matched();}else{check(phase().equals("verify_upgrade"),"Missed partial checkpoint");finish("Same builder and order resumed actual level2 upgrade; all checked structural cells, supported lamps, single gatehouse connection, 17 sentinel items retained. Construction supplies topped up by fixture; sentinel conservation is not a complete material accounting audit.");return;}}
   if(ticks%400==0)Files.writeString(r.output.resolve("builder-restart-diagnostics.txt"),"ticks="+ticks+" wanted="+wanted+" matches="+count+"/"+required.size()+" baseline="+baseline+" order="+(order==null?"none":order.getID())+" progress="+r.builder.getProgress()+" ai="+r.worker.getJob().getWorkerAI().getState()+" pos="+entity.position()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
   check(ticks<160000,"Builder restart timeout");schedule();
  }
  void validate()throws Exception{var b=r.targets.getFirst();check(b.getStructurePack().equals("Arcology Horizons"),"Style changed");var origin=TARGET.subtract(r.blueprint(PATH.replace("1.blueprint",wanted+".blueprint")).getPrimaryBlockOffset());for(int x:new int[]{2,12}){var p=origin.offset(x,4,7);var s=r.level.getBlockState(p);check(s.is(Blocks.LANTERN)&&s.canSurvive(r.level,p),"Lantern missing/unsupported");}int terminals=0;for(var tag:r.fixture.colony().getConnectionManager().serializeNBT(r.level.registryAccess()).getList("gatehouses",Tag.TAG_COMPOUND))if(com.minecolonies.api.util.BlockPosUtil.read((CompoundTag)tag,"pos").equals(TARGET))terminals++;check(terminals==1,"Connection missing/duplicated");}
  void finish(String evidence)throws Exception{ProbeReport.write(r.output,"ArcologyBuilderRestart",List.of(new ProbeResult("builder_"+phase(),ProbeResult.Outcome.PASS,evidence)));r.h.succeed();}
 }
 static void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
 static final class Expected extends SavedData {UUID owner,entity;int colony,worker,order,wanted;long[] placed;
  public CompoundTag save(CompoundTag t,HolderLookup.Provider p){t.putUUID("owner",owner);t.putUUID("entity",entity);t.putInt("colony",colony);t.putInt("worker",worker);t.putInt("order",order);t.putInt("wanted",wanted);t.putLongArray("placed",placed);return t;}
  static Expected load(CompoundTag t,HolderLookup.Provider p){var e=new Expected();e.owner=t.getUUID("owner");e.entity=t.getUUID("entity");e.colony=t.getInt("colony");e.worker=t.getInt("worker");e.order=t.getInt("order");e.wanted=t.getInt("wanted");e.placed=t.getLongArray("placed");return e;}
 }
}

