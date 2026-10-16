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
public final class RemoteBuildingCompletionTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=100000)
 public static void finite_native_build(GameTestHelper h){h.runAfterDelay(40,()->new Run(h).start());}
 private static final class Run {
  final GameTestHelper h; final List<ProbeResult> rows=new ArrayList<>();
  final boolean full=System.getProperty("colonyhorizons.foundationSuite").equals("RemoteSettlementCompletion");
  final List<net.minecraft.world.level.block.entity.HopperBlockEntity> reserves=new ArrayList<>();
  final boolean settlement=full||System.getProperty("colonyhorizons.foundationSuite").equals("RemoteHousingCompletion");
  Map<ItemKey,Integer> homeRemainder;
  RemoteTestEnvironment env; net.minecraft.server.level.ServerPlayer owner; long started;
  Run(GameTestHelper h){this.h=h;}
  void start(){try{
   var l=h.getLevel();h.assertTrue(!com.minecolonies.api.MinecoloniesAPIProxy.getInstance().getConfig().getServer().creativeResolve.get()&&!com.minecolonies.api.util.constant.Constants.BUILDER_INF_RESOURECES,"No creative resource resolution");l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
   var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());
   var bill=new HashMap<ItemKey,Integer>();
   for(var path:full?List.of("builder","citizen","citizen","citizen","citizen","lumberjack","lumberjack"):settlement?List.of("builder","citizen"):List.of("builder")){
   var materialBlueprint=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/"+path+"1.blueprint",l.registryAccess());var placer=new StructurePlacer(new LoadOnlyStructureHandler(l,new BlockPos(1000,-58,0),materialBlueprint,RotationMirror.NONE));
   var progress=AbstractBlueprintIterator.NULL_POS;
   for(int guard=0;;guard++){
    h.assertTrue(guard<10000,"Bounded native bill calculation");
    var result=placer.executeStructureStep(l,null,progress,StructurePlacer.Operation.GET_RES_REQUIREMENTS,()->placer.getIterator().increment((info,pos,handler)->false),true);
    for(var item:result.getBlockResult().getRequiredItems())if(!item.isEmpty())bill.merge(new ItemKey(item),item.getCount(),Integer::sum);
    progress=result.getIteratorPos();if(result.getBlockResult().getResult()==BlockPlacementResult.Result.FINISHED)break;
   }
   }
   var stock=new HashMap<>(bill);FoundingSupplies.bill().forEach((k,v)->stock.merge(k,v,Integer::sum));
   stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutBuilder)),1,Integer::sum);stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutLumberjack)),2,Integer::sum);stock.merge(new ItemKey(new ItemStack(ModBlocks.blockHutHome)),4,Integer::sum);stock.merge(new ItemKey(new ItemStack(Items.DIRT)),5,Integer::sum);
   env=new RemoteTestEnvironment(l,1024,new Budget(stock,Map.of()));
   ModBlocks.blockHutBuilder.setup(env.owner,l,env.home.center(),bp,RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint");
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
   for(int i=0;i<8;i++)h.assertTrue(RemoteFoundingService.advance(l,id).blockers().isEmpty(),"Founding advances");
   if(data.journey(id).orElseThrow().remainingTicks()>0){FoundingTravel.reconcile(l,id);h.runAfterDelay(20,()->arrive());return;}
   h.assertTrue(RemoteFoundingService.advance(l,id).blockers().isEmpty(),"Original paid party supplied");
   MayorService.appoint(owner,id,env.draft.request().mayorId());
   h.assertTrue(MayorService.available(l,id),"Original Mayor appointed; no test-driven planning");
   var builder=env.home.colony().getCitizenManager().getCivilian(env.draft.request().citizenIds().get(1));builder.setPaused(false);env.home.colony().getCitizenManager().getCivilian(env.draft.request().mayorId()).setPaused(false);
   // Original native Mayor and builder AI perform all orders, reviews and work.
   started=l.getGameTime();poll();
  }catch(Throwable e){fail(e);}}
  void poll(){try{
   var l=h.getLevel();var id=env.draft.id();var plan=env.draft.buildings().getFirst();
   var result=new StepResult("Observing native Mayor",List.of(),false);var building=(BuildingBuilder)env.home.colony().getServerBuildingManager().getBuilding(plan.anchor());
   if(building.isBuilt()&&building.getBuildingLevel()==1&&(!settlement||env.draft.buildings().stream().filter(p->full||p.role().equals("builder")||p.operationId().equals(env.draft.buildings().get(3).operationId())).allMatch(p->{var b=env.home.colony().getServerBuildingManager().getBuilding(p.anchor());return b.isBuilt()&&b.getBuildingLevel()==1;}))){
    if(RemoteDevelopmentData.get(l).construction(id).values().stream().filter(e->e.state()==ConstructionJournal.State.COMPLETE).count()<(full?7:settlement?2:1)){h.runAfterDelay(20,()->poll());return;}
    h.assertTrue(RemoteDevelopmentData.get(l).construction(id).values().stream().anyMatch(e->e.state()==ConstructionJournal.State.COMPLETE),"Native completion journal");
    h.assertTrue(owner.blockPosition().closerThan(env.home.center(),3),"Owner stayed at Home");
    h.assertTrue(env.home.colony().getCitizenManager().getCitizens().size()==4,"No replacement citizens");
    if(settlement&&!env.draft.request().citizenIds().stream().map(i->env.home.colony().getCitizenManager().getCivilian(i)).anyMatch(c->c.getHomeBuilding()!=null)){if(l.getGameTime()-started>90000)throw new IllegalStateException("Completed home remains unassigned");h.runAfterDelay(20,()->poll());return;}
    if(settlement)h.assertTrue(env.draft.request().citizenIds().stream().map(i->env.home.colony().getCitizenManager().getCivilian(i)).anyMatch(c->c.getHomeBuilding()!=null&&c.getHomeBuilding().getPosition().equals(env.draft.buildings().get(3).anchor())),"A founder is assigned to the completed local home");
    var source=(TileEntityColonyBuilding)l.getBlockEntity(env.home.center());for(var e:homeRemainder.entrySet()){int left=0;for(int slot=0;slot<source.getInventory().getSlots();slot++){var stack=source.getInventory().getStackInSlot(slot);if(ItemStack.isSameItemSameComponents(stack,e.getKey().stack()))left+=stack.getCount();}for(var reserve:reserves)for(int slot=0;slot<reserve.getContainerSize();slot++){var stack=reserve.getItem(slot);if(ItemStack.isSameItemSameComponents(stack,e.getKey().stack()))left+=stack.getCount();}int spent=MayorReservations.get(l).entries().values().stream().mapToInt(entry->entry.dispatched().getOrDefault(e.getKey(),0)).sum();h.assertTrue(left+spent==e.getValue(),"Finite Home conservation for "+e.getKey().stack());}
    if(full)h.assertTrue(env.draft.request().citizenIds().stream().allMatch(i->env.home.colony().getCitizenManager().getCivilian(i).getHomeBuilding()!=null),"All original founders housed");
    rows.add(new ProbeResult("finite_native_build",ProbeResult.Outcome.PASS,"Original remote builder completed native level one from finite Home supplies"));report();h.succeed();return;
   }
   if(l.getGameTime()-started>(settlement?90000:12500))throw new IllegalStateException("Native completion timed out: "+diagnostic(building,result));
   if((l.getGameTime()-started)%200==0)System.out.println("REMOTE_COMPLETION "+diagnostic(building,result));
   h.runAfterDelay(20,()->poll());
  }catch(Throwable e){fail(e);}}
  void load(TileEntityColonyBuilding hut,ItemKey item,int count){
   var left=ItemHandlerHelper.insertItemStacked(hut.getInventory(),item.stack().copyWithCount(count),false);
   while(!left.isEmpty()){
    h.assertTrue(full,"Finite Home fixture capacity");
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
  String diagnostic(BuildingBuilder b,StepResult result){var c=env.home.colony().getCitizenManager().getCivilian(env.draft.request().citizenIds().get(1));return result+" level="+b.getBuildingLevel()+" needs="+b.getNeededResources()+" worker="+c.getEntity().map(e->e.blockPosition()+" "+e.getCitizenData().getJob()).orElse("missing")+" ai="+(c.getJob()==null?"not assigned yet":c.getJob().getWorkerAI().getState())+" citizenAI="+((com.minecolonies.core.entity.citizen.EntityCitizen)c.getEntity().orElseThrow()).getCitizenAI().getState()+" saturation="+c.getSaturation()+" open="+b.getOpenRequests(c.getId()).stream().map(r->r.getRequest().toString()+":"+r.getState()).toList()+" buildingOpen="+b.getOpenRequests(-1).stream().map(r->r.getRequest().toString()+":"+r.getState()).toList()+" routes="+dev.colonyhorizons.supply.NativeSupplyService.get(h.getLevel()).routes(env.owner.getUUID())+" supplyProblem="+dev.colonyhorizons.supply.NativeSupplyService.get(h.getLevel()).problem();}
  void report()throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),System.getProperty("colonyhorizons.foundationSuite"),rows);}
  void fail(Throwable e){try{rows.add(new ProbeResult("finite_native_build",ProbeResult.Outcome.FAIL,e.toString()));report();}catch(Exception ignored){}h.fail(e.toString().substring(0,Math.min(900,e.toString().length())));}
 }
}
