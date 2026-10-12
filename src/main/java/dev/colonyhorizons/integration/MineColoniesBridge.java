package dev.colonyhorizons.integration;

import com.minecolonies.api.IMinecoloniesAPI;
import com.mojang.logging.LogUtils;

/** Keep MineColonies-facing code here; do not duplicate its colony simulation. */
public final class MineColoniesBridge {
    private static volatile boolean ready;
    public static void verifyLoaded() {
        var api = IMinecoloniesAPI.getInstance();
        if (api == null || api.getColonyManager() == null || api.getBuildingRegistry() == null)
            throw new IllegalStateException("MineColonies API did not initialize");
        ready = true;
        LogUtils.getLogger().info("Colony Horizons connected to the MineColonies API");
    }
    public static boolean ready() { return ready; }
    private MineColoniesBridge() {}
}
