package dev.colonyhorizons.qa;
import dev.colonyhorizons.client.recipes.RecipeCatalogWindow;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.*;
import java.util.*;

/** Disposable layout fixture, not proof of live server/client mutation. */
public final class RecipeCatalogPreviewClient {
 private static int ticks,phase;private static boolean started,opening;
 public static void tick()throws Exception{
  var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
  if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
  if(!started){if(mc.level==null||mc.player==null){
   if(mc.screen instanceof TitleScreen&&!opening){opening=true;mc.createWorldOpenFlows().openWorld("Catalog QA",()->{throw new IllegalStateException("Disposable catalog world load cancelled");});return;}
   if(++ticks%40==0&&mc.screen!=null){System.out.println("CATALOG_WAIT_SCREEN "+mc.screen.getClass().getName()+" "+mc.screen.getTitle().getString());
    for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button){System.out.println("CATALOG_WAIT_BUTTON "+button.getMessage().getString());if(button.getMessage().getString().equals("Proceed")||button.getMessage().getString().equals("I know what I'm doing!"))button.onPress();}
   }return;}started=true;Class.forName("com.minecolonies.core.client.gui.townhall.WindowMainPage");mc.options.guiScale().set(2);mc.resizeDisplay();show();}
  if(++ticks%40!=0)return;
  Screenshot.grab(mc.gameDirectory,"catalog-preview-"+phase+".png",mc.getMainRenderTarget(),c->System.out.println("CATALOG_SCREENSHOT "+c.getString()));
  if(++phase==8){System.out.println("CATALOG_PREVIEW_COMPLETE: 8 render states; disposable copied test world");mc.stop();return;}
  if(phase==4){mc.options.guiScale().set(3);mc.resizeDisplay();}show();
 }
 static CompoundTag stack(String id,int count){var t=new CompoundTag();t.putString("id","minecraft:"+id);t.putInt("count",count);return t;}
 private static void show()throws Exception{
  int state=phase%4;
  var window=new RecipeCatalogWindow(null,null){@Override public void onOpened(){}};
  var data=new CompoundTag();data.putInt("used",state==1?2:1);data.putInt("capacity",2);data.putInt("pages",3);data.putInt("page",0);
  var rows=new ListTag();for(String name:List.of("Oak Slab","Spruce Trapdoor","Dark Oak Pressure Plate","Birch Fence Gate","Jungle Sign","Acacia Boat")){var row=new CompoundTag();row.putString("key",name);row.putString("name",name);row.put("item",stack(name.toLowerCase(Locale.ROOT).replace(' ','_'),1));rows.add(row);}data.put("rows",rows);
  var detail=new CompoundTag();detail.putString("name","Oak Slab");detail.put("output",stack("oak_slab",6));detail.putBoolean("learn",state==0);detail.putString("note",switch(state){case 1->"Recipe limit reached. Upgrade the hut or free a slot.";case 2->"Automatic recipe - Building level 3; requires research.";case 3->"Already learned; manage it in the normal recipe list.";default->"No ingredients required to learn.";});
  var inputs=new ListTag();var input=new CompoundTag();input.put("item",stack("oak_planks",3));input.putInt("options",8);input.putInt("choice",0);inputs.add(input);detail.put("ingredients",inputs);data.put("detail",detail);
  var f=RecipeCatalogWindow.class.getDeclaredField("data");f.setAccessible(true);f.set(window,data);
  window.open();var render=RecipeCatalogWindow.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(window);
  if(window.findPaneByID("learn").isEnabled()!=(state==0))throw new AssertionError("Learn control state");
  if(!window.findPaneByID("manual").isEnabled()||!window.findPaneByID("back").isEnabled())throw new AssertionError("Native fallback/exit unavailable");
  if(state==0){
   var identity=RecipeCatalogWindow.class.getDeclaredField("id");identity.setAccessible(true);
   var reply=RecipeCatalogWindow.class.getDeclaredMethod("reply",dev.colonyhorizons.network.RecipeCatalogNetwork.Reply.class);reply.setAccessible(true);
   reply.invoke(window,new dev.colonyhorizons.network.RecipeCatalogNetwork.Reply((UUID)identity.get(window),0,new CompoundTag(),"Please wait and try again."));
   if(window.findPaneByID("learn").isEnabled())throw new AssertionError("Rejected selection left stale Learn enabled");
   f.set(window,data);data.put("detail",detail);render.invoke(window);
  }
  System.out.println("CATALOG_RENDER_STATE "+phase+" PASS");
 }
}
