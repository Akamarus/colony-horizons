package dev.colonyhorizons.menu;
import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import static dev.colonyhorizons.menu.SupplyMenuModels.*;
/** Bounded snapshot serialization; never serializes server-side review authority. */
public final class SupplyMenuWire {
 private SupplyMenuWire(){}
 public static CompoundTag write(MenuSnapshot s,HolderLookup.Provider registry){
  var t=new CompoundTag();t.putInt("colony",s.colony());t.putString("name",s.name());t.putString("problem",s.problem());
  t.putInt("hp",s.huts().page());t.putBoolean("hn",s.huts().hasNext());t.putInt("dp",s.districts().page());t.putBoolean("dn",s.districts().hasNext());t.putInt("rp",s.routes().page());t.putBoolean("rn",s.routes().hasNext());
  var huts=new ListTag();for(var h:s.huts().rows()){var r=new CompoundTag();r.putLong("pos",h.position().asLong());r.putString("name",h.name());r.putString("district",h.district());r.putString("status",h.status());r.putBoolean("eligible",h.eligible());huts.add(r);}t.put("huts",huts);
  var districts=new ListTag();for(var d:s.districts().rows()){var r=new CompoundTag();r.putUUID("id",d.id());r.putString("name",d.name());r.putString("status",d.status());r.putInt("huts",d.huts());r.putBoolean("paused",d.paused());districts.add(r);}t.put("districts",districts);
  var routes=new ListTag();for(var d:s.routes().rows()){var r=new CompoundTag();r.putUUID("id",d.id());r.putString("source",d.source());r.putString("destination",d.destination());r.put("item",d.item().save(registry));r.putString("itemName",text(d.item().getHoverName().getString()));r.putInt("target",d.target());r.putInt("reserve",d.reserve());r.putInt("batch",d.batch());r.putBoolean("paused",d.paused());r.putBoolean("disabled",d.disabled());r.putString("status",d.status());r.putInt("cargo",d.cargo());r.putInt("seconds",d.seconds());r.putLong("delivered",d.delivered());routes.add(r);}t.put("routes",routes);return t;
 }
}

