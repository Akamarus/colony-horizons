// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
public record GatheringOffer(ResourceLocation target,int count,int ticks,int bread,SurveyResource.Tool tool,int wear) {
 public GatheringOffer {if(target==null||tool==null||count<1||count>16||ticks!=6000||bread!=2||wear<0||wear>16||tool==SurveyResource.Tool.HAND&&wear!=0)throw new IllegalArgumentException("Invalid gathering offer");}
 public CompoundTag save(){var t=new CompoundTag();t.putString("target",target.toString());t.putInt("count",count);t.putInt("ticks",ticks);t.putInt("bread",bread);t.putString("tool",tool.name());t.putInt("wear",wear);return t;}
 public static GatheringOffer load(CompoundTag t){var result=new GatheringOffer(ResourceLocation.parse(t.getString("target")),t.getInt("count"),t.getInt("ticks"),t.getInt("bread"),SurveyResource.Tool.valueOf(t.getString("tool")),t.getInt("wear"));if(!GatheringCatalog.find(result.target()).filter(result::equals).isPresent())throw new IllegalArgumentException("Unknown gathering offer");return result;}
}
