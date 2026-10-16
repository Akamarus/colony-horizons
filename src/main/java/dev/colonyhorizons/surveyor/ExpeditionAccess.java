// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import net.minecraft.server.level.ServerLevel;
public final class ExpeditionAccess {
 private ExpeditionAccess(){}
 public static void requireIdle(ServerLevel level,int colony){if(SurveySavedData.existing(level).stream().flatMap(d->d.records().stream()).anyMatch(r->r.colony()==colony&&r.active())||GatheringSavedData.existing(level).flatMap(d->d.active(colony)).isPresent())throw new IllegalStateException("This colony already has an active expedition");}
}
