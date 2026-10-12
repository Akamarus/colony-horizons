package dev.colonyhorizons.supply;

import java.nio.file.Files;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Transport-owned buffers and cargo share one save unit. Native inventories are not attached. */
public final class SupplySavedData extends SavedData {
    private static final String KEY="colonyhorizons_supply";
    private final String dimension;
    private SupplyNetwork network;
    private SupplySavedData(String dimension){this.dimension=dimension;network=new SupplyNetwork(this::setDirty);}
    public SupplyNetwork network(){return network;}
    public static SupplySavedData get(ServerLevel level){
        String dimension=level.dimension().location().toString();
        return level.getDataStorage().computeIfAbsent(new Factory<>(()->{
            var file=DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data/"+KEY+".dat");
            if(Files.exists(file))throw new IllegalStateException("Supply data could not be loaded; preserve for recovery: "+file);
            return new SupplySavedData(dimension);
        },(tag,registries)->load(tag,registries,dimension)),KEY);
    }
    private static SupplySavedData load(CompoundTag tag,HolderLookup.Provider registries,String dimension){
        if(!tag.contains("dimension",Tag.TAG_STRING) || !dimension.equals(tag.getString("dimension")) || !tag.contains("network",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid supply dimension/network");
        var data=new SupplySavedData(dimension);data.network=SupplyNetwork.load(tag.getCompound("network"),registries,data::setDirty);return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putString("dimension",dimension);tag.put("network",network.save(registries));return tag;}
}
