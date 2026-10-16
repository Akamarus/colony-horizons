package dev.colonyhorizons.supply;

import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Minecraft logs some failed saves instead of throwing: verify persisted bytes before publishing custody. */
public final class NativeSupplyCheckpoint {
    private NativeSupplyCheckpoint(){}
    public static void flushAndVerify(ServerLevel level,Collection<? extends net.minecraft.world.level.block.entity.BlockEntity> huts){
        var expected=new LinkedHashMap<BlockPos,CompoundTag>();
        for(var hut:huts)expected.put(hut.getBlockPos(),hut.saveWithFullMetadata(level.registryAccess()));
        var chunks=level.getChunkSource();chunks.save(true);
        var saved=new HashMap<ChunkPos,CompoundTag>();
        for(var pos:expected.keySet()){
            var cp=new ChunkPos(pos);
            var chunk=saved.computeIfAbsent(cp,key->chunks.chunkMap.read(key).join().orElseThrow(()->new IllegalStateException("Supply endpoint chunk was not saved")));
            var entities=chunk.getList("block_entities",Tag.TAG_COMPOUND);CompoundTag found=null;
            for(int i=0;i<entities.size();i++){
                var entity=entities.getCompound(i);
                if(entity.getInt("x")==pos.getX()&&entity.getInt("y")==pos.getY()&&entity.getInt("z")==pos.getZ()){found=entity.copy();break;}
            }
            if(found!=null)found.remove("keepPacked");
            if(!expected.get(pos).equals(found))throw new IllegalStateException("Native hut save verification failed at "+pos.toShortString());
        }
    }
}
