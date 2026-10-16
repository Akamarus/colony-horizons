// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import net.minecraft.world.item.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
/** Simulated rewards have separate custody from actual discovery samples. */
public record GatheringCargo(boolean paid,ItemStack tool,ItemStack reward,boolean earned,boolean delivered) {
 public GatheringCargo {tool=tool.copy();reward=reward.copy();if(earned&&!delivered&&reward.isEmpty()||!tool.isEmpty()&&tool.getCount()!=1||!reward.isEmpty()&&(reward.getCount()>16||!earned)||!paid&&(!tool.isEmpty()||!reward.isEmpty()||earned||delivered)||delivered&&(!tool.isEmpty()||!reward.isEmpty()))throw new IllegalArgumentException("Invalid gathering custody");}
 @Override public ItemStack tool(){return tool.copy();}
 @Override public ItemStack reward(){return reward.copy();}
 public static GatheringCargo empty(){return new GatheringCargo(false,ItemStack.EMPTY,ItemStack.EMPTY,false,false);}
 public void verify(GatheringRecord record){
  if(!paid||earned!=(record.remaining()==0)||delivered&&record.state()!=GatheringRecord.State.RETURNING)throw new IllegalStateException("Gathering custody disagrees with timer");
  if(earned&&!delivered){var expected=new ItemStack(BuiltInRegistries.ITEM.get(record.offer().target()),record.offer().count());if(!ItemStack.matches(reward,expected))throw new IllegalStateException("Gathering reward disagrees with offer");}
  if(!tool.isEmpty()&&(record.offer().tool()==SurveyResource.Tool.HAND||record.offer().tool()==SurveyResource.Tool.AXE&&!(tool.getItem() instanceof AxeItem)||record.offer().tool()==SurveyResource.Tool.SHEARS&&!tool.is(Items.SHEARS)))throw new IllegalStateException("Gathering tool disagrees with offer");
 }
 public GatheringCargo earn(GatheringOffer offer){if(!paid||earned||delivered)throw new IllegalStateException("Reward already earned or unpaid");return new GatheringCargo(true,tool,new ItemStack(BuiltInRegistries.ITEM.get(offer.target()),offer.count()),true,false);}
 public GatheringCargo deliveredCargo(){if(!paid||delivered)throw new IllegalStateException("Cargo already delivered or unpaid");return new GatheringCargo(true,ItemStack.EMPTY,ItemStack.EMPTY,earned,true);}
 public CompoundTag save(HolderLookup.Provider regs){var t=new CompoundTag();t.putInt("version",1);t.putBoolean("paid",paid);t.putBoolean("earned",earned);t.putBoolean("delivered",delivered);if(!tool.isEmpty())t.put("tool",tool.save(regs));if(!reward.isEmpty())t.put("reward",reward.save(regs));return t;}
 public static GatheringCargo load(CompoundTag t,HolderLookup.Provider regs){if(t.contains("pending"))throw new IllegalStateException("Interrupted gathering custody needs recovery");if(t.getInt("version")!=1||!t.contains("paid",Tag.TAG_BYTE)||!t.contains("earned",Tag.TAG_BYTE)||!t.contains("delivered",Tag.TAG_BYTE))throw new IllegalArgumentException("Invalid gathering cargo schema");return new GatheringCargo(t.getBoolean("paid"),stack(t,"tool",regs),stack(t,"reward",regs),t.getBoolean("earned"),t.getBoolean("delivered"));}
 private static ItemStack stack(CompoundTag t,String key,HolderLookup.Provider regs){return t.contains(key)?ItemStack.parse(regs,t.get(key)).orElseThrow(()->new IllegalArgumentException("Unknown gathering item")):ItemStack.EMPTY;}
}
