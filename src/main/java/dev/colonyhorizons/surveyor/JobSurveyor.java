// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;

public final class JobSurveyor extends AbstractJob<JobSurveyor.SurveyorAI, JobSurveyor> {
    public JobSurveyor(ICitizenData citizen) { super(citizen); setNameTag("Explorer"); }
    @Override public SurveyorAI generateAI() { return new SurveyorAI(this); }
    public static final class SurveyorAI extends AbstractEntityAIBasic<JobSurveyor, BuildingSurveyor> {
        SurveyorAI(JobSurveyor job) {
            super(job);
            registerTarget(new TickingTransition<>(AIWorkerState.IDLE, () -> true, () -> AIWorkerState.IDLE, 20));
        }
        @Override public Class<BuildingSurveyor> getExpectedBuildingClass() { return BuildingSurveyor.class; }
    }
}
