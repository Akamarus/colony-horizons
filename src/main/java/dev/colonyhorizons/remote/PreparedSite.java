// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Versioned immutable preparation authority attached only to newly reviewed drafts. */
public record PreparedSite(SitePreparationPlan terrain,BlockPos camp,List<BlockPos> arrivals,List<BlockPos> storage) {
 public PreparedSite(SitePreparationPlan terrain,BlockPos camp,List<BlockPos> arrivals){this(terrain,camp,arrivals,List.of());}
 public PreparedSite {Objects.requireNonNull(terrain);storage=storage.stream().map(BlockPos::immutable).toList();if(storage.size()>8||new HashSet<>(storage).size()!=storage.size()||storage.contains(camp)||storage.stream().anyMatch(arrivals::contains))throw new IllegalArgumentException("Invalid camp storage");camp=camp.immutable();arrivals=arrivals.stream().map(BlockPos::immutable).toList();if(!terrain.approved()||arrivals.size()!=4||new HashSet<>(arrivals).size()!=4||arrivals.contains(camp))throw new IllegalArgumentException("Invalid prepared site");}
 public static PreparedSite review(ServerLevel level,RemoteModels.SiteRequest request,List<RemoteModels.BuildingPlan> buildings){
  if(!FoundingRoster.free(request)||buildings.isEmpty())throw new IllegalArgumentException("Terrain preparation needs a four-settler founding plan");
  var volumes=new ArrayList<BoundingBox>();buildings.forEach(b->volumes.add(b.upgradeVolume()));
  // Shared approaches have the same ground height as the modules; preserve land outside this access area.
  var c=request.center();volumes.add(new BoundingBox(c.getX()-36,c.getY(),c.getZ()-28,c.getX()+46,c.getY()+SitePreparationPlan.MAX_DEPTH,c.getZ()+40));
  BlockPos camp=null;List<BlockPos> arrivals=List.of();
  for(int z=8;z<=20&&camp==null;z+=4){var candidate=c.offset(-5,0,z);var targets=new ArrayList<BlockPos>();for(int i=0;i<4;i++)targets.add(c.offset(i*2-2,0,z));
   var all=new ArrayList<>(targets);all.add(candidate);
   if(all.stream().allMatch(p->request.allowedArea().isInside(p)&&buildings.stream().noneMatch(b->b.upgradeVolume().isInside(p))&&dry(level,p))){camp=candidate;arrivals=targets;}
  }
  if(camp==null)throw new IllegalArgumentException("No safe natural arrival camp; survey another site");
  for(var v:volumes)if(!RemoteModels.contains(request.allowedArea(),v))throw new IllegalArgumentException("Prepared access lies outside approved land");
  var plan=SitePreparationPlan.reviewContainedWater(level,volumes,Set.of());if(!plan.approved())throw new IllegalArgumentException(String.join("; ",plan.blockers()));
  int count=Math.min(8,(plan.clearRequired()+255)/256);var storage=new ArrayList<BlockPos>();
  for(int radius=2;radius<=6&&storage.size()<count;radius+=2)for(int dx=-radius;dx<=radius&&storage.size()<count;dx+=2)for(int dz=-radius;dz<=radius&&storage.size()<count;dz+=2){if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;var target=camp.offset(dx,0,dz);if(request.allowedArea().isInside(target)&&!arrivals.contains(target)&&buildings.stream().noneMatch(b->b.upgradeVolume().isInside(target))&&dry(level,target))storage.add(target);}
  if(storage.size()!=count)throw new IllegalArgumentException("No supported temporary storage area; survey another site");
  return new PreparedSite(plan,camp,arrivals,storage);
 }
 static boolean dry(ServerLevel l,BlockPos p){return l.hasChunkAt(p)&&l.getWorldBorder().isWithinBounds(p)&&l.getBlockEntity(p)==null&&l.getBlockEntity(p.below())==null&&l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP)&&l.getFluidState(p.below()).isEmpty()&&l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir();}
 public String fingerprint(){return ArcologySettlementLayout.sha(terrain.fingerprint()+camp+arrivals.toString()+(storage.isEmpty()?"":storage.toString()));}
 public CompoundTag save(){var t=new CompoundTag();t.putInt("version",2);t.put("terrain",terrain.save());t.putLong("camp",camp.asLong());t.putLongArray("arrivals",arrivals.stream().mapToLong(BlockPos::asLong).toArray());t.putLongArray("storage",storage.stream().mapToLong(BlockPos::asLong).toArray());return t;}
 public static PreparedSite load(CompoundTag t){if((t.getInt("version")!=1&&t.getInt("version")!=2)||t.getInt("version")==2&&!t.contains("storage",Tag.TAG_LONG_ARRAY)||!t.contains("camp",Tag.TAG_LONG)||!t.contains("arrivals",Tag.TAG_LONG_ARRAY))throw new IllegalArgumentException("Unknown preparation version");return new PreparedSite(SitePreparationPlan.load(t.getCompound("terrain")),BlockPos.of(t.getLong("camp")),Arrays.stream(t.getLongArray("arrivals")).mapToObj(BlockPos::of).toList(),Arrays.stream(t.getLongArray("storage")).mapToObj(BlockPos::of).toList());}
}
