// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.ldtteam.structurize.api.RotationMirror;
import dev.colonyhorizons.remote.RemoteModels.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import java.util.function.Supplier;

/** Separate authority file: an old local district is never implicitly a remote development. */
public final class RemoteDevelopmentData extends SavedData {
    private static final String KEY="colonyhorizons_remote_development";
    private final ResourceKey<Level> dimension;
    private final Map<UUID,Development> records=new LinkedHashMap<>();
    private final Map<UUID,PreparationProgress> preparationProgress=new HashMap<>();
    public Optional<PreparationProgress> preparationProgress(UUID id){return Optional.ofNullable(preparationProgress.get(id));}
    void putPreparationProgress(UUID id,PreparationProgress next){
        var r=find(id).orElseThrow();var plan=r.draft().preparation().orElseThrow().terrain();var old=preparationProgress.get(id);
        if(!next.work().site().equals(id)||!next.work().fingerprint().equals(plan.fingerprint())||next.work().total()!=plan.operations().size())throw new IllegalArgumentException("Preparation journal identity changed");
        if(old!=null&&(old.inventoryPending()||next.inventoryPending())&&(old.stage()!=next.stage()||!old.work().equals(next.work())))throw new IllegalArgumentException("Camp custody must finish before further work");
        if(old==null){if(next.inventoryPending()||next.stage()!=PreparationProgress.Stage.PLACING||next.work().cursor()!=0||next.work().pending())throw new IllegalArgumentException("Invalid preparation start");}
        else if(next.stage()!=old.stage()){if(next.stage().ordinal()!=old.stage().ordinal()+1||!next.work().equals(old.work()))throw new IllegalArgumentException("Invalid camp transition");}
        else if(!next.work().equals(old.work())){var expected=old.work().pending()?old.work().complete(old.work().cursor()):old.work().begin(old.work().cursor());if(!expected.equals(next.work()))throw new IllegalArgumentException("Invalid terrain receipt");}
        preparationProgress.put(id,next);setDirty();
    }
    private final Map<UUID,Map<UUID,ExpansionProposal>> expansions=new HashMap<>();
    public Map<UUID,ExpansionProposal> expansions(UUID id){return Map.copyOf(expansions.getOrDefault(id,Map.of()));}
    public List<BuildingPlan> buildings(UUID id){var plans=new ArrayList<>(find(id).orElseThrow().draft().buildings());expansions(id).values().stream().filter(ExpansionProposal::approved).map(ExpansionProposal::plan).forEach(plans::add);return List.copyOf(plans);}
    void putExpansion(UUID id,ExpansionProposal next){var r=find(id).orElseThrow();validateExpansion(r,next);var entries=expansions.computeIfAbsent(id,k->new LinkedHashMap<>());var old=entries.get(next.plan().operationId());if(old==null){if(next.state()!=ExpansionProposal.State.PROPOSED||entries.size()>=3)throw new IllegalArgumentException("Expansion review limit reached");}else if(old.state()==ExpansionProposal.State.REJECTED&&next.state()==ExpansionProposal.State.PROPOSED){if(next.revision()!=ExpansionProposal.renewedRevision(old))throw new IllegalArgumentException("Stale replacement review");}else if(!old.plan().equals(next.plan())||!old.cost().equals(next.cost())||!old.next(next.state()).equals(next))throw new IllegalArgumentException("Expansion review changed");entries.put(next.plan().operationId(),next);setDirty();}
    private void validateExpansion(Development r,ExpansionProposal proposal){var p=proposal.plan();if(!foundingManaged(r.id())||starterDelivery(r.id()).filter(FoundingSupplies.Delivery::complete).isEmpty()||!RemoteModels.contains(r.draft().request().allowedArea(),p.upgradeVolume()))throw new IllegalArgumentException("Expansion has no founding authority");for(var old:r.draft().buildings())if(old.operationId().equals(p.operationId())||old.upgradeVolume().intersects(p.upgradeVolume()))throw new IllegalArgumentException("Expansion overlaps original building");for(var old:expansions(r.id()).values())if(!old.plan().operationId().equals(p.operationId())&&old.plan().upgradeVolume().intersects(p.upgradeVolume()))throw new IllegalArgumentException("Expansion overlaps another review");var v=p.upgradeVolume();for(int x=v.minX()>>4;x<=v.maxX()>>4;x++)for(int z=v.minZ()>>4;z<=v.maxZ()>>4;z++)if(!r.draft().chunks().contains(new ChunkPos(x,z)))throw new IllegalArgumentException("Expansion exceeds approved claims");}
    private final Map<UUID,Map<UUID,ConstructionJournal.Entry>> construction=new HashMap<>();
    public Map<UUID,ConstructionJournal.Entry> construction(UUID id){return Map.copyOf(construction.getOrDefault(id,Map.of()));}
    void putConstruction(UUID id,ConstructionJournal.Entry entry){
        validateConstruction(find(id).orElseThrow(),entry);var entries=construction.computeIfAbsent(id,k->new LinkedHashMap<>());var old=entries.get(entry.operation());
        if(old==null&&entry.state()!=ConstructionJournal.State.INTENT||old!=null&&(entry.state().ordinal()<old.state().ordinal()||old.nativeOrder()!=0&&old.nativeOrder()!=entry.nativeOrder()))throw new IllegalStateException("Invalid construction journal transition");
        entries.put(entry.operation(),entry);setDirty();
    }
    private void validateConstruction(Development r,ConstructionJournal.Entry entry){if(!foundingManaged(r.id())||entry.target()>r.draft().request().maxBuildingLevel()||buildings(r.id()).stream().noneMatch(b->b.operationId().equals(entry.building()))||starterDelivery(r.id()).filter(FoundingSupplies.Delivery::complete).isEmpty())throw new IllegalArgumentException("Unapproved construction operation");}
    private final Map<UUID,Map<ItemKey,Integer>> bootstrapSpent=new HashMap<>();
    private final Set<UUID> bootstrapPending=new HashSet<>();
    public Map<ItemKey,Integer> bootstrapSpent(UUID id){return Map.copyOf(bootstrapSpent.getOrDefault(id,Map.of()));}
    public boolean bootstrapPending(UUID id){return bootstrapPending.contains(id);}
    void beginBootstrapHandoff(UUID id,ItemKey item,int amount){
        if(bootstrapPending(id)||amount<=0||!FoundingConstructionSupplies.warehouseFirst(find(id).orElseThrow()))throw new IllegalStateException("Invalid founding construction custody");
        var next=new HashMap<>(bootstrapSpent(id));next.merge(item,amount,Math::addExact);bootstrapSpent.put(id,Map.copyOf(next));bootstrapPending.add(id);setDirty();
    }
    void finishBootstrapHandoff(UUID id){if(!bootstrapPending.remove(id))throw new IllegalStateException("No pending founding handoff");setDirty();}
    private final Map<UUID,SurveyCursor> surveys=new HashMap<>();
    private final Map<UUID,Set<ChunkPos>> claimed=new HashMap<>();
    private final Map<UUID,CompoundTag> foundingCargo=new HashMap<>();
    private final Map<UUID,FoundingTravel.Journey> journeys=new HashMap<>();
    private final Map<UUID,Map<UUID,FoundingPlacement.Receipt>> anchors=new HashMap<>();
    private final Set<UUID> managedFounding=new HashSet<>();
    private final Map<UUID,Map<Integer,UUID>> approvedParties=new HashMap<>();
    private final Map<UUID,List<FoundingRoster.Member>> founderRosters=new HashMap<>();
    private final Map<UUID,FoundingGrantJournal> grants=new HashMap<>();
    public Optional<FoundingGrantJournal> grant(UUID id){return Optional.ofNullable(grants.get(id));}
    void putGrant(UUID id,FoundingGrantJournal next){
        if(!FoundingRoster.free(find(id).orElseThrow().draft().request()))throw new IllegalArgumentException("Legacy grant forbidden");
        if(next.productionRole()!=FoundingRoster.productionRole(find(id).orElseThrow().draft().request()))throw new IllegalArgumentException("Grant policy changed");
        var old=grants.get(id);
        if(old!=null&&(next.returned()<old.returned()||!old.returnTargets().isEmpty()&&!old.returnTargets().equals(next.returnTargets())))throw new IllegalArgumentException("Return journal cannot reset");
        if(old==null&&next.state()!=FoundingGrantJournal.State.RESERVED||old!=null&&(!next.originalCitizens().equals(old.originalCitizens())||old.consumed()&&!next.consumed()||next.receipts().size()<old.receipts().size()||!next.receipts().subList(0,old.receipts().size()).equals(old.receipts())||old.state()==FoundingGrantJournal.State.RECOVERY_REQUIRED&&next.state()!=old.state()))throw new IllegalArgumentException("Grant receipt cannot be changed or reset");
        grants.put(id,next);setDirty();
    }
    void enableFreeFounding(UUID id){if(find(id).orElseThrow().phase()!=Phase.PROPOSED||grant(id).isEmpty())throw new IllegalArgumentException("Grant not reserved");managedFounding.add(id);setDirty();}

    public List<FoundingRoster.Member> founderRoster(UUID id){return founderRosters.getOrDefault(id,List.of());}
    void putFounderRoster(UUID id,List<FoundingRoster.Member> members){
        if(!FoundingRoster.free(find(id).orElseThrow().draft().request()))throw new IllegalArgumentException("Legacy founding cannot grant citizens");
        var roster=FoundingRoster.validate(members);FoundingRoster.validatePolicy(find(id).orElseThrow().draft().request(),roster);var old=founderRosters.get(id);
        if(old!=null&&!old.equals(roster))throw new IllegalArgumentException("Founder identities cannot change");
        founderRosters.put(id,roster);var party=new HashMap<Integer,UUID>();roster.forEach(m->party.put(m.citizenId(),m.entityId()));approvedParties.put(id,Map.copyOf(party));setDirty();
    }

    private final Map<UUID,FoundingSupplies.Delivery> starterDeliveries=new HashMap<>();
    private final Set<UUID> mayorInitialized=new HashSet<>();
    public boolean mayorInitialized(UUID id){return mayorInitialized.contains(id);}
    private final Map<UUID,dev.colonyhorizons.mayor.MayorAppointment> mayors=new HashMap<>();
    public Optional<dev.colonyhorizons.mayor.MayorAppointment> mayor(UUID id){return Optional.ofNullable(mayors.get(id));}
    public void putMayor(UUID id,dev.colonyhorizons.mayor.MayorAppointment a){validateMayor(find(id).orElseThrow(),a);var old=mayors.get(id);if(old!=null&&!old.equals(a))throw new IllegalArgumentException("Mayor already appointed");mayors.put(id,a);mayorInitialized.add(id);setDirty();}
    public void removeMayor(UUID id){mayors.remove(id);mayorInitialized.add(id);setDirty();}
    private void validateMayor(Development r,dev.colonyhorizons.mayor.MayorAppointment a){if(!foundingManaged(r.id())||a.citizenId()!=FoundingRoster.mayorId(this,r.id())||!a.entityId().equals(approvedParty(r.id()).get(a.citizenId()))||r.draft().buildings().stream().noneMatch(b->b.role().equals("builder")&&b.anchor().equals(a.station()))||starterDelivery(r.id()).filter(FoundingSupplies.Delivery::complete).isEmpty())throw new IllegalArgumentException("Invalid approved Mayor appointment");}
    public Optional<FoundingSupplies.Delivery> starterDelivery(UUID id){return Optional.ofNullable(starterDeliveries.get(id));}
    void putStarterDelivery(UUID id,FoundingSupplies.Delivery delivery){
        if(find(id).orElseThrow().phase()!=Phase.STAGING||journey(id).orElseThrow().state()!=FoundingTravel.State.ARRIVED)throw new IllegalStateException("No arrived founders for starter delivery");
        if(!delivery.profile().equals(find(id).orElseThrow().draft().request().surveyOrigin().map(SurveyOrigin::profile)))throw new IllegalArgumentException("Starter profile changed");
        var old=starterDeliveries.get(id);if(old==null&&delivery.complete()||old!=null&&(old.complete()||!delivery.complete()||!old.spent().equals(delivery.spent())))throw new IllegalStateException("Invalid starter delivery transition");
        starterDeliveries.put(id,delivery);setDirty();
    }
    public Map<Integer,UUID> approvedParty(UUID id){return Map.copyOf(approvedParties.getOrDefault(id,Map.of()));}
    private final Map<UUID,Integer> refunds=new HashMap<>();
    public boolean foundingRefunded(UUID id){return refunds.getOrDefault(id,0)==2;}
    boolean refundPending(UUID id){return refunds.getOrDefault(id,0)==1;}
    void refundStage(UUID id,int stage) {
        if(find(id).orElseThrow().phase()!=Phase.STAGING || !foundingCargo.containsKey(id) || !anchorReceipts(id).isEmpty() || journeys.containsKey(id) || stage!=refunds.getOrDefault(id,0)+1 || stage>2)throw new IllegalStateException("Invalid founding refund transition");
        refunds.put(id,stage);setDirty();
    }
    public boolean foundingManaged(UUID id){return managedFounding.contains(id);}
    void enableFounding(UUID id,List<FoundingTravel.Member> members){
        var r=find(id).orElseThrow();if(r.phase()!=Phase.PROPOSED)throw new IllegalStateException("Founding requires proposal approval");
        var party=new HashMap<Integer,UUID>();for(var m:members)if(party.put(m.citizenId(),m.entityId())!=null)throw new IllegalArgumentException("Duplicate approved citizen");
        validateParty(r,party);if(approvedParties.containsKey(id)&&!approvedParty(id).equals(party))throw new IllegalArgumentException("Approved party identity changed");
        approvedParties.put(id,Map.copyOf(party));managedFounding.add(id);setDirty();
    }
    private void validateParty(Development r,Map<Integer,UUID> party){
        if(FoundingRoster.free(r.draft().request())){var expected=new HashMap<Integer,UUID>();founderRoster(r.id()).forEach(m->expected.put(m.citizenId(),m.entityId()));if(expected.size()!=4||!expected.equals(party))throw new IllegalArgumentException("Invalid free founding identity");}
        else if(!party.keySet().equals(new HashSet<>(r.draft().request().citizenIds()))||new HashSet<>(party.values()).size()!=party.size())throw new IllegalArgumentException("Invalid approved original party");
    }
    public Map<UUID,FoundingPlacement.Receipt> anchorReceipts(UUID id){return Map.copyOf(anchors.getOrDefault(id,Map.of()));}
    void putAnchorReceipt(UUID id,FoundingPlacement.Receipt receipt) {
        var r=find(id).orElseThrow();
        if(r.phase()!=Phase.STAGING || r.draft().buildings().stream().noneMatch(b->b.operationId().equals(receipt.operation())))throw new IllegalArgumentException("Unapproved anchor receipt");
        var values=anchors.computeIfAbsent(id,k->new HashMap<>());var old=values.get(receipt.operation());
        if(old==null && receipt.complete() || old!=null && (old.complete() || !receipt.complete() || !old.spent().equals(receipt.spent())))throw new IllegalStateException("Invalid anchor receipt transition");
        values.put(receipt.operation(),receipt);setDirty();
    }
    public record SurveyCursor(int index,long nextTick) {
        public SurveyCursor {if(index<0 || index>256 || nextTick<0)throw new IllegalArgumentException("Invalid survey cursor");}
    }
    private Supplier<RemoteLimits> limits;
    public RemoteDevelopmentData(ResourceKey<Level> dimension){this(dimension,RemoteLimits::defaults);}
    public RemoteDevelopmentData(ResourceKey<Level> dimension,Supplier<RemoteLimits> limits){this.dimension=Objects.requireNonNull(dimension);this.limits=Objects.requireNonNull(limits);}
    public static Optional<RemoteDevelopmentData> existing(ServerLevel level) {
        var data=level.getDataStorage().get(new Factory<>(()->{throw new IllegalStateException("Not a creation path");},(tag,registries)->load(tag,registries,level.dimension())),KEY);
        if(data==null) {
            var file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");
            if(java.nio.file.Files.exists(file))throw new IllegalStateException("Remote authority is unreadable; preserve for recovery");
            return Optional.empty();
        }
        data.limits=()->RemoteLimits.forServer(level.getServer());return Optional.of(data);
    }
    public static RemoteDevelopmentData get(ServerLevel level) {
        var data=level.getDataStorage().computeIfAbsent(new Factory<>(()->{
            var file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");
            // Vanilla catches deserialization exceptions. Refuse to overwrite an unreadable authority file.
            if(java.nio.file.Files.exists(file)) throw new IllegalStateException("Remote authority could not be read; preserve for recovery: "+file);
            return new RemoteDevelopmentData(level.dimension());
        },(tag,registries)->load(tag,registries,level.dimension())),KEY);
        data.limits=()->RemoteLimits.forServer(level.getServer());return data;
    }
    public Optional<Development> find(UUID id){return Optional.ofNullable(records.get(id));}
    public List<Development> all(){return List.copyOf(records.values());}
    public Optional<FoundingTravel.Journey> journey(UUID id){return Optional.ofNullable(journeys.get(id));}
    void putJourney(UUID id,FoundingTravel.Journey journey) {
        var record=find(id).orElseThrow();validateJourney(record,journey);
        var old=journeys.get(id);
        if(old==null && journey.state()!=FoundingTravel.State.PREPARED)throw new IllegalStateException("Journey requires prepared intent");
        if(old!=null && (!old.members().equals(journey.members()) || !old.targets().equals(journey.targets()) || journey.state().ordinal()<old.state().ordinal() || journey.state().ordinal()>old.state().ordinal()+1 || journey.remainingTicks()>old.remainingTicks()))throw new IllegalStateException("Invalid journey transition");
        journeys.put(id,journey);setDirty();
    }
    private void validateJourney(Development r,FoundingTravel.Journey j) {
        if(r.phase()==Phase.PROPOSED || r.phase()==Phase.CLAIMING || !FoundingRoster.citizenIds(this,r.id()).equals(j.members().stream().map(FoundingTravel.Member::citizenId).toList()))throw new IllegalArgumentException("Travel party differs from approved development");
        for(var target:j.targets())if(!r.draft().request().allowedArea().isInside(target)||!r.draft().chunks().contains(new ChunkPos(target)))throw new IllegalArgumentException("Travel outside approved footprint");
    }
    public Optional<CompoundTag> foundingCargo(UUID id){return Optional.ofNullable(foundingCargo.get(id)).map(CompoundTag::copy);}
    void storeFoundingCargo(ServerLevel level,UUID id,CompoundTag tag) {
        var record=find(id).orElseThrow();
        if(record.phase()!=Phase.STAGING)throw new IllegalStateException("Founding cargo is not awaiting withdrawal");
        validateFoundingCargo(id,tag,level.registryAccess());var prior=foundingCargo.get(id);
        if(prior==null && !tag.contains("pending"))throw new IllegalStateException("Founding withdrawal must first record pending custody");
        if(prior!=null && (!prior.contains("pending") || !prior.getCompound("pending").equals(tag)))throw new IllegalStateException("Founding cargo cannot be overwritten or replayed");
        foundingCargo.put(id,tag.copy());
        try{checkpoint(level);}catch(RuntimeException failure){if(prior==null)foundingCargo.remove(id);else foundingCargo.put(id,prior);setDirty();throw failure;}
    }
    private static void validateFoundingCargo(UUID id,CompoundTag tag,HolderLookup.Provider registries) {
        var before=tag.copy();before.remove("pending");var cargo=FoundingCargo.load(before,registries);
        if(!cargo.operation().equals(FoundingCargo.operationFor(id)))throw new IllegalArgumentException("Founding custody belongs to a different operation");
        if(tag.contains("pending")) {
            require(tag,"pending",Tag.TAG_COMPOUND);var after=FoundingCargo.load(tag.getCompound("pending"),registries);
            if(!cargo.items().isEmpty() || after.items().isEmpty() || !cargo.operation().equals(after.operation()) || !cargo.source().equals(after.source()))throw new IllegalArgumentException("Invalid founding handoff journal");
        }
    }
    public boolean hasClaimJournal(UUID id){return claimed.containsKey(id);}
    public Set<ChunkPos> confirmedClaims(UUID id){return Set.copyOf(claimed.getOrDefault(id,Set.of()));}
    public void beginClaims(UUID id) {
        if(find(id).orElseThrow().phase()!=Phase.CLAIMING || claimed.containsKey(id))throw new IllegalArgumentException("Invalid claim journal start");
        claimed.put(id,new HashSet<>());setDirty();
    }
    public void confirmClaim(UUID id,ChunkPos chunk) {
        if(!claimed.containsKey(id)||!find(id).orElseThrow().draft().chunks().contains(chunk))throw new IllegalArgumentException("Claim outside journal");
        claimed.get(id).add(chunk);setDirty();
    }
    public void checkpoint(ServerLevel level) {
        if(level.getDataStorage().get(new Factory<>(()->{throw new IllegalStateException("Missing remote data");},(t,p)->load(t,p,level.dimension())),KEY)!=this)throw new IllegalStateException("Detached remote state cannot checkpoint");
        var expected=save(new CompoundTag(),level.registryAccess());setDirty();level.getDataStorage().save();
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        var file=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)).resolve("data/"+KEY+".dat");
        try {if(!NbtIo.readCompressed(file,NbtAccounter.create(32L*1024*1024)).getCompound("data").equals(expected))throw new IllegalStateException("Remote journal save verification failed");}
        catch(java.io.IOException e){throw new IllegalStateException("Remote journal could not be saved",e);}
    }
    public SurveyCursor survey(UUID id){if(!records.containsKey(id))throw new IllegalArgumentException("Missing survey proposal");return surveys.getOrDefault(id,new SurveyCursor(0,0));}
    public OptionalInt advanceSurvey(UUID id,long now,int candidates) {
        if(candidates<0 || candidates>256 || now<0 || now>Long.MAX_VALUE-20)throw new IllegalArgumentException("Invalid survey bounds");
        var r=find(id).orElseThrow(()->new IllegalArgumentException("Missing survey proposal"));var cursor=survey(id);
        if(r.phase()!=Phase.PROPOSED || cursor.index()>=candidates || now<cursor.nextTick())return OptionalInt.empty();
        surveys.put(id,new SurveyCursor(cursor.index()+1,now+20));setDirty();return OptionalInt.of(cursor.index());
    }
    public void create(Development development) {
        if(development.phase()!=Phase.PROPOSED || development.revision()!=0) throw new IllegalArgumentException("New development must be an unapproved proposal");
        if(records.containsKey(development.id())) throw new IllegalArgumentException("Duplicate development");
        if(records.size()>=4096) throw new IllegalArgumentException("Remote proposal storage full");
        records.put(development.id(),development);setDirty();
    }
    /** Remove only a failed menu preflight that never acquired native authority or custody. */
    public void discardUnapproved(UUID id) {
        var r=find(id).orElseThrow();
        if(r.phase()!=Phase.PROPOSED||r.revision()!=0||foundingManaged(id)||hasClaimJournal(id)||foundingCargo(id).isPresent()||!anchorReceipts(id).isEmpty()||journey(id).isPresent()||!construction(id).isEmpty())throw new IllegalArgumentException("Preserve a founding operation with authority or native effects");
        records.remove(id);surveys.remove(id);setDirty();
    }
    void decideExpansion(UUID id,long expectedRevision,ExpansionProposal next) {
        // Validate the phase before granting executable expansion authority. Neither map changes on refusal.
        if(next.state()!=ExpansionProposal.State.APPROVED&&next.state()!=ExpansionProposal.State.REJECTED)throw new IllegalArgumentException("Not an expansion decision");
        var phase=next.state()==ExpansionProposal.State.APPROVED?checkedTransition(id,expectedRevision,Phase.BUILDING):null;
        putExpansion(id,next);
        if(phase!=null)records.put(id,phase);
        setDirty();
    }
    public Development transition(UUID id,long expectedRevision,Phase phase) {
        var next=checkedTransition(id,expectedRevision,phase);records.put(id,next);setDirty();return next;
    }
    private Development checkedTransition(UUID id,long expectedRevision,Phase phase) {
        var prior=records.get(id);
        if(prior==null || prior.revision()!=expectedRevision) throw new IllegalArgumentException("Stale or missing development");
        Objects.requireNonNull(phase);
        if(prior.phase()==Phase.CANCELLED || phase==Phase.PROPOSED || prior.phase()==phase) throw new IllegalArgumentException("Invalid phase transition");
        if(prior.phase()==Phase.PROPOSED && phase!=Phase.CLAIMING && phase!=Phase.CANCELLED) throw new IllegalArgumentException("Proposal must be approved before activation");
        var next=new Development(id,prior.owner(),prior.draft(),prior.mode(),phase,Math.addExact(prior.revision(),1));
        if(phase!=Phase.CANCELLED && phase!=Phase.PAUSED && phase!=Phase.RECOVERY_NEEDED) validateActivation(next);
        return next;
    }
    private void validateActivation(Development next) {
        var cap=limits.get();
        if(!next.draft().blockers().isEmpty()) throw new IllegalArgumentException("Unresolved site blockers");
        if(next.draft().chunks().size()>cap.chunks()) throw new IllegalArgumentException("Remote footprint exceeds server limit");
        int active=1, developing=developing(next.phase())?1:0;
        for(var other:records.values()) {
            if(other.id().equals(next.id()) || !approved(other.phase())) continue;
            if(!Collections.disjoint(other.draft().chunks(),next.draft().chunks())) throw new IllegalArgumentException("Remote footprints overlap");
            if(other.draft().request().colonyId()==next.draft().request().colonyId() && sameCitizen(other.id(),next.id())) throw new IllegalArgumentException("Citizen already allocated remotely");
            if(other.owner().equals(next.owner())) {active++;if(developing(other.phase())) developing++;}
        }
        if(active>cap.active() || developing>cap.developing()) throw new IllegalArgumentException("Owner remote settlement limit reached");
    }
    private boolean sameCitizen(UUID first,UUID second){
        var a=FoundingRoster.resolve(this,first);var b=FoundingRoster.resolve(this,second);
        if(!a.isEmpty()&&!b.isEmpty())return a.stream().anyMatch(x->b.stream().anyMatch(y->x.entityId().equals(y.entityId())));
        // An unapproved legacy request has no durable UUIDs yet; retain its original reservation check.
        return !Collections.disjoint(FoundingRoster.citizenIds(this,first),FoundingRoster.citizenIds(this,second));
    }
    private static boolean approved(Phase phase){return phase!=Phase.PROPOSED && phase!=Phase.CANCELLED;}
    private static boolean developing(Phase phase){return phase==Phase.CLAIMING || phase==Phase.STAGING || phase==Phase.BUILDING || phase==Phase.RECOVERY_NEEDED || phase==Phase.PAUSED;}

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        tag.putInt("schema",1);tag.putString("dimension",dimension.location().toString());var list=new ListTag();
        for(var r:records.values()) {
            var t=new CompoundTag();t.putUUID("id",r.id());t.putUUID("owner",r.owner());t.putString("mode",r.mode().name());t.putString("phase",r.phase().name());t.putLong("revision",r.revision());
            t.putBoolean("managedFounding",foundingManaged(r.id()));preparationProgress(r.id()).ifPresent(p->t.put("preparationProgress",p.save()));
            r.draft().request().surveyOrigin().ifPresent(origin->t.put("surveyOrigin",origin.save()));
            if(FoundingRoster.free(r.draft().request()))t.putString("foundingPolicy",r.draft().request().foundingPolicy().name());
            if(founderRosters.containsKey(r.id()))t.put("founderRoster",FoundingRoster.save(founderRoster(r.id())));
            if(grants.containsKey(r.id()))t.put("founderGrant",grants.get(r.id()).save());
            var party=new ListTag();approvedParty(r.id()).forEach((citizen,entity)->{var member=new CompoundTag();member.putInt("citizen",citizen);member.putUUID("entity",entity);party.add(member);});t.put("approvedParty",party);
            t.putInt("refund",refunds.getOrDefault(r.id(),0));
            if(starterDeliveries.containsKey(r.id())){var delivery=starterDeliveries.get(r.id());var d=new CompoundTag();d.putBoolean("complete",delivery.complete());delivery.profile().ifPresent(profile->d.put("profile",profile.save()));d.put("spent",budget(delivery.spent(),registries));t.put("starterDelivery",d);}
            t.putBoolean("mayorInitialized",mayorInitialized(r.id()));
            if(mayors.containsKey(r.id())){var a=mayors.get(r.id());var m=new CompoundTag();m.putInt("citizen",a.citizenId());m.putUUID("entity",a.entityId());m.putLong("station",a.station().asLong());t.put("mayorAppointment",m);}
            t.put("bootstrapSpent",budget(bootstrapSpent(r.id()),registries));t.putBoolean("bootstrapPending",bootstrapPending(r.id()));
            var additions=new ListTag();for(var addition:expansions(r.id()).values())additions.add(addition.save(registries));t.put("expansions",additions);
            var builds=new ListTag();for(var entry:construction(r.id()).values()){var b=new CompoundTag();b.putUUID("building",entry.building());b.putInt("target",entry.target());b.putString("state",entry.state().name());b.putInt("order",entry.nativeOrder());builds.add(b);}t.put("construction",builds);
            var cursor=survey(r.id());t.putInt("surveyIndex",cursor.index());t.putLong("surveyNextTick",cursor.nextTick());
            t.putBoolean("claimsStarted",claimed.containsKey(r.id()));t.putLongArray("confirmedClaims",confirmedClaims(r.id()).stream().mapToLong(ChunkPos::toLong).sorted().toArray());
            if(foundingCargo.containsKey(r.id()))t.put("foundingCargo",foundingCargo.get(r.id()).copy());
            if(journeys.containsKey(r.id()))t.put("journey",FoundingTravel.save(journeys.get(r.id())));
            var receipts=new ListTag();for(var receipt:anchorReceipts(r.id()).values()){var a=new CompoundTag();a.putUUID("operation",receipt.operation());a.putBoolean("complete",receipt.complete());a.put("spent",budget(receipt.spent(),registries));receipts.add(a);}t.put("anchors",receipts);
            r.draft().preparation().ifPresent(p->t.put("preparation",p.save()));var d=r.draft();var q=d.request();t.putInt("colony",q.colonyId());t.putString("name",q.name());t.putLong("center",q.center().asLong());t.putLong("homeSupply",q.homeSupply().asLong());t.putIntArray("area",box(q.allowedArea()));t.putIntArray("citizens",q.citizenIds());t.putInt("mayor",q.mayorId());t.putInt("maxLevel",q.maxBuildingLevel());
            t.put("allowances",budget(q.budget().allowances(),registries));t.put("reserves",budget(q.budget().homeReserves(),registries));
            t.putLongArray("chunks",d.chunks().stream().mapToLong(ChunkPos::toLong).sorted().toArray());t.putString("fingerprint",d.fingerprint());
            var blockers=new ListTag();d.blockers().forEach(b->blockers.add(StringTag.valueOf(b)));t.put("blockers",blockers);
            var buildings=new ListTag();for(var b:d.buildings()) {
                var bt=new CompoundTag();bt.putUUID("operation",b.operationId());bt.putString("role",b.role());bt.putLong("anchor",b.anchor().asLong());bt.putString("pack",b.pack());bt.putString("blueprint",b.blueprint());bt.putString("hash",b.hash());bt.putString("transform",b.transform().name());bt.putIntArray("volume",box(b.upgradeVolume()));bt.putInt("level",b.targetLevel());buildings.add(bt);
            }t.put("buildings",buildings);list.add(t);
        }tag.put("developments",list);return tag;
    }
    public static RemoteDevelopmentData load(CompoundTag tag,HolderLookup.Provider registries,ResourceKey<Level> dimension) {
        require(tag,"schema",Tag.TAG_INT);require(tag,"dimension",Tag.TAG_STRING);
        if(tag.getInt("schema")!=1 || !tag.getString("dimension").equals(dimension.location().toString())) throw new IllegalArgumentException("Unsupported remote schema or dimension");
        var list=list(tag,"developments",Tag.TAG_COMPOUND,4096);var data=new RemoteDevelopmentData(dimension);
        boolean recovered=false;
        for(var element:list) {
            var t=(CompoundTag)element;var id=uuid(t,"id");var owner=uuid(t,"owner");
            strings(t,"mode","phase","name","fingerprint");ints(t,"colony","mayor","maxLevel","surveyIndex");longs(t,"revision","center","homeSupply","surveyNextTick");
            require(t,"area",Tag.TAG_INT_ARRAY);require(t,"citizens",Tag.TAG_INT_ARRAY);require(t,"chunks",Tag.TAG_LONG_ARRAY);
            var q=new SiteRequest(t.getInt("colony"),t.getString("name"),BlockPos.of(t.getLong("center")),readBox(t.getIntArray("area")),BlockPos.of(t.getLong("homeSupply")),Arrays.stream(t.getIntArray("citizens")).boxed().toList(),t.getInt("mayor"),t.getInt("maxLevel"),new Budget(readBudget(t,"allowances",registries),readBudget(t,"reserves",registries)),readPolicy(t),readSurveyOrigin(t));
            var chunks=new HashSet<ChunkPos>();for(long c:t.getLongArray("chunks")) if(!chunks.add(new ChunkPos(c))) throw new IllegalArgumentException("Duplicate remote chunk");
            var blockers=new ArrayList<String>();for(var b:list(t,"blockers",Tag.TAG_STRING,64)) blockers.add(b.getAsString());
            var buildings=new ArrayList<BuildingPlan>();for(var entry:list(t,"buildings",Tag.TAG_COMPOUND,32)) {
                var b=(CompoundTag)entry;strings(b,"role","pack","blueprint","hash","transform");longs(b,"anchor");ints(b,"level");require(b,"volume",Tag.TAG_INT_ARRAY);
                buildings.add(new BuildingPlan(uuid(b,"operation"),b.getString("role"),BlockPos.of(b.getLong("anchor")),b.getString("pack"),b.getString("blueprint"),b.getString("hash"),RotationMirror.valueOf(b.getString("transform")),readBox(b.getIntArray("volume")),b.getInt("level")));
            }
            var r=new Development(id,owner,new SiteDraft(id,q,chunks,buildings,t.getString("fingerprint"),blockers,t.contains("preparation")?Optional.of(PreparedSite.load(t.getCompound("preparation"))):Optional.empty()),Mode.valueOf(t.getString("mode")),Phase.valueOf(t.getString("phase")),t.getLong("revision"));
            if(data.records.putIfAbsent(id,r)!=null) throw new IllegalArgumentException("Duplicate remote development");
            if(t.contains("preparationProgress")){var p=PreparationProgress.load(t.getCompound("preparationProgress"));var plan=r.draft().preparation().orElseThrow().terrain();if(!p.work().site().equals(id)||!p.work().fingerprint().equals(plan.fingerprint())||p.work().total()!=plan.operations().size())throw new IllegalArgumentException("Invalid saved preparation authority");data.preparationProgress.put(id,p);if(p.uncertain()&&r.phase()!=Phase.CANCELLED&&r.phase()!=Phase.RECOVERY_NEEDED){data.records.put(id,new Development(id,owner,r.draft(),r.mode(),Phase.RECOVERY_NEEDED,r.revision()+1));recovered=true;}}
            if(t.contains("founderGrant")){require(t,"founderGrant",Tag.TAG_COMPOUND);if(!FoundingRoster.free(q))throw new IllegalArgumentException("Legacy grant journal");var grant=FoundingGrantJournal.load(t.getCompound("founderGrant"));if(grant.productionRole()!=FoundingRoster.productionRole(q))throw new IllegalArgumentException("Saved grant policy changed");data.grants.put(id,grant);}
            if(t.contains("founderRoster")){if(!FoundingRoster.free(q))throw new IllegalArgumentException("Legacy grant roster");var roster=FoundingRoster.load(t);FoundingRoster.validatePolicy(q,roster);data.founderRosters.put(id,roster);}
            if(t.contains("managedFounding")){require(t,"managedFounding",Tag.TAG_BYTE);if(t.getBoolean("managedFounding"))data.managedFounding.add(id);}
            if(t.contains("approvedParty")){var party=new HashMap<Integer,UUID>();for(var value:list(t,"approvedParty",Tag.TAG_COMPOUND,32)){var member=(CompoundTag)value;require(member,"citizen",Tag.TAG_INT);if(party.put(member.getInt("citizen"),uuid(member,"entity"))!=null)throw new IllegalArgumentException("Duplicate approved citizen");}if(!party.isEmpty()){data.validateParty(r,party);data.approvedParties.put(id,Map.copyOf(party));}}
            if(data.foundingManaged(id)&&data.approvedParty(id).isEmpty()&&(!FoundingRoster.free(q)||data.grant(id).isEmpty()))throw new IllegalArgumentException("Managed founding lacks original party identity");
            if(data.grant(id).isPresent()){
                var grant=data.grant(id).orElseThrow();
                if(!data.founderRoster(id).isEmpty()&&!data.founderRoster(id).equals(grant.receipts()))throw new IllegalArgumentException("Grant and roster disagree");
                if(grant.intent().isPresent()||grant.state()==FoundingGrantJournal.State.RECOVERY_REQUIRED){data.grants.put(id,grant.recovery());if(r.phase()!=Phase.CANCELLED){data.records.put(id,new Development(r.id(),r.owner(),r.draft(),r.mode(),Phase.RECOVERY_NEEDED,Math.addExact(r.revision(),1)));recovered=true;}}
            }
            data.surveys.put(id,new SurveyCursor(t.getInt("surveyIndex"),t.getLong("surveyNextTick")));
            require(t,"claimsStarted",Tag.TAG_BYTE);require(t,"confirmedClaims",Tag.TAG_LONG_ARRAY);var confirmed=new HashSet<ChunkPos>();
            for(long value:t.getLongArray("confirmedClaims"))if(!confirmed.add(new ChunkPos(value)))throw new IllegalArgumentException("Duplicate confirmed claim");
            if(!chunks.containsAll(confirmed) || (!t.getBoolean("claimsStarted") && !confirmed.isEmpty()) || (t.getBoolean("claimsStarted") && r.phase()==Phase.PROPOSED))throw new IllegalArgumentException("Invalid claim journal");
            if(t.getBoolean("claimsStarted"))data.claimed.put(id,confirmed);
            if(t.contains("foundingCargo")) {
                require(t,"foundingCargo",Tag.TAG_COMPOUND);var cargo=t.getCompound("foundingCargo");validateFoundingCargo(id,cargo,registries);
                if(!data.claimed.containsKey(id) || r.phase()==Phase.PROPOSED || r.phase()==Phase.CLAIMING)throw new IllegalArgumentException("Founding cargo without approved staging");
                data.foundingCargo.put(id,cargo.copy());
            }
            if(t.contains("journey")){require(t,"journey",Tag.TAG_COMPOUND);var journey=FoundingTravel.load(t.getCompound("journey"));data.validateJourney(r,journey);if(!data.foundingCargo.containsKey(id))throw new IllegalArgumentException("Travel without secured cargo");FoundingCargo.load(data.foundingCargo.get(id),registries);data.journeys.put(id,journey);}
            if(t.contains("anchors"))for(var element2:list(t,"anchors",Tag.TAG_COMPOUND,32)) {
                var a=(CompoundTag)element2;require(a,"complete",Tag.TAG_BYTE);var receipt=new FoundingPlacement.Receipt(uuid(a,"operation"),readBudget(a,"spent",registries),a.getBoolean("complete"));
                if(buildings.stream().noneMatch(b->b.operationId().equals(receipt.operation())) || !data.foundingCargo.containsKey(id))throw new IllegalArgumentException("Unapproved paid anchor");
                if(data.anchors.computeIfAbsent(id,k->new HashMap<>()).putIfAbsent(receipt.operation(),receipt)!=null)throw new IllegalArgumentException("Duplicate anchor receipt");
            }
            if(t.contains("starterDelivery")){require(t,"starterDelivery",Tag.TAG_COMPOUND);var d=t.getCompound("starterDelivery");require(d,"complete",Tag.TAG_BYTE);if(data.journey(id).isEmpty()||data.journey(id).orElseThrow().state()!=FoundingTravel.State.ARRIVED)throw new IllegalArgumentException("Starter delivery without arrival");var profile=d.contains("profile")?Optional.of(ProductionProfile.load(d.getCompound("profile"))):Optional.<ProductionProfile>empty();if(!profile.equals(q.surveyOrigin().map(SurveyOrigin::profile)))throw new IllegalArgumentException("Saved starter profile changed");data.starterDeliveries.put(id,new FoundingSupplies.Delivery(readBudget(d,"spent",registries),d.getBoolean("complete"),profile));}
            if(t.contains("mayorAppointment")){require(t,"mayorAppointment",Tag.TAG_COMPOUND);var m=t.getCompound("mayorAppointment");ints(m,"citizen");longs(m,"station");var a=new dev.colonyhorizons.mayor.MayorAppointment(m.getInt("citizen"),uuid(m,"entity"),BlockPos.of(m.getLong("station")));data.validateMayor(r,a);data.mayors.put(id,a);data.mayorInitialized.add(id);}
            if(t.contains("mayorInitialized")){require(t,"mayorInitialized",Tag.TAG_BYTE);if(t.getBoolean("mayorInitialized"))data.mayorInitialized.add(id);}
            if(t.contains("expansions"))for(var value:list(t,"expansions",Tag.TAG_COMPOUND,3)){var addition=ExpansionProposal.load((CompoundTag)value,registries);data.validateExpansion(r,addition);if(data.expansions.computeIfAbsent(id,k->new LinkedHashMap<>()).putIfAbsent(addition.plan().operationId(),addition)!=null)throw new IllegalArgumentException("Duplicate expansion");if(addition.state()==ExpansionProposal.State.PLACING){data.records.put(id,new Development(r.id(),r.owner(),r.draft(),r.mode(),Phase.RECOVERY_NEEDED,Math.addExact(r.revision(),1)));recovered=true;}}
            if(t.contains("construction"))for(var element2:list(t,"construction",Tag.TAG_COMPOUND,160)){
                var b=(CompoundTag)element2;ints(b,"target","order");strings(b,"state");var entry=new ConstructionJournal.Entry(uuid(b,"building"),b.getInt("target"),ConstructionJournal.State.valueOf(b.getString("state")),b.getInt("order"));data.validateConstruction(r,entry);
                if(data.construction.computeIfAbsent(id,k->new LinkedHashMap<>()).putIfAbsent(entry.operation(),entry)!=null)throw new IllegalArgumentException("Duplicate construction operation");
                if(entry.state()==ConstructionJournal.State.INTENT||entry.state()==ConstructionJournal.State.RECOVERY){data.records.put(id,new Development(r.id(),r.owner(),r.draft(),r.mode(),Phase.RECOVERY_NEEDED,Math.addExact(r.revision(),1)));recovered=true;}
            }
            if(!data.anchorReceipts(id).isEmpty()||data.starterDelivery(id).isPresent()) {
                var available=new HashMap<>(FoundingCargo.load(data.foundingCargo.get(id),registries).items());
                for(var a:data.anchorReceipts(id).values())for(var e:a.spent().entrySet()){int left=available.getOrDefault(e.getKey(),0)-e.getValue();if(left<0)throw new IllegalArgumentException("Paid anchors exceed custody");available.put(e.getKey(),left);}
                for(var e:data.starterDelivery(id).map(FoundingSupplies.Delivery::spent).orElse(Map.of()).entrySet()){int left=available.getOrDefault(e.getKey(),0)-e.getValue();if(left<0)throw new IllegalArgumentException("Starter supplies exceed custody");available.put(e.getKey(),left);}
            }
            if(t.contains("bootstrapSpent")){
                var spent=readBudget(t,"bootstrapSpent",registries);require(t,"bootstrapPending",Tag.TAG_BYTE);
                if(!spent.isEmpty()&&!FoundingConstructionSupplies.warehouseFirst(r))throw new IllegalArgumentException("Unexpected warehouse bootstrap spending");
                var available=new HashMap<ItemKey,Integer>();if(data.foundingCargo.containsKey(id))available.putAll(FoundingCargo.load(data.foundingCargo.get(id),registries).items());
                for(var a:data.anchorReceipts(id).values())a.spent().forEach((k,n)->available.merge(k,-n,Integer::sum));
                data.starterDelivery(id).ifPresent(d->d.spent().forEach((k,n)->available.merge(k,-n,Integer::sum)));
                for(var e:spent.entrySet())if(e.getValue()<0||e.getValue()>available.getOrDefault(e.getKey(),0))throw new IllegalArgumentException("Bootstrap spending exceeds custody");
                data.bootstrapSpent.put(id,spent);
                if(t.getBoolean("bootstrapPending")){data.bootstrapPending.add(id);data.records.put(id,new Development(r.id(),r.owner(),r.draft(),r.mode(),Phase.RECOVERY_NEEDED,Math.addExact(r.revision(),1)));recovered=true;}
            }
            if(t.contains("refund")) {
                require(t,"refund",Tag.TAG_INT);int stage=t.getInt("refund");
                if(stage<0||stage>2)throw new IllegalArgumentException("Invalid refund state");
                if(stage>0){if(!data.foundingCargo.containsKey(id)||!data.anchorReceipts(id).isEmpty()||data.journeys.containsKey(id))throw new IllegalArgumentException("Refund conflicts with native founding effects");FoundingCargo.load(data.foundingCargo.get(id),registries);data.refunds.put(id,stage);}
            }
            if(r.phase()==Phase.STAGING && (data.starterDelivery(id).filter(d->!d.complete()).isPresent() || data.refundPending(id) || data.foundingCargo.containsKey(id)&&data.foundingCargo.get(id).contains("pending") || data.anchorReceipts(id).values().stream().anyMatch(a->!a.complete()))) {
                data.records.put(id,new Development(r.id(),r.owner(),r.draft(),r.mode(),Phase.RECOVERY_NEEDED,Math.addExact(r.revision(),1)));
                recovered=true;
            }
        }
        // Read at supported maxima, not today's lower config: reductions must retain recoverable data.
        for(var r:data.records.values()) if(approved(r.phase()) && r.phase()!=Phase.RECOVERY_NEEDED && r.phase()!=Phase.PAUSED) data.validateActivation(r);
        data.setDirty(recovered);return data;
    }
    private static ListTag budget(Map<ItemKey,Integer> items,HolderLookup.Provider registries) {
        var list=new ListTag();for(var e:items.entrySet()){var t=new CompoundTag();t.put("item",e.getKey().stack().save(registries));t.putInt("quantity",e.getValue());list.add(t);}return list;
    }
    private static Map<ItemKey,Integer> readBudget(CompoundTag tag,String key,HolderLookup.Provider registries) {
        var result=new LinkedHashMap<ItemKey,Integer>();for(var entry:list(tag,key,Tag.TAG_COMPOUND,128)) {
            var t=(CompoundTag)entry;require(t,"item",Tag.TAG_COMPOUND);ints(t,"quantity");
            var stack=ItemStack.parse(registries,t.getCompound("item")).orElseThrow(()->new IllegalArgumentException("Invalid budget item"));
            if(stack.getCount()!=1 || result.put(new ItemKey(stack),t.getInt("quantity"))!=null) throw new IllegalArgumentException("Duplicate or non-normalized budget item");
        }return result;
    }
    private static Optional<SurveyOrigin> readSurveyOrigin(CompoundTag t){if(!t.contains("surveyOrigin"))return Optional.empty();require(t,"surveyOrigin",Tag.TAG_COMPOUND);return Optional.of(SurveyOrigin.load(t.getCompound("surveyOrigin")));}
    private static FoundingRoster.Policy readPolicy(CompoundTag t){if(!t.contains("foundingPolicy"))return FoundingRoster.Policy.LEGACY_SELECTED;require(t,"foundingPolicy",Tag.TAG_STRING);return FoundingRoster.Policy.valueOf(t.getString("foundingPolicy"));}
    private static UUID uuid(CompoundTag t,String key){if(!t.hasUUID(key)) throw new IllegalArgumentException("Invalid remote UUID: "+key);return t.getUUID(key);}
    private static void require(CompoundTag t,String key,int type){if(!t.contains(key,type)) throw new IllegalArgumentException("Missing or invalid remote field: "+key);}
    private static void strings(CompoundTag t,String... keys){for(var k:keys) require(t,k,Tag.TAG_STRING);}
    private static void ints(CompoundTag t,String... keys){for(var k:keys) require(t,k,Tag.TAG_INT);}
    private static void longs(CompoundTag t,String... keys){for(var k:keys) require(t,k,Tag.TAG_LONG);}
    private static ListTag list(CompoundTag t,String key,int type,int max){require(t,key,Tag.TAG_LIST);var list=(ListTag)t.get(key);if(list.size()>max || (!list.isEmpty() && list.getElementType()!=type)) throw new IllegalArgumentException("Invalid remote list: "+key);return list;}
    private static int[] box(BoundingBox b){return new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()};}
    private static BoundingBox readBox(int[] a){if(a.length!=6 || a[0]>a[3] || a[1]>a[4] || a[2]>a[5]) throw new IllegalArgumentException("Invalid remote volume");return new BoundingBox(a[0],a[1],a[2],a[3],a[4],a[5]);}
}
