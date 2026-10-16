// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;

/** Real sampled biome, bound to a survey; not an unlock until return completes. */
public record DiscoveryEvidence(UUID survey,int colony,UUID owner,ResourceLocation dimension,
 ResourceLocation biome,ResourceLocation target,BlockPos source) {
 public DiscoveryEvidence {
  Objects.requireNonNull(survey);Objects.requireNonNull(owner);Objects.requireNonNull(dimension);Objects.requireNonNull(biome);source=Objects.requireNonNull(source).immutable();
  if(colony<=0||SurveyResourceCatalog.find(target).isEmpty())throw new IllegalArgumentException("Invalid discovery evidence");
 }
 public void verify(SurveyRecord r,SampleJournal samples){
  if(!survey.equals(r.id())||colony!=r.colony()||!owner.equals(r.owner())||!target.equals(r.target())||!source.equals(samples.source())||samples.pending().isEmpty()&&samples.count()==0)throw new IllegalArgumentException("Discovery evidence does not match survey");
 }
 public CompoundTag save(){var t=new CompoundTag();t.putInt("version",1);t.putUUID("survey",survey);t.putInt("colony",colony);t.putUUID("owner",owner);t.putString("dimension",dimension.toString());t.putString("biome",biome.toString());t.putString("target",target.toString());t.putLong("source",source.asLong());return t;}
 public static DiscoveryEvidence load(CompoundTag t){
  if(t.getInt("version")!=1||!t.hasUUID("survey")||!t.hasUUID("owner")||!t.contains("colony",Tag.TAG_INT)||!t.contains("source",Tag.TAG_LONG)||!t.contains("dimension",Tag.TAG_STRING)||!t.contains("biome",Tag.TAG_STRING)||!t.contains("target",Tag.TAG_STRING))throw new IllegalArgumentException("Malformed discovery evidence");
  return new DiscoveryEvidence(t.getUUID("survey"),t.getInt("colony"),t.getUUID("owner"),ResourceLocation.parse(t.getString("dimension")),ResourceLocation.parse(t.getString("biome")),ResourceLocation.parse(t.getString("target")),BlockPos.of(t.getLong("source")));
 }
}
