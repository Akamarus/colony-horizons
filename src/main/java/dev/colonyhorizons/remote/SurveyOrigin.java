// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import dev.colonyhorizons.surveyor.*;

/** Saved evidence identity, never authority to create claims or free resources. */
public record SurveyOrigin(UUID survey,BlockPos source,ProductionProfile profile) {
 public SurveyOrigin {Objects.requireNonNull(survey);source=Objects.requireNonNull(source).immutable();Objects.requireNonNull(profile);}
 public void verify(ServerLevel level,UUID owner,int colony){
  verifyEvidence(level,owner,colony,false);
 }
 void verifyEvidence(ServerLevel level,UUID owner,int colony,boolean inspection){
  var data=SurveySavedData.get(level);var r=data.find(survey).orElseThrow(()->new IllegalArgumentException("Survey report is unavailable"));var samples=data.samples(survey);
  boolean validState=r.state()==SurveyRecord.State.COMPLETE||(inspection&&r.state()==SurveyRecord.State.SEARCHING);
  if(!r.owner().equals(owner)||r.colony()!=colony||!validState||!r.target().equals(profile.target())||samples.pending().isPresent()||samples.count()<1||!samples.source().equals(source))throw new IllegalArgumentException("Completed survey evidence changed");
 }
 public CompoundTag save(){var t=new CompoundTag();t.putInt("version",1);t.putUUID("survey",survey);t.putLong("source",source.asLong());t.put("profile",profile.save());return t;}
 public static SurveyOrigin load(CompoundTag t){if(t.getInt("version")!=1||!t.hasUUID("survey")||!t.contains("source",Tag.TAG_LONG)||!t.contains("profile",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid survey origin");return new SurveyOrigin(t.getUUID("survey"),BlockPos.of(t.getLong("source")),ProductionProfile.load(t.getCompound("profile")));}
}
