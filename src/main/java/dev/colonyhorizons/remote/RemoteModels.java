// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.ldtteam.structurize.api.RotationMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Immutable approved intent. Neither these records nor legacy districts grant native privileges alone. */
public final class RemoteModels {
    private RemoteModels() {}
    public enum Mode { APPROVAL, AUTOMATIC }
    public enum Phase { PROPOSED, CLAIMING, STAGING, BUILDING, OPERATING, PAUSED, RECOVERY_NEEDED, CANCELLED }

    public static final class ItemKey {
        private final ItemStack item;
        public ItemKey(ItemStack stack) {
            if(stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty budget item");
            item=stack.copyWithCount(1);
        }
        public ItemStack stack() { return item.copy(); }
        @Override public boolean equals(Object other) { return other instanceof ItemKey key && ItemStack.isSameItemSameComponents(item,key.item); }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(item); }
    }
    public record Budget(Map<ItemKey,Integer> allowances, Map<ItemKey,Integer> homeReserves) {
        public Budget { allowances=quantities(allowances); homeReserves=quantities(homeReserves); }
        private static Map<ItemKey,Integer> quantities(Map<ItemKey,Integer> source) {
            if(source.size()>128) throw new IllegalArgumentException("Too many budget items");
            for(var e:source.entrySet()) if(e.getKey()==null || e.getValue()==null || e.getValue()<0 || e.getValue()>1_000_000)
                throw new IllegalArgumentException("Invalid budget quantity");
            return Map.copyOf(source);
        }
    }
    public record SiteRequest(int colonyId,String name,BlockPos center,BoundingBox allowedArea,BlockPos homeSupply,
                              List<Integer> citizenIds,int mayorId,int maxBuildingLevel,Budget budget,FoundingRoster.Policy foundingPolicy,Optional<SurveyOrigin> surveyOrigin) {
        public SiteRequest(int colonyId,String name,BlockPos center,BoundingBox allowedArea,BlockPos homeSupply,List<Integer> citizenIds,int mayorId,int maxBuildingLevel,Budget budget){this(colonyId,name,center,allowedArea,homeSupply,citizenIds,mayorId,maxBuildingLevel,budget,FoundingRoster.Policy.LEGACY_SELECTED);}
        public SiteRequest(int colonyId,String name,BlockPos center,BoundingBox allowedArea,BlockPos homeSupply,List<Integer> citizenIds,int mayorId,int maxBuildingLevel,Budget budget,FoundingRoster.Policy foundingPolicy){this(colonyId,name,center,allowedArea,homeSupply,citizenIds,mayorId,maxBuildingLevel,budget,foundingPolicy,Optional.empty());}
        public SiteRequest {
            if(colonyId<=0 || maxBuildingLevel<1 || maxBuildingLevel>5) throw new IllegalArgumentException("Invalid colony or development level");
            name=text(name,64);center=Objects.requireNonNull(center).immutable();homeSupply=Objects.requireNonNull(homeSupply).immutable();
            allowedArea=copy(allowedArea); citizenIds=List.copyOf(citizenIds); Objects.requireNonNull(budget);
            Objects.requireNonNull(foundingPolicy);Objects.requireNonNull(surveyOrigin);
            if(surveyOrigin.isPresent()!=(foundingPolicy==FoundingRoster.Policy.RESOURCE_FOUR_V2))throw new IllegalArgumentException("Survey founding requires four free settlers");
            if(foundingPolicy!=FoundingRoster.Policy.LEGACY_SELECTED){if(!citizenIds.isEmpty()||mayorId!=0)throw new IllegalArgumentException("Free founding intent cannot select existing citizens");}
            else if(citizenIds.isEmpty() || citizenIds.size()>32 || new HashSet<>(citizenIds).size()!=citizenIds.size()
                || citizenIds.stream().anyMatch(i->i<=0) || !citizenIds.contains(mayorId)) throw new IllegalArgumentException("Invalid founding party");
            if(!allowedArea.isInside(center)) throw new IllegalArgumentException("Site outside authorized area");
        }
        @Override public BoundingBox allowedArea(){return copy(allowedArea);}
    }
    public record BuildingPlan(UUID operationId,String role,BlockPos anchor,String pack,String blueprint,String hash,
                               RotationMirror transform,BoundingBox upgradeVolume,int targetLevel) {
        public BuildingPlan {
            Objects.requireNonNull(operationId);role=text(role,32);anchor=Objects.requireNonNull(anchor).immutable();
            pack=text(pack,128);blueprint=text(blueprint,256);hash=digest(hash);Objects.requireNonNull(transform);upgradeVolume=copy(upgradeVolume);
            if(blueprint.startsWith("/") || blueprint.contains("..") || blueprint.contains("\\") || blueprint.contains(":")) throw new IllegalArgumentException("Invalid blueprint path");
            if(targetLevel<1 || targetLevel>5 || !upgradeVolume.isInside(anchor)) throw new IllegalArgumentException("Invalid building plan");
        }
        @Override public BoundingBox upgradeVolume(){return copy(upgradeVolume);}
    }
    public record SiteDraft(UUID id,SiteRequest request,Set<ChunkPos> chunks,List<BuildingPlan> buildings,String fingerprint,List<String> blockers,Optional<PreparedSite> preparation) {
        public SiteDraft(UUID id,SiteRequest request,Set<ChunkPos> chunks,List<BuildingPlan> buildings,String fingerprint,List<String> blockers){this(id,request,chunks,buildings,fingerprint,blockers,Optional.empty());}
        public SiteDraft {
            Objects.requireNonNull(preparation);
            if(preparation.isPresent()&&!preparation.orElseThrow().terrain().within(request.allowedArea(),chunks))throw new IllegalArgumentException("Terrain preparation outside approved area");
            if(preparation.isPresent()){var p=preparation.orElseThrow();if(!FoundingRoster.free(request)||!request.allowedArea().isInside(p.camp())||!chunks.contains(new ChunkPos(p.camp())))throw new IllegalArgumentException("Invalid preparation authority");for(var target:java.util.stream.Stream.concat(p.arrivals().stream(),p.storage().stream()).toList())if(!request.allowedArea().isInside(target)||!chunks.contains(new ChunkPos(target)))throw new IllegalArgumentException("Arrival outside approved area");}
            Objects.requireNonNull(id);Objects.requireNonNull(request);chunks=Set.copyOf(chunks);buildings=List.copyOf(buildings);
            fingerprint=digest(fingerprint);blockers=messages(blockers);
            if(chunks.size()>49 || buildings.size()>32) throw new IllegalArgumentException("Remote footprint too large");
            var operations=new HashSet<UUID>();var anchors=new HashSet<BlockPos>();
            for(var b:buildings) {
                if(!operations.add(b.operationId()) || !anchors.add(b.anchor()) || b.targetLevel()>request.maxBuildingLevel()) throw new IllegalArgumentException("Duplicate or over-level building");
                if(!contains(request.allowedArea(),b.upgradeVolume())) throw new IllegalArgumentException("Building outside authorized area");
            }
            if(blockers.isEmpty() && (chunks.isEmpty() || buildings.isEmpty())) throw new IllegalArgumentException("Empty approved layout");
        }
    }
    public record Development(UUID id,UUID owner,SiteDraft draft,Mode mode,Phase phase,long revision) {
        public Development {
            Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(draft);Objects.requireNonNull(mode);Objects.requireNonNull(phase);
            if(!id.equals(draft.id()) || revision<0 || revision==Long.MAX_VALUE) throw new IllegalArgumentException("Invalid remote identity or revision");
        }
    }
    public record StepResult(String stage,List<String> blockers,boolean progressed) {
        public StepResult {stage=text(stage,256);blockers=messages(blockers);}
    }
    static BoundingBox copy(BoundingBox b) {Objects.requireNonNull(b);return new BoundingBox(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
    static boolean contains(BoundingBox a,BoundingBox b) {return a.isInside(new BlockPos(b.minX(),b.minY(),b.minZ())) && a.isInside(new BlockPos(b.maxX(),b.maxY(),b.maxZ()));}
    static String text(String s,int max) {if(s==null || s.isBlank() || s.length()>max || s.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid remote text");return s;}
    static String digest(String s) {if(s==null || !s.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid blueprint fingerprint");return s;}
    static List<String> messages(List<String> values) {if(values.size()>64) throw new IllegalArgumentException("Too many blockers");values.forEach(s->text(s,512));return List.copyOf(values);}
}
