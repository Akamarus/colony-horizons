// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Bounded candidate enumeration. The caller reviews every returned candidate with RemoteSitePlanner. */
public final class RemoteSurvey {
    private RemoteSurvey() {}
    public static Optional<BlockPos> next(ServerLevel level,UUID development) {
        var data=RemoteDevelopmentData.get(level);var draft=data.find(development).orElseThrow(()->new IllegalArgumentException("Missing survey proposal")).draft();
        var area=draft.request().allowedArea();
        // Only full chunks inside the authorized rectangle, with three chunks of footprint margin on every side.
        int minX=Math.floorDiv(area.minX()+15,16)+3,minZ=Math.floorDiv(area.minZ()+15,16)+3;
        int maxX=Math.floorDiv(area.maxX()-15,16)-3,maxZ=Math.floorDiv(area.maxZ()-15,16)-3;
        long width=Math.max(0L,(long)maxX-minX+1),height=Math.max(0L,(long)maxZ-minZ+1);
        int count=(int)Math.min(256,width*height);var candidate=data.advanceSurvey(development,level.getGameTime(),count);
        if(candidate.isEmpty())return Optional.empty();
        int x=minX+(int)(candidate.getAsInt()%width),z=minZ+(int)(candidate.getAsInt()/width);
        for(int cx=x-3;cx<=x+3;cx++)for(int cz=z-3;cz<=z+3;cz++)if(!level.hasChunk(cx,cz))return Optional.empty();
        return Optional.of(new BlockPos(x*16+8,draft.request().center().getY(),z*16+8));
    }
    public static String status(ServerLevel level,UUID development) {
        var data=RemoteDevelopmentData.get(level);var draft=data.find(development).orElseThrow().draft();var area=draft.request().allowedArea();
        long width=Math.max(0L,(long)Math.floorDiv(area.maxX()-15,16)-Math.floorDiv(area.minX()+15,16)-5);
        long height=Math.max(0L,(long)Math.floorDiv(area.maxZ()-15,16)-Math.floorDiv(area.minZ()+15,16)-5);
        return data.survey(development).index()>=Math.min(256,width*height)?"No suitable site found":"Surveying explored terrain";
    }
}
