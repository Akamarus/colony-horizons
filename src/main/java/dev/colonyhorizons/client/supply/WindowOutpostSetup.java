package dev.colonyhorizons.client.supply;

import com.ldtteam.blockui.controls.*;

import net.minecraft.core.BlockPos;

import net.minecraft.nbt.*;

import dev.colonyhorizons.network.SupplyMenuPayloads.*;

import java.util.*;

public final class WindowOutpostSetup extends WindowSupplyBase {

 private final Set<BlockPos> selected=new LinkedHashSet<>();private boolean reviewed,waitingReview;private String reviewedName="";

 public WindowOutpostSetup(){super("outpost");}

 String rowText(CompoundTag r){if(reviewed)return r.getString("name")+" · "+BlockPos.of(r.getLong("pos")).toShortString();return (selected.contains(BlockPos.of(r.getLong("pos")))?"✓ ":"")+r.getString("name")+" · "+r.getString("district")+" · "+BlockPos.of(r.getLong("pos")).toShortString();}

 void select(CompoundTag r){if(reviewed){text("message","Reviewed huts shown. Change the name or return to start over.");return;}if(!r.getBoolean("eligible")||!r.getString("district").equals("Home")){text("message","Choose a completed Home hut");return;}var pos=BlockPos.of(r.getLong("pos"));if(!selected.remove(pos)){if(selected.size()==32){text("message","Maximum 32 huts");return;}selected.add(pos);}reviewed=false;render();}

 void render(){text("title","Local District");text("hint","Name it, then select its huts");if(reviewed)showRows(SupplyMenuClient.data.getCompound("draft").getList("buildings",Tag.TAG_COMPOUND));else list("huts");text("details",reviewed?reviewText():selected.size()+" huts selected. Review includes any other huts inside the boundary. Couriers and their warehouses must stay together.");enable("create",reviewed);enable("review",!selected.isEmpty());enable("prev",SupplyMenuClient.data.getInt("hp")>0);enable("next",SupplyMenuClient.data.getBoolean("hn"));}

 private String reviewText(){var d=SupplyMenuClient.data.getCompound("draft");return d.getInt("huts")+" huts / "+d.getInt("chunks")+" chunks\nScroll the list to review ALL included huts. Activate when ready. Couriers stay within this district.";}

 @Override public void reply(MenuReply reply){if(reply.ok()&&reply.message().equals("Outpost activated")){SupplyMenuClient.show(new WindowOutpostsSupplies());return;}if(waitingReview&&reply.ok()&&reply.data().contains("draft")){reviewed=true;reviewedName=reply.data().getCompound("draft").getString("name");if(!reviewedName.equals(findPaneOfTypeByID("name",TextField.class).getText().trim()))reviewed=false;}waitingReview=false;super.reply(reply);}

 @Override public void onButtonClicked(Button b){if(b.getID().equals("back")&&SupplyMenuClient.pending){SupplyMenuClient.finish();return;}if(SupplyMenuClient.pending)return;String name=findPaneOfTypeByID("name",TextField.class).getText().trim();switch(b.getID()){

 case "back"->SupplyMenuClient.show(new WindowOutpostsSupplies());case "prev","next"->SupplyMenuClient.page("hp",b.getID().equals("next")?1:-1);

 case "review"->{reviewed=false;waitingReview=true;SupplyMenuClient.send(Action.REVIEW_OUTPOST,name,List.copyOf(selected),new UUID(0,0),64,0,8);}

 case "create"->{if(!name.equals(reviewedName)){reviewed=false;render();text("message","Name changed; review again");}else SupplyMenuClient.send(Action.CREATE_OUTPOST,new UUID(0,0));}default->{} }}

}

