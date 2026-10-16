package dev.colonyhorizons.qa.outpost;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Disposable home, finite citizen party and surveyed flat ground; production performs founding. */
final class RemoteTestEnvironment {
    final ServerLevel level;
    final OutpostProbeFixture home;
    final ServerPlayer owner;
    final SiteDraft draft;
    RemoteTestEnvironment(ServerLevel level,int remoteX) {
        this(level,remoteX,new Budget(Map.of(),Map.of()));
    }
    RemoteTestEnvironment(ServerLevel level,int remoteX,Budget budget) {
        this(level,remoteX,budget,4);
    }
    RemoteTestEnvironment(ServerLevel level,int remoteX,Budget budget,int partySize) {
        this.level=level;var id=UUID.randomUUID();level.getServer().getProfileCache().add(new GameProfile(id,"RemoteOwner"));level.getServer().getProfileCache().save();home=OutpostProbeFixture.create(level,new BlockPos(0,-59,0),id);
        owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(id,"RemoteOwner"));
        owner.setPos(0.5,-59,0.5);var party=new ArrayList<Integer>();for(int i=0;i<partySize;i++){var c=home.createCitizen();c.setPaused(true);party.add(c.getId());}
        for(int x=remoteX-48;x<=remoteX+63;x++)for(int z=-48;z<=63;z++) {
            for(int y=-63;y<=-60;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState());
            for(int y=-59;y<=-40;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        draft=RemoteSitePlanner.review(owner,new SiteRequest(home.colony().getID(),"Remote Forestry",new BlockPos(remoteX,-59,0),
            new BoundingBox(remoteX-48,-64,-48,remoteX+63,150,63),home.center(),party,party.isEmpty()?0:party.getFirst(),5,budget,partySize==0?FoundingRoster.Policy.FREE_FOUR_V1:FoundingRoster.Policy.LEGACY_SELECTED));
        if(!draft.blockers().isEmpty())throw new IllegalStateException("Fixture site rejected: "+draft.blockers());
        RemoteDevelopmentData.get(level).create(new Development(draft.id(),id,draft,Mode.APPROVAL,Phase.PROPOSED,0));
    }
}
