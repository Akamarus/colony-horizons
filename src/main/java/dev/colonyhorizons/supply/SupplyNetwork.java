package dev.colonyhorizons.supply;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Server-thread transport custody. Depot buffers and cargo MUST be persisted as one snapshot.
 * No external inventories, citizens, chunk loading or offline clock are touched here.
 * Registration/deposit/withdraw are trusted integration APIs, not player authorization endpoints.
 */
public final class SupplyNetwork {
    public static final int SLOTS=9, MAX_DEPOTS=128, MAX_ROUTES=256;
    public enum Reason { IDLE, TRAVELLING, TARGET_MET, SOURCE_RESERVE, SOURCE_UNAVAILABLE,
        DESTINATION_UNAVAILABLE, DESTINATION_FULL, RETURN_FULL, PAUSED, RETURNING, DISABLED }
    public record RouteView(UUID id, UUID source, UUID destination, ItemStack cargo,
                            int remainingTicks, Reason reason, long delivered) {
        public RouteView { cargo=cargo.copy(); }
        @Override public ItemStack cargo(){return cargo.copy();}
    }
    private static final class Depot {
        final UUID id,owner; final int colony; final ItemStackHandler inventory=new ItemStackHandler(SLOTS);
        Depot(UUID id,UUID owner,int colony){this.id=id;this.owner=owner;this.colony=colony;}
    }
    private static final class Route {
        final UUID id,source,destination; final ItemStack item;
        final int target,reserve,batch,travel;
        ItemStack cargo=ItemStack.EMPTY; int remaining; long delivered;
        boolean paused,disabled,returning; Reason reason=Reason.IDLE;
        Route(UUID id,UUID source,UUID destination,ItemStack item,int target,int reserve,int batch,int travel){
            this.id=id;this.source=source;this.destination=destination;this.item=item.copyWithCount(1);
            this.target=target;this.reserve=reserve;this.batch=batch;this.travel=travel;
        }
    }
    private final Map<UUID,Depot> depots=new LinkedHashMap<>();
    private final Map<UUID,Route> routes=new LinkedHashMap<>();
    private final Runnable dirty;
    public SupplyNetwork(){this(()->{});}
    SupplyNetwork(Runnable dirty){this.dirty=Objects.requireNonNull(dirty);}

    public UUID addDepot(UUID owner,int colony){
        Objects.requireNonNull(owner); require(colony>0 && depots.size()<MAX_DEPOTS,"Depot limit/colony");
        UUID id=UUID.randomUUID();depots.put(id,new Depot(id,owner,colony));dirty.run();return id;
    }
    public UUID addRoute(UUID actor,UUID source,UUID destination,ItemStack item,int target,int reserve,int batch,int travel){
        var from=owned(actor,source);var to=owned(actor,destination);
        validatePolicy(from,to,item,target,reserve,batch,travel);
        require(routes.size()<MAX_ROUTES,"Route limit");
        UUID id=UUID.randomUUID();routes.put(id,new Route(id,source,destination,item,target,reserve,batch,travel));dirty.run();return id;
    }
    private static void validatePolicy(Depot from,Depot to,ItemStack item,int target,int reserve,int batch,int travel){
        require(from!=to && from.colony==to.colony && from.owner.equals(to.owner),"Different colony/owner or identical endpoints");
        require(!item.isEmpty() && target>0 && target<=4096 && reserve>=0 && reserve<=4096 && batch>0 && batch<=64 && travel>0 && travel<=72000,"Invalid stock/travel policy");
    }
    /** Returns unaccepted items; input is never mutated. External custody must commit separately. */
    public ItemStack deposit(UUID actor,UUID depot,ItemStack input){
        var d=owned(actor,depot);require(input.getCount()>=0 && input.getCount()<=4096,"Invalid deposit count");
        var rest=insert(d,input);if(rest.getCount()!=input.getCount())dirty.run();return rest;
    }
    public ItemStack withdraw(UUID actor,UUID depot,ItemStack item,int count){
        var d=owned(actor,depot);require(!item.isEmpty() && count>0 && count<=item.getMaxStackSize(),"Invalid withdrawal");
        var result=extract(d,item,count);if(!result.isEmpty())dirty.run();return result;
    }
    public int stock(UUID depot,ItemStack item){
        var d=depot(depot);int total=0;
        for(int slot=0;slot<SLOTS;slot++){var s=d.inventory.getStackInSlot(slot);if(ItemStack.isSameItemSameComponents(item,s))total+=s.getCount();}
        return total;
    }
    public Set<UUID> depotIds(){return Set.copyOf(depots.keySet());}
    public Set<UUID> routeIds(){return Set.copyOf(routes.keySet());}
    public RouteView route(UUID id){var r=routeData(id);return new RouteView(r.id,r.source,r.destination,r.cargo,r.remaining,r.reason,r.delivered);}
    public void pause(UUID actor,UUID id,boolean paused){var r=authorizedRoute(actor,id);r.paused=paused;r.reason=paused?Reason.PAUSED:Reason.IDLE;dirty.run();}
    /** Stop future dispatches and return only undelivered cargo. Never remove cargo on cancellation. */
    public void cancel(UUID actor,UUID id){
        var r=authorizedRoute(actor,id);r.disabled=true;r.paused=false;r.returning=!r.cargo.isEmpty();
        r.reason=r.returning?Reason.RETURNING:Reason.DISABLED;dirty.run();
    }
    /** Advance actual server ticks only. Availability is supplied by the future validated endpoint adapter. */
    public void tick(int ticks,Set<UUID> available){
        require(ticks>0 && ticks<=1200,"Invalid tick budget");Objects.requireNonNull(available);
        for(var r:routes.values()){
            if(r.paused){r.reason=Reason.PAUSED;continue;}
            if(!r.cargo.isEmpty()){
                r.remaining=Math.max(0,r.remaining-ticks);
                if(r.remaining>0){r.reason=r.returning?Reason.RETURNING:Reason.TRAVELLING;continue;}
                UUID target=r.returning?r.source:r.destination;
                if(!available.contains(target)){r.reason=r.returning?Reason.SOURCE_UNAVAILABLE:Reason.DESTINATION_UNAVAILABLE;continue;}
                int before=r.cargo.getCount();r.cargo=insert(depot(target),r.cargo);
                if(!r.returning)r.delivered=Math.addExact(r.delivered,before-r.cargo.getCount());
                if(r.cargo.isEmpty()){r.returning=false;r.reason=r.disabled?Reason.DISABLED:Reason.IDLE;}
                else r.reason=r.returning?Reason.RETURN_FULL:Reason.DESTINATION_FULL;
                continue;
            }
            if(r.disabled){r.reason=Reason.DISABLED;continue;}
            if(!available.contains(r.source)){r.reason=Reason.SOURCE_UNAVAILABLE;continue;}
            if(!available.contains(r.destination)){r.reason=Reason.DESTINATION_UNAVAILABLE;continue;}
            int incoming=0,reserve=r.reserve;
            for(var other:routes.values()){
                if((other.returning?other.source:other.destination).equals(r.destination) && ItemStack.isSameItemSameComponents(other.item,r.item))incoming+=other.cargo.getCount();
                if(other.source.equals(r.source) && !other.disabled && ItemStack.isSameItemSameComponents(other.item,r.item))reserve=Math.max(reserve,other.reserve);
            }
            int wanted=r.target-stock(r.destination,r.item)-incoming;
            if(wanted<=0){r.reason=Reason.TARGET_MET;continue;}
            int surplus=stock(r.source,r.item)-reserve;
            if(surplus<=0){r.reason=Reason.SOURCE_RESERVE;continue;}
            int amount=Math.min(Math.min(r.batch,r.item.getMaxStackSize()),Math.min(wanted,surplus));
            r.cargo=extract(depot(r.source),r.item,amount);r.remaining=r.travel;r.reason=Reason.TRAVELLING;
        }
        if(!routes.isEmpty())dirty.run();
    }
    private static ItemStack insert(Depot d,ItemStack input){
        var rest=input.copy();
        for(int slot=0;slot<SLOTS && !rest.isEmpty();slot++)rest=d.inventory.insertItem(slot,rest,false);
        return rest.copy();
    }
    private static ItemStack extract(Depot d,ItemStack item,int count){
        int moved=0;
        for(int slot=0;slot<SLOTS && moved<count;slot++)if(ItemStack.isSameItemSameComponents(item,d.inventory.getStackInSlot(slot)))moved+=d.inventory.extractItem(slot,count-moved,false).getCount();
        return moved==0?ItemStack.EMPTY:item.copyWithCount(moved);
    }
    private Depot depot(UUID id){var d=depots.get(id);require(d!=null,"Unknown depot");return d;}
    private Depot owned(UUID actor,UUID id){var d=depot(id);if(!d.owner.equals(actor))throw new SecurityException("Depot owner mismatch");return d;}
    private Route routeData(UUID id){var r=routes.get(id);require(r!=null,"Unknown route");return r;}
    private Route authorizedRoute(UUID actor,UUID id){var r=routeData(id);owned(actor,r.source);owned(actor,r.destination);return r;}
    private static void require(boolean condition,String why){if(!condition)throw new IllegalArgumentException(why);}

    public CompoundTag save(HolderLookup.Provider registries){
        var tag=new CompoundTag();tag.putInt("schema",1);
        var ds=new ListTag();
        for(var d:depots.values()){
            var t=new CompoundTag();t.putUUID("id",d.id);t.putUUID("owner",d.owner);t.putInt("colony",d.colony);
            var slots=new ListTag();for(int i=0;i<SLOTS;i++)slots.add(stackTag(d.inventory.getStackInSlot(i),registries));t.put("slots",slots);ds.add(t);
        }
        tag.put("depots",ds);var rs=new ListTag();
        for(var r:routes.values()){
            var t=new CompoundTag();t.putUUID("id",r.id);t.putUUID("source",r.source);t.putUUID("destination",r.destination);
            t.put("item",stackTag(r.item,registries));t.put("cargo",stackTag(r.cargo,registries));
            t.putInt("target",r.target);t.putInt("reserve",r.reserve);t.putInt("batch",r.batch);t.putInt("travel",r.travel);t.putInt("remaining",r.remaining);
            t.putLong("delivered",r.delivered);t.putBoolean("paused",r.paused);t.putBoolean("disabled",r.disabled);t.putBoolean("returning",r.returning);t.putString("reason",r.reason.name());rs.add(t);
        }
        tag.put("routes",rs);return tag;
    }
    public static SupplyNetwork load(CompoundTag tag,HolderLookup.Provider registries){return load(tag,registries,()->{});}
    static SupplyNetwork load(CompoundTag tag,HolderLookup.Provider registries,Runnable dirty){
        require(tag.contains("schema",Tag.TAG_INT) && tag.getInt("schema")==1,"Unsupported supply schema");
        var n=new SupplyNetwork(dirty);var ds=list(tag,"depots");var rs=list(tag,"routes");
        require(ds.size()<=MAX_DEPOTS && rs.size()<=MAX_ROUTES,"Saved network limit");
        for(int i=0;i<ds.size();i++){
            var t=ds.getCompound(i);var id=uuid(t,"id");var owner=uuid(t,"owner");int colony=integer(t,"colony");require(colony>0,"Invalid colony");
            var d=new Depot(id,owner,colony);var slots=list(t,"slots");require(slots.size()==SLOTS,"Invalid slot count");
            for(int slot=0;slot<SLOTS;slot++)d.inventory.setStackInSlot(slot,readStack(slots.getCompound(slot),registries));
            require(n.depots.putIfAbsent(id,d)==null,"Duplicate depot");
        }
        for(int i=0;i<rs.size();i++){
            var t=rs.getCompound(i);var id=uuid(t,"id");var source=uuid(t,"source");var destination=uuid(t,"destination");
            require(t.contains("item",Tag.TAG_COMPOUND) && t.contains("cargo",Tag.TAG_COMPOUND),"Missing stack fields");
            var item=readStack(t.getCompound("item"),registries);require(item.getCount()==1,"Invalid item template");
            int target=integer(t,"target"),reserve=integer(t,"reserve"),batch=integer(t,"batch"),travel=integer(t,"travel");
            validatePolicy(n.depot(source),n.depot(destination),item,target,reserve,batch,travel);
            var r=new Route(id,source,destination,item,target,reserve,batch,travel);r.cargo=readStack(t.getCompound("cargo"),registries);
            r.remaining=integer(t,"remaining");r.paused=bool(t,"paused");r.disabled=bool(t,"disabled");r.returning=bool(t,"returning");
            require(t.contains("delivered",Tag.TAG_LONG) && t.contains("reason",Tag.TAG_STRING),"Missing route accounting");
            r.delivered=t.getLong("delivered");r.reason=Reason.valueOf(t.getString("reason"));
            require(r.delivered>=0 && r.delivered<=Long.MAX_VALUE-64 && r.remaining>=0 && r.remaining<=r.travel,"Invalid progress");
            require(r.cargo.isEmpty() || (r.cargo.getCount()<=r.batch && ItemStack.isSameItemSameComponents(r.item,r.cargo)),"Invalid cargo");
            require(!r.cargo.isEmpty() || (r.remaining==0 && !r.returning),"Empty cargo with progress");
            require(!r.returning || r.disabled,"Return without cancellation");
            require(!r.disabled || r.cargo.isEmpty() || r.returning,"Cancelled cargo must return");
            require(n.routes.putIfAbsent(id,r)==null,"Duplicate route");
        }
        return n;
    }
    private static CompoundTag stackTag(ItemStack stack,HolderLookup.Provider registries){return stack.isEmpty()?new CompoundTag():(CompoundTag)stack.save(registries);}
    private static ItemStack readStack(CompoundTag tag,HolderLookup.Provider registries){
        if(tag.isEmpty())return ItemStack.EMPTY;
        var stack=ItemStack.parse(registries,tag).orElseThrow(()->new IllegalArgumentException("Invalid stack"));
        require(!stack.isEmpty() && stack.getCount()>0 && stack.getCount()<=stack.getMaxStackSize(),"Invalid stack count");return stack;
    }
    private static ListTag list(CompoundTag tag,String key){
        require(tag.contains(key,Tag.TAG_LIST),"Missing list: "+key);var list=(ListTag)tag.get(key);
        require(list.isEmpty() || list.getElementType()==Tag.TAG_COMPOUND,"Invalid list: "+key);return list;
    }
    private static UUID uuid(CompoundTag tag,String key){require(tag.hasUUID(key),"Invalid UUID: "+key);return tag.getUUID(key);}
    private static int integer(CompoundTag tag,String key){require(tag.contains(key,Tag.TAG_INT),"Missing integer: "+key);return tag.getInt(key);}
    private static boolean bool(CompoundTag tag,String key){require(tag.contains(key,Tag.TAG_BYTE) && (tag.getByte(key)==0 || tag.getByte(key)==1),"Invalid boolean: "+key);return tag.getBoolean(key);}
}
