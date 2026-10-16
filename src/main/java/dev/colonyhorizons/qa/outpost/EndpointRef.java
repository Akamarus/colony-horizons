package dev.colonyhorizons.qa.outpost;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
public record EndpointRef(UUID endpointId, ResourceKey<Level> dimension, BlockPos position, UUID ownerId) {
    public EndpointRef { position=position.immutable(); }
}
