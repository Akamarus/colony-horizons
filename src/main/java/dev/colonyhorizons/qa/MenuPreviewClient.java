package dev.colonyhorizons.qa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import dev.colonyhorizons.client.supply.*;
import dev.colonyhorizons.network.SupplyMenuPayloads.MenuReply;
import java.util.UUID;
/** Disposable rendering fixture only: no world, connection, native stock or production claims. */
@EventBusSubscriber(modid="colonyhorizons",value=Dist.CLIENT)
public final class MenuPreviewClient {
 private static int ticks,phase;private static boolean started;private static WindowSurveys explorationPreview;
 private static void field(Class<?> c,Object object,String name,Object value)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("colonyhorizons.menuPreview"))return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
  if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
  if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;Class.forName("com.minecolonies.core.client.gui.townhall.WindowMainPage");mc.options.guiScale().set(2);mc.resizeDisplay();if(Boolean.getBoolean("colonyhorizons.menuRegression")){routeNavigationRegression();mc.stop();return;}show(0);}
  if(explorationPreview!=null)field(WindowSurveys.class,explorationPreview,"ticks",0);
  if(++ticks%60!=0)return;
  Screenshot.grab(mc.gameDirectory,"menu-preview-"+phase+".png",mc.getMainRenderTarget(),message->System.out.println("MENU_PREVIEW_SCREENSHOT "+message.getString()));
  if(++phase==38){System.out.println("MENU_PREVIEW_COMPLETE: thirty-eight render states; no world opened");mc.stop();return;}
  if(phase==19){mc.options.guiScale().set(3);mc.resizeDisplay();}show(phase%19);
 }
 private static CompoundTag fixture(){var t=new CompoundTag();t.putString("name","Riverbend");t.putInt("colony",1);var districts=new ListTag();for(String name:new String[]{"Home","Northern Woods"}){var r=new CompoundTag();r.putUUID("id",name.equals("Home")?new UUID(0,0):UUID.randomUUID());r.putString("name",name);r.putString("status","Active");r.putInt("huts",6);districts.add(r);}t.put("districts",districts);
  var huts=new ListTag();for(int i=0;i<16;i++){var r=new CompoundTag();r.putLong("pos",new net.minecraft.core.BlockPos(300+i*16,64,400).asLong());r.putString("name","Forester's Hut "+i);r.putString("district",i<8?"Home":"Northern Woods");r.putBoolean("eligible",true);huts.add(r);}t.put("huts",huts);t.putBoolean("hn",true);
  var routes=new ListTag();var r=new CompoundTag();r.putUUID("id",UUID.randomUUID());r.putString("itemName","Oak Log");r.putString("status","Delivering");r.putString("source","Northern Woods Forester");r.putString("destination","Home Builder");r.putInt("target",64);r.putInt("batch",8);r.putInt("reserve",4);r.putInt("cargo",8);r.putInt("seconds",12);r.putLong("delivered",128);routes.add(r);t.put("routes",routes);
  var draft=new CompoundTag();draft.putInt("huts",16);draft.putInt("chunks",12);draft.put("buildings",huts.copy());t.put("draft",draft);return t;
 }
 private static void show(int screen)throws Exception{
  SupplyMenuClient.receive(new MenuReply(UUID.randomUUID(),0,true,"",fixture()));field(SupplyMenuClient.class,null,"pending",true);field(SupplyMenuClient.class,null,"sentAt",System.currentTimeMillis());
  Object window;
  if(screen>=14){explorationPreview=exploration(screen-14);return;}
  if(screen>=4){
   var data=fixture();var choices=new CompoundTag();var entries=new ListTag();for(int i=0;i<16;i++){var row=new CompoundTag();row.putInt("id",i+1);row.putLong("pos",new net.minecraft.core.BlockPos(1,64,i).asLong());row.putString("name","Available citizen or warehouse "+(i+1));row.putInt("stock",128);entries.add(row);}choices.put("entries",entries);choices.putBoolean("next",true);data.put("remoteChoices",choices);
   var review=new CompoundTag();var costs=new ListTag();for(int i=0;i<20;i++){var cost=new CompoundTag();cost.putString("name","Spruce construction material "+i);cost.putInt("needed",64);cost.putInt("allowance",128);costs.add(cost);}review.put("costs",costs);review.putBoolean("ready",true);data.put("remoteReview",review);
   var mayor=new CompoundTag();var id=UUID.randomUUID();mayor.putUUID("id",id);mayor.putString("name","Northern Forestry");mayor.putString("phase","OPERATING");mayor.putString("mayor","Mayor Rowan");mayor.putString("mode","Ask before expanding");mayor.putInt("cap",5);mayor.putInt("completed",8);mayor.putInt("total",8);mayor.putInt("people",4);mayor.putInt("plannedPeople",4);mayor.putInt("arrivedPeople",4);mayor.putBoolean("courierReady",true);var needs=new ListTag();for(String text:new String[]{"Worker Ash: sick; needs carrot and potato","8 bread for the local foresters","1 stone axe within the approved allowance"}){var row=new CompoundTag();row.putString("text",text);needs.add(row);}mayor.put("needs",needs);data.put("mayor",mayor);field(SupplyMenuClient.class,null,"data",data);
   var upgrades=new ListTag();var hut=new CompoundTag();hut.putUUID("building",UUID.randomUUID());hut.putString("text","Builder at 1024, 64, 0 - review level 2");upgrades.add(hut);mayor.put("upgrades",upgrades);
   var u=new CompoundTag();u.putUUID("id",UUID.randomUUID());u.putUUID("settlement",id);u.putString("title","builder - level 1 to 2");u.putString("details","Hut at 1024, 64, 0. Local builder; existing allowance and Home reserves apply.");u.putString("state","PROPOSED");var uc=new ListTag();for(String text:new String[]{"32 Spruce Planks","16 Spruce Log","8 Oak Fence"}){var row=new CompoundTag();row.putString("text",text);uc.add(row);}u.put("costs",uc);data.put("upgrade",u);
   if(screen==12){var additions=new ListTag();for(String label:new String[]{"Review a new home","Review a new forester hut (staff separately)"}){var row=new CompoundTag();row.putString("text",label);additions.add(row);}mayor.put("expansions",additions);window=new WindowMayor(id);field(WindowMayor.class,window,"expansions",true);}else if(screen==13){u.putBoolean("expansion",true);u.putString("title","New forester hut");u.putString("details","Inside approved land. Native builder; warehouse supplies. No additional workers are recruited.");window=new WindowUpgradeReview();}else if(screen==11)window=new WindowUpgradeReview();else if(screen>=9){window=new WindowMayor(id);if(screen==10)field(WindowMayor.class,window,"upgrades",true);}else{window=new WindowRemoteFounding();field(WindowRemoteFounding.class,window,"requested",true);field(WindowRemoteFounding.class,window,"step",screen==5?4:screen-4);if(screen==5){review.putBoolean("ready",false);review.putString("problem","Needs room for 4 settlers within the colony population limit");}}
   field(SupplyMenuClient.class,null,"pending",false);field(SupplyMenuClient.class,null,"window",window);((com.ldtteam.blockui.views.BOWindow)window).open();return;
  }
  if(screen==0)return;
  if(screen==1){var f=SupplyMenuClient.class.getDeclaredField("window");f.setAccessible(true);window=f.get(null);field(WindowOutpostsSupplies.class,window,"routes",true);}
  else if(screen==2){window=new WindowOutpostSetup();field(WindowOutpostSetup.class,window,"reviewed",true);}
  else{window=new WindowSupplyRouteSetup();field(WindowSupplyRouteSetup.class,window,"sourceName","Home Storehouse");field(WindowSupplyRouteSetup.class,window,"destinationName","Northern Woods Forester");field(WindowSupplyRouteSetup.class,window,"advanced",true);}
  field(SupplyMenuClient.class,null,"window",window);((com.ldtteam.blockui.views.BOWindow)window).open();
 }
 private static WindowSurveys exploration(int state)throws Exception{
  var data=fixture();var survey=new CompoundTag();var rows=new ListTag();var resource=new CompoundTag();resource.putString("name","Mangrove Propagule");resource.putString("target","minecraft:mangrove_propagule");resource.putString("tool","No tool");resource.putBoolean("unlocked",state!=0);resource.putBoolean("gatherable",true);resource.putInt("yield",4);resource.putInt("bread",2);resource.putInt("wear",0);resource.putString("biome","minecraft:mangrove_swamp");rows.add(resource);survey.put("resources",rows);survey.putString("status","Ready. Discover first, then gather from known biomes.");
  if(state==2||state==3){survey.putBoolean("active",true);survey.putString("kind","gathering");survey.putUUID("id",UUID.randomUUID());survey.putLong("revision",1);survey.putInt("remainingTicks",3600);survey.putBoolean("repeat",state==2);survey.putBoolean("returning",state==3);survey.putString("status",state==3?"Make room in the hut; repeat stopped":"Gathering resources");}
  if(state==4){var findings=new ListTag();var old=resource.copy();old.putString("evidence","Collected 1 real sample");old.putString("production","Rediscover this resource to unlock gathering.");findings.add(old);survey.put("findings",findings);resource=old;}
  data.put("survey",survey);field(SupplyMenuClient.class,null,"data",data);field(SupplyMenuClient.class,null,"pending",false);var window=new WindowSurveys();field(WindowSurveys.class,window,"selected",resource);field(WindowSurveys.class,window,"findings",state==4);field(SupplyMenuClient.class,null,"window",window);window.open();return window;
 }
 /** Exercises real BlockUI handlers: returning from a route form must show route rows, not districts. */
 private static void routeNavigationRegression() throws Exception {
  int failures=0;
  for(boolean created:new boolean[]{false,true}){
   SupplyMenuClient.receive(new MenuReply(UUID.randomUUID(),0,true,"",fixture()));
   var form=new WindowSupplyRouteSetup();form.open();
   if(created)form.reply(new MenuReply(UUID.randomUUID(),1,true,"Supply route created",fixture()));
   else form.onButtonClicked(form.findPaneOfTypeByID("back",com.ldtteam.blockui.controls.Button.class));
   var f=SupplyMenuClient.class.getDeclaredField("window");f.setAccessible(true);var shown=f.get(null);
   ((com.ldtteam.blockui.views.BOWindow)shown).onOpened();
   var rowsField=shown.getClass().getSuperclass().getDeclaredField("rows");rowsField.setAccessible(true);
   var rows=(java.util.List<?>)rowsField.get(shown);
   boolean passed=rows.size()==1&&rows.getFirst() instanceof CompoundTag row&&row.getString("itemName").equals("Oak Log");
   System.out.println("MENU_NAVIGATION_"+(created?"CREATED":"BACK")+": "+(passed?"PASS":"FAIL expected route rows after leaving route form; got "+rows));
   if(!passed)failures++;
  }
  show(6);var wf=SupplyMenuClient.class.getDeclaredField("window");wf.setAccessible(true);var founding=(WindowRemoteFounding)wf.get(null);
  founding.findPaneOfTypeByID("y",com.ldtteam.blockui.controls.TextField.class).setText("4160");founding.onButtonClicked(founding.findPaneOfTypeByID("continue",com.ldtteam.blockui.controls.Button.class));
  var step=WindowRemoteFounding.class.getDeclaredField("step");step.setAccessible(true);boolean coordinates=step.getInt(founding)==2;System.out.println("MENU_NAVIGATION_COORDINATES: "+(coordinates?"PASS":"FAIL"));if(!coordinates)failures++;
  founding.onButtonClicked(founding.findPaneOfTypeByID("cap",com.ldtteam.blockui.controls.Button.class));var ff=WindowRemoteFounding.class.getDeclaredField("form");ff.setAccessible(true);boolean cap=((CompoundTag)ff.get(founding)).getInt("cap")==2;System.out.println("MENU_NAVIGATION_LEVEL: "+(cap?"PASS":"FAIL"));if(!cap)failures++;
  show(9);var mayor=(WindowMayor)wf.get(null);mayor.onButtonClicked(mayor.findPaneOfTypeByID("view",com.ldtteam.blockui.controls.Button.class));var bf=WindowMayor.class.getDeclaredField("budget");bf.setAccessible(true);boolean budget=bf.getBoolean(mayor);System.out.println("MENU_NAVIGATION_BUDGET: "+(budget?"PASS":"FAIL"));if(!budget)failures++;
  mayor.onButtonClicked(mayor.findPaneOfTypeByID("view",com.ldtteam.blockui.controls.Button.class));var uf=WindowMayor.class.getDeclaredField("upgrades");uf.setAccessible(true);boolean upgrades=uf.getBoolean(mayor);System.out.println("MENU_NAVIGATION_UPGRADES: "+(upgrades?"PASS":"FAIL"));if(!upgrades)failures++;
  show(11);var upgrade=(WindowUpgradeReview)wf.get(null);upgrade.onButtonClicked(upgrade.findPaneOfTypeByID("back",com.ldtteam.blockui.controls.Button.class));boolean back=wf.get(null) instanceof WindowMayor;System.out.println("MENU_NAVIGATION_UPGRADE_BACK: "+(back?"PASS":"FAIL"));if(!back)failures++;
  String[] names={"EXPLORATION_LOCKED","EXPLORATION_READY","EXPLORATION_RUNNING","EXPLORATION_FULL","EXPLORATION_HISTORY"};
  for(int i=0;i<5;i++){var w=exploration(i);w.onOpened();boolean gather=w.findPaneByID("gather").isEnabled(),start=w.findPaneByID("start").isEnabled(),recall=w.findPaneByID("cancel").isEnabled();boolean passed=switch(i){case 0->start&&!gather&&!recall;case 1->start&&gather&&w.findPaneByID("repeat").isEnabled();case 2->!start&&!gather&&recall;case 3->!start&&!gather&&!recall;default->!start&&!gather;};System.out.println("MENU_NAVIGATION_"+names[i]+": "+(passed?"PASS":"FAIL"));if(!passed)failures++;}
  var feedback=exploration(1);String reason="Wait for the Explorer to reach the hut";
  feedback.reply(new MenuReply(UUID.randomUUID(),1,false,reason,new CompoundTag()));
  feedback.reply(new MenuReply(UUID.randomUUID(),2,true,"",new CompoundTag()));
  feedback.onOpened();
  boolean retained=feedback.findPaneOfTypeByID("message",com.ldtteam.blockui.controls.Text.class).getTextAsString().equals(reason);
  feedback.reply(new MenuReply(UUID.randomUUID(),3,true,"Gathering started",new CompoundTag()));
  feedback.reply(new MenuReply(UUID.randomUUID(),4,true,"",new CompoundTag()));
  boolean cleared=!feedback.findPaneOfTypeByID("message",com.ldtteam.blockui.controls.Text.class).getTextAsString().equals(reason);
  System.out.println("MENU_NAVIGATION_EXPLORATION_FEEDBACK: "+(retained&&cleared?"PASS":"FAIL"));if(!retained||!cleared)failures++;
  System.out.println("MENU_NAVIGATION_RESULT failures="+failures+" cases=13");
 }
}
