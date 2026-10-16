package dev.colonyhorizons.client.supply;

import com.ldtteam.blockui.controls.*;

import com.ldtteam.blockui.views.*;

import com.ldtteam.blockui.PaneBuilders;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.chat.Component;

import net.minecraft.nbt.*;

import dev.colonyhorizons.network.SupplyMenuPayloads.*;

import java.util.*;

abstract class WindowSupplyBase extends BOWindow implements ButtonHandler {

 final List<CompoundTag> rows=new ArrayList<>();private int ticks;

 WindowSupplyBase(String layout){super(ResourceLocation.fromNamespaceAndPath("colonyhorizons","gui/supply/"+layout+".xml"));}

 void text(String id,String value){var pane=findPaneOfTypeByID(id,Text.class);pane.setText(Component.literal(value));PaneBuilders.singleLineTooltip(Component.literal(value),pane);}

 void label(String id,String value){findPaneOfTypeByID(id,Button.class).setText(Component.literal(value));}

 void enable(String id,boolean value){findPaneByID(id).setEnabled(value&&!SupplyMenuClient.pending);}

 void list(String key){showRows(SupplyMenuClient.data.getList(key,Tag.TAG_COMPOUND));}

 void showRows(ListTag tags){rows.clear();for(var tag:tags)rows.add((CompoundTag)tag);var list=findPaneOfTypeByID("entries",ScrollingList.class);list.setDataProvider(()->rows.size(),(i,p)->{var b=p.findPaneOfTypeByID("row",Button.class);var icon=p.findPaneOfTypeByID("icon",ItemIcon.class);if(icon!=null){var level=net.minecraft.client.Minecraft.getInstance().level;icon.setVisible(rows.get(i).contains("item"));if(level!=null&&rows.get(i).contains("item"))icon.setItem(net.minecraft.world.item.ItemStack.parseOptional(level.registryAccess(),rows.get(i).getCompound("item")));}String value=rowText(rows.get(i));b.setText(Component.literal(value.length()>46?value.substring(0,43)+"...":value));PaneBuilders.singleLineTooltip(Component.literal(value),b);b.setHandler(clicked->select(rows.get(i)));});list.refreshElementPanes();}

 abstract String rowText(CompoundTag row);abstract void select(CompoundTag row);abstract void render();

 public void reply(MenuReply reply){render();text("message",reply.message());}

 @Override public void onOpened(){render();}

 @Override public void onUpdate(){super.onUpdate();for(var pane:getChildren())if(pane instanceof TextField) pane.setEnabled(!SupplyMenuClient.pending);if(SupplyMenuClient.pending&&System.currentTimeMillis()-SupplyMenuClient.sentAt>10000){text("message","Waiting for server. You can close and reopen this menu.");}if(this instanceof WindowOutpostsSupplies&&++ticks%40==0&&!SupplyMenuClient.pending)SupplyMenuClient.send(Action.REFRESH,new UUID(0,0));}

 @Override public boolean onUnhandledKeyTyped(int key,int scan){if(key==256){onButtonClicked(findPaneOfTypeByID("back",Button.class));return true;}return super.onUnhandledKeyTyped(key,scan);}

}

