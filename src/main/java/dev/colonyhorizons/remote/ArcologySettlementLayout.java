// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.ldtteam.structurize.api.RotationMirror;
import dev.colonyhorizons.integration.ArcologyCatalog;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Ground-level modules with open access lanes; every upgrade envelope is measured from the pack. */
public final class ArcologySettlementLayout {
    private ArcologySettlementLayout() {}
    public static List<BuildingPlan> plan(UUID development,SiteRequest request,ArcologyCatalog.Inspection catalog) {
        if(!FoundingRoster.free(request) && request.citizenIds().size()!=4 && request.citizenIds().size()!=5) throw new IllegalArgumentException("Forestry founding requires a Mayor, builder and two foresters");
        var profile=request.surveyOrigin().map(SurveyOrigin::profile);int expected=profile.filter(p->!p.hutRole().equals("lumberjack")).isPresent()?30:25;
        if(catalog.entries().size()!=expected || catalog.diagnostics().stream().anyMatch(s->!s.startsWith("Missing optional route terminal"))) throw new IllegalArgumentException("Arcology pack is incomplete or incompatible");
        boolean free=FoundingRoster.free(request);boolean warehouseFirst=free||request.citizenIds().size()==5;
        var roles=free?List.of("builder","warehouse","deliveryman",profile.map(ProductionProfile::hutRole).orElse("lumberjack"),"citizen","citizen","citizen","citizen"):warehouseFirst?List.of("builder","warehouse","deliveryman","lumberjack","lumberjack","citizen","citizen","citizen","citizen","citizen"):List.of("builder","lumberjack","lumberjack","citizen","citizen","citizen","citizen");
        var offsets=free?List.of(new BlockPos(-32,0,-24),new BlockPos(0,0,-24),new BlockPos(32,0,-24),new BlockPos(-24,0,0),new BlockPos(-32,0,32),new BlockPos(-16,0,32),new BlockPos(0,0,32),new BlockPos(16,0,32)):warehouseFirst?List.of(new BlockPos(-32,0,-24),new BlockPos(0,0,-24),new BlockPos(32,0,-24),new BlockPos(-24,0,0),new BlockPos(24,0,0),new BlockPos(-32,0,32),new BlockPos(-16,0,32),new BlockPos(0,0,32),new BlockPos(16,0,32),new BlockPos(32,0,32)):List.of(new BlockPos(-24,0,0),new BlockPos(-8,0,0),new BlockPos(8,0,0),new BlockPos(-24,0,24),new BlockPos(-8,0,24),new BlockPos(8,0,24),new BlockPos(24,0,24));
        var result=new ArrayList<BuildingPlan>();
        for(int i=0;i<roles.size();i++) {
            var path=profile.isPresent()&&i==3?profile.orElseThrow().blueprintPrefix():(Set.of("warehouse","deliveryman").contains(roles.get(i))?"craftsmanship/storage/":"fundamentals/")+roles.get(i);
            var base=catalog.entries().stream().filter(e->e.relativePath().equals(path+"1.blueprint")).findFirst().orElseThrow();
            var anchor=request.center().offset(offsets.get(i)).above(base.anchor().getY());BoundingBox envelope=null;var hashes=new StringBuilder();
            for(int level=1;level<=request.maxBuildingLevel();level++) {
                var target=path+level+".blueprint";
                var e=catalog.entries().stream().filter(b->b.relativePath().equals(target)).findFirst().orElseThrow(()->new IllegalArgumentException("Missing "+target));
                var origin=anchor.subtract(e.anchor());var b=new BoundingBox(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+e.size().getX()-1,origin.getY()+e.size().getY()-1,origin.getZ()+e.size().getZ()-1);
                if(envelope==null) envelope=b;else envelope.encapsulate(b);
                hashes.append(target).append(':').append(e.sha256()).append(';');
            }
            for(var old:result)if(old.upgradeVolume().intersects(envelope)) throw new IllegalArgumentException("Arcology upgrade envelopes overlap");
            var operation=UUID.nameUUIDFromBytes((development+":"+i).getBytes(StandardCharsets.UTF_8));
            result.add(new BuildingPlan(operation,roles.get(i).equals("citizen")?"home":roles.get(i).equals("deliveryman")?"courier":roles.get(i),anchor,catalog.packName(),path+"1.blueprint",sha(hashes.toString()),RotationMirror.NONE,envelope,1));
        }
        // The pinned native HomeBuildingModule provides one inhabitant per level, so four level-one homes.
        return List.copyOf(result);
    }
    static String sha(String text) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
