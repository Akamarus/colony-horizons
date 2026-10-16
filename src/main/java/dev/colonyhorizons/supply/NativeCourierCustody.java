package dev.colonyhorizons.supply;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.*;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import dev.colonyhorizons.supply.CourierDistrictPolicy.Decision;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Identity evidence only; native inventory/request persistence remains the cargo authority. */
final class NativeCourierCustody {
    private static final String KEY="colonyhorizons:courier_custody";
    private static final String HUT="colonyhorizons:courier_hut_identity";
    private NativeCourierCustody() {}
    static Decision validate(AbstractEntityCitizen citizen,JobDeliveryman job,ServerLevel level) {
        var persistent=citizen.getPersistentData();
        // A Pickup completes before its goods reach the warehouse. Keep custody while anything is held.
        if(job.getTaskQueue().isEmpty() && citizen.getInventoryCitizen().isEmpty()) {
            persistent.remove(KEY);return new Decision(true,"");
        }
        try {
            var context=new CompoundTag();
            var owner=job.getColony().getPermissions().getOwner();
            if(owner==null)throw new IllegalStateException("Colony owner unavailable");
            context.putUUID("owner",owner);
            context.put("work",identity(level,job.getCitizen().getWorkBuilding().getPosition()));
            context.put("warehouse",identity(level,job.findWareHouse().getPosition()));
            var proposed=new CompoundTag();
            proposed.put("context",context);proposed.put("requests",new CompoundTag());
            if(persistent.contains(KEY)) {
                if(!persistent.contains(KEY,Tag.TAG_COMPOUND))throw new IllegalStateException("Invalid courier custody record");
                proposed=persistent.getCompound(KEY).copy();
                if(!proposed.getCompound("context").equals(context) || !proposed.contains("requests",Tag.TAG_COMPOUND))
                    throw new IllegalStateException("Courier owner, workplace or warehouse changed while work is assigned");
            }
            var requests=proposed.getCompound("requests");
            for(var token:job.getTaskQueue()) {
                var request=job.getColony().getRequestManager().getRequestForToken(token);
                if(request==null)throw new IllegalStateException("Assigned request unavailable");
                var endpoints=new CompoundTag();
                if(request.getRequest() instanceof Delivery delivery) {
                    endpoints.put("source",identity(level,delivery.getStart().getInDimensionLocation()));
                    endpoints.put("target",identity(level,delivery.getTarget().getInDimensionLocation()));
                } else if(request.getRequest() instanceof Pickup) {
                    endpoints.put("source",identity(level,request.getRequester().getLocation().getInDimensionLocation()));
                    endpoints.put("target",context.getCompound("warehouse"));
                } else throw new IllegalStateException("Unknown courier request");
                String key=token.getIdentifier().toString();
                if(requests.contains(key) && !requests.getCompound(key).equals(endpoints))
                    throw new IllegalStateException("Courier request endpoint was replaced");
                requests.put(key,endpoints);
            }
            // Publish all identity evidence together, after every existing reference validates.
            persistent.put(KEY,proposed);
            return new Decision(true,"");
        } catch(IllegalStateException failure) {
            return new Decision(false,failure.getMessage());
        }
    }
    private static CompoundTag identity(ServerLevel level,BlockPos position) {
        if(!level.hasChunkAt(position))throw new IllegalStateException("Courier hut unloaded");
        var tile=level.getBlockEntity(position);
        if(tile==null)throw new IllegalStateException("Courier hut missing");
        var tag=tile.getPersistentData();
        if(!tag.hasUUID(HUT)) {
            if(tag.contains(HUT))throw new IllegalStateException("Invalid courier hut identity");
            tag.putUUID(HUT,UUID.randomUUID());tile.setChanged();
        }
        var identity=new CompoundTag();identity.putLong("position",position.asLong());identity.putUUID("identity",tag.getUUID(HUT));return identity;
    }
}
