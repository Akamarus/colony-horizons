// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.api.colony.IColonyManager;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.SupplyInventoryBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;

/** Verified, finite handoff from founding custody to the original native citizens. */
public final class FoundingSupplies {
 private FoundingSupplies() {}
 public record Delivery(Map<ItemKey,Integer> spent,boolean complete,Optional<ProductionProfile> profile){
  public Delivery(Map<ItemKey,Integer> spent,boolean complete){this(spent,complete,Optional.empty());}
  public Delivery{spent=Map.copyOf(spent);Objects.requireNonNull(profile);if(profile.isPresent()?!spent.equals(resourceBill(profile.orElseThrow())):!spent.equals(bill())&&!spent.equals(bill(5))&&!spent.equals(legacyFiveBill())&&!spent.equals(freeBill()))throw new IllegalArgumentException("Invalid starter supply receipt");}
 }
 public static Map<ItemKey,Integer> bill(SiteRequest request){return request.surveyOrigin().map(o->resourceBill(o.profile())).orElseGet(()->FoundingRoster.free(request)?freeBill():bill(request.citizenIds().size()));}
 private static Map<ItemKey,Integer> resourceBill(ProductionProfile profile){var result=new HashMap<ItemKey,Integer>();result.put(new ItemKey(new ItemStack(Items.BREAD)),16);for(var item:List.of(Items.STONE_AXE,Items.STONE_PICKAXE,Items.STONE_SHOVEL))result.put(new ItemKey(new ItemStack(item)),1);profile.starterSupplies().forEach((k,n)->result.merge(k,n,Math::addExact));return Map.copyOf(result);}
 private static Map<ItemKey,Integer> freeBill(){var result=new HashMap<>(bill());result.put(new ItemKey(new ItemStack(Items.STONE_AXE)),2);result.put(new ItemKey(new ItemStack(Items.OAK_SAPLING)),8);result.put(new ItemKey(new ItemStack(Items.STONE_HOE)),1);return Map.copyOf(result);}
 public static Map<ItemKey,Integer> bill(){return Map.of(new ItemKey(new ItemStack(Items.BREAD)),16,new ItemKey(new ItemStack(Items.STONE_AXE)),3,new ItemKey(new ItemStack(Items.STONE_PICKAXE)),1,new ItemKey(new ItemStack(Items.STONE_SHOVEL)),1,new ItemKey(new ItemStack(Items.OAK_SAPLING)),16);}
 public static Map<ItemKey,Integer> bill(int partySize){if(partySize!=4&&partySize!=5)throw new IllegalArgumentException("Unsupported founding party");var result=new HashMap<>(bill());result.put(new ItemKey(new ItemStack(Items.BREAD)),partySize*4);if(partySize==5)result.put(new ItemKey(new ItemStack(Items.STONE_HOE)),2);return Map.copyOf(result);}
 private static Map<ItemKey,Integer> legacyFiveBill(){var result=new HashMap<>(bill());result.put(new ItemKey(new ItemStack(Items.BREAD)),20);return Map.copyOf(result);}
 public static boolean deliver(ServerLevel level,UUID development) {
  var data=RemoteDevelopmentData.get(level);var r=data.find(development).orElseThrow();var prior=data.starterDelivery(development);
  if(prior.isPresent()){if(!prior.orElseThrow().complete())throw new IllegalStateException("Interrupted starter handoff needs recovery");return false;}
  if(r.phase()!=Phase.STAGING||data.journey(development).orElseThrow().state()!=FoundingTravel.State.ARRIVED)throw new IllegalStateException("Founders have not arrived");
  var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
  if(colony==null||!r.owner().equals(colony.getPermissions().getOwner()))throw new SecurityException("Founding owner changed");
  var bill=bill(r.draft().request());
  var available=FoundingPlacement.remaining(level,development);for(var e:bill.entrySet())if(available.getOrDefault(e.getKey(),0)<e.getValue())throw new IllegalStateException("Paid starter supplies are missing");
  var targets=new LinkedHashMap<UUID,IItemHandlerModifiable>();var identities=new HashMap<Integer,UUID>();
  for(var m:data.journey(development).orElseThrow().members()) {
   var citizen=colony.getCitizenManager().getCivilian(m.citizenId());
   if(citizen==null||!citizen.getUUID().equals(m.entityId())||citizen.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).isEmpty())throw new IllegalStateException("Original founder is unavailable for supplies");
   targets.put(m.entityId(),citizen.getInventory());identities.put(m.citizenId(),m.entityId());
  }
  var staged=new SupplyInventoryBatch(targets);
  for(var target:targets.keySet())insert(staged,target,Items.BREAD,4);
  var roster=FoundingRoster.resolve(data,development);var builder=FoundingRoster.member(data,development,FoundingRoster.Role.BUILDER).orElseThrow().entityId();
  insert(staged,builder,Items.STONE_AXE,1);insert(staged,builder,Items.STONE_PICKAXE,1);insert(staged,builder,Items.STONE_SHOVEL,1);
  for(var member:roster)if(member.role()==FoundingRoster.Role.FORESTER){insert(staged,member.entityId(),Items.STONE_AXE,1);insert(staged,member.entityId(),Items.OAK_SAPLING,8);if(FoundingRoster.warehouseFirst(r))insert(staged,member.entityId(),Items.STONE_HOE,1);}
  var profile=r.draft().request().surveyOrigin().map(SurveyOrigin::profile);
  if(profile.isPresent()){var producer=FoundingRoster.member(data,development,FoundingRoster.Role.PRODUCER).orElseThrow();for(var e:profile.orElseThrow().starterSupplies().entrySet())if(!staged.insert(producer.entityId(),e.getKey().stack().copyWithCount(e.getValue())).isEmpty())throw new IllegalStateException("Make room in producer inventory for starter stock");}
  try {
   data.putStarterDelivery(development,new Delivery(bill,false,profile));data.checkpoint(level);staged.apply();NativeFoundingCheckpoint.save(level,colony);
   data.putStarterDelivery(development,new Delivery(bill,true,profile));data.checkpoint(level);return true;
  }catch(RuntimeException failure){var current=data.find(development).orElseThrow();data.transition(development,current.revision(),Phase.RECOVERY_NEEDED);try{data.checkpoint(level);}catch(RuntimeException save){failure.addSuppressed(save);}throw failure;}
 }
 private static void insert(SupplyInventoryBatch staged,UUID target,Item item,int count){if(!staged.insert(target,new ItemStack(item,count)).isEmpty())throw new IllegalStateException("Make room in the founder's inventory for starter supplies");}
}
