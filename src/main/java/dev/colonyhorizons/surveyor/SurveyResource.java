// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

/** Exact requested output; matching a biome alone never earns a sample. */
public record SurveyResource(ResourceLocation item, Set<Block> sources, Tool tool, Profile profile) {
 public enum Tool { HAND, AXE, SHEARS }
 public enum Profile { FORESTRY, FLOWERS, SEEDS, VINES, KELP, SEAGRASS }
 public SurveyResource { sources=Set.copyOf(sources);if(sources.isEmpty())throw new IllegalArgumentException("Missing resource source"); }
 public boolean matches(BlockState state){return sources.contains(state.getBlock());}
}
