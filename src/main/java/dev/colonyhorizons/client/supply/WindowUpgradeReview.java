package dev.colonyhorizons.client.supply;
import com.ldtteam.blockui.controls.Button;
import dev.colonyhorizons.network.SupplyMenuPayloads.*;
import net.minecraft.nbt.CompoundTag;
public final class WindowUpgradeReview extends WindowSupplyBase {
 private java.util.UUID settlement;
 public WindowUpgradeReview(){super("upgrade");}
 String rowText(CompoundTag row){return row.getString("text");}
 void select(CompoundTag row){text("message",row.getString("text"));}
 void render(){var u=SupplyMenuClient.data.getCompound("upgrade");if(u.hasUUID("settlement"))settlement=u.getUUID("settlement");text("title",u.getString("title"));text("details",u.getString("details"));showRows(u.getList("costs",10));enable("approve",u.getString("state").equals("PROPOSED"));enable("reject",u.getString("state").equals("PROPOSED"));text("message",u.getString("state").equals("PROPOSED")?"Approval does not bypass research, staffing or supply limits.":u.getString("state"));}
 @Override public void reply(MenuReply reply){if(reply.ok()&&(reply.message().equals("Upgrade decision saved")||reply.message().equals("Expansion decision saved"))){SupplyMenuClient.show(new WindowMayor(settlement));return;}super.reply(reply);}
 @Override public void onButtonClicked(Button b){if(b.getID().equals("back")){if(SupplyMenuClient.pending)SupplyMenuClient.finish();else SupplyMenuClient.show(new WindowMayor(settlement));return;}if(SupplyMenuClient.pending)return;var u=SupplyMenuClient.data.getCompound("upgrade");if(b.getID().equals("approve")||b.getID().equals("reject"))SupplyMenuClient.send(u.getBoolean("expansion")?(b.getID().equals("approve")?Action.APPROVE_EXPANSION:Action.REJECT_EXPANSION):(b.getID().equals("approve")?Action.APPROVE_UPGRADE:Action.REJECT_UPGRADE),u.getUUID("id"));render();}
}
