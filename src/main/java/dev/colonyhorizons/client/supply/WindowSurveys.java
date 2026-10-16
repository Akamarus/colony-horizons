// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.client.supply;
import com.ldtteam.blockui.controls.Button;
import net.minecraft.nbt.*;
import dev.colonyhorizons.network.SupplyMenuPayloads.Action;
import dev.colonyhorizons.network.SupplyMenuPayloads.MenuReply;
import java.util.UUID;
public final class WindowSurveys extends WindowSupplyBase {
 private CompoundTag selected=new CompoundTag();private int ticks;private boolean findings;private String feedback="";
 public WindowSurveys(){super("surveys");}
 String rowText(CompoundTag r){return r.getString("name")+(!findings&&r.getBoolean("unlocked")?" *":"");}
 void select(CompoundTag r){selected=r;render();}
 void render(){var survey=SupplyMenuClient.data.getCompound("survey");text("title","Exploration Hut - Biome Resources");var entries=survey.getList(findings?"findings":"resources",10);String target=selected.getString("target");if(!findings&&!target.isEmpty())selected=entries.stream().map(t->(CompoundTag)t).filter(t->t.getString("target").equals(target)).findFirst().orElse(new CompoundTag());showRows(entries);label("findings",findings?"Choose Resource":"Findings");
  String detail="Choose a resource. Discover brings back a real sample and unlocks gathering.";
  if(!selected.isEmpty()){if(findings){detail=selected.getString("name")+"\n"+selected.getString("evidence")+"\n"+selected.getString("production");if(selected.contains("biome"))detail+="\nBiome:\n"+selected.getString("biome");}else{detail=selected.getString("name")+"\nDiscover: 8 bread\nTool: "+selected.getString("tool");if(selected.getBoolean("unlocked")&&selected.getBoolean("gatherable")){detail+="\nGather: "+selected.getInt("yield")+" for "+selected.getInt("bread")+" bread\n5 minutes per trip\nWear: "+selected.getInt("wear")+" (fixed, even enchanted)";}else detail+=selected.getBoolean("gatherable")?"\nDiscover to unlock gathering.":"\nDiscovery only.";}}
  text("details",detail);String status=survey.getString("status");if(survey.getBoolean("active")&&survey.getString("kind").equals("gathering")&&!survey.getBoolean("returning"))status+="\n"+((survey.getInt("remainingTicks")+19)/20)+" seconds left"+(survey.getBoolean("repeat")?"; repeating":"");text("status",status);text("message",!feedback.isEmpty()?feedback:findings?"Historical reports may need rediscovery.":"Simulated gathering; distant blocks are not harvested.");
  boolean idle=!survey.getBoolean("active")&&!findings&&!selected.isEmpty();enable("start",idle);enable("gather",idle&&selected.getBoolean("unlocked")&&selected.getBoolean("gatherable"));enable("repeat",idle&&selected.getBoolean("unlocked")&&selected.getBoolean("gatherable"));enable("cancel",survey.getBoolean("active")&&!survey.getBoolean("recovery")&&!survey.getBoolean("otherHut")&&(!survey.getBoolean("returning")||survey.getBoolean("repeat")));
 }
 @Override public void onButtonClicked(Button b){if(b.getID().equals("back")){SupplyMenuClient.finish();return;}if(SupplyMenuClient.pending)return;var survey=SupplyMenuClient.data.getCompound("survey");switch(b.getID()){
  case "findings"->{findings=!findings;selected=new CompoundTag();render();}
  case "start","gather","repeat"->{if(findings||selected.isEmpty())return;var form=new CompoundTag();form.putString("target",selected.getString("target"));form.putBoolean("repeat",b.getID().equals("repeat"));SupplyMenuClient.remote(b.getID().equals("start")?Action.START_SURVEY:Action.START_GATHERING,form,new UUID(0,0));}
  case "cancel"->{if(!survey.hasUUID("id"))return;var form=new CompoundTag();form.putLong("revision",survey.getLong("revision"));SupplyMenuClient.remote(survey.getString("kind").equals("gathering")?Action.RECALL_GATHERING:Action.CANCEL_SURVEY,form,survey.getUUID("id"));}
  default->{}
 }}
 @Override public void reply(MenuReply reply){if(!reply.message().isBlank())feedback=reply.message();render();}

 @Override public void onUpdate(){super.onUpdate();if(++ticks%40==0&&!SupplyMenuClient.pending)SupplyMenuClient.send(Action.REFRESH,new UUID(0,0));}
}
