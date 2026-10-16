package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Training against registered blueprint targets plus native guard combat against one supplied hostile. */
@PrefixGameTestTemplate(false)
public final class ArcologyDefenseTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void defense(GameTestHelper h){for(int x=28;x<=36;x++)for(int z=-2;z<=5;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final String[] names={"archery","combatacademy","guardtower"};final List<IBuilding> huts=new ArrayList<>();final List<ICitizenData> workers=new ArrayList<>();
  final List<UUID> ids=new ArrayList<>();final List<net.minecraft.world.phys.Vec3> previous=new ArrayList<>();final List<ProbeResult> rows=new ArrayList<>();
  final double[] initialXp=new double[3];final boolean[] done=new boolean[3];boolean arrowObserved,dummyObserved,guardDamageObserved;int ticks;Husk hostile;OutpostProbeFixture fixture;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=580;x++)for(int z=-16;z<=72;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"DefenseProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(512,16,0);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(528,16,24),6,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   String[] paths={"military/archery5.blueprint","military/combatacademy5.blueprint","military/guardtower5.blueprint"};BlockPos[] anchors={new BlockPos(512,16,0),new BlockPos(544,16,0),new BlockPos(512,16,40)};
   for(int i=0;i<3;i++){
    var b=setup.place(paths[i],anchors[i],true);huts.add(b);var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Citizen spawn");workers.add(c);
    var e=c.getEntity().orElseThrow();e.setPos(anchors[i].getX()+2.5,16,anchors[i].getZ()-4.5);ids.add(e.getUUID());previous.add(e.position());
    boolean assigned=switch(i){case 0->b.getModule(BuildingModules.ARCHERY_WORK_HOME).assignCitizen(c);case 1->b.getModule(BuildingModules.KNIGHT_TRAINING).assignCitizen(c);default->b.getModule(BuildingModules.KNIGHT_TOWER_WORK).assignCitizen(c);};check(assigned,"Native assignment "+names[i]);
    c.getInventory().setStackInSlot(8,new ItemStack(Items.BREAD,64));c.setSaturation(20);initialXp[i]=c.getCitizenSkillHandler().getTotalXP();
    ((MinecoloniesAdvancedPathNavigate)e.getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());
   }
   var archery=(BuildingArchery)huts.get(0);var random=level.getRandom();var target=archery.getRandomShootingTarget(random);check(target!=null&&level.getBlockState(target).is(Blocks.TARGET)&&archery.getRandomShootingStandPosition(random)!=null,"Missing registered blueprint archery target/stand");
   var academy=(BuildingCombatAcademy)huts.get(1);var dummy=academy.getRandomCombatTarget(random);check(dummy!=null&&level.getBlockState(dummy).getBlock() instanceof net.minecraft.world.level.block.HayBlock&&level.getBlockState(dummy.above()).getBlock() instanceof net.minecraft.world.level.block.CarvedPumpkinBlock,"Missing registered blueprint training dummy: native registration requires carved-pumpkin block above hay; plain pumpkin is invalid; registered target="+dummy);
   supply(0,Items.BOW);supply(1,Items.IRON_SWORD);supply(1,Items.SHIELD);
   for(var item:new Item[]{Items.IRON_SWORD,Items.SHIELD,Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS})supply(2,item);
   schedule();
  }catch(Throwable e){fail(e);}}
  void supply(int i,Item item){check(ItemHandlerHelper.insertItemStacked(huts.get(i).getItemHandlerCap((Direction)null),new ItemStack(item),false).isEmpty(),"Equipment insertion "+item);}
  int damage(int i,Item item){var inv=workers.get(i).getInventory();int n=0;for(int s=0;s<inv.getSlots();s++)if(inv.getStackInSlot(s).is(item))n+=inv.getStackInSlot(s).getDamageValue();return n;}
  int stat(int i,String key){return huts.get(i).getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal(key);}
  void pass(int i,String evidence)throws Exception{done[i]=true;workers.get(i).setPaused(true);rows.add(new ProbeResult(names[i]+"_cycle",ProbeResult.Outcome.PASS,evidence+" Prebuilt level5 Arcology hut, equipment, food and claim/research fixtures supplied. Same worker/assignment with navigation recovery disabled. Other levels, restart, promotion and raids not certified."));ProbeReport.write(output,"ArcologyDefense",rows);}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;
   for(int i=0;i<3;i++){var c=workers.get(i);var e=c.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(ids.get(i))&&c.getWorkBuilding()==huts.get(i),"Worker identity/assignment lost "+names[i]);check(e.position().distanceTo(previous.get(i))<=12,"Recovery teleport "+names[i]);previous.set(i,e.position());c.setSaturation(20);}
   var archer=workers.get(0);arrowObserved|=!level.getEntitiesOfClass(Arrow.class,new AABB(490,10,-20,534,50,24),a->a.getOwner()!=null&&a.getOwner().getUUID().equals(ids.get(0))).isEmpty();
   if(!done[0]&&stat(0,"arrows_fired")>0&&stat(0,"arrows_hit")>0&&archer.getCitizenSkillHandler().getTotalXP()>initialXp[0]){check(arrowObserved,"No worker-owned native arrow observed");pass(0,"Native archer trained from a registered blueprint shooting stand, fired a worker-owned arrow at a registered target, recorded native arrows-fired/hit statistics and gained XP. No projectile, XP or training-state injection.");}
   var trainee=workers.get(1);dummyObserved|=trainee.getJob().getWorkerAI()!=null&&trainee.getJob().getWorkerAI().getState()==com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.KNIGHT_ATTACK_DUMMY;
   if(!done[1]&&dummyObserved&&damage(1,Items.IRON_SWORD)>0&&trainee.getCitizenSkillHandler().getTotalXP()>initialXp[1])pass(1,"Native academy trainee navigated to a registered blueprint hay/pumpkin dummy, executed dummy training, damaged the supplied sword and gained XP. No XP, partner or training-state injection; partner sparring is not certified.");
   if(!done[2]){
    var guard=workers.get(2).getEntity().orElseThrow();
    if(hostile==null&&ticks>=1000){hostile=EntityType.HUSK.create(level);check(hostile!=null,"Hostile spawn");hostile.setPos(516.5,16,35.5);hostile.setPersistenceRequired();hostile.setNoAi(true);level.addFreshEntity(hostile);}
    if(hostile!=null){guardDamageObserved|=hostile.getHealth()<hostile.getMaxHealth()&&hostile.getLastHurtByMob()==guard;if(!hostile.isAlive()&&stat(2,"mob_killed;entity.minecraft.husk")>0){check(guardDamageObserved&&hostile.getLastHurtByMob()==guard,"Kill lacks native guard damage attribution");pass(2,"Native knight guard independently acquired and killed one supplied full-health stationary husk, with worker-attributed damage/death and native mob-killed statistics. Husk AI disabled solely to isolate guard targeting; pursuit of moving targets, incoming damage and raid defense not certified.");}}
   }
   if(rows.size()==3){h.succeed();return;}
   if(ticks%400==0){var s=new StringBuilder("tick="+ticks+" arrow="+arrowObserved+" dummy="+dummyObserved+" hostile="+(hostile==null?"none":hostile.getHealth())+"\n");for(int i=0;i<3;i++){var c=workers.get(i);s.append(names[i]).append(" done=").append(done[i]).append(" pos=").append(c.getEntity().orElseThrow().position()).append(" XP=").append(c.getCitizenSkillHandler().getTotalXP()).append(" baseline=").append(initialXp[i]).append(" AI=").append(c.getJob().getWorkerAI()==null?"none":c.getJob().getWorkerAI().getState()).append('\n');for(var r:((com.minecolonies.core.colony.buildings.AbstractBuilding)huts.get(i)).getOpenRequestsOfCitizenOrBuilding(c.getId(),r->true))s.append(r.getLongDisplayString().getString()).append(' ').append(r.getState()).append('\n');}Files.writeString(output.resolve("defense-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   check(ticks<60000,"Defense timeout; see diagnostics");schedule();
  }
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("defense_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyDefense",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
