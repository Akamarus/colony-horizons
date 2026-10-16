// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public final class BlockHutSurveyor extends AbstractBlockHut<BlockHutSurveyor> {
    @Override public String getHutName() { return "blockhutsurveyor"; }
    @Override public BuildingEntry getBuildingEntry() { return SurveyorRegistration.SURVEYOR.get(); }
}
