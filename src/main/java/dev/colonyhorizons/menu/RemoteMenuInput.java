package dev.colonyhorizons.menu;

import net.minecraft.core.BlockPos;

/** Validate typed coordinates before BlockPos packing can truncate their bits. */
public final class RemoteMenuInput {
    private RemoteMenuInput() {}

    public static BlockPos center(String x, String y, String z) {
        int px = Integer.parseInt(x), py = Integer.parseInt(y), pz = Integer.parseInt(z);
        if (px < -29999984 || px > 29999984 || pz < -29999984 || pz > 29999984
                || py < -2048 || py > 2047) {
            throw new IllegalArgumentException("Coordinates are outside Minecraft's supported range.");
        }
        return new BlockPos(px, py, pz);
    }
}
