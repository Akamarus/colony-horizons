package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.placement.*;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.util.LoadOnlyStructureHandler;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.mayor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.Path;
import java.util.*;

/** Finite Home stock, original citizens and native construction. No resource injection after approval. */
@PrefixGameTestTemplate(false)
public final class WarehouseCompletionTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=300000)
 public static void finite_native_build(GameTestHelper h){h.runAfterDelay(40,()->new Run(h).start());}
 private static final class Run {
  final GameTestHelper h; final List<ProbeResult> rows=new ArrayList<>();
  final boolean full=true;
  final boolean free=System.getProperty("colonyhorizons.foundationSuite").startsWith("FreeFounder");
  final boolean upgrade=System.getProperty("colonyhorizons.foundationSuite").startsWith("MayorUpgrade");
  java.util.UUID upgradeProposal,expansionProposal;
  final boolean expansion=System.getProperty("colonyhorizons.foundationSuite").startsWith("MayorExpansion");
  final String expansionRole=System.getProperty("colonyhorizons.foundationSuite").endsWith("Forester")?"lumberjack":"home";
  final boolean health=System.getProperty("colonyhorizons.foundationSuite").matches("RemoteEndurance|FreeFounderEndurance");
  final Set<Integer> recovered=new HashSet<>();
  final List<BlockPos> trees=new ArrayList<>();final Set<BlockPos> replanted=new HashSet<>();
  final List<net.minecraft.world.level.block.entity.HopperBlockEntity> reserves=new ArrayList<>();
  final boolean settlement=full||System.getProperty("colonyhorizons.foundationSuite").equals("RemoteHousingCompletion");
  boolean courierCarriedMaterials,repeatIllness;long cureImportsBefore;
  Map<ItemKey,Integer> homeRemainder;
  RemoteTestEnvironment env; net.minecraft.server.level.ServerPlayer owner; long started;
  Run(GameTestHelper h){this.h=h;}
  List<Integer> people(){return FoundingRoster.citizenIds(RemoteDevelopmentData.get(h.getLevel()),env.draft.id());}
  void start(){try{
   if(!System.getProperty("colonyhorizons.probePhase","seed").equals("seed")){if(UpgradeInFlightAssertions.enabled())UpgradeInFlightAssertions.reload(h);else WarehouseRestartAssertions.reload(h);return;}
   var l=h.getLevel();h.assertTrue(!com.minecolonies.api.MinecoloniesAPIProxy.getInstance().getConfig().getServer().creativeResolve.get()&&!com.minecolonies.api.util.constant.Constants.BUILDER_INF_RESOURECES,"No creative resource resolution");l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());
   var bill=new HashMap<ItemKey,Integer>();
   for(var path:free?List.of("builder","../craftsmanship/storage/warehouse","../craftsmanship/storage/deliveryman","citizen","citizen","citizen","citizen","lumberjack"):full?List.of("builder","../craftsmanship/storage/warehouse","../craftsmanship/storage/deliveryman","citizen","citizen","citizen","citizen","citizen","lumberjack","lumberjack"):settlement?List.of("builder","citizen"):List.of("builder")){
   var materialBlueprint=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons",(path.startsWith("../")?path.substring(3):"fundamentals/"+path)+"1.blueprint",l.registryAccess());var placer=new StructurePlacer(new LoadOnlyStructureHandler(l,new BlockPos(1000,-58,0),materialBlueprint,RotationMirror.NONE));
   var progress=AbstractBlueprintIterator.NULL_POS;
   for(int guard=0;;guard++){
    h.assertTrue(guard<10000,"Bounded native bill calculation");
    var result=placer.executeStructureStep(l,null,progress,StructurePlacer.Operation.GET_RES_REQUIREMENTS,()->placer.getIterator().increment((info,pos,handler)->false),true);
    for(var item:result.getBlockResult().getRequiredItems())if(!item.isEmpty())bill.merge(new ItemKey(item),item.getCount(),Integer::sum);
    progress=result.getIteratorPos();if(result.getBlockResult().getResult()==BlockPlacementResult.Result.FINISHED)break;
   }
   }
   if(expansion){var extra=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons",(expansionRole.equals("home")?"fundamentals/citizen1.blueprint":"fundamentals/lumberjack1.blueprint"),l.registryAccess());var placer=new StructurePlacer(new LoadOnlyStructureHandler(l,new BlockPos(1200,-58,0),extra,RotationMirror.NONE));var cursor=AbstractBlueprintIterator.NULL_POS;for(int guard=0;;guard++){h.assertTrue(guard<10000,"Bounded expansion stock");var step=placer.executeStructureStep(l,null,cursor,StructurePlacer.Operation.GET_RES_REQUIREMENTS,()->placer.getIterator().increment((info,pos,handler)->false),true);for(var item:step.getBlockResult().getRequiredItems())if(!item.isEmpty())bill.merge(new ItemKey(item),item.getCount(),Integer::sum);cursor=step.getIteratorPos();if(step.getBlockResult().getResult()==BlockPlacementResult.Result.FINISHED)break;}bill.merge(new ItemKey(new ItemStack(expansionRole.equals("home")?ModBlocks.blockHutHome:ModBlocks.blockHutLumberjack)),1,Integer::sum);}
   if(upgrade){var upgradeBp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder2.blueprint",l.registryAccess());var p=new StructurePlacer(new LoadOnlyStructureHandler(l,new BlockPos(1200,-58,0),upgradeBp,RotationMirror.NONE));var cursor=AbstractBlueprintIterator.NULL_POS;for(int guard=0;;guard++){h.assertTrue(guard<10000,"Bounded upgrade stock");var step=p.executeStructureStep(l,null,cursor,StructurePlacer.Operation.GET_RES_REQUIREMENTS,()->p.getIterator().increment((info,pos,handler)->false),true);for(var item:step.getBlockResult().getRequiredItems())if(!item.isEmpty())bill.merge(new ItemKey(item),item.getCount(),Integer::sum);cursor=step.getIteratorPos();if(step.getBlockResult().getResult()==BlockPlacementResult.Result.FINISHED)break;}}
   var stock=new HashMap<>(bill);FoundingConstructionSupplies.operatingReserve().forEach((k,v)->stock.merge(k,v,Integer::sum));(free?FreeFounderGrantTests.starter():FoundingSupplies.bill(5)).forEach((k,v)->stock.merge(k,v,Integer::sum));
   stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutBuilder)),1,Integer::sum);stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutLumberjack)),free?1:2,Integer::sum);stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutHome)),free?4:5,Integer::sum);stock.merge(new ItemKey(new ItemStack(Items.DIRT)),20,Integer::sum);
   stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutWareHouse)),1,Integer::sum);stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutDeliveryman)),1,Integer::sum);
   stock.put(new ItemKey(WarehouseRestartAssertions.cargo()),5);stock.merge(new ItemKey(new ItemStack(Items.BREAD)),128,Integer::sum);stock.merge(new ItemKey(new ItemStack(Items.STONE_AXE)),4,Integer::sum);
   if(health)RemoteCare.starterMedicines().forEach((k,v)->stock.merge(k,32,Integer::sum));
   env=new RemoteTestEnvironment(l,1024,new Budget(stock,Map.of()),free?0:5);
   // Isolate logistics from random disease: one initial fixture immunity period, no health/food refills during work.
   if(!health){var immunity=com.minecolonies.core.entity.citizen.citizenhandlers.CitizenDiseaseHandler.class.getDeclaredField("immunityTicks");immunity.setAccessible(true);for(int citizenId:people())immunity.setInt(env.home.colony().getCitizenManager().getCivilian(citizenId).getCitizenDiseaseHandler(),350000);}
   if(health){for(int x:new int[]{-42,-36,42,50})for(int z:new int[]{8,16,24}){var tree=env.draft.request().center().offset(x,0,z);trees.add(tree);for(int y=0;y<5;y++)l.setBlockAndUpdate(tree.above(y),net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=3;y<=5;y++)if(dx!=0||dz!=0||y==5)l.setBlockAndUpdate(tree.offset(dx,y,dz),net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState());}}
   var homeBp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","craftsmanship/storage/warehouse1.blueprint",l.registryAccess());ModBlocks.blockHutWareHouse.setup(env.owner,l,env.home.center(),homeBp,RotationMirror.NONE,true,"Arcology Horizons","craftsmanship/storage/warehouse1.blueprint");
   var homeBuilding=env.home.colony().getServerBuildingManager().getBuilding(env.home.center());for(int i=0;i<16;i++){var pos=env.home.center().offset(i%4+2,0,i/4);l.setBlockAndUpdate(pos,ModBlocks.blockRack.defaultBlockState());homeBuilding.registerBlockPosition(l.getBlockState(pos),pos,l);}
   var hut=(TileEntityColonyBuilding)l.getBlockEntity(env.home.center());
   // Bootstrap goes in first; surplus stays in real vanilla hoppers loaded before approval.
   var bootstrap=RemoteFoundingService.bootstrapBill(l,env.draft.id());
   for(var e:bootstrap.entrySet())load(hut,e.getKey(),e.getValue());
   for(var e:stock.entrySet())load(hut,e.getKey(),e.getValue()-bootstrap.getOrDefault(e.getKey(),0));
   homeRemainder=new HashMap<>(stock);RemoteFoundingService.bootstrapBill(l,env.draft.id()).forEach((k,v)->homeRemainder.merge(k,-v,Integer::sum));
   var result=RemoteFoundingService.approve(env.owner,env.draft.id(),0);h.assertTrue(result.blockers().isEmpty(),"Finite approval: "+result);
   owner=RemoteActivityTests.loginAtHome(env);RemoteActivityService.tick(l);
   h.runAfterDelay(40,()->arrive());
  }catch(Throwable e){fail(e);}}
  void arrive(){try{
   var l=h.getLevel();var id=env.draft.id();var data=RemoteDevelopmentData.get(l);
   for(int i=0;i<env.draft.buildings().size()+2;i++)h.assertTrue(RemoteFoundingService.advance(l,id).blockers().isEmpty(),"Founding advances");
   if(data.journey(id).orElseThrow().remainingTicks()>0){FoundingTravel.reconcile(l,id);h.runAfterDelay(20,()->arrive());return;}
   h.assertTrue(RemoteFoundingService.advance(l,id).blockers().isEmpty(),"Original paid party supplied");
   if(System.getProperty("colonyhorizons.foundationSuite").matches("WarehouseRestart|MayorUpgradeRestart")){
    h.assertTrue(data.construction(id).isEmpty(),"No initial construction order yet");var reserve=FoundingConstructionSupplies.supply(l,id,env.draft.buildings().getFirst().anchor(),new ItemStack(Items.BREAD),8);h.assertTrue(reserve.progressed(),"Paid operating reserve available between construction orders: "+reserve);
   }
   // The approved original Mayor must be appointed by production founding, not by this fixture.
   h.assertTrue(MayorService.available(l,id),"Original Mayor appointed; no test-driven planning");
   var builder=env.home.colony().getCitizenManager().getCivilian(people().get(1));builder.setPaused(false);env.home.colony().getCitizenManager().getCivilian(FoundingRoster.mayorId(RemoteDevelopmentData.get(l),env.draft.id())).setPaused(false);
   for(int citizenId:people())env.home.colony().getCitizenManager().getCivilian(citizenId).setPaused(false);
   if(health){var disease=com.minecolonies.core.datalistener.DiseasesListener.getDiseases().getFirst();var field=com.minecolonies.core.entity.citizen.citizenhandlers.CitizenDiseaseHandler.class.getDeclaredField("disease");field.setAccessible(true);for(int citizenId:people())field.set(env.home.colony().getCitizenManager().getCivilian(citizenId).getCitizenDiseaseHandler(),disease);}
   // Original native Mayor and builder AI perform all orders, reviews and work.
   started=l.getGameTime();poll();
  }catch(Throwable e){fail(e);}}
  dev.colonyhorizons.network.SupplyMenuPayloads.MenuReply menu(UUID session,long number,dev.colonyhorizons.network.SupplyMenuPayloads.Action action,UUID selected,net.minecraft.nbt.CompoundTag form){return dev.colonyhorizons.menu.SupplyMenuSessions.handle(owner,new dev.colonyhorizons.network.SupplyMenuPayloads.MenuRequest(session,number,action,env.home.colony().getID(),0,0,0,"",List.of(),selected,0,0,0,form));}
  void poll(){try{
   var l=h.getLevel();var id=env.draft.id();var plan=env.draft.buildings().getFirst();
   if(UpgradeInFlightAssertions.enabled()&&UpgradeInFlightAssertions.seed(h,env,owner,upgradeProposal))return;
   for(int personId:people()){var person=env.home.colony().getCitizenManager().getCivilian(personId);person.getEntity().ifPresent(e->h.assertTrue(env.draft.chunks().contains(new net.minecraft.world.level.ChunkPos(e.blockPosition())),"Original founder stays inside settlement"));}
   if(health&&!repeatIllness&&l.getGameTime()-started>=120000&&MayorLogistics.ready(l,id)){
    var disease=com.minecolonies.core.datalistener.DiseasesListener.getDiseases().getFirst();
    // Model exhaustion of local medicine, never add supplies or change Home stock.
    var inventories=new java.util.ArrayList<net.neoforged.neoforge.items.IItemHandlerModifiable>();
    for(var p:env.draft.buildings())if(l.getBlockEntity(p.anchor()) instanceof TileEntityColonyBuilding hut)inventories.add(dev.colonyhorizons.supply.WarehouseInventory.resolve(l,hut).inventory());
    for(int personId:people())inventories.add(env.home.colony().getCitizenManager().getCivilian(personId).getInventory());
    for(var inventory:inventories)for(int slot=0;slot<inventory.getSlots();slot++){var stack=inventory.getStackInSlot(slot);if(disease.cureItems().stream().anyMatch(c->com.minecolonies.core.datalistener.model.Disease.hasCureItem(c).test(stack)))inventory.setStackInSlot(slot,ItemStack.EMPTY);}
    cureImportsBefore=medicineImports();var field=com.minecolonies.core.entity.citizen.citizenhandlers.CitizenDiseaseHandler.class.getDeclaredField("disease");field.setAccessible(true);for(int personId:people())field.set(env.home.colony().getCitizenManager().getCivilian(personId).getCitizenDiseaseHandler(),disease);recovered.clear();repeatIllness=true;System.out.println("REMOTE_REPEAT_ILLNESS local medicine exhausted; Home supply unchanged");
   }
   if(health)for(var tree:trees)if(l.getBlockState(tree).is(net.minecraft.world.level.block.Blocks.OAK_SAPLING))replanted.add(tree);
   if(health)for(int personId:people())if(!env.home.colony().getCitizenManager().getCivilian(personId).getCitizenDiseaseHandler().isSick())recovered.add(personId);
   var courier=env.home.colony().getCitizenManager().getCivilian(FoundingRoster.member(RemoteDevelopmentData.get(h.getLevel()),env.draft.id(),FoundingRoster.Role.COURIER).orElseThrow().citizenId());for(int slot=0;slot<courier.getInventory().getSlots();slot++)if(courier.getInventory().getStackInSlot(slot).getItem() instanceof BlockItem)courierCarriedMaterials=true;
   var result=new StepResult("Observing native Mayor",List.of(),false);var building=(BuildingBuilder)env.home.colony().getServerBuildingManager().getBuilding(plan.anchor());
   if(building.isBuilt()&&building.getBuildingLevel()>=1&&(!settlement||env.draft.buildings().stream().filter(p->full||p.role().equals("builder")||p.operationId().equals(env.draft.buildings().get(3).operationId())).allMatch(p->{var b=env.home.colony().getServerBuildingManager().getBuilding(p.anchor());return b.isBuilt()&&(upgrade?b.getBuildingLevel()>=1:b.getBuildingLevel()==1);}))){
    if(RemoteDevelopmentData.get(l).construction(id).values().stream().filter(e->e.state()==ConstructionJournal.State.COMPLETE).count()<(free?8:full?10:settlement?2:1)){h.runAfterDelay(1,()->poll());return;}
    h.assertTrue(RemoteDevelopmentData.get(l).construction(id).values().stream().anyMatch(e->e.state()==ConstructionJournal.State.COMPLETE),"Native completion journal");
    h.assertTrue(owner.blockPosition().closerThan(env.home.center(),3),"Owner stayed at Home");
    h.assertTrue(env.home.colony().getCitizenManager().getCitizens().size()==(free?4:expansionProposal==null?5:6),"Original five plus only the explicit Home hiring-test adult");
    if(settlement&&!people().stream().map(i->env.home.colony().getCitizenManager().getCivilian(i)).anyMatch(c->c.getHomeBuilding()!=null)){if(l.getGameTime()-started>260000)throw new IllegalStateException("Completed home remains unassigned");h.runAfterDelay(1,()->poll());return;}
    if(settlement)h.assertTrue(people().stream().map(i->env.home.colony().getCitizenManager().getCivilian(i)).anyMatch(c->c.getHomeBuilding()!=null&&env.draft.buildings().stream().anyMatch(home->home.role().equals("home")&&home.anchor().equals(c.getHomeBuilding().getPosition()))),"A founder is assigned to the completed local home");
    var source=(TileEntityColonyBuilding)l.getBlockEntity(env.home.center());for(var e:homeRemainder.entrySet()){int left=0;var sourceInventory=dev.colonyhorizons.supply.WarehouseInventory.resolve(l,source).inventory();for(int slot=0;slot<sourceInventory.getSlots();slot++){var stack=sourceInventory.getStackInSlot(slot);if(ItemStack.isSameItemSameComponents(stack,e.getKey().stack()))left+=stack.getCount();}for(var reserve:reserves)for(int slot=0;slot<reserve.getContainerSize();slot++){var stack=reserve.getItem(slot);if(ItemStack.isSameItemSameComponents(stack,e.getKey().stack()))left+=stack.getCount();}int spent=MayorReservations.get(l).entries().values().stream().mapToInt(entry->entry.dispatched().getOrDefault(e.getKey(),0)).sum();h.assertTrue(left+spent==e.getValue(),"Finite Home conservation for "+e.getKey().stack());}
    if(full)h.assertTrue(people().stream().allMatch(i->env.home.colony().getCitizenManager().getCivilian(i).getHomeBuilding()!=null),"All original founders housed");
    h.assertTrue(dev.colonyhorizons.supply.NativeSupplyService.get(l).routes(env.owner.getUUID()).stream().allMatch(route->dev.colonyhorizons.supply.NativeSupplyService.get(l).endpoints(env.owner.getUUID()).stream().filter(e->e.id().equals(route.source())||e.id().equals(route.destination())).allMatch(e->env.home.colony().getServerBuildingManager().getBuilding(e.position()) instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse)),"Warehouse-only recurring routes");
    h.assertTrue(FoundingPlacement.remaining(l,id).isEmpty(),"Unused paid founding stores released into completed warehouse");
    h.assertTrue(courierCarriedMaterials&&MayorLogistics.ready(l,id),"Original native courier physically carried local construction materials");
    var saved=RemoteDevelopmentData.get(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());var restored=RemoteDevelopmentData.load(saved,l.registryAccess(),l.dimension());h.assertTrue(restored.bootstrapSpent(id).equals(RemoteDevelopmentData.get(l).bootstrapSpent(id)),"Finite bootstrap spending survives load");
    var interrupted=saved.copy();interrupted.getList("developments",10).getCompound(0).putBoolean("bootstrapPending",true);h.assertTrue(RemoteDevelopmentData.load(interrupted,l.registryAccess(),l.dimension()).find(id).orElseThrow().phase()==Phase.RECOVERY_NEEDED,"Interrupted bootstrap handoff quarantined");
    if(health){
     long left=replanted.stream().filter(p->p.getX()<env.draft.request().center().getX()).count(),right=replanted.size()-left;
     var nativeService=dev.colonyhorizons.supply.NativeSupplyService.get(l);var homeEndpoint=nativeService.endpointAt(env.owner.getUUID(),env.home.center());long returned=homeEndpoint.isEmpty()?0:nativeService.routes(env.owner.getUUID()).stream().filter(route->route.destination().equals(homeEndpoint.orElseThrow().id())&&nativeService.settings(route.id()).item().is(Items.OAK_LOG)).mapToLong(route->route.delivered()).sum();
     if(l.getGameTime()-started<240000||left<3||!free&&right<3||returned<8||people().stream().anyMatch(personId->{var person=env.home.colony().getCitizenManager().getCivilian(personId);return person.getSaturation()<=0||person.getCitizenDiseaseHandler().isSick();})||RemoteDevelopmentData.get(l).find(id).orElseThrow().phase()!=Phase.OPERATING){if(l.getGameTime()-started>260000)throw new IllegalStateException("Endurance incomplete: replanted="+left+"/"+right+" returned="+returned+" "+diagnostic(building,result));if((l.getGameTime()-started)%2000==0)System.out.println("REMOTE_ENDURANCE replanted="+left+"/"+right+" returned="+returned+" "+diagnostic(building,result));h.runAfterDelay(1,()->poll());return;}
    }
    if(upgrade){
     if(upgradeProposal==null&&RemoteDevelopmentData.get(l).find(id).orElseThrow().phase()!=Phase.OPERATING){h.runAfterDelay(1,()->poll());return;}
     if(upgradeProposal==null){var proposal=dev.colonyhorizons.mayor.MayorUpgrades.review(owner,id,plan.operationId());h.assertTrue(proposal.state()==MayorProposals.State.PROPOSED&&env.home.colony().getWorkManager().getWorkOrders().isEmpty(),"Review does not queue an upgrade");upgradeProposal=proposal.id();
      var guards=new ArrayList<String>();
      var targetBp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint(plan.pack(),proposal.blueprint(),l.registryAccess());var targetMin=plan.anchor().subtract(targetBp.getPrimaryBlockOffset());var changed=BlockPos.betweenClosed(targetMin,targetMin.offset(targetBp.getSizeX()-1,targetBp.getSizeY()-1,targetBp.getSizeZ()-1)).iterator();BlockPos custom=null;while(changed.hasNext()){var pos=changed.next();if(l.getBlockState(pos).isAir()){custom=pos.immutable();break;}}h.assertTrue(custom!=null,"Custom block test position");
      var ground=targetMin.below();var originalGround=l.getBlockState(ground);l.setBlock(ground,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);boolean supportRefused=false;try{MayorUpgrades.validateApproved(l,RemoteDevelopmentData.get(l).find(id).orElseThrow(),proposal);}catch(IllegalArgumentException expected){supportRefused=true;}finally{l.setBlock(ground,originalGround,3);}if(!supportRefused)guards.add("missing ground accepted");
      l.setBlock(custom,net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),3);boolean refused=false;try{MayorUpgrades.decide(owner,id,proposal.id(),proposal.revision(),true);}catch(IllegalArgumentException expected){refused=true;}l.setBlock(custom,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);h.assertTrue(refused,"Changed player structure blocks approval");
      var session=dev.colonyhorizons.menu.SupplyMenuSessions.open(owner,env.home.colony().getID());var form=new net.minecraft.nbt.CompoundTag();form.putUUID("building",plan.operationId());
      var view=menu(session.session(),1,dev.colonyhorizons.network.SupplyMenuPayloads.Action.VIEW_MAYOR,id,new net.minecraft.nbt.CompoundTag());h.assertTrue(view.ok(),"Open Mayor view: "+view.message());
      var review=menu(session.session(),2,dev.colonyhorizons.network.SupplyMenuPayloads.Action.REVIEW_UPGRADE,id,form);h.assertTrue(review.ok()&&review.data().getCompound("upgrade").getUUID("id").equals(proposal.id()),"Server-held native upgrade review: "+review.message());
      var forged=menu(session.session(),3,dev.colonyhorizons.network.SupplyMenuPayloads.Action.APPROVE_UPGRADE,UUID.randomUUID(),form);h.assertTrue(!forged.ok(),"Reject unreviewed upgrade ID");
      var accepted=menu(session.session(),4,dev.colonyhorizons.network.SupplyMenuPayloads.Action.APPROVE_UPGRADE,proposal.id(),form);var replay=menu(session.session(),4,dev.colonyhorizons.network.SupplyMenuPayloads.Action.APPROVE_UPGRADE,proposal.id(),form);h.assertTrue(accepted.ok()&&accepted.equals(replay),"Approval menu accepts once and replays inertly: "+accepted.message());
      var researchTree=com.minecolonies.api.MinecoloniesAPIProxy.getInstance().getGlobalResearchTree();var effectId=env.home.colony().getResearchManager().getResearchEffectIdFrom(building.getBuildingType().getBuildingBlock());var effectsField=researchTree.getClass().getDeclaredField("researchEffectsIds");effectsField.setAccessible(true);var effects=(Map)effectsField.get(researchTree);var prior=effects.put(effectId,new HashSet<>());try{var blocked=RemoteConstruction.queueUpgrade(l,id,proposal.id());if(blocked.blockers().isEmpty()||RemoteDevelopmentData.get(l).find(id).orElseThrow().phase()!=Phase.OPERATING||RemoteDevelopmentData.get(l).construction(id).size()!=10)guards.add("research refusal changed operating settlement");}finally{if(prior==null)effects.remove(effectId);else effects.put(effectId,prior);}h.assertTrue(guards.isEmpty(),"Upgrade safety guards: "+guards);

}
     if(building.getBuildingLevel()!=2||MayorProposals.get(l).entries().get(upgradeProposal).state()!=MayorProposals.State.COMPLETE){if(l.getGameTime()-started>260000)throw new IllegalStateException("Upgrade did not complete: "+diagnostic(building,result));h.runAfterDelay(1,()->poll());return;}
     h.assertTrue(RemoteDevelopmentData.get(l).construction(id).values().stream().anyMatch(e->e.building().equals(plan.operationId())&&e.target()==2&&e.state()==ConstructionJournal.State.COMPLETE),"Original native builder completes explicitly approved level2 once");
    }
    if(expansion){
     if(expansionProposal==null){if(RemoteDevelopmentData.get(l).find(id).orElseThrow().phase()!=Phase.OPERATING){h.runAfterDelay(1,()->poll());return;}
      var proposed=ExpansionReviewAssertions.check(h,env,owner,id,expansionRole);expansionProposal=proposed.plan().operationId();var d=RemoteDevelopmentData.get(l);
      h.assertTrue(d.find(id).orElseThrow().draft().buildings().size()==10&&d.buildings(id).size()==10&&env.home.colony().getServerBuildingManager().getBuilding(proposed.plan().anchor())==null,"Review does not mutate original founding authority or place huts");
      var before=d.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());boolean denied=false;try{RemoteExpansion.decide(owner,id,expansionProposal,99,true);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied&&before.equals(d.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess())),"Stale expansion approval is inert");
      var ground=new BlockPos(proposed.plan().upgradeVolume().minX(),proposed.plan().upgradeVolume().minY()-1,proposed.plan().upgradeVolume().minZ());var block=l.getBlockState(ground);l.setBlockAndUpdate(ground,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());denied=false;try{RemoteExpansion.decide(owner,id,expansionProposal,proposed.revision(),true);}catch(IllegalArgumentException expected){denied=true;}finally{l.setBlockAndUpdate(ground,block);}h.assertTrue(denied,"Expansion refuses unsupported terrain without spending");
      var session=dev.colonyhorizons.menu.SupplyMenuSessions.open(owner,env.home.colony().getID());var form=new net.minecraft.nbt.CompoundTag();form.putString("role",expansionRole);
      h.assertTrue(menu(session.session(),1,dev.colonyhorizons.network.SupplyMenuPayloads.Action.VIEW_MAYOR,id,form).ok(),"Open expansion Mayor");h.assertTrue(menu(session.session(),2,dev.colonyhorizons.network.SupplyMenuPayloads.Action.REVIEW_EXPANSION,id,form).ok(),"Server holds expansion review");h.assertTrue(!menu(session.session(),3,dev.colonyhorizons.network.SupplyMenuPayloads.Action.APPROVE_EXPANSION,UUID.randomUUID(),form).ok(),"Reject unreviewed expansion identity");var approval=menu(session.session(),4,dev.colonyhorizons.network.SupplyMenuPayloads.Action.APPROVE_EXPANSION,expansionProposal,form);h.assertTrue(approval.ok()&&approval.equals(menu(session.session(),4,dev.colonyhorizons.network.SupplyMenuPayloads.Action.APPROVE_EXPANSION,expansionProposal,form)),"Expansion menu approval and exact replay");h.assertTrue(d.buildings(id).size()==11&&d.find(id).orElseThrow().draft().buildings().size()==10,"Approved expansion is separate from immutable founding plan");
     }
     var d=RemoteDevelopmentData.get(l);var added=d.buildings(id).stream().filter(p->p.operationId().equals(expansionProposal)).findFirst().orElseThrow();var nativeAdded=env.home.colony().getServerBuildingManager().getBuilding(added.anchor());
     if(nativeAdded==null||!nativeAdded.isBuilt()||nativeAdded.getBuildingLevel()!=1||d.expansions(id).get(expansionProposal).state()!=ExpansionProposal.State.COMPLETE){h.assertTrue(l.getGameTime()-started<260000,"Expansion did not complete: "+d.expansions(id)+" "+diagnostic(building,result));h.runAfterDelay(1,()->poll());return;}
     h.assertTrue(FoundingPlacement.isPaidAnchor(l,added)&&d.construction(id).size()==11&&d.confirmedClaims(id).size()==49,"Paid native expansion completed within original claims");var round=RemoteDevelopmentData.load(d.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess()),l.registryAccess(),l.dimension());h.assertTrue(round.expansions(id).equals(d.expansions(id)),"Expansion custody survives serialization");
     var savedExpansion=d.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());var pending=savedExpansion.copy();var state=pending.getList("developments",10).getCompound(0).getList("expansions",10).getCompound(0);state.putString("state","PLACING");state.putLong("revision",2);h.assertTrue(RemoteDevelopmentData.load(pending,l.registryAccess(),l.dimension()).find(id).orElseThrow().phase()==Phase.RECOVERY_NEEDED,"Interrupted expansion payment is quarantined");
     for(String key:List.of("cost","operation","volume","revision")){var bad=savedExpansion.copy();bad.getList("developments",10).getCompound(0).getList("expansions",10).getCompound(0).remove(key);boolean refused=false;try{RemoteDevelopmentData.load(bad,l.registryAccess(),l.dimension());}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,"Corrupt expansion field refused: "+key);}
     if(expansionRole.equals("lumberjack")){h.assertTrue(((com.minecolonies.core.colony.buildings.workerbuildings.BuildingLumberjack)nativeAdded).shouldRestrict(),"New forester is restricted to outpost land");var outsider=env.home.colony().getCitizenManager().getCitizens().stream().filter(c->!d.approvedParty(id).containsKey(c.getId())).findFirst().orElseThrow();h.assertTrue(!nativeAdded.getModule(com.minecolonies.core.colony.buildings.modules.BuildingModules.FORESTER_WORK).assignCitizen(outsider)&&outsider.getWorkBuilding()==null,"Vacant expansion rejects native assignment of unapproved Home adult");}
    }
    if(health)h.assertTrue(repeatIllness&&medicineImports()>cureImportsBefore&&recovered.size()==(free?4:5),"All five original founders recovered from native illness without immunity or free healing");
    if(System.getProperty("colonyhorizons.foundationSuite").matches("WarehouseRestart|MayorUpgradeRestart"))WarehouseRestartAssertions.seed(h,env,owner);
    rows.add(new ProbeResult("finite_native_build",ProbeResult.Outcome.PASS,"Original remote builder completed native level one from finite Home supplies"));report();h.succeed();return;
   }
   if(l.getGameTime()-started>(settlement?260000:12500))throw new IllegalStateException("Native completion timed out: "+diagnostic(building,result));
   if((l.getGameTime()-started)%2000==0)System.out.println("REMOTE_COMPLETION "+diagnostic(building,result));
   h.runAfterDelay(1,()->poll());
  }catch(Throwable e){fail(e);}}
  long medicineImports(){var l=h.getLevel();var service=dev.colonyhorizons.supply.NativeSupplyService.get(l);var disease=com.minecolonies.core.datalistener.DiseasesListener.getDiseases().getFirst();return service.routes(env.owner.getUUID()).stream().filter(route->disease.cureItems().stream().anyMatch(c->com.minecolonies.core.datalistener.model.Disease.hasCureItem(c).test(service.settings(route.id()).item()))).mapToLong(route->route.delivered()).sum();}
  void load(TileEntityColonyBuilding hut,ItemKey item,int count){
   var left=ItemHandlerHelper.insertItemStacked(dev.colonyhorizons.supply.WarehouseInventory.resolve(h.getLevel(),hut).inventory(),item.stack().copyWithCount(count),false);
   while(!left.isEmpty()){
    h.assertTrue(false,"Finite warehouse fixture capacity");
    if(reserves.isEmpty()||reserves.getLast().getItem(4).getCount()==reserves.getLast().getItem(4).getMaxStackSize()){
     h.assertTrue(reserves.size()<24,"Bounded finite hopper reserve");
     var pos=env.home.center().above(reserves.size()+1);
     h.getLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
     reserves.add((net.minecraft.world.level.block.entity.HopperBlockEntity)h.getLevel().getBlockEntity(pos));
    }
    var reserve=reserves.getLast();boolean inserted=false;
    for(int slot=0;slot<reserve.getContainerSize()&&!left.isEmpty();slot++)if(reserve.getItem(slot).isEmpty()){
     var take=Math.min(left.getCount(),left.getMaxStackSize());reserve.setItem(slot,left.copyWithCount(take));left.shrink(take);inserted=true;
    }
    if(!inserted){var pos=env.home.center().above(reserves.size()+1);h.assertTrue(reserves.size()<24,"Bounded finite hopper reserve");h.getLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());reserves.add((net.minecraft.world.level.block.entity.HopperBlockEntity)h.getLevel().getBlockEntity(pos));}
   }
  }
  String diagnostic(BuildingBuilder b,StepResult result){var c=env.home.colony().getCitizenManager().getCivilian(people().get(1));return "people="+people().stream().map(i->{var person=env.home.colony().getCitizenManager().getCivilian(i);return person.getName()+":citizenAI="+person.getEntity().map(e->((com.minecolonies.core.entity.citizen.EntityCitizen)e).getCitizenAI().getState().toString()).orElse("missing")+":sick="+person.getCitizenDiseaseHandler().isSick()+":foodSlot="+com.minecolonies.api.util.FoodUtils.getBestFoodForCitizen(person.getInventory(),person,null)+":"+person.getSaturation()+":"+(person.getJob()==null?"none":person.getJob().getWorkerAI().getState())+":"+person.getEntity().map(e->e.blockPosition().toString()).orElse("missing")+":"+java.util.stream.IntStream.range(0,person.getInventory().getSlots()).mapToObj(slot->person.getInventory().getStackInSlot(slot)).filter(stack->!stack.isEmpty()).toList();}).toList()+" warehouseFood="+MayorLogistics.warehouse(h.getLevel(),env.draft.id()).map(w->{var inv=dev.colonyhorizons.supply.WarehouseInventory.resolve(h.getLevel(),w.getTileEntity()).inventory();return java.util.stream.IntStream.range(0,inv.getSlots()).mapToObj(inv::getStackInSlot).filter(st->st.is(Items.BREAD)).toList();})+" buildings="+env.draft.buildings().stream().map(p->{var b2=env.home.colony().getServerBuildingManager().getBuilding(p.anchor());return p.role()+":"+b2.getBuildingLevel();}).toList()+" courier="+env.home.colony().getCitizenManager().getCivilian(FoundingRoster.member(RemoteDevelopmentData.get(h.getLevel()),env.draft.id(),FoundingRoster.Role.COURIER).orElseThrow().citizenId()).getJob()+" "+result+" level="+b.getBuildingLevel()+" needs="+b.getNeededResources()+" worker="+c.getEntity().map(e->e.blockPosition()+" "+e.getCitizenData().getJob()).orElse("missing")+" ai="+(c.getJob()==null?"not assigned yet":c.getJob().getWorkerAI().getState())+" citizenAI="+((com.minecolonies.core.entity.citizen.EntityCitizen)c.getEntity().orElseThrow()).getCitizenAI().getState()+" saturation="+c.getSaturation()+" open="+b.getOpenRequests(c.getId()).stream().map(r->r.getRequest().toString()+":"+r.getState()).toList()+" buildingOpen="+b.getOpenRequests(-1).stream().map(r->r.getRequest().toString()+":"+r.getState()).toList()+" routes="+dev.colonyhorizons.supply.NativeSupplyService.get(h.getLevel()).routes(env.owner.getUUID())+" supplyProblem="+dev.colonyhorizons.supply.NativeSupplyService.get(h.getLevel()).problem();}
  void report()throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),System.getProperty("colonyhorizons.foundationSuite"),rows);}
  void fail(Throwable e){try{rows.add(new ProbeResult("finite_native_build",ProbeResult.Outcome.FAIL,e.toString()));report();}catch(Exception ignored){}h.fail(e.toString().substring(0,Math.min(900,e.toString().length())));}
 }
}
