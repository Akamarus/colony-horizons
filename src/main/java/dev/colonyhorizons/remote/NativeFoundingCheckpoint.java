// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.util.BackUpHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

public final class NativeFoundingCheckpoint {
    private NativeFoundingCheckpoint(){}
    public static void save(ServerLevel level,IColony colony) {
        level.save(null,true,false);BackUpHelper.saveColonies(level.registryAccess());
        var expected=new CompoundTag();colony.write(expected,level.registryAccess());
        var dimension=level.dimension().location();var path=level.getServer().getWorldPath(LevelResource.ROOT).resolve("minecolonies").resolve(dimension.getNamespace()).resolve(dimension.getPath()).resolve("colony"+colony.getID()+".dat");
        try{if(!expected.equals(NbtIo.read(path)))throw new IllegalStateException("Native founding colony save verification failed");}
        catch(java.io.IOException e){throw new IllegalStateException("Native founding colony save failed",e);}
    }
}
