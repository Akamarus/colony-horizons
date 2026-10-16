// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

/** Real vanilla loot and finite tool wear. Coordinator must persist intent/custody around commit. */
public final class SurveyHarvest {
 private SurveyHarvest(){}
 public static Optional<Plan> prepare(ServerLevel level,ServerPlayer actor,BlockPos pos,SurveyResource resource,ItemStack tool,int capacity){
  if(capacity<=0||capacity>16||!permitted(level,actor,pos)||!resource.matches(level.getBlockState(pos)))return Optional.empty();
  if(resource.tool()==SurveyResource.Tool.SHEARS&&!tool.is(Items.SHEARS)||resource.tool()==SurveyResource.Tool.AXE&&!(tool.getItem() instanceof AxeItem))return Optional.empty();
  if(!tool.isEmpty()&&tool.isDamageableItem()&&tool.getDamageValue()>=tool.getMaxDamage())return Optional.empty();
  var state=level.getBlockState(pos);
  if(state.hasProperty(LeavesBlock.PERSISTENT)&&state.getValue(LeavesBlock.PERSISTENT))return Optional.empty();
  // Take the top of aquatic columns: do not spill an unbounded chain of drops below a survey.
  if(state.is(Blocks.KELP_PLANT))return Optional.empty();
  var event=new BlockEvent.BreakEvent(level,pos,state,actor);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return Optional.empty();
  var drops=Block.getDrops(state,level,pos,null,actor,tool).stream().map(ItemStack::copy).toList();
  int count=drops.stream().mapToInt(ItemStack::getCount).sum();
  if(count>capacity||drops.stream().noneMatch(s->BuiltInRegistries.ITEM.getKey(s.getItem()).equals(resource.item())))return Optional.empty();
  return Optional.of(new Plan(level,actor,pos.immutable(),state,tool,drops));
 }
 private static boolean permitted(ServerLevel level,ServerPlayer actor,BlockPos pos){
  if(actor.serverLevel()!=level||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||level.getBlockEntity(pos)!=null||!level.mayInteract(actor,pos))return false;
  var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(pos));return claim==null||claim.getOwningColony()==0;
 }
 public static final class Plan {
  private final ServerLevel level;private final ServerPlayer actor;private final BlockPos pos;private final BlockState before;
  private final ItemStack tool,originalTool;private final List<ItemStack> drops;private boolean attempted;
  private Plan(ServerLevel level,ServerPlayer actor,BlockPos pos,BlockState before,ItemStack tool,List<ItemStack> drops){this.level=level;this.actor=actor;this.pos=pos;this.before=before;this.tool=tool;this.originalTool=tool.copy();this.drops=drops;}
  public List<ItemStack> drops(){return drops.stream().map(ItemStack::copy).toList();}
  public List<ItemStack> commit(Runnable durableIntent,Runnable durableReceipt){
   if(attempted)throw new IllegalStateException("Harvest already attempted");attempted=true;
   if(!permitted(level,actor,pos)||!level.getBlockState(pos).equals(before)||!ItemStack.matches(tool,originalTool))throw new IllegalStateException("Harvest source or tool changed");
   var event=new BlockEvent.BreakEvent(level,pos,before,actor);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())throw new IllegalStateException("Harvest permission changed");
   durableIntent.run();
   if(!level.setBlock(pos,before.getFluidState().createLegacyBlock(),Block.UPDATE_ALL))throw new IllegalStateException("Harvest block was not removed");
   if(tool.isDamageableItem())tool.hurtAndBreak(1,actor,EquipmentSlot.MAINHAND);
   durableReceipt.run();return drops();
  }
 }
}
