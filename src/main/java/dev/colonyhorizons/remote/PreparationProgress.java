// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import net.minecraft.nbt.*;
import java.util.Objects;
public record PreparationProgress(Stage stage,SitePreparationJournal work,boolean inventoryPending) {
 public PreparationProgress(Stage stage,SitePreparationJournal work){this(stage,work,false);}
 public enum Stage { PLACING, CAMP_READY, HANDOFF_INTENT, HANDED_OVER, CLEANUP_INTENT, COMPLETE }
 public PreparationProgress {Objects.requireNonNull(stage);Objects.requireNonNull(work);if(inventoryPending&&(stage!=Stage.CAMP_READY||work.pending()||work.cancelled()))throw new IllegalArgumentException("Invalid camp inventory intent");if(stage.ordinal()>=Stage.HANDOFF_INTENT.ordinal()&&!work.finished())throw new IllegalArgumentException("Camp handoff before terrain completion");}
 public boolean uncertain(){return inventoryPending||work.pending()||stage==Stage.PLACING||stage==Stage.HANDOFF_INTENT||stage==Stage.CLEANUP_INTENT;}
 public CompoundTag save(){var t=new CompoundTag();t.putInt("version",1);t.putString("stage",stage.name());t.put("work",work.save());t.putBoolean("inventoryPending",inventoryPending);return t;}
 public static PreparationProgress load(CompoundTag t){if(t.getInt("version")!=1)throw new IllegalArgumentException("Unknown preparation progress");return new PreparationProgress(Stage.valueOf(t.getString("stage")),SitePreparationJournal.load(t.getCompound("work")),t.getBoolean("inventoryPending"));}
}
