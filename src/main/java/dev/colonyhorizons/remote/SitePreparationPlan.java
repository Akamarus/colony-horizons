// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Read-only, bounded estimate. Not authority to mutate terrain or a replacement for live permission checks. */
public final class SitePreparationPlan {
    public static final int MAX_DEPTH=8, MAX_OPERATIONS=32768, MAX_INSPECTED=262144;
    public enum Kind { CLEAR, FILL }
    public record Operation(BlockPos pos,BlockState original,BlockState target,Kind kind) {
        public Operation {pos=pos.immutable();Objects.requireNonNull(original);Objects.requireNonNull(target);Objects.requireNonNull(kind);}
    }
    private final List<Operation> operations;
    private final Map<BlockPos,BlockState> observed;
    private final List<String> blockers;
    private String fingerprint;
    private SitePreparationPlan(List<Operation> operations,Map<BlockPos,BlockState> observed,Collection<String> blockers) {
        this.operations=List.copyOf(operations);this.observed=Map.copyOf(observed);this.blockers=List.copyOf(blockers);
    }
    public List<Operation> operations(){return operations;}
    public boolean within(BoundingBox area,Set<ChunkPos> chunks){return observed.keySet().stream().allMatch(p->area.isInside(p)&&chunks.contains(new ChunkPos(p)));}
    public List<String> blockers(){return blockers;}
    public boolean approved(){return blockers.isEmpty();}
    public int dirtRequired(){return (int)operations.stream().filter(o->o.kind()==Kind.FILL).count();}
    public int clearRequired(){return (int)operations.stream().filter(o->o.kind()==Kind.CLEAR).count();}
    public String fingerprint(){if(fingerprint==null)fingerprint=ArcologySettlementLayout.sha(save().toString());return fingerprint;}
    public CompoundTag save() {
        if(!approved())throw new IllegalStateException("Only approved terrain estimates can be saved");
        var tag=new CompoundTag();tag.putInt("version",operations.stream().anyMatch(o->water(o.original()))?2:1);var states=new ListTag();
        observed.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var row=new CompoundTag();row.putLong("pos",e.getKey().asLong());row.put("state",NbtUtils.writeBlockState(e.getValue()));states.add(row);});
        tag.put("observed",states);var ops=new ListTag();
        for(var op:operations){var row=new CompoundTag();row.putLong("pos",op.pos().asLong());row.putString("kind",op.kind().name());ops.add(row);}tag.put("operations",ops);return tag;
    }
    public static SitePreparationPlan load(CompoundTag tag) {
        if((tag.getInt("version")!=1&&tag.getInt("version")!=2)||!tag.contains("observed",Tag.TAG_LIST)||!tag.contains("operations",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown terrain estimate");
        var states=tag.getList("observed",Tag.TAG_COMPOUND);var ops=tag.getList("operations",Tag.TAG_COMPOUND);
        if(states.isEmpty()||states.size()>MAX_INSPECTED||ops.size()>MAX_OPERATIONS)throw new IllegalArgumentException("Invalid terrain estimate size");
        var observed=new LinkedHashMap<BlockPos,BlockState>();
        for(var raw:states){var row=(CompoundTag)raw;var stateTag=row.getCompound("state");
            var state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),stateTag);
            if(!row.contains("pos",Tag.TAG_LONG)||!NbtUtils.writeBlockState(state).equals(stateTag)||observed.putIfAbsent(BlockPos.of(row.getLong("pos")),state)!=null)throw new IllegalArgumentException("Invalid terrain observation");
        }
        var operations=new ArrayList<Operation>();var seen=new HashSet<BlockPos>();
        for(var raw:ops){var row=(CompoundTag)raw;var p=BlockPos.of(row.getLong("pos"));var before=observed.get(p);var kind=Kind.valueOf(row.getString("kind"));
            if(!row.contains("pos",Tag.TAG_LONG)||before==null||!seen.add(p)||!before.getFluidState().isEmpty()&&!(tag.getInt("version")==2&&kind==Kind.FILL&&water(before))
                ||kind==Kind.CLEAR&&(before.isAir()||!natural(before))||kind==Kind.FILL&&!before.isAir()&&!natural(before)&&!water(before))throw new IllegalArgumentException("Invalid terrain operation");
            operations.add(new Operation(p,before,kind==Kind.CLEAR?Blocks.AIR.defaultBlockState():Blocks.DIRT.defaultBlockState(),kind));
        }
        waterBoundary(operations,observed);
        return new SitePreparationPlan(operations,observed,List.of());
    }
    public boolean matches(ServerLevel level) {
        return approved()&&observed.entrySet().stream().allMatch(e->available(level,e.getKey())
            &&level.getBlockEntity(e.getKey())==null&&level.getBlockState(e.getKey()).equals(e.getValue()));
    }
    public static SitePreparationPlan review(ServerLevel level,RemoteModels.SiteRequest request,List<RemoteModels.BuildingPlan> buildings) {
        if(buildings.stream().anyMatch(b->!RemoteModels.contains(request.allowedArea(),b.upgradeVolume())))
            return rejected("Building lies outside the approved area");
        return review(level,buildings.stream().map(RemoteModels.BuildingPlan::upgradeVolume).toList());
    }
    public static SitePreparationPlan review(ServerLevel level,List<BoundingBox> volumes) {
        return review(level,volumes,Set.of(),false);
    }
    public static SitePreparationPlan reviewContainedWater(ServerLevel level,List<BoundingBox> volumes,Set<BlockPos> preserved) {
        return review(level,volumes,Set.copyOf(preserved),true);
    }
    private static SitePreparationPlan review(ServerLevel level,List<BoundingBox> volumes,Set<BlockPos> preserved,boolean wet) {
        if(volumes.isEmpty()||volumes.size()>32)return rejected("Invalid preparation footprint");
        long inspection=0;
        for(var v:volumes) {
            long width=(long)v.maxX()-v.minX()+1,depth=(long)v.maxZ()-v.minZ()+1,height=(long)v.maxY()-v.minY()+1;
            if(width>112||depth>112||height>128||v.minY()<=level.getMinBuildHeight()||v.maxY()>=level.getMaxBuildHeight())
                return rejected("Preparation footprint exceeds safe bounds");
            inspection+=width*depth*(height+MAX_DEPTH+1);
            if(inspection>MAX_INSPECTED)return rejected("Terrain inspection budget exceeded");
        }
        var observed=new LinkedHashMap<BlockPos,BlockState>();var changes=new LinkedHashMap<BlockPos,Operation>();var blockers=new LinkedHashSet<String>();
        for(var v:volumes)for(int x=v.minX();x<=v.maxX();x++)for(int z=v.minZ();z<=v.maxZ();z++) {
            var fill=new ArrayList<BlockPos>();boolean supported=false;
            for(int dy=1;dy<=MAX_DEPTH+1;dy++) {
                var p=new BlockPos(x,v.minY()-dy,z);var s=observe(level,p,observed,blockers);if(s==null)break;
                if(!s.getFluidState().isEmpty()&&!(wet&&water(s)&&dy<=2)){blockers.add("Wet foundations need enclosed source water at most two blocks deep");break;}
                if(s.isFaceSturdy(level,p,Direction.UP)) {
                    if(!natural(s))blockers.add("Foundation touches an existing structure");
                    else supported=true;
                    break;
                }
                if(!s.isAir()&&!natural(s)&&!(wet&&water(s))){blockers.add("Foundation contains protected blocks");break;}
                if(dy<=MAX_DEPTH)fill.add(p);else break;
            }
            if(!supported)blockers.add("Foundation cannot reach solid ground within eight blocks");
            else for(int i=fill.size()-1;i>=0;i--) {
                var p=fill.get(i);put(changes,new Operation(p,observed.get(p),Blocks.DIRT.defaultBlockState(),Kind.FILL),blockers);
            }
            int cut=0;
            for(int y=v.maxY();y>=v.minY();y--) {
                var p=new BlockPos(x,y,z);var s=observe(level,p,observed,blockers);if(s==null)continue;
                if(s.isAir())continue;
                if(!s.getFluidState().isEmpty()){blockers.add("Water must be preserved or separately contained");continue;}
                if(!natural(s)){blockers.add("Clearing would disturb a protected or constructed block");continue;}
                cut++;
                put(changes,new Operation(p,s,Blocks.AIR.defaultBlockState(),Kind.CLEAR),blockers);
            }
            if(cut>MAX_DEPTH)blockers.add("Excavation exceeds eight blocks in one column");
            if(changes.size()>MAX_OPERATIONS)return rejected("Preparation exceeds the block-operation budget");
        }
        if(changes.keySet().stream().anyMatch(preserved::contains))blockers.add("Preparation would disturb a preserved aquatic area");
        if(wet){
            var waters=changes.values().stream().filter(o->water(o.original())).toList();
            if(waters.size()>512)blockers.add("Shallow-water preparation exceeds 512 blocks");
            else {
                for(var op:waters)for(var direction:Direction.values())observe(level,op.pos().relative(direction),observed,blockers);
                if(observed.size()>MAX_INSPECTED)blockers.add("Terrain inspection budget exceeded");
                try{waterBoundary(new ArrayList<>(changes.values()),observed);}catch(IllegalArgumentException e){blockers.add(e.getMessage());}
            }
        }
        // Finish each clearing column top-down, exposing its neighbor rather than leaving a solid lower shelf.
        // All clearing still precedes bottom-up fill, avoiding unsupported intermediate fill blocks.
        var sorted=new ArrayList<>(changes.values());sorted.sort(Comparator.comparing(Operation::kind)
            .thenComparingInt(o->o.kind()==Kind.CLEAR?o.pos().getX():o.pos().getY())
            .thenComparingInt(o->o.kind()==Kind.CLEAR?o.pos().getZ():o.pos().getX())
            .thenComparingInt(o->o.kind()==Kind.CLEAR?-o.pos().getY():o.pos().getZ()));
        return new SitePreparationPlan(sorted,observed,blockers);
    }
    static boolean water(BlockState state){return state.equals(Blocks.WATER.defaultBlockState());}
    private static boolean wall(BlockState state,BlockPos pos){return state!=null&&state.getFluidState().isEmpty()
        &&(state.is(BlockTags.DIRT)||state.is(BlockTags.BASE_STONE_OVERWORLD))
        &&net.minecraft.world.level.block.Block.isShapeFullBlock(state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,pos));}
    /** Derive the persisted containment boundary; never authorize unobserved surrounding water. */
    private static Set<BlockPos> waterBoundary(List<Operation> ops,Map<BlockPos,BlockState> states){
        var wet=new HashSet<BlockPos>();var changed=new HashSet<BlockPos>();
        for(var op:ops){changed.add(op.pos());if(water(op.original())){if(op.kind()!=Kind.FILL)throw new IllegalArgumentException("Water cannot be cleared");wet.add(op.pos());}}
        if(wet.size()>512)throw new IllegalArgumentException("Shallow-water preparation exceeds 512 blocks");
        var boundary=new HashSet<BlockPos>(wet);
        for(var p:wet){
            if(wet.contains(p.above())&&wet.contains(p.below()))throw new IllegalArgumentException("Water exceeds two blocks deep");
            for(var direction:Direction.values()){
                var n=p.relative(direction);boundary.add(n);if(wet.contains(n))continue;
                var state=states.get(n);
                if(changed.contains(n)||state==null||(direction==Direction.UP?!state.isAir():!wall(state,n)))
                    throw new IllegalArgumentException("Water needs intact natural walls, bottom and a clear surface");
            }
        }
        return boundary;
    }
    public void validateWater(ServerLevel level,int cursor){
        if(cursor<0||cursor>=operations.size()||!water(operations.get(cursor).original()))throw new IllegalArgumentException("Not a planned water fill");
        var boundary=waterBoundary(operations,observed);var expected=new HashMap<BlockPos,BlockState>();
        for(var p:boundary)expected.put(p,observed.get(p));
        for(int i=0;i<cursor;i++){var op=operations.get(i);if(expected.containsKey(op.pos()))expected.put(op.pos(),op.target());}
        for(var entry:expected.entrySet()){var p=entry.getKey();if(!level.hasChunkAt(p)||!level.getWorldBorder().isWithinBounds(p)||level.getBlockEntity(p)!=null||!level.getBlockState(p).equals(entry.getValue()))
            throw new IllegalStateException("Water containment changed; review the site");}
    }
    private static void put(Map<BlockPos,Operation> changes,Operation op,Set<String> blockers) {
        var old=changes.putIfAbsent(op.pos(),op);if(old!=null&&!old.equals(op))blockers.add("Preparation footprints conflict");
    }
    private static BlockState observe(ServerLevel level,BlockPos p,Map<BlockPos,BlockState> observed,Set<String> blockers) {
        if(!available(level,p)){blockers.add("Terrain is unloaded, claimed, or outside the world border");return null;}
        if(level.getBlockEntity(p)!=null){blockers.add("Terrain contains a protected container or block entity");return null;}
        var s=level.getBlockState(p);observed.putIfAbsent(p,s);return s;
    }
    private static boolean available(ServerLevel level,BlockPos p) {
        if(level.isOutsideBuildHeight(p)||!level.hasChunkAt(p)||!level.getWorldBorder().isWithinBounds(p))return false;
        var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(p));
        return claim==null||claim.getOwningColony()==0;
    }
    static boolean natural(BlockState s) {
        if(s.is(BlockTags.LEAVES))return !s.hasProperty(LeavesBlock.PERSISTENT)||!s.getValue(LeavesBlock.PERSISTENT);
        return s.is(BlockTags.LOGS)||s.is(BlockTags.DIRT)||s.is(BlockTags.BASE_STONE_OVERWORLD)
            ||s.is(BlockTags.FLOWERS)||s.is(BlockTags.SAPLINGS)||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)
            ||s.is(Blocks.GRAVEL)||s.is(Blocks.SHORT_GRASS)||s.is(Blocks.TALL_GRASS)||s.is(Blocks.FERN)
            ||s.is(Blocks.LARGE_FERN)||s.is(Blocks.VINE)||s.is(Blocks.SNOW);
    }
    private static SitePreparationPlan rejected(String reason){return new SitePreparationPlan(List.of(),Map.of(),List.of(reason));}
}
