package dev.colonyhorizons.client.recipes;
import com.ldtteam.blockui.controls.*;
import com.ldtteam.blockui.views.*;
import com.ldtteam.blockui.PaneBuilders;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import dev.colonyhorizons.network.RecipeCatalogNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public class RecipeCatalogWindow extends BOWindow implements ButtonHandler {
 private final CraftingModuleView module;private final BOWindow parent;private final UUID id=UUID.randomUUID();
 private String selected="";private int[] choices=new int[0];private int page,sequence;private boolean pending;private long sent;
 private CompoundTag data=new CompoundTag();
 public RecipeCatalogWindow(CraftingModuleView module,BOWindow parent){super(ResourceLocation.fromNamespaceAndPath("colonyhorizons","gui/recipes/catalog.xml"));this.module=module;this.parent=parent;}
 private void text(String key,String value){var p=findPaneOfTypeByID(key,Text.class);p.setText(Component.literal(value));PaneBuilders.singleLineTooltip(Component.literal(value),p);}
 private void send(boolean learn){if(pending)return;pending=true;sent=System.currentTimeMillis();var query=findPaneOfTypeByID("query",TextField.class).getText();if(query.length()>64)query=query.substring(0,64);PacketDistributor.sendToServer(new RecipeCatalogNetwork.Request(id,++sequence,module.getColony().getID(),module.getBuildingView().getID(),module.getProducer().getRuntimeID(),page,query,selected,choices,learn));render();}
 @Override public void onOpened(){RecipeCatalogNetwork.receiver=this::reply;send(false);}
 private void reply(RecipeCatalogNetwork.Reply r){if(!id.equals(r.window())||r.sequence()!=sequence)return;pending=false;if(!r.data().isEmpty()){data=r.data();page=data.getInt("page");}else{data.remove("detail");}text("message",r.message());render();}
 private net.minecraft.core.HolderLookup.Provider registry(){return Minecraft.getInstance().level==null?net.minecraft.core.RegistryAccess.EMPTY:Minecraft.getInstance().level.registryAccess();}
 private void render(){
  text("capacity","Recipes: "+data.getInt("used")+" / "+data.getInt("capacity"));text("page",(page+1)+" / "+Math.max(1,data.getInt("pages")));
  var rows=data.getList("rows",10);var list=findPaneOfTypeByID("entries",ScrollingList.class);
  list.setDataProvider(()->rows.size(),(i,p)->{var row=rows.getCompound(i);var b=p.findPaneOfTypeByID("row",Button.class);String name=row.getString("name");b.setText(Component.literal(name.length()>21?name.substring(0,19)+"…":name));PaneBuilders.singleLineTooltip(Component.literal(name),b);b.setEnabled(!pending);p.findPaneOfTypeByID("icon",ItemIcon.class).setItem(ItemStack.parseOptional(registry(),row.getCompound("item")));b.setHandler(v->{if(pending)return;selected=row.getString("key");choices=new int[0];send(false);});});list.refreshElementPanes();
  var detail=data.getCompound("detail");text("name",detail.getString("name"));text("note",detail.getString("note"));
  findPaneOfTypeByID("output",ItemIcon.class).setItem(ItemStack.parseOptional(registry(),detail.getCompound("output")));
  var inputs=detail.getList("ingredients",10);
  for(int i=0;i<9;i++){var value=i<inputs.size()?inputs.getCompound(i):new CompoundTag();findPaneOfTypeByID("i"+i,ItemIcon.class).setItem(ItemStack.parseOptional(registry(),value.getCompound("item")));var button=findPaneOfTypeByID("v"+i,Button.class);button.setVisible(value.getInt("options")>1);button.setEnabled(!pending);PaneBuilders.singleLineTooltip(Component.literal("Change ingredient ("+(value.getInt("choice")+1)+"/"+value.getInt("options")+")"),button);}
  findPaneByID("learn").setEnabled(!pending&&detail.getBoolean("learn"));findPaneByID("search").setEnabled(!pending);findPaneByID("prev").setEnabled(!pending&&page>0);findPaneByID("next").setEnabled(!pending&&page+1<data.getInt("pages"));
 }
 @Override public void onButtonClicked(Button button){
  String key=button.getID();if(key.equals("back")){RecipeCatalogNetwork.receiver=r->{};parent.open();return;}if(key.equals("manual")){RecipeCatalogNetwork.receiver=r->{};module.openCraftingGUI();return;}if(pending)return;
  if(key.equals("learn")){send(true);return;}
  if(key.startsWith("v")){int slot=Integer.parseInt(key.substring(1));var inputs=data.getCompound("detail").getList("ingredients",10);choices=new int[inputs.size()];for(int i=0;i<choices.length;i++)choices[i]=inputs.getCompound(i).getInt("choice");choices[slot]=(choices[slot]+1)%inputs.getCompound(slot).getInt("options");send(false);return;}
  if(key.equals("prev"))page=Math.max(0,page-1);else if(key.equals("next"))page++;else if(key.equals("search")){page=0;selected="";choices=new int[0];}send(false);
 }
 @Override public void onUpdate(){super.onUpdate();if(pending&&System.currentTimeMillis()-sent>10000){pending=false;data.remove("detail");text("message","Server did not reply. Refresh or reopen the catalog.");render();}}
 @Override public boolean onUnhandledKeyTyped(int key,int scan){if(key==256){RecipeCatalogNetwork.receiver=r->{};parent.open();return true;}return super.onUnhandledKeyTyped(key,scan);}
}
