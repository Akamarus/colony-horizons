package dev.colonyhorizons.client.supply;

import com.ldtteam.blockui.controls.Button;

import net.minecraft.nbt.CompoundTag;

import dev.colonyhorizons.network.SupplyMenuPayloads.*;

import java.util.*;

public final class WindowOutpostsSupplies extends WindowSupplyBase {

 private boolean routes,confirmStop;private UUID selected=new UUID(0,0);private CompoundTag chosen=new CompoundTag();

 public WindowOutpostsSupplies(){this(false);}

 public WindowOutpostsSupplies(boolean routes){super("main");this.routes=routes;}

 String rowText(CompoundTag r){return routes?r.getString("itemName")+" — "+r.getString("status"):r.getString("name")+" — "+r.getString("status");}

 void select(CompoundTag r){chosen=r;selected=r.getUUID("id");confirmStop=false;details();}

 void render(){text("title",SupplyMenuClient.data.getString("name")+" · Outposts & Supplies");if(routes)list("routes");else{var combined=new net.minecraft.nbt.ListTag();for(var t:SupplyMenuClient.data.getList("districts",10)){var local=(CompoundTag)t.copy();if(!local.getBoolean("remote")&&!local.getUUID("id").equals(new UUID(0,0)))local.putString("name",local.getString("name")+" (Local District)");combined.add(local);}showRows(combined);}chosen=rows.stream().filter(r->r.getUUID("id").equals(selected)).findFirst().orElse(new CompoundTag());label("new",routes?"New Route":"Found Outpost");enable("prev",SupplyMenuClient.data.getInt(routes?"rp":"dp")>0);enable("next",SupplyMenuClient.data.getBoolean(routes?"rn":"dn"));text("message",SupplyMenuClient.data.getString("problem"));details();}

 private void details(){findPaneByID("manage").setVisible(!routes&&chosen.getBoolean("remote"));enable("manage",!chosen.isEmpty());findPaneByID("local").setVisible(!routes);label("stop",confirmStop?"Confirm Stop":"Stop Route");boolean active=!chosen.isEmpty()&&!selected.equals(new UUID(0,0));enable("pause",active&&!chosen.getBoolean("disabled")&&!chosen.getBoolean("remote"));findPaneByID("stop").setVisible(routes);enable("stop",active&&!chosen.getBoolean("disabled"));label("pause",chosen.getBoolean("paused")?"Resume":"Pause");

  if(confirmStop){text("details",net.minecraft.network.chat.Component.translatable("colonyhorizons.menu.stop_confirm").getString());return;}
  if(chosen.isEmpty()){text("details",routes?"Create a route to supply an outpost or bring its goods home. Remote cargo uses warehouses; couriers deliver locally.":"Found a remote settlement, or select an outpost to open its Mayor page.");return;}

  if(chosen.getBoolean("remote")){text("details",chosen.getString("name")+"\n"+chosen.getString("status")+"\nOpen Mayor for buildings, supplies and health.");return;}
  text("details",routes?chosen.getString("source")+" → "+chosen.getString("destination")+"\nKeep: "+chosen.getInt("target")+" · Leave: "+chosen.getInt("reserve")+"\nShipment: "+chosen.getInt("batch")+" · Cargo: "+chosen.getInt("cargo")+"\nArrival: "+chosen.getInt("seconds")+"s · Delivered: "+chosen.getLong("delivered")+"\n"+chosen.getString("status"):chosen.getString("name")+"\n"+chosen.getInt("huts")+" huts · "+chosen.getString("status")+"\nPausing stops local courier work. Supply routes have separate pause controls.");

 }

 @Override public void onButtonClicked(Button b){if(b.getID().equals("back")){if(SupplyMenuClient.data.contains("survey"))SupplyMenuClient.show(new WindowSurveys());else SupplyMenuClient.finish();return;}if(SupplyMenuClient.pending)return;switch(b.getID()){

 case "back"->SupplyMenuClient.finish();case "outposts","routes"->{routes=b.getID().equals("routes");selected=new UUID(0,0);confirmStop=false;render();}

 case "new"->SupplyMenuClient.show(routes?new WindowSupplyRouteSetup():new WindowRemoteFounding());

 case "local"->SupplyMenuClient.show(new WindowOutpostSetup());
 case "manage"->{SupplyMenuClient.show(new WindowMayor(selected));SupplyMenuClient.send(Action.VIEW_MAYOR,selected);}
 case "prev","next"->SupplyMenuClient.page(routes?"rp":"dp",b.getID().equals("next")?1:-1);

 case "pause"->SupplyMenuClient.send(routes?(chosen.getBoolean("paused")?Action.RESUME_ROUTE:Action.PAUSE_ROUTE):(chosen.getBoolean("paused")?Action.RESUME_OUTPOST:Action.PAUSE_OUTPOST),selected);

 case "stop"->{if(confirmStop){SupplyMenuClient.send(Action.STOP_ROUTE,selected);confirmStop=false;label("stop","Stop Route");}else{confirmStop=true;label("stop","Confirm Stop");text("details",net.minecraft.network.chat.Component.translatable("colonyhorizons.menu.stop_confirm").getString());}}

 default->{} }}

}

