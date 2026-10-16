package dev.colonyhorizons.supply;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Shared decision for warehouse assignment and every leg of native courier work. */
public final class CourierDistrictPolicy {
    private CourierDistrictPolicy() {}
    public enum Status { UNMANAGED, ACTIVE, BLOCKED }
    public record District(ResourceKey<Level> dimension, int colonyId, Optional<UUID> outpostId) {}
    public record Endpoint(Status status, Optional<District> district, String reason) {}
    public record Decision(boolean allowed, String reason) {}
    public static Decision check(Endpoint workplace, Endpoint warehouse, Endpoint source, Endpoint target) {
        var endpoints = new Endpoint[]{workplace, warehouse, source, target};
        boolean unmanaged = true;
        for (var endpoint : endpoints) {
            if (endpoint == null || endpoint.status() == null)
                return new Decision(false, "Unresolved courier endpoint");
            if (endpoint.status() == Status.BLOCKED)
                return new Decision(false, endpoint.reason() == null || endpoint.reason().isBlank()
                    ? "Courier endpoint blocked" : endpoint.reason());
            unmanaged &= endpoint.status() == Status.UNMANAGED;
        }
        if (unmanaged) return new Decision(true, "");
        for (var endpoint : endpoints) {
            if (endpoint.status() != Status.ACTIVE || endpoint.district() == null || endpoint.district().isEmpty())
                return new Decision(false, "Missing courier district identity");
            var district = endpoint.district().get();
            if (district.dimension() == null || district.colonyId() <= 0 || district.outpostId() == null)
                return new Decision(false, "Invalid courier district identity");
            if (!endpoint.district().equals(workplace.district()))
                return new Decision(false, "Courier work crosses a district boundary");
        }
        return new Decision(true, "");
    }
}
