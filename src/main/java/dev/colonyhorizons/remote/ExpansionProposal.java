// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import com.ldtteam.structurize.api.RotationMirror;
import java.util.*;

/** Additional intent is persisted separately from the original founding contract. */
public record ExpansionProposal(BuildingPlan plan,Map<ItemKey,Integer> cost,State state,long revision) {
 public enum State { PROPOSED, APPROVED, REJECTED, PLACING, PLACED, COMPLETE }
 public ExpansionProposal {
  Objects.requireNonNull(plan);Objects.requireNonNull(state);cost=Map.copyOf(cost);
  if(!Set.of("home","lumberjack").contains(plan.role())||plan.targetLevel()!=1||plan.transform()!=RotationMirror.NONE||cost.isEmpty()||cost.size()>128||cost.values().stream().anyMatch(n->n<=0||n>1_000_000)||revision<0||revision%8!=(state==State.PROPOSED?0:state==State.APPROVED||state==State.REJECTED?1:state==State.PLACING?2:state==State.PLACED?3:4))throw new IllegalArgumentException("Invalid expansion review");
 }
 public static long renewedRevision(ExpansionProposal rejected){if(rejected.state()!=State.REJECTED)throw new IllegalArgumentException("Only rejected reviews can be renewed");return Math.addExact(rejected.revision(),7);}
 public boolean approved(){return state!=State.PROPOSED&&state!=State.REJECTED;}
 public ExpansionProposal next(State next){if(!(state==State.PROPOSED&&(next==State.APPROVED||next==State.REJECTED)||state==State.APPROVED&&next==State.PLACING||state==State.PLACING&&next==State.PLACED||state==State.PLACED&&next==State.COMPLETE))throw new IllegalArgumentException("Invalid expansion transition");return new ExpansionProposal(plan,cost,next,revision+1);}
 public CompoundTag save(HolderLookup.Provider registry){var t=new CompoundTag();t.putUUID("operation",plan.operationId());t.putString("role",plan.role());t.putLong("anchor",plan.anchor().asLong());t.putString("pack",plan.pack());t.putString("blueprint",plan.blueprint());t.putString("hash",plan.hash());var v=plan.upgradeVolume();t.putIntArray("volume",new int[]{v.minX(),v.minY(),v.minZ(),v.maxX(),v.maxY(),v.maxZ()});t.putString("state",state.name());t.putLong("revision",revision);var costs=new ListTag();cost.forEach((item,n)->{var c=new CompoundTag();c.put("item",item.stack().save(registry));c.putInt("count",n);costs.add(c);});t.put("cost",costs);return t;}
 public static ExpansionProposal load(CompoundTag t,HolderLookup.Provider registry){
  if(!t.hasUUID("operation")||!t.contains("anchor",Tag.TAG_LONG)||!t.contains("revision",Tag.TAG_LONG)||!t.contains("volume",Tag.TAG_INT_ARRAY)||!(t.get("cost") instanceof ListTag list)||list.isEmpty()||list.size()>128||list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Incomplete expansion record");
  var box=t.getIntArray("volume");if(box.length!=6||box[0]>box[3]||box[1]>box[4]||box[2]>box[5])throw new IllegalArgumentException("Invalid expansion bounds");var cost=new LinkedHashMap<ItemKey,Integer>();for(var value:list){var c=(CompoundTag)value;var item=ItemStack.parseOptional(registry,c.getCompound("item"));if(item.isEmpty()||item.getCount()!=1||!c.contains("count",Tag.TAG_INT)||cost.putIfAbsent(new ItemKey(item),c.getInt("count"))!=null)throw new IllegalArgumentException("Invalid expansion material");}
  return new ExpansionProposal(new BuildingPlan(t.getUUID("operation"),t.getString("role"),BlockPos.of(t.getLong("anchor")),t.getString("pack"),t.getString("blueprint"),t.getString("hash"),RotationMirror.NONE,new BoundingBox(box[0],box[1],box[2],box[3],box[4],box[5]),1),cost,State.valueOf(t.getString("state")),t.getLong("revision"));
 }
}
