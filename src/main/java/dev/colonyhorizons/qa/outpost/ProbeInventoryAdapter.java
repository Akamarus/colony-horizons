package dev.colonyhorizons.qa.outpost;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import java.util.*;
/** QA-owned endpoint registry; not an authorization adapter for arbitrary colony warehouses. */
public final class ProbeInventoryAdapter {
    private static final String ID="colonyhorizons:probe_endpoint", OWNER="colonyhorizons:probe_owner";
    public static void register(ServerLevel level,EndpointRef endpoint) {
        if(!level.dimension().equals(endpoint.dimension()) || !level.hasChunkAt(endpoint.position()))throw new IllegalArgumentException("Wrong dimension/unloaded endpoint");
        var entity=level.getBlockEntity(endpoint.position());
        if(entity==null)throw new IllegalArgumentException("No endpoint block entity");
        entity.getPersistentData().putUUID(ID,endpoint.endpointId());entity.getPersistentData().putUUID(OWNER,endpoint.ownerId());entity.setChanged();
    }
    public static Optional<IItemHandler> resolve(ServerLevel level,EndpointRef endpoint,UUID actor){
        if(!actor.equals(endpoint.ownerId()) || !level.dimension().equals(endpoint.dimension()) || !level.hasChunkAt(endpoint.position()))return Optional.empty();
        var entity=level.getBlockEntity(endpoint.position());if(entity==null)return Optional.empty();
        var data=entity.getPersistentData();
        if(!data.hasUUID(ID) || !data.hasUUID(OWNER) || !data.getUUID(ID).equals(endpoint.endpointId()) || !data.getUUID(OWNER).equals(actor))return Optional.empty();
        return Optional.ofNullable(level.getCapability(Capabilities.ItemHandler.BLOCK,endpoint.position(),null));
    }
}
