package dev.colonyhorizons.qa.outpost;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.research.*;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.entity.pathfinding.navigation.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.*;
import java.util.*;

/** Native education/service outcomes. Fixture supplies population, study/cure resources and a zero-progress research start. */
@PrefixGameTestTemplate(false)
public final class ArcologyCivicTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=65000)
 public static void services(GameTestHelper h){for(int x=28;x<=38;x++)for(int z=-2;z<=7;z++)h.getLevel().setChunkForced(x,z,true);h.runAfterDelay(40,()->new Run(h).start());}
 static final class Run {
  final GameTestHelper h; final ServerLevel level; final Path output=Path.of(System.getProperty("colonyhorizons.probeOutput"));
  final List<ProbeResult> rows=new ArrayList<>();final Set<String> passed=new HashSet<>();
  final List<ICitizenData> citizens=new ArrayList<>(); final List<UUID> ids=new ArrayList<>();final List<net.minecraft.world.phys.Vec3> positions=new ArrayList<>();
  OutpostProbeFixture fixture; IBuilding library,school,university,tavern,hospital; ICitizenData student,teacher,pupil,researcher,healer,patient; ILocalResearch research;
  com.minecolonies.core.datalistener.model.Disease disease; double studentXp,pupilXp; int studentLevel,pupilLevel,ticks; boolean sawPatient,diseaseApplied;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void start(){try{
   level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
   for(int x=468;x<=600;x++)for(int z=-16;z<=100;z++)for(int y=13;y<16;y++)level.setBlock(new BlockPos(x,y,z),(y==15?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),18);
   var profile=new GameProfile(UUID.randomUUID(),"CivicProbe");level.getServer().getProfileCache().add(profile);fixture=OutpostProbeFixture.create(level,new BlockPos(480,16,0),profile.getId());
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,profile);
   setup.owner=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());setup.owner.connection=fake.connection;setup.owner.setPos(528,16,32);
   fixture.colony().getPackageManager().addImportantColonyPlayer(setup.owner);fixture.colony().getPackageManager().addCloseSubscriber(setup.owner);
   com.minecolonies.core.util.ChunkDataHelper.staticClaimInRange((com.minecolonies.core.colony.Colony)fixture.colony(),true,new BlockPos(528,16,32),6,level,false);
   setup.place("fundamentals/townhall5.blueprint",new BlockPos(480,16,0),true);
   library=setup.place("education/library5.blueprint",new BlockPos(512,16,0),true);
   school=setup.place("education/school5.blueprint",new BlockPos(552,16,0),true);
   university=setup.place("education/university5.blueprint",new BlockPos(512,16,40),true);
   tavern=setup.place("fundamentals/tavern3.blueprint",new BlockPos(552,16,40),true);

   hospital=setup.place("fundamentals/hospital5.blueprint",new BlockPos(512,16,80),true);
   student=citizen(library,false);check(library.getModule(BuildingModules.STUDENT_WORK).assignCitizen(student),"Student assignment");
   teacher=citizen(school,false);check(school.getModule(BuildingModules.TEACHER_WORK).assignCitizen(teacher),"Teacher assignment");
   pupil=citizen(school,true);check(school.getModule(BuildingModules.PUPIL_WORK).assignCitizen(pupil),"Pupil assignment");
   researcher=citizen(university,false);check(university.getModule(BuildingModules.UNIVERSITY_WORK).assignCitizen(researcher),"Researcher assignment");
   healer=citizen(hospital,false);check(hospital.getModule(BuildingModules.HEALER_WORK).assignCitizen(healer),"Healer assignment");patient=citizen(hospital,false);
   studentXp=xp(student);pupilXp=xp(pupil);studentLevel=student.getCitizenSkillHandler().getLevel(Skill.Intelligence);pupilLevel=pupil.getCitizenSkillHandler().getLevel(Skill.Intelligence);
   insert(library,new ItemStack(Items.PAPER,64));insert(school,new ItemStack(Items.PAPER,64));
   check(((BuildingSchool)school).getRandomPlaceToSit()!=null,"No native school study seat");
   var global=IGlobalResearchTree.getInstance().getResearch(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecolonies","civilian/stamina"));check(global!=null,"Missing loaded stamina research");
   global.startResearch(fixture.colony().getResearchManager().getResearchTree());research=fixture.colony().getResearchManager().getResearchTree().getResearch(global.getBranch(),global.getId());check(research!=null&&research.getProgress()==0,"Research must start at zero");
   disease=com.minecolonies.core.datalistener.DiseasesListener.getDiseases().stream().filter(d->!d.cureItems().isEmpty()).findFirst().orElseThrow();
   for(var cure:disease.cureItems()){var stack=cure.getItemStack().copy();stack.setCount(cure.getAmount()*4);insert(hospital,stack);}
   check(!patient.getCitizenDiseaseHandler().isSick(),"Patient must start healthy before disease fixture");
   check(fixture.colony().getCitizenManager().getCurrentCitizenCount()>com.minecolonies.api.IMinecoloniesAPI.getInstance().getConfig().getServer().initialCitizenAmount.get(),"Disease requires population above initial citizen threshold");
   check(tavern.getModule(BuildingModules.TAVERN_VISITOR).getExternalCitizens().isEmpty(),"Visitors must start absent");
   double before=com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler.getMysticalSiteFactor(fixture.colony());
   setup.place("mystic/mysticalsite5.blueprint",new BlockPos(552,16,80),true);
   double after=com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler.getMysticalSiteFactor(fixture.colony());
   check(before==1&&after==2.5,"Mystical native happiness factor unchanged");
   pass("mysticalsite_happiness","Native happiness calculation increased from 1.0 to 2.5 after placing Arcology level5 mystical site. Prebuilt fixture; resurrection and lower levels not tested.");schedule();
  }catch(Throwable e){fail(e);}}
  ICitizenData citizen(IBuilding b,boolean child){var c=fixture.createCitizen();check(c!=null&&c.getEntity().isPresent(),"Citizen spawn");c.setIsChild(child);if(child)c.getEntity().orElseThrow().setIsChild(true);c.getCitizenSkillHandler().init(1);var p=b.getPosition();c.getEntity().orElseThrow().setPos(p.getX()+2.5,16,p.getZ()-4.5);c.setSaturation(20);c.getInventory().setStackInSlot(8,new ItemStack(Items.GOLDEN_CARROT,64));((MinecoloniesAdvancedPathNavigate)c.getEntity().orElseThrow().getNavigation()).setStuckHandler(PathingStuckHandler.createStuckHandler());citizens.add(c);ids.add(c.getEntity().orElseThrow().getUUID());positions.add(c.getEntity().orElseThrow().position());return c;}
  void insert(IBuilding b,ItemStack s){check(ItemHandlerHelper.insertItemStacked(b.getItemHandlerCap((Direction)null),s,false).isEmpty(),"Resource insertion");}
  double xp(ICitizenData c){return c.getCitizenSkillHandler().getSkills().get(Skill.Intelligence).getExperience();}
  boolean learned(ICitizenData c,double prior,int priorLevel){return xp(c)>prior||c.getCitizenSkillHandler().getLevel(Skill.Intelligence)>priorLevel;}
  void schedule(){h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;for(int i=0;i<citizens.size();i++){var c=citizens.get(i);var e=c.getEntity().orElseThrow();check(e.isAlive()&&e.getUUID().equals(ids.get(i)),"Citizen identity lost");check(e.position().distanceTo(positions.get(i))<=12,"Recovery teleport");positions.set(i,e.position());c.setSaturation(20);}
   // Native setDisease rejects inactive colonies; owner subscription becomes active on colony ticks.
   // Apply this one-time disclosed disease fixture only after that prerequisite is true.
   if(!diseaseApplied){
    if(fixture.colony().isActive()){
     check(patient.getCitizenDiseaseHandler().setDisease(disease),"Native disease fixture rejected despite active colony/population; sick="+patient.getCitizenDiseaseHandler().isSick()+" job="+patient.getJob());
     check(patient.getCitizenDiseaseHandler().isSick()&&patient.getCitizenDiseaseHandler().getDisease().id().equals(disease.id()),"Native disease fixture not applied");
     diseaseApplied=true;
    }else check(ticks<2000,"Colony did not activate for disease fixture");
   }
   if(!passed.contains("library_learning")&&learned(student,studentXp,studentLevel)&&library.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("int_leveled")>0)pass("library_learning","Native student fetched supplied paper and gained intelligence XP; native study statistic observed. Level5 prebuilt library, low starting skills, food and paper supplied; recovery disabled.");
   if(!passed.contains("school_teaching")&&learned(pupil,pupilXp,pupilLevel)&&school.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("lessons_given")>0)pass("school_teaching","Native teacher taught a naturally seated child and pupil intelligence XP increased. Child population, low initial skills and hut paper supplied; no sitting/AI/XP injection after start.");
   if(!passed.contains("university_research")&&research.getProgress()>=5)pass("university_research","Native university colony ticks advanced loaded "+research.getId()+" from zero to "+research.getProgress()+" with assigned researcher. Research start supplied via native startResearch (requirements/cost bypass); no progress injection. Completion and researcher mana bonus not certified.");
   var visitors=tavern.getModule(BuildingModules.TAVERN_VISITOR).getExternalCitizens();
   if(!passed.contains("tavern_visitor")&&!visitors.isEmpty()){var v=fixture.colony().getVisitorManager().getVisitor(visitors.get(0));if(v!=null&&v.getEntity().isPresent()&&v.getEntity().orElseThrow().isAlive()&&v.getHomeBuilding()==tavern&&tavern.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("new_visitors")>0)pass("tavern_visitor","Native timed tavern logic spawned a living visitor assigned to Arcology tavern and incremented new_visitors. No spawnVisitor call or cooldown injection; recruitment and music not tested.");}
   sawPatient|=diseaseApplied&&!((BuildingHospital)hospital).getPatients().isEmpty();
   if(!passed.contains("hospital_treatment")&&diseaseApplied&&sawPatient&&!patient.getCitizenDiseaseHandler().isSick()&&hospital.getModule(BuildingModules.STATS_MODULE).getBuildingStatisticsManager().getStatTotal("diseases_treated;"+disease.name().getString())>0)pass("hospital_treatment","Native healer registered and treated fixture disease "+disease.id()+", native treatment statistic incremented and patient recovered. Cure resources supplied only to hospital; native free cure may occur. No patient-state, cure, healing or AI-state injection.");
   if(ticks%1000==0){var s=new StringBuilder("tick="+ticks+" passed="+passed+" research="+research.getProgress()+" sick="+patient.getCitizenDiseaseHandler().isSick()+" patients="+((BuildingHospital)hospital).getPatients().size()+"\n");for(var c:citizens)s.append(c.getName()).append(" position=").append(c.getEntity().orElseThrow().position()).append(" job=").append(c.getJob()==null?"none":c.getJob().getWorkerAI()==null?"no AI":c.getJob().getWorkerAI().getState()).append(" XP=").append(xp(c)).append("\n");Files.writeString(output.resolve("civic-diagnostics.txt"),s,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
   if(passed.size()==6){h.succeed();return;}check(ticks<60000,"Civic timeout; passed="+passed);schedule();
  }
  void pass(String id,String detail)throws Exception{passed.add(id);rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,detail));ProbeReport.write(output,"ArcologyCivic",rows);}
  void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
  void fail(Throwable e){try{rows.add(new ProbeResult("civic_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyCivic",rows);}catch(Exception x){e.addSuppressed(x);}h.fail(e.toString());}
 }
}
