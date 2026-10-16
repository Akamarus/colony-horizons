// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;

public final class JobMayor extends AbstractJob<JobMayor.MayorAI,JobMayor> {
 public JobMayor(ICitizenData citizen){super(citizen);setNameTag("Mayor");}
 @Override public MayorAI generateAI(){return new MayorAI(this);}
 public static final class MayorAI extends AbstractEntityAIBasic<JobMayor,BuildingBuilder> {
  MayorAI(JobMayor job){super(job);registerTarget(new TickingTransition<>(AIWorkerState.IDLE,()->true,()->{MayorService.tick(job.getCitizen());return AIWorkerState.IDLE;},100));}
  @Override public Class<BuildingBuilder> getExpectedBuildingClass(){return BuildingBuilder.class;}
 }
}
