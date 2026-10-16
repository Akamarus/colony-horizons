// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import java.util.*;

/** Intent is durable before harvest. A pending intent on restart requires recovery, never another harvest. */
public record SampleJournal(Set<UUID> receipts,Optional<UUID> pending,BlockPos source,int count) {
 public SampleJournal {receipts=Set.copyOf(receipts);source=source.immutable();if(count<0||count>16||receipts.size()>16||pending.filter(receipts::contains).isPresent())throw new IllegalArgumentException("Invalid sample journal");}
 public static SampleJournal empty(){return new SampleJournal(Set.of(),Optional.empty(),BlockPos.ZERO,0);}
 public SampleJournal prepare(UUID op,BlockPos pos){if(pending.isPresent()||receipts.contains(op)||count>=16)throw new IllegalStateException("Unresolved or repeated sample");return new SampleJournal(receipts,Optional.of(op),pos,count);}
 public SampleJournal collected(UUID op,int amount){if(pending.isEmpty()||!pending.get().equals(op)||amount<0||amount>16-count||receipts.size()>=16)throw new IllegalStateException("Invalid sample receipt");var next=new HashSet<>(receipts);next.add(op);return new SampleJournal(next,Optional.empty(),source,count+amount);}
 public CompoundTag save(){var t=new CompoundTag();var list=new ListTag();for(var id:receipts)list.add(StringTag.valueOf(id.toString()));t.put("receipts",list);pending.ifPresent(id->t.putUUID("pending",id));t.putLong("source",source.asLong());t.putInt("count",count);return t;}
 public static SampleJournal load(CompoundTag t){var ids=new HashSet<UUID>();var list=t.getList("receipts",Tag.TAG_STRING);if(list.size()>16)throw new IllegalArgumentException("Too many receipts");for(var value:list)if(!ids.add(UUID.fromString(value.getAsString())))throw new IllegalArgumentException("Duplicate sample receipt");return new SampleJournal(ids,t.hasUUID("pending")?Optional.of(t.getUUID("pending")):Optional.empty(),BlockPos.of(t.getLong("source")),t.getInt("count"));}
}
