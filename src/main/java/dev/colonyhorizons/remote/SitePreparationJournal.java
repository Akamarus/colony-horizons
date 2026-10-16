// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import net.minecraft.nbt.*;
import java.util.*;

/** A pending write remains pending after reload: never guess whether world/inventory saves completed. */
public record SitePreparationJournal(UUID site,String fingerprint,int total,int cursor,boolean pending,boolean cancelled) {
    public SitePreparationJournal {
        Objects.requireNonNull(site);Objects.requireNonNull(fingerprint);
        if(!fingerprint.matches("[a-f0-9]{64}")||total<0||total>SitePreparationPlan.MAX_OPERATIONS||cursor<0||cursor>total||pending&&cursor==total)
            throw new IllegalArgumentException("Invalid terrain work receipt");
    }
    public static SitePreparationJournal create(UUID site,String fingerprint,int total){return new SitePreparationJournal(site,fingerprint,total,0,false,false);}
    public boolean finished(){return !cancelled&&!pending&&cursor==total;}
    public SitePreparationJournal begin(int expectedCursor){
        if(cancelled||pending||cursor!=expectedCursor||cursor==total)throw new IllegalStateException("Terrain work is stale, complete, or needs recovery");
        return new SitePreparationJournal(site,fingerprint,total,cursor,true,false);
    }
    public SitePreparationJournal complete(int expectedCursor){
        if(cancelled||!pending||cursor!=expectedCursor)throw new IllegalStateException("No matching pending terrain work");
        return new SitePreparationJournal(site,fingerprint,total,cursor+1,false,false);
    }
    public SitePreparationJournal cancel(){return new SitePreparationJournal(site,fingerprint,total,cursor,pending,true);}
    public CompoundTag save(){var tag=new CompoundTag();tag.putInt("version",1);tag.putUUID("site",site);tag.putString("fingerprint",fingerprint);tag.putInt("total",total);tag.putInt("cursor",cursor);tag.putBoolean("pending",pending);tag.putBoolean("cancelled",cancelled);return tag;}
    public static SitePreparationJournal load(CompoundTag tag){
        if(tag.getInt("version")!=1||!tag.hasUUID("site")||!tag.contains("total",Tag.TAG_INT)||!tag.contains("cursor",Tag.TAG_INT)||!tag.contains("pending",Tag.TAG_BYTE)||!tag.contains("cancelled",Tag.TAG_BYTE))throw new IllegalArgumentException("Unknown terrain work journal");
        return new SitePreparationJournal(tag.getUUID("site"),tag.getString("fingerprint"),tag.getInt("total"),tag.getInt("cursor"),tag.getBoolean("pending"),tag.getBoolean("cancelled"));
    }
}
