package dev.colonyhorizons.client.supply;

import com.ldtteam.blockui.controls.*;

import net.minecraft.core.BlockPos;

import net.minecraft.nbt.CompoundTag;

import dev.colonyhorizons.network.SupplyMenuPayloads.*;

import java.util.*;

public final class WindowSupplyRouteSetup extends WindowSupplyBase {

 private BlockPos source,destination;private String sourceName="Choose source",destinationName="Choose destination";private boolean pickingSource=true,advanced;

 public WindowSupplyRouteSetup(){super("route");findPaneOfTypeByID("target",TextField.class).setText("64");findPaneOfTypeByID("reserve",TextField.class).setText("0");findPaneOfTypeByID("batch",TextField.class).setText("8");}

 String rowText(CompoundTag r){return r.getString("district")+" · "+r.getString("name")+" · "+BlockPos.of(r.getLong("pos")).toShortString();}

 void select(CompoundTag r){if(!r.getBoolean("eligible")){text("message",r.getString("status"));return;}if(pickingSource){source=BlockPos.of(r.getLong("pos"));sourceName=r.getString("name");pickingSource=false;}else{destination=BlockPos.of(r.getLong("pos"));destinationName=r.getString("name");}render();}

 void render(){text("title","New Supply Route");list("huts");label("source",pickingSource?"> Source":"Source");label("destination",pickingSource?"Destination":"> Destination");var player=net.minecraft.client.Minecraft.getInstance().player;text("details",sourceName+" → "+destinationName+"\nItem: "+(player==null||player.getMainHandItem().isEmpty()?"Hold an item":player.getMainHandItem().getHoverName().getString()));text("targetLabel","Keep at destination");text("reserveLabel","Leave at source");findPaneByID("batch").setVisible(advanced);label("advanced",advanced?"Shipment size":"Advanced");enable("create",source!=null&&destination!=null);enable("prev",SupplyMenuClient.data.getInt("hp")>0);enable("next",SupplyMenuClient.data.getBoolean("hn"));text("message","Uses hut inventories. Forester exports: set Pickup to Never.");}

 @Override public void reply(MenuReply reply){if(reply.ok()&&reply.message().equals("Supply route created")){SupplyMenuClient.show(new WindowOutpostsSupplies(true));return;}super.reply(reply);}

 @Override public void onButtonClicked(Button b){if(b.getID().equals("back")&&SupplyMenuClient.pending){SupplyMenuClient.finish();return;}if(SupplyMenuClient.pending)return;switch(b.getID()){

 case "back"->SupplyMenuClient.show(new WindowOutpostsSupplies(true));case "prev","next"->SupplyMenuClient.page("hp",b.getID().equals("next")?1:-1);case "source"->{pickingSource=true;render();}case "destination"->{pickingSource=false;render();}case "advanced"->{advanced=!advanced;render();}

 case "create"->{try{SupplyMenuClient.send(Action.CREATE_ROUTE,"",List.of(source,destination),new UUID(0,0),Integer.parseInt(findPaneOfTypeByID("target",TextField.class).getText()),Integer.parseInt(findPaneOfTypeByID("reserve",TextField.class).getText()),Integer.parseInt(findPaneOfTypeByID("batch",TextField.class).getText()));}catch(NumberFormatException e){text("message","Enter whole numbers for stock and shipment size");}}default->{} }}

}

