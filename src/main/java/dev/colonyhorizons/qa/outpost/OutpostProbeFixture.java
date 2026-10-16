package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.colony.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.UUID;
public record OutpostProbeFixture(IColony colony, UUID ownerId, BlockPos center) {
    public static OutpostProbeFixture create(ServerLevel level, BlockPos center, UUID ownerId) {
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
            level.setBlockAndUpdate(center.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(center.offset(x,0,z),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(center.offset(x,1,z),Blocks.AIR.defaultBlockState());
        }
        var owner=FakePlayerFactory.get(level,new GameProfile(ownerId,"OutpostProbe"));
        owner.setPos(center.getX()+0.5,center.getY(),center.getZ()+0.5);
        var colony=IColonyManager.getInstance().createColony(level,center,owner,"Outpost Probe","Medieval Oak");
        return new OutpostProbeFixture(colony,ownerId,center);
    }
    public ICitizenData createCitizen() {
        var citizen=colony.getCitizenManager().createAndRegisterCivilianData();
        return colony.getCitizenManager().spawnOrCreateCitizen(citizen,colony.getWorld(),center);
    }
}
