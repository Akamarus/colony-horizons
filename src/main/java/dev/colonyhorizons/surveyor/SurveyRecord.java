// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record SurveyRecord(UUID id,int colony,UUID owner,BlockPos hut,int worker,UUID workerUuid,
 ResourceLocation target,long revision,State state,int inspected) {
 public enum State { PREPARING, SEARCHING, RETURNING, COMPLETE, CANCELLED, RECOVERY_REQUIRED }
 public SurveyRecord {
  Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(workerUuid);Objects.requireNonNull(state);hut=hut.immutable();
  if(colony<=0||worker<=0||revision<0||inspected<0||inspected>32||SurveyResourceCatalog.find(target).isEmpty())throw new IllegalArgumentException("Invalid survey record");
 }
 public static SurveyRecord create(UUID id,int colony,UUID owner,BlockPos hut,int worker,UUID workerUuid,ResourceLocation target){return new SurveyRecord(id,colony,owner,hut,worker,workerUuid,target,0,State.PREPARING,0);}
 public boolean belongsToWorker(int citizen,UUID entity){return worker==citizen&&workerUuid.equals(entity);}
 public boolean active(){return state!=State.COMPLETE&&state!=State.CANCELLED;}
 public SurveyRecord next(State next){
  boolean allowed=switch(state){case PREPARING->next==State.SEARCHING||next==State.CANCELLED;case SEARCHING->next==State.RETURNING;case RETURNING->next==State.COMPLETE||next==State.CANCELLED;default->false;};
  if(active()&&state!=State.RECOVERY_REQUIRED&&next==State.RECOVERY_REQUIRED)allowed=true;
  if(!allowed)throw new IllegalStateException("Invalid survey transition "+state+" -> "+next);
  return new SurveyRecord(id,colony,owner,hut,worker,workerUuid,target,Math.addExact(revision,1),next,inspected);
 }
 public SurveyRecord advanceCandidate(){if(state!=State.SEARCHING||inspected>=32)throw new IllegalStateException("Search budget exhausted");return new SurveyRecord(id,colony,owner,hut,worker,workerUuid,target,Math.addExact(revision,1),state,inspected+1);}
 public CompoundTag save(){var t=new CompoundTag();t.putInt("version",1);t.putUUID("id",id);t.putInt("colony",colony);t.putUUID("owner",owner);t.putLong("hut",hut.asLong());t.putInt("worker",worker);t.putUUID("workerUuid",workerUuid);t.putString("target",target.toString());t.putLong("revision",revision);t.putString("state",state.name());t.putInt("inspected",inspected);return t;}
 public static SurveyRecord load(CompoundTag t){if(t.getInt("version")!=1)throw new IllegalArgumentException("Unsupported survey version");return new SurveyRecord(t.getUUID("id"),t.getInt("colony"),t.getUUID("owner"),BlockPos.of(t.getLong("hut")),t.getInt("worker"),t.getUUID("workerUuid"),ResourceLocation.parse(t.getString("target")),t.getLong("revision"),State.valueOf(t.getString("state")),t.getInt("inspected"));}
}
