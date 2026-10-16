package dev.colonyhorizons.qa.outpost;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
/** Prototype custody record. It is not a cross-file atomic transaction journal. */
public final class ProbeShipment {
    public enum State { RESERVED, LOADING, IN_TRANSIT, UNLOADING, DELIVERED, DELAYED, RETURNING, RECOVERY_NEEDED }
    public final UUID id;
    long travelTicks;
    final String policy="PROTECTED";
    final List<ItemStack> pending=new ArrayList<>();
    final List<ItemStack> cargo=new ArrayList<>();
    State state=State.RESERVED;
    int delivered;
    public ProbeShipment(){this(UUID.randomUUID());}
    private ProbeShipment(UUID id){this.id=id;}
    public List<ItemStack> cargo(){return cargo.stream().map(ItemStack::copy).toList();}
    public int count(){return cargo.stream().mapToInt(ItemStack::getCount).sum();}
    public State state(){return state;}
    public int delivered(){return delivered;}
    public CompoundTag save(HolderLookup.Provider registries){
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putUUID("id",id);tag.putString("state",state.name());
        tag.putString("policy",policy);tag.putLong("travelTicks",travelTicks);tag.putInt("delivered",delivered);
        tag.put("cargo",saveStacks(cargo,registries));tag.put("pending",saveStacks(pending,registries));return tag;
    }
    private static ListTag saveStacks(List<ItemStack> stacks,HolderLookup.Provider registries){
        var list=new ListTag();for(var stack:stacks)if(!stack.isEmpty())list.add(stack.save(registries));return list;
    }
    public static ProbeShipment load(CompoundTag tag,HolderLookup.Provider registries){
        if(tag.getInt("schema")!=1 || !tag.hasUUID("id") || !"PROTECTED".equals(tag.getString("policy")))throw new IllegalArgumentException("Unsupported or invalid shipment record");
        var s=new ProbeShipment(tag.getUUID("id"));
        try{s.state=State.valueOf(tag.getString("state"));}catch(Exception e){throw new IllegalArgumentException("Invalid shipment state",e);}
        s.travelTicks=tag.getLong("travelTicks");s.delivered=tag.getInt("delivered");
        if(s.travelTicks<0 || s.delivered<0)throw new IllegalArgumentException("Negative shipment accounting");
        readStacks(tag,"cargo",s.cargo,registries);readStacks(tag,"pending",s.pending,registries);
        if(s.state==State.LOADING || s.state==State.UNLOADING)s.state=State.RECOVERY_NEEDED;
        if(s.state==State.DELIVERED && !s.cargo.isEmpty())throw new IllegalArgumentException("Delivered shipment retains cargo");
        return s;
    }
    private static void readStacks(CompoundTag tag,String key,List<ItemStack> into,HolderLookup.Provider registries){
        if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("Missing stack list: "+key);
        var list=tag.getList(key,Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++)into.add(ItemStack.parse(registries,list.getCompound(i)).orElseThrow(()->new IllegalArgumentException("Invalid saved cargo")));
    }
}
