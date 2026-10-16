// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import net.minecraft.core.BlockPos;
import java.util.*;
public record MayorAppointment(int citizenId,UUID entityId,BlockPos station) {
 public MayorAppointment{if(citizenId<=0)throw new IllegalArgumentException("Invalid Mayor citizen");Objects.requireNonNull(entityId);station=station.immutable();}
}
