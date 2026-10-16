// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.ModConfigSpec;

public record RemoteLimits(int developing,int active,int chunks,int endpointChunks) {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    private static final ModConfigSpec.IntValue DEVELOPING=BUILDER.defineInRange("remote.developingPerOwner",1,0,1);
    private static final ModConfigSpec.IntValue ACTIVE=BUILDER.defineInRange("remote.activePerOwner",2,0,2);
    private static final ModConfigSpec.IntValue CHUNKS=BUILDER.defineInRange("remote.chunksPerOutpost",49,0,49);
    private static final ModConfigSpec.IntValue ENDPOINTS=BUILDER.defineInRange("remote.homeEndpointChunksPerOwner",16,0,16);
    static final ModConfigSpec SPEC=BUILDER.build();
    public RemoteLimits {
        if(developing<0 || developing>1 || active<0 || active>2 || chunks<0 || chunks>49 || endpointChunks<0 || endpointChunks>16)
            throw new IllegalArgumentException("Remote limits exceed supported bounds");
    }
    public static RemoteLimits forServer(MinecraftServer server) {
        java.util.Objects.requireNonNull(server);
        return new RemoteLimits(DEVELOPING.get(),ACTIVE.get(),CHUNKS.get(),ENDPOINTS.get());
    }
    public static RemoteLimits defaults(){return new RemoteLimits(1,2,49,16);}
}
