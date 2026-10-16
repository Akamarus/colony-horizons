// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.IColonyManager;
import dev.colonyhorizons.remote.RemoteModels.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.*;
import com.minecolonies.api.colony.ICitizenData;
import java.util.*;

/** Native-party preflight. No reservation, citizen creation or movement occurs during review. */
public final class FoundingTravel {
    private FoundingTravel() {}
    public enum State { PREPARED, IN_TRANSIT, ARRIVING, ARRIVED }
    public record Journey(List<Member> members,List<BlockPos> targets,State state,int remainingTicks,long lastTick) {
        public Journey {
            members=List.copyOf(members);targets=targets.stream().map(BlockPos::immutable).toList();Objects.requireNonNull(state);
            if((members.size()!=4 && members.size()!=5) || targets.size()!=members.size() || new HashSet<>(targets).size()!=members.size() || members.stream().map(Member::citizenId).distinct().count()!=members.size() || members.stream().map(Member::entityId).distinct().count()!=members.size() || remainingTicks<0 || remainingTicks>72000 || lastTick<0)throw new IllegalArgumentException("Invalid founding journey");
            if((state==State.ARRIVING || state==State.ARRIVED) && remainingTicks!=0)throw new IllegalArgumentException("Arrival before journey completion");
        }
    }
    private static final ThreadLocal<UUID> RELEASING=new ThreadLocal<>();
    public record Member(int citizenId,UUID entityId,BlockPos departure,Optional<BlockPos> previousHome) {
        public Member {Objects.requireNonNull(entityId);departure=departure.immutable();previousHome=previousHome.map(BlockPos::immutable);}
    }
    public static List<Member> review(ServerPlayer actor,UUID development) {
        var data=RemoteDevelopmentData.get(actor.serverLevel());var record=data.find(development).orElseThrow(()->new IllegalArgumentException("Unknown founding proposal"));
        var request=record.draft().request();var colony=IColonyManager.getInstance().getColonyByDimension(request.colonyId(),actor.level().dimension());
        if(!actor.getUUID().equals(record.owner()) || colony==null || !actor.getUUID().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only the current colony owner can allocate its founding party");
        if(record.phase()!=Phase.PROPOSED && record.phase()!=Phase.STAGING)throw new IllegalStateException("Founding party is no longer awaiting departure");
        if(request.citizenIds().size()!=4 && request.citizenIds().size()!=5)throw new IllegalArgumentException("Forestry founding needs a Mayor, builder and two foresters");
        var members=new ArrayList<Member>();var identities=new HashSet<UUID>();
        for(int id:request.citizenIds()) {
            var citizen=colony.getCitizenManager().getCivilian(id);
            if(citizen==null || citizen.getColony()!=colony || citizen.isChild())throw new IllegalArgumentException("Choose existing adult citizens from this colony");
            if(data.foundingManaged(development)&&!citizen.getUUID().equals(data.approvedParty(development).get(id)))throw new IllegalStateException("Original approved founder is missing; replacement requires review");
            if(citizen.getJob()!=null || citizen.getWorkBuilding()!=null || colony.getTravellingManager().getTravellingTargetFor(id).isPresent())throw new IllegalArgumentException("Founding citizen is already working or travelling");
            for(var other:data.all())if(!other.id().equals(development) && other.phase()!=Phase.PROPOSED && other.phase()!=Phase.CANCELLED && other.draft().request().colonyId()==colony.getID() && FoundingRoster.citizenIds(data,other.id()).contains(id))throw new IllegalArgumentException("Founding citizen is allocated to another settlement");
            var entity=citizen.getEntity().orElseThrow(()->new IllegalStateException("Founding citizen is not currently available"));
            if(!entity.isAlive() || entity.isRemoved() || entity.level()!=actor.level() || !actor.serverLevel().hasChunkAt(entity.blockPosition()) || !entity.getUUID().equals(citizen.getUUID()) || !identities.add(citizen.getUUID()))throw new IllegalStateException("Founding citizen identity is unavailable or unsafe");
            members.add(new Member(id,citizen.getUUID(),entity.blockPosition(),Optional.ofNullable(citizen.getHomeBuilding()).map(b->b.getPosition())));
        }
        return List.copyOf(members);
    }
    public static void begin(ServerLevel level,UUID development,List<Integer> party) {
        var data=RemoteDevelopmentData.get(level);var record=data.find(development).orElseThrow();
        if(!FoundingRoster.citizenIds(data,record.id()).equals(party))throw new IllegalArgumentException("Founding party changed");
        if(data.journey(development).isPresent())return;
        if(record.phase()!=Phase.STAGING || !RemoteTickets.snapshot(level).containsKey(development))throw new IllegalStateException("Founding site is not active");
        FoundingCargo.load(data.foundingCargo(development).orElseThrow(()->new IllegalStateException("Founding supplies not secured")),level.registryAccess());
        var owner=level.getServer().getPlayerList().getPlayer(record.owner());if(owner==null)throw new IllegalStateException("Owner must be online");
        var members=review(owner,development);var center=record.draft().request().center();var targets=new ArrayList<BlockPos>();
        for(int i=0;i<members.size();i++)targets.add(center.offset(i*2-2,0,12));
        for(var target:targets)if(!safe(level,record.draft().request().colonyId(),target))throw new IllegalStateException("Founding arrival requires dry supported space");
        int duration=(int)Math.min(72000,Math.max(200,Math.ceil(Math.sqrt(record.draft().request().homeSupply().distSqr(center))/8)*20));
        data.putJourney(development,new Journey(members,targets,State.PREPARED,duration,level.getGameTime()));data.checkpoint(level);
        reconcile(level,development);
    }
    public static RemoteModels.StepResult beginGranted(ServerLevel level,UUID development) {
        var data=RemoteDevelopmentData.get(level);var r=data.find(development).orElseThrow();
        if(!FoundingRoster.free(r.draft().request())||r.phase()!=Phase.STAGING||!RemoteTickets.snapshot(level).containsKey(development))throw new IllegalStateException("Free founders are not ready for arrival");
        var grant=data.grant(development).orElseThrow();if(grant.state()!=FoundingGrantJournal.State.REGISTERED||grant.receipts().size()!=4||!grant.receipts().equals(FoundingRoster.resolve(data,development)))throw new IllegalStateException("Founding grant is incomplete");
        FoundingCargo.load(data.foundingCargo(development).orElseThrow(),level.registryAccess());
        if(data.journey(development).isEmpty()){
            var members=new ArrayList<Member>();var targets=new ArrayList<BlockPos>();
            for(int i=0;i<4;i++){var m=grant.receipts().get(i);var target=PreparedFounding.arrival(r.draft(),i);if(!safe(level,r.draft().request().colonyId(),target))throw new IllegalStateException("Founding arrival needs dry supported space");members.add(new Member(m.citizenId(),m.entityId(),r.draft().request().homeSupply(),Optional.empty()));targets.add(target);}
            data.putJourney(development,new Journey(members,targets,State.PREPARED,200,level.getGameTime()));data.checkpoint(level);
        }
        reconcile(level,development);return new RemoteModels.StepResult("Founding settlers arriving",List.of(),true);
    }
    public static void reconcile(ServerLevel level,UUID development) {
        var data=RemoteDevelopmentData.get(level);var journey=data.journey(development).orElse(null);if(journey==null || journey.state()==State.ARRIVED)return;
        var record=data.find(development).orElseThrow();
        if(record.phase()!=Phase.STAGING || !RemoteTickets.snapshot(level).containsKey(development))return;
        var colony=IColonyManager.getInstance().getColonyByDimension(record.draft().request().colonyId(),level.dimension());
        if(colony==null || !record.owner().equals(colony.getPermissions().getOwner()))throw new IllegalStateException("Founding colony ownership changed");
        for(var member:journey.members()){var c=colony.getCitizenManager().getCivilian(member.citizenId());if(c==null || !member.entityId().equals(c.getUUID()))throw new IllegalStateException("Original founding citizen missing; recovery required");}
        if(journey.state()==State.PREPARED) {
            for(int i=0;i<journey.members().size();i++){var c=colony.getCitizenManager().getCivilian(journey.members().get(i).citizenId());colony.getTravellingManager().startTravellingTo(c,journey.targets().get(i),Integer.MAX_VALUE);c.getEntity().ifPresent(net.minecraft.world.entity.Entity::discard);}
            NativeFoundingCheckpoint.save(level,colony);
            data.putJourney(development,new Journey(journey.members(),journey.targets(),State.IN_TRANSIT,journey.remainingTicks(),level.getGameTime()));data.checkpoint(level);return;
        }
        if(journey.remainingTicks()>0) {
            if(level.getGameTime()-journey.lastTick()<20)return;
            journey=new Journey(journey.members(),journey.targets(),State.IN_TRANSIT,Math.max(0,journey.remainingTicks()-20),level.getGameTime());data.putJourney(development,journey);
            if(journey.remainingTicks()>0)return;
        }
        for(var target:journey.targets())if(!com.minecolonies.api.util.WorldUtil.isEntityBlockLoaded(level,target) || !safe(level,colony.getID(),target))return;
        if(journey.state()!=State.ARRIVING){journey=new Journey(journey.members(),journey.targets(),State.ARRIVING,0,level.getGameTime());data.putJourney(development,journey);data.checkpoint(level);}
        for(int i=0;i<journey.members().size();i++) {
            var member=journey.members().get(i);var c=colony.getCitizenManager().getCivilian(member.citizenId());var target=journey.targets().get(i);
            var live=c.getEntity().filter(e->e.isAlive()&&!e.isRemoved());
            if(live.isPresent()){if(!live.orElseThrow().blockPosition().equals(target))throw new IllegalStateException("Founding citizen appeared outside its arrival; recovery required");continue;}
            RELEASING.set(member.entityId());
            try{colony.getTravellingManager().startTravellingTo(c,target,0);c.updateEntityIfNecessary();}
            finally{RELEASING.remove();}
            var arrived=c.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).orElseThrow(()->new IllegalStateException("Native founding arrival did not create original entity"));
            arrived.setPos(target.getX()+0.5,target.getY(),target.getZ()+0.5);
            if(!arrived.getUUID().equals(member.entityId()) || !level.noCollision(arrived))throw new IllegalStateException("Native founding arrival requires recovery");
            if(FoundingRoster.free(record.draft().request()))com.minecolonies.api.IMinecoloniesAPI.getInstance().getEventBus().post(new com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent(c,com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent.CitizenAddedSource.INITIAL));
        }
        NativeFoundingCheckpoint.save(level,colony);
        data.putJourney(development,new Journey(journey.members(),journey.targets(),State.ARRIVED,0,level.getGameTime()));data.checkpoint(level);
    }
    static void returnGranted(ServerLevel level,ICitizenData citizen,BlockPos target){
        if(!safe(level,citizen.getColony().getID(),target))throw new IllegalStateException("Unsafe return");
        RELEASING.set(citizen.getUUID());
        try{
            var live=citizen.getEntity().filter(e->e.isAlive()&&!e.isRemoved());
            if(live.isPresent())live.orElseThrow().teleportTo(target.getX()+0.5,target.getY(),target.getZ()+0.5);
            else{citizen.getColony().getTravellingManager().startTravellingTo(citizen,target,0);citizen.updateEntityIfNecessary();}
            var entity=citizen.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).orElseThrow(()->new IllegalStateException("Original settler return not available"));
            entity.setPos(target.getX()+0.5,target.getY(),target.getZ()+0.5);entity.getNavigation().stop();citizen.getColony().getTravellingManager().finishTravellingFor(citizen.getId());
        }finally{RELEASING.remove();}
    }
    public static boolean blocksSpawn(ICitizenData citizen) {
        return citizen.getColony().getWorld() instanceof ServerLevel level && blocksEntity(level,citizen.getUUID());
    }
    public static boolean blocksEntity(ServerLevel level,UUID entity) {
        if(entity.equals(RELEASING.get()))return false;
        var existing=RemoteDevelopmentData.existing(level);if(existing.isEmpty())return false;var data=existing.orElseThrow();
        for(var r:data.all())if(data.grant(r.id()).isPresent()){
            var grant=data.grant(r.id()).orElseThrow();
            if(!grant.returnTargets().isEmpty()&&grant.receipts().stream().limit(grant.returned()).anyMatch(m->m.entityId().equals(entity)))continue;
            if(data.journey(r.id()).filter(j->j.state()==State.ARRIVED).isEmpty()&&grant.receipts().stream().anyMatch(m->m.entityId().equals(entity)))return true;
            // An unreceipted native record has no proven identity. Keep the grant consumed
            // and recovery-required, but never quarantine unrelated native immigration.
        }
        for(var r:data.all()){var j=data.journey(r.id()).orElse(null);if(data.grant(r.id()).filter(g->g.receipts().stream().limit(g.returned()).anyMatch(m->m.entityId().equals(entity))).isPresent())continue;if(j!=null)for(int i=0;i<j.members().size();i++)if(j.members().get(i).entityId().equals(entity)&&(j.state()!=State.ARRIVED||!com.minecolonies.api.util.WorldUtil.isEntityBlockLoaded(level,j.targets().get(i))))return true;}
        return false;
    }
    static boolean safe(ServerLevel level,int colony,BlockPos pos) {
        for(var p:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,2,1)))if(!level.hasChunkAt(p)||!level.getFluidState(p).isEmpty())return false;
        var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new net.minecraft.world.level.ChunkPos(pos));
        var box=new net.minecraft.world.phys.AABB(pos.getX()+0.15,pos.getY(),pos.getZ()+0.15,pos.getX()+0.85,pos.getY()+2,pos.getZ()+0.85);
        return claim!=null && claim.getOwningColony()==colony && level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP) && level.noCollision(box);
    }
    static CompoundTag save(Journey journey) {
        var t=new CompoundTag();t.putString("state",journey.state().name());t.putInt("remaining",journey.remainingTicks());t.putLong("tick",journey.lastTick());var list=new ListTag();
        for(int i=0;i<journey.members().size();i++){var m=journey.members().get(i);var v=new CompoundTag();v.putInt("citizen",m.citizenId());v.putUUID("entity",m.entityId());v.putLong("departure",m.departure().asLong());m.previousHome().ifPresent(p->v.putLong("home",p.asLong()));v.putLong("target",journey.targets().get(i).asLong());list.add(v);}t.put("members",list);return t;
    }
    static Journey load(CompoundTag t) {
        if(!t.contains("state",Tag.TAG_STRING)||!t.contains("remaining",Tag.TAG_INT)||!t.contains("tick",Tag.TAG_LONG)||!t.contains("members",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid travel journal");
        var list=t.getList("members",Tag.TAG_COMPOUND);if(list.size()!=4 && list.size()!=5)throw new IllegalArgumentException("Invalid travel party");var members=new ArrayList<Member>();var targets=new ArrayList<BlockPos>();
        for(var item:list){var v=(CompoundTag)item;if(!v.contains("citizen",Tag.TAG_INT)||v.getInt("citizen")<=0||!v.hasUUID("entity")||!v.contains("departure",Tag.TAG_LONG)||!v.contains("target",Tag.TAG_LONG)||v.contains("home")&&!v.contains("home",Tag.TAG_LONG))throw new IllegalArgumentException("Invalid travel member");members.add(new Member(v.getInt("citizen"),v.getUUID("entity"),BlockPos.of(v.getLong("departure")),v.contains("home")?Optional.of(BlockPos.of(v.getLong("home"))):Optional.empty()));targets.add(BlockPos.of(v.getLong("target")));}
        return new Journey(members,targets,State.valueOf(t.getString("state")),t.getInt("remaining"),t.getLong("tick"));
    }
}
