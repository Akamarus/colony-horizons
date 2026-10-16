// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import net.minecraft.core.BlockPos;

public final class BuildingSurveyor extends AbstractBuilding {
    public BuildingSurveyor(IColony colony, BlockPos position) { super(colony, position); }
    @Override public String getSchematicName() { return "surveyor"; }
    @Override public int getMaxBuildingLevel() { return 5; }
}
