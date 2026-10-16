// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Bounded, informational site search. A result must be revalidated before founding. */
public record SurveySiteSearch(BlockPos source,int inspected,Optional<BlockPos> site,String reason) {
 public static final int LIMIT=9;
 public SurveySiteSearch {
  source=Objects.requireNonNull(source).immutable();site=Objects.requireNonNull(site).map(BlockPos::immutable);Objects.requireNonNull(reason);
  if(inspected<0||inspected>LIMIT||reason.length()>512||site.isPresent()&&inspected==0)throw new IllegalArgumentException("Invalid site search");
  if(site.isPresent()){var p=site.orElseThrow();if(Math.abs((long)p.getX()-source.getX())>64||Math.abs((long)p.getZ()-source.getZ())>64)throw new IllegalArgumentException("Site is too far from survey evidence");}
 }
 public static SurveySiteSearch start(BlockPos source){return new SurveySiteSearch(source,0,Optional.empty(),"");}
 public boolean complete(){return site.isPresent()||inspected==LIMIT;}
 public BlockPos candidate(){if(complete())throw new IllegalStateException("Site search finished");int[][] offsets={{0,0},{32,0},{0,32},{-32,0},{0,-32},{32,32},{-32,32},{-32,-32},{32,-32}};return source.offset(offsets[inspected][0],0,offsets[inspected][1]);}
 public SurveySiteSearch checked(Optional<BlockPos> found,String why){if(complete())throw new IllegalStateException("Site search finished");if(found.isPresent()&&(found.orElseThrow().getX()!=candidate().getX()||found.orElseThrow().getZ()!=candidate().getZ()))throw new IllegalArgumentException("Result does not match inspected candidate");return new SurveySiteSearch(source,inspected+1,found,why.length()>512?why.substring(0,512):why);}
 public CompoundTag save(){var t=new CompoundTag();t.putInt("version",1);t.putLong("source",source.asLong());t.putInt("inspected",inspected);site.ifPresent(p->t.putLong("site",p.asLong()));t.putString("reason",reason);return t;}
 public static SurveySiteSearch load(CompoundTag t){if(t.getInt("version")!=1||!t.contains("source",Tag.TAG_LONG)||!t.contains("inspected",Tag.TAG_INT)||!t.contains("reason",Tag.TAG_STRING)||t.contains("site")&&!t.contains("site",Tag.TAG_LONG))throw new IllegalArgumentException("Invalid saved site search");return new SurveySiteSearch(BlockPos.of(t.getLong("source")),t.getInt("inspected"),t.contains("site")?Optional.of(BlockPos.of(t.getLong("site"))):Optional.empty(),t.getString("reason"));}
}

