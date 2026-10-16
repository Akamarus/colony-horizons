package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import com.minecolonies.api.blocks.ModBlocks;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.api.RotationMirror;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.nio.file.Path;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class FreeFounderGrantTests {
 static Map<ItemKey,Integer> starter(){return Map.of(new ItemKey(new ItemStack(Items.BREAD)),16,new ItemKey(new ItemStack(Items.STONE_AXE)),2,new ItemKey(new ItemStack(Items.STONE_PICKAXE)),1,new ItemKey(new ItemStack(Items.STONE_SHOVEL)),1,new ItemKey(new ItemStack(Items.STONE_HOE)),1,new ItemKey(new ItemStack(Items.OAK_SAPLING)),8);}
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=4000)
 public static void grant(GameTestHelper h){h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();try{
  var l=h.getLevel();var env=new RemoteTestEnvironment(l,1024,new Budget(Map.of(),Map.of()),0);var data=RemoteDevelopmentData.get(l);var draft=env.draft;var q=draft.request();
  var stock=new HashMap<>(FoundingPlacement.bill(l,draft.id()));FoundingConstructionSupplies.bill(l,data.find(draft.id()).orElseThrow()).forEach((k,v)->stock.merge(k,v,Integer::sum));starter().forEach((k,v)->stock.merge(k,v,Integer::sum));
  data.discardUnapproved(draft.id());var request=new SiteRequest(q.colonyId(),q.name(),q.center(),q.allowedArea(),q.homeSupply(),List.of(),0,5,new Budget(stock,Map.of()),FoundingRoster.Policy.FREE_FOUR_V1);var reviewed=RemoteSitePlanner.review(env.owner,request);data.create(new Development(reviewed.id(),env.owner.getUUID(),reviewed,Mode.APPROVAL,Phase.PROPOSED,0));var id=reviewed.id();
  h.assertTrue(env.home.colony().getCitizenManager().getCitizens().isEmpty(),"No existing settlers needed for review");pass(rows,"preview_no_creation");
  var bp=StructurePacks.getBlueprint("Arcology Horizons","craftsmanship/storage/warehouse1.blueprint",l.registryAccess());ModBlocks.blockHutWareHouse.setup(env.owner,l,env.home.center(),bp,RotationMirror.NONE,true,"Arcology Horizons","craftsmanship/storage/warehouse1.blueprint");
  var home=env.home.colony().getServerBuildingManager().getBuilding(env.home.center());for(int i=0;i<16;i++){var pos=env.home.center().offset(i%4+2,0,i/4);l.setBlockAndUpdate(pos,ModBlocks.blockRack.defaultBlockState());home.registerBlockPosition(l.getBlockState(pos),pos,l);}
  var inventory=dev.colonyhorizons.supply.WarehouseInventory.resolve(l,(com.minecolonies.core.tileentities.TileEntityColonyBuilding)home.getTileEntity()).inventory();for(var e:stock.entrySet()){int left=e.getValue();while(left>0){int n=Math.min(left,e.getKey().stack().getMaxStackSize());h.assertTrue(ItemHandlerHelper.insertItemStacked(inventory,e.getKey().stack().copyWithCount(n),false).isEmpty(),"Fixture stock fits");left-=n;}}
  var approval=RemoteFoundingService.approve(env.owner,id,0);h.assertTrue(approval.blockers().isEmpty(),"No idle Home workers required: "+approval);pass(rows,"no_idle_home_workers");
  h.assertTrue(env.home.colony().getCitizenManager().getCitizens().isEmpty(),"Approval alone must not spawn settlers");pass(rows,"approval_no_creation");
  var owner=RemoteActivityTests.loginAtHome(env);RemoteActivityService.tick(l);
  h.runAfterDelay(40,()->{try{
   for(int i=0;i<20&&FoundingRoster.resolve(data,id).isEmpty();i++){if(System.getProperty("colonyhorizons.foundationSuite").equals("FreeFounderPreCancel")&&data.anchorReceipts(id).size()==1){var cancelled=RemoteFoundingService.cancel(owner,id,data.find(id).orElseThrow().revision());h.assertTrue(cancelled.blockers().isEmpty()&&data.find(id).orElseThrow().phase()==Phase.CANCELLED,"Pre-creation cancellation releases reservation: "+cancelled);h.assertTrue(env.home.colony().getCitizenManager().getCurrentCitizenCount()==0&&!data.grant(id).orElseThrow().consumed(),"No entitlement used");pass(rows,"cancel_before_creation");report(h,rows);return;}if(System.getProperty("colonyhorizons.foundationSuite").equals("FreeFounderRestart")&&data.anchorReceipts(id).size()==8){FreeFounderRestartTests.seed(h,env,id,owner);return;}var result=RemoteFoundingService.advance(l,id);h.assertTrue(result.blockers().isEmpty(),"Founding advances: "+result);}
   var roster=FoundingRoster.resolve(data,id);h.assertTrue(roster.size()==4&&env.home.colony().getCitizenManager().getCitizens().size()==4,"Exactly four native records");h.assertTrue(roster.stream().allMatch(m->!env.home.colony().getCitizenManager().getCivilian(m.citizenId()).isChild()),"Native adults");pass(rows,"four_native_adults");
   var identities=roster.stream().map(FoundingRoster.Member::entityId).toList();for(int i=0;i<12;i++)RemoteFoundingService.advance(l,id);h.assertTrue(identities.equals(FoundingRoster.resolve(data,id).stream().map(FoundingRoster.Member::entityId).toList())&&env.home.colony().getCitizenManager().getCitizens().size()==4,"Retries preserve identities");pass(rows,"retry_no_duplicate");
   var saved=data.save(new net.minecraft.nbt.CompoundTag(),l.registryAccess());var loaded=RemoteDevelopmentData.load(saved,l.registryAccess(),l.dimension());h.assertTrue(FoundingRoster.resolve(loaded,id).equals(roster),"Roster survives reload");pass(rows,"grant_roster_reload");
   if(System.getProperty("colonyhorizons.foundationSuite").matches("FreeFounderFlow|FreeFounderCancel")){
    var step=RemoteFoundingService.advance(l,id);h.assertTrue(step.blockers().isEmpty()&&data.journey(id).isPresent(),"Free settlers enter protected arrival: "+step);pass(rows,"protected_arrival");
    while(data.journey(id).orElseThrow().remainingTicks()>0){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+20);FoundingTravel.reconcile(l,id);}
    h.assertTrue(data.journey(id).orElseThrow().state()==FoundingTravel.State.ARRIVED,"All original granted identities arrived");pass(rows,"four_distinct_arrivals");
    var mayor=env.home.colony().getCitizenManager().getCivilian(FoundingRoster.mayorId(data,id));var inventoryBefore=new ArrayList<ItemStack>();for(int i=0;i<mayor.getInventory().getSlots();i++){inventoryBefore.add(mayor.getInventory().getStackInSlot(i).copy());mayor.getInventory().setStackInSlot(i,new ItemStack(Items.STONE,64));}
    h.assertTrue(!RemoteFoundingService.advance(l,id).blockers().isEmpty()&&data.starterDelivery(id).isEmpty(),"Full inventory retains whole starter batch");for(int i=0;i<inventoryBefore.size();i++)mayor.getInventory().setStackInSlot(i,inventoryBefore.get(i));pass(rows,"full_starter_inventory_waits");
    h.assertTrue(RemoteFoundingService.advance(l,id).blockers().isEmpty()&&data.starterDelivery(id).filter(FoundingSupplies.Delivery::complete).isPresent(),"Native supplies delivered");pass(rows,"starter_delivery");
    h.assertTrue(dev.colonyhorizons.mayor.MayorService.available(l,id),"Granted Mayor appointed natively");pass(rows,"native_mayor");
    var forester=env.home.colony().getCitizenManager().getCivilian(FoundingRoster.member(data,id,FoundingRoster.Role.FORESTER).orElseThrow().citizenId());int saplings=0,hoes=0;for(int i=0;i<forester.getInventory().getSlots();i++){var stack=forester.getInventory().getStackInSlot(i);if(stack.is(Items.OAK_SAPLING))saplings+=stack.getCount();if(stack.is(Items.STONE_HOE))hoes+=stack.getCount();}h.assertTrue(saplings==8&&hoes==1,"One forester receives its eight saplings and hoe");pass(rows,"forester_starter_kit");
    var anchor=data.buildings(id).getFirst().anchor();var foodHut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)l.getBlockEntity(anchor);var containers=foodHut.getBuilding().getContainers();boolean included=containers.remove(anchor);var entity=(com.minecolonies.core.entity.citizen.EntityCitizen)mayor.getEntity().orElseThrow();var previousPos=entity.position();var carried=new ArrayList<ItemStack>();for(int i=0;i<mayor.getInventory().getSlots();i++){carried.add(mayor.getInventory().getStackInSlot(i).copy());mayor.getInventory().setStackInSlot(i,ItemStack.EMPTY);}var previousFood=foodHut.getInventory().getStackInSlot(0).copy();foodHut.getInventory().setStackInSlot(0,new ItemStack(Items.BREAD,2));entity.setPos(anchor.getX()+0.5,anchor.getY(),anchor.getZ()+1.5);
    h.assertTrue(RemoteCare.food(entity)==com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.EAT&&foodHut.getInventory().getStackInSlot(0).getCount()==1,"Food in paid hut inventory is collectable even when native rack list omits the hut");pass(rows,"hut_food_without_rack_entry");
    foodHut.getInventory().setStackInSlot(0,previousFood);for(int i=0;i<carried.size();i++)mayor.getInventory().setStackInSlot(i,carried.get(i));entity.setPos(previousPos);if(included)containers.add(anchor);

   }
   if(System.getProperty("colonyhorizons.foundationSuite").equals("FreeFounderCancel")){
    var result=RemoteFoundingService.cancel(owner,id,data.find(id).orElseThrow().revision());h.assertTrue(result.blockers().isEmpty()&&data.find(id).orElseThrow().phase()==Phase.CANCELLED,"Cancellation safely returns settlers: "+result);
    for(var m:roster){var c=env.home.colony().getCitizenManager().getCivilian(m.citizenId());h.assertTrue(c!=null&&m.entityId().equals(c.getUUID())&&c.getJob()==null&&c.getEntity().orElseThrow().blockPosition().closerThan(env.home.center(),24),"Surviving original settler safely returned Home");}pass(rows,"cancel_returns_originals");
    h.assertTrue(data.grant(id).orElseThrow().consumed(),"Cancelled grant stays consumed");pass(rows,"cancel_keeps_entitlement");
    var hut=data.buildings(id).stream().filter(b->b.role().equals("builder")).findFirst().orElseThrow();var building=env.home.colony().getServerBuildingManager().getBuilding(hut.anchor());
    h.assertTrue(!FoundingReservation.permits(env.home.colony().getCitizenManager().getCivilian(roster.getFirst().citizenId()),building.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class)),"Cancelled remote hut cannot recruit a Home settler");pass(rows,"cancelled_hut_not_rehired");
    h.assertTrue(!RemoteWorkerLocality.permitsWork(home,hut.anchor()),"Cancelled remote site cannot receive Home work orders");pass(rows,"cancelled_site_stays_local");
    var duplicate=RemoteSitePlanner.review(owner,request);data.create(new Development(duplicate.id(),owner.getUUID(),duplicate,Mode.APPROVAL,Phase.PROPOSED,0));h.assertTrue(!FoundingGrantService.review(l,duplicate.id()).blockers().isEmpty(),"Overlapping cancellation cannot farm settlers");pass(rows,"cancel_overlap_blocked");
   }
   RemoteActivityTests.connect(owner,false);RemoteActivityService.tick(l);l.removePlayerImmediately(owner,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);report(h,rows);
  }catch(Throwable e){fail(h,rows,e);}});
 }catch(Throwable e){fail(h,rows,e);}});}
 static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native grant behavior verified"));}
 static void report(GameTestHelper h,List<ProbeResult> rows)throws Exception{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),System.getProperty("colonyhorizons.foundationSuite"),rows);h.succeed();}
 static void fail(GameTestHelper h,List<ProbeResult> rows,Throwable e){try{rows.add(new ProbeResult("grant_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),System.getProperty("colonyhorizons.foundationSuite"),rows);}catch(Exception ignored){}h.fail(e.toString());}
}
