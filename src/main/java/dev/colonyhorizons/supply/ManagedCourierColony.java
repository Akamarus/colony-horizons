package dev.colonyhorizons.supply;

/** Marker stored with the native colony, independently of the outpost index. */
public interface ManagedCourierColony {
    boolean colonyHorizons$managedCouriers();
    void colonyHorizons$markManagedCouriers();
}
