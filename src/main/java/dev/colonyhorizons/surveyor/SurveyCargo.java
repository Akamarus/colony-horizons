// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Paid equipment and actual harvested stacks, never a table of generated rewards. */
public record SurveyCargo(boolean paid,ItemStack tool,List<ItemStack> samples) {
 public SurveyCargo {tool=tool.copy();samples=samples.stream().map(ItemStack::copy).toList();int count=samples.stream().mapToInt(ItemStack::getCount).sum();if(samples.size()>16||samples.stream().anyMatch(s->s.isEmpty()||s.getCount()>16)||count>16||!tool.isEmpty()&&tool.getCount()!=1||!paid&&(!tool.isEmpty()||!samples.isEmpty()))throw new IllegalArgumentException("Invalid survey cargo");}
 @Override public ItemStack tool(){return tool.copy();}
 @Override public List<ItemStack> samples(){return samples.stream().map(ItemStack::copy).toList();}
 public int count(){return samples.stream().mapToInt(ItemStack::getCount).sum();}
 public static SurveyCargo empty(){return new SurveyCargo(false,ItemStack.EMPTY,List.of());}
 public CompoundTag save(HolderLookup.Provider registries){var t=new CompoundTag();t.putInt("version",1);t.putBoolean("paid",paid);if(!tool.isEmpty())t.put("tool",tool.save(registries));var list=new ListTag();samples.forEach(s->list.add(s.save(registries)));t.put("samples",list);return t;}
 public static SurveyCargo load(CompoundTag t,HolderLookup.Provider registries){
  if(t.contains("pending"))throw new IllegalStateException("Interrupted survey custody requires recovery");
  if(t.getInt("version")!=1||!t.contains("paid",Tag.TAG_BYTE)||!t.contains("samples",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid survey cargo schema");
  var list=(ListTag)t.get("samples");if(list.size()>16||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid survey samples");
  var samples=new ArrayList<ItemStack>();for(var value:list)samples.add(ItemStack.parse(registries,value).orElseThrow(()->new IllegalArgumentException("Unknown survey sample")));
  return new SurveyCargo(t.getBoolean("paid"),t.contains("tool")?ItemStack.parse(registries,t.getCompound("tool")).orElseThrow(()->new IllegalArgumentException("Unknown survey tool")):ItemStack.EMPTY,samples);
 }
}
