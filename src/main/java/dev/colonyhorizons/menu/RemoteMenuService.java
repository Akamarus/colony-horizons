// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.menu;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.mayor.*;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Owner-only bounded views. A reviewed site has no authority until its server-held draft is approved. */
public final class RemoteMenuService {
 private RemoteMenuService(){}
 public static CompoundTag options(ServerPlayer actor,int colonyId){
  var result=new CompoundTag();var form=new CompoundTag();form.putString("kind","warehouses");result.put("warehouses",choices(actor,colonyId,form).getList("entries",10));form.putString("kind","citizens");result.put("citizens",choices(actor,colonyId,form).getList("entries",10));return result;
 }
 public static CompoundTag choices(ServerPlayer actor,int colonyId,CompoundTag form){
  var colony=SupplyMenuService.owned(actor,colonyId);int page=form.getInt("page");if(page<0||page>4096)throw new IllegalArgumentException("Invalid page");
  var all=new ArrayList<CompoundTag>();String kind=form.getString("kind");
  if(kind.equals("warehouses")){
   colony.getServerBuildingManager().getBuildings().values().stream().filter(b->b instanceof BuildingWareHouse&&b.isBuilt()&&b.getBuildingLevel()>0&&actor.serverLevel().hasChunkAt(b.getPosition())&&isHome(actor,b.getPosition())).sorted(Comparator.comparingLong(b->b.getPosition().asLong())).forEach(b->{var t=new CompoundTag();t.putLong("pos",b.getPosition().asLong());t.putString("name",SupplyMenuModels.text(b.getCustomName().isBlank()?"Home warehouse":b.getCustomName()));all.add(t);});
  }else if(kind.equals("citizens")){
   colony.getCitizenManager().getCitizens().stream().filter(c->!c.isChild()&&c.getJob()==null&&RemoteWorkerLocality.settlement(c).isEmpty()).sorted(Comparator.comparingInt(c->c.getId())).forEach(c->{var t=new CompoundTag();t.putInt("id",c.getId());t.putString("name",SupplyMenuModels.text(c.getName()));all.add(t);});
  }else if(kind.equals("budget")){
   var warehouse=warehouse(actor,colonyId,BlockPos.of(form.getLong("home")));var inventory=dev.colonyhorizons.supply.WarehouseInventory.resolve(actor.serverLevel(),warehouse.getTileEntity()).inventory();var stock=new LinkedHashMap<ItemKey,Integer>();
   for(int slot=0;slot<inventory.getSlots();slot++){var stack=inventory.getStackInSlot(slot);if(!stack.isEmpty())stock.merge(new ItemKey(stack),stack.getCount(),Math::addExact);}
   stock.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().stack().getHoverName().getString())).forEach(e->{var t=new CompoundTag();t.put("item",e.getKey().stack().save(actor.registryAccess()));t.putString("name",SupplyMenuModels.text(e.getKey().stack().getHoverName().getString()));t.putInt("stock",e.getValue());all.add(t);});
  }else throw new IllegalArgumentException("Choose warehouse, founders or supplies");
  var result=new CompoundTag();var entries=new ListTag();int start=Math.multiplyExact(page,16);if(start>0&&start>=all.size())throw new IllegalArgumentException("Page changed; return to the first page");for(int i=start;i<Math.min(start+16,all.size());i++)entries.add(all.get(i));result.put("entries",entries);result.putBoolean("next",start+16<all.size());result.putInt("page",page);result.putString("kind",kind);result.putLong("player",actor.blockPosition().asLong());return result;
 }
 private static boolean isHome(ServerPlayer actor,BlockPos pos){return RemoteDevelopmentData.get(actor.serverLevel()).all().stream().noneMatch(r->r.phase()!=Phase.CANCELLED&&r.phase()!=Phase.PROPOSED&&r.draft().chunks().contains(new net.minecraft.world.level.ChunkPos(pos)));}
 private static BuildingWareHouse warehouse(ServerPlayer actor,int colonyId,BlockPos pos){
  var colony=SupplyMenuService.owned(actor,colonyId);var b=colony.getServerBuildingManager().getBuilding(pos);
  if(!(b instanceof BuildingWareHouse warehouse)||!b.isBuilt()||b.getBuildingLevel()<1||!actor.serverLevel().hasChunkAt(pos)||!isHome(actor,pos))throw new IllegalArgumentException("Choose a completed Home warehouse");return warehouse;
 }
 public static SiteDraft review(ServerPlayer actor,int colonyId,CompoundTag form){
  SupplyMenuService.owned(actor,colonyId);
  if(form.contains("citizens")&&(!form.contains("citizens",Tag.TAG_INT_ARRAY)||form.getIntArray("citizens").length!=0)||form.contains("mayor")||form.contains("founderRoster")||form.contains("foundingPolicy"))throw new IllegalArgumentException("New outposts receive four free settlers; existing identities cannot be selected");
  var home=BlockPos.of(form.getLong("home"));warehouse(actor,colonyId,home);
  var allowances=new LinkedHashMap<ItemKey,Integer>();var reserves=new LinkedHashMap<ItemKey,Integer>();
  if(!form.contains("budget",Tag.TAG_LIST))throw new IllegalArgumentException("Review a finite supply allowance");var list=form.getList("budget",Tag.TAG_COMPOUND);if(list.size()>128)throw new IllegalArgumentException("Maximum 128 allowance items");
  for(var value:list){var t=(CompoundTag)value;var stack=ItemStack.parseOptional(actor.registryAccess(),t.getCompound("item"));if(stack.isEmpty()||stack.getCount()!=1||!t.contains("allowance",Tag.TAG_INT)||!t.contains("reserve",Tag.TAG_INT))throw new IllegalArgumentException("Invalid allowance item");var key=new ItemKey(stack);if(allowances.putIfAbsent(key,t.getInt("allowance"))!=null)throw new IllegalArgumentException("Duplicate allowance item");reserves.put(key,t.getInt("reserve"));}
  if(!form.contains("center",Tag.TAG_LONG)||!form.contains("home",Tag.TAG_LONG))throw new IllegalArgumentException("Choose the site and warehouse");
  var center=BlockPos.of(form.getLong("center"));int x=(center.getX()>>4)<<4,z=(center.getZ()>>4)<<4;var l=actor.serverLevel();
  return RemoteSitePlanner.reviewPrepared(l,actor.getUUID(),new SiteRequest(colonyId,form.getString("name").trim(),center,new BoundingBox(x-48,l.getMinBuildHeight(),z-48,x+63,l.getMaxBuildHeight()-1,z+63),home,List.of(),0,form.getInt("cap"),new Budget(allowances,reserves),FoundingRoster.Policy.FREE_FOUR_V1));
 }
 public static CompoundTag reviewView(ServerPlayer actor,SiteDraft draft){
  var result=new CompoundTag();result.putUUID("id",draft.id());result.putString("name",draft.request().name());result.putInt("buildings",draft.buildings().size());result.putInt("chunks",draft.chunks().size());result.putLong("center",draft.request().center().asLong());result.putString("problem",String.join("; ",draft.blockers()));result.putBoolean("ready",draft.blockers().isEmpty());
  result.putString("founders",FoundingRoster.free(draft.request())?"4 free settlers: Mayor, Builder, Courier, Forester.":"Original selected settlers");result.putString("foundingPolicy",draft.request().foundingPolicy().name());
  var costs=new ListTag();if(draft.blockers().isEmpty()){
   var temporary=new Development(draft.id(),actor.getUUID(),draft,Mode.APPROVAL,Phase.PROPOSED,0);var bill=new HashMap<>(FoundingPlacement.bill(actor.serverLevel(),temporary));FoundingConstructionSupplies.bill(actor.serverLevel(),temporary).forEach((k,v)->bill.merge(k,v,Math::addExact));FoundingSupplies.bill(draft.request()).forEach((k,v)->bill.merge(k,v,Math::addExact));PreparedFounding.bill(draft).forEach((k,v)->bill.merge(k,v,Math::addExact));
   for(var e:bill.entrySet()){var t=new CompoundTag();t.putString("name",SupplyMenuModels.text(e.getKey().stack().getHoverName().getString()));t.putInt("needed",e.getValue());t.putInt("allowance",draft.request().budget().allowances().getOrDefault(e.getKey(),0));costs.add(t);if(t.getInt("allowance")<e.getValue())result.putBoolean("ready",false);}
  }result.put("costs",costs);return result;
 }
 public static StepResult approve(ServerPlayer actor,int colonyId,SiteDraft draft,Mode mode){
  SupplyMenuService.owned(actor,colonyId);if(draft==null||draft.request().colonyId()!=colonyId||!draft.blockers().isEmpty())throw new IllegalArgumentException("Review a valid site first");
  var data=RemoteDevelopmentData.get(actor.serverLevel());if(data.find(draft.id()).isEmpty())data.create(new Development(draft.id(),actor.getUUID(),draft,mode,Phase.PROPOSED,0));var result=RemoteFoundingService.approve(actor,draft.id(),0);if(!result.blockers().isEmpty()&&!data.foundingManaged(draft.id())&&data.find(draft.id()).orElseThrow().phase()==Phase.PROPOSED){data.discardUnapproved(draft.id());data.checkpoint(actor.serverLevel());}return result;
 }
 private static Development owned(ServerPlayer actor,int colonyId,UUID id){SupplyMenuService.owned(actor,colonyId);var r=RemoteDevelopmentData.get(actor.serverLevel()).find(id).orElseThrow(()->new IllegalArgumentException("Unknown remote outpost"));if(!r.owner().equals(actor.getUUID())||r.draft().request().colonyId()!=colonyId)throw new SecurityException("This is not your outpost");return r;}
 public static void pause(ServerPlayer actor,int colonyId,UUID id,long revision,boolean pause){
  var r=owned(actor,colonyId,id);var data=RemoteDevelopmentData.get(actor.serverLevel());
  if(r.revision()!=revision||!data.foundingManaged(id)||!data.hasClaimJournal(id))throw new IllegalArgumentException("Outpost changed; refresh before continuing");
  if(pause&&!Set.of(Phase.STAGING,Phase.BUILDING,Phase.OPERATING).contains(r.phase())||!pause&&r.phase()!=Phase.PAUSED)throw new IllegalArgumentException("Outpost is not ready for this action");
  var preparing=r.draft().preparation().isPresent()&&data.preparationProgress(id).map(p->p.stage().ordinal()<PreparationProgress.Stage.HANDED_OVER.ordinal()).orElse(true);var next=pause?Phase.PAUSED:preparing?Phase.STAGING:data.journey(id).filter(j->j.state()==FoundingTravel.State.ARRIVED).isEmpty()?Phase.STAGING:Phase.BUILDING;
  data.transition(id,revision,next);data.checkpoint(actor.serverLevel());RemoteActivityService.tick(actor.serverLevel());
 }
 public static CompoundTag mayor(ServerPlayer actor,int colonyId,UUID id){
  var r=owned(actor,colonyId,id);var data=RemoteDevelopmentData.get(actor.serverLevel());var colony=SupplyMenuService.owned(actor,colonyId);var t=new CompoundTag();t.putUUID("id",id);t.putString("name",r.draft().request().name());t.putString("phase",r.phase().name());t.putLong("revision",r.revision());t.putString("mode",r.mode()==Mode.APPROVAL?"Ask before expanding":"Automatic within approved limits");t.putInt("cap",r.draft().request().maxBuildingLevel());int planned=FoundingRoster.free(r.draft().request())?4:r.draft().request().citizenIds().size();t.putInt("people",planned);t.putInt("plannedPeople",planned);t.putInt("arrivedPeople",data.journey(id).filter(j->j.state()==FoundingTravel.State.ARRIVED).map(j->j.members().size()).orElse(0));t.putString("foundingPolicy",r.draft().request().foundingPolicy().name());t.putInt("total",data.buildings(id).size());t.putInt("completed",(int)data.buildings(id).stream().filter(p->{var b=colony.getServerBuildingManager().getBuilding(p.anchor());return b!=null&&b.isBuilt()&&b.getBuildingLevel()>=p.targetLevel();}).count());t.putBoolean("courierReady",MayorLogistics.ready(actor.serverLevel(),id));t.putString("mayor",MayorService.available(actor.serverLevel(),id)?colony.getCitizenManager().getCivilian(data.mayor(id).orElseThrow().citizenId()).getName():FoundingRoster.free(r.draft().request())?"Mayor arriving":"Needs original Mayor");t.putLong("home",r.draft().request().homeSupply().asLong());
  var needs=new ListTag();if(FoundingRoster.free(r.draft().request())&&data.founderRoster(id).isEmpty()){var blockers=FoundingGrantService.review(actor.serverLevel(),id).blockers();t.putString("populationBlocker",String.join("; ",blockers));for(var blocker:blockers){var n=new CompoundTag();n.putString("text",blocker);needs.add(n);}}for(int person:FoundingRoster.citizenIds(data,id)){var c=colony.getCitizenManager().getCivilian(person);if(c==null)continue;var disease=c.getCitizenDiseaseHandler().getDisease();if(disease!=null){var n=new CompoundTag();n.putString("text",SupplyMenuModels.text(c.getName()+": "+disease.name().getString()+"; needs "+disease.getCureString().getString()));needs.add(n);}}
  for(var p:data.buildings(id)){var b=colony.getServerBuildingManager().getBuilding(p.anchor());if(b instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder builder)for(var resource:builder.getNeededResources().values()){if(needs.size()>=32)break;var n=new CompoundTag();n.putString("text",SupplyMenuModels.text(resource.getAmount()+" "+resource.getItemStack().getHoverName().getString()+" for construction"));needs.add(n);}}
  for(int person:FoundingRoster.citizenIds(data,id)){var c=colony.getCitizenManager().getCivilian(person);if(c==null||c.getWorkBuilding()==null)continue;for(var request:c.getWorkBuilding().getOpenRequests(person)){if(needs.size()>=32)break;if(request.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.Food||request.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.Tool){var n=new CompoundTag();n.putString("text",SupplyMenuModels.text(c.getName()+": "+request.getLongDisplayString().getString()));needs.add(n);}}}
  var charged=new HashMap<ItemKey,Integer>();data.foundingCargo(id).ifPresent(cargo->FoundingCargo.load(cargo,actor.registryAccess()).items().forEach((k,v)->charged.merge(k,v,Math::addExact)));for(var entry:dev.colonyhorizons.mayor.MayorReservations.get(actor.serverLevel()).entries().values())if(entry.development().equals(id))(entry.closed()?entry.dispatched():entry.cost()).forEach((k,v)->charged.merge(k,v,Math::addExact));
  t.put("needs",needs);var budget=new ListTag();for(var e:r.draft().request().budget().allowances().entrySet()){var n=new CompoundTag();n.putString("text",SupplyMenuModels.text(e.getKey().stack().getHoverName().getString()+": remaining "+Math.max(0,e.getValue()-charged.getOrDefault(e.getKey(),0))+" / "+e.getValue()+", leave Home "+r.draft().request().budget().homeReserves().getOrDefault(e.getKey(),0)));budget.add(n);}t.put("budget",budget);var upgrades=new ListTag();for(var plan:data.buildings(id)){var b=colony.getServerBuildingManager().getBuilding(plan.anchor());if(b==null||!b.isBuilt()||b.getBuildingLevel()>=r.draft().request().maxBuildingLevel())continue;var row=new CompoundTag();row.putUUID("building",plan.operationId());row.putString("text",plan.role()+" at "+plan.anchor().toShortString()+": review level "+(b.getBuildingLevel()+1));upgrades.add(row);}t.put("upgrades",upgrades);var additions=new ListTag();for(var e:data.expansions(id).values()){var row=new CompoundTag();row.putString("text",e.plan().role()+" at "+e.plan().anchor().toShortString()+": "+e.state().name().toLowerCase(java.util.Locale.ROOT));if(e.state()==ExpansionProposal.State.PROPOSED)row.putString("role",e.plan().role());additions.add(row);}if(r.phase()==Phase.OPERATING&&data.expansions(id).values().stream().filter(e->e.state()!=ExpansionProposal.State.REJECTED).count()<3){for(String role:List.of("home","lumberjack")){var row=new CompoundTag();row.putString("role",role);row.putString("text",role.equals("home")?"Review a new home":"Review a new forester hut (staff separately)");additions.add(row);}}t.put("expansions",additions);return t;
 }
}
