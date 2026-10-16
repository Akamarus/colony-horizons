package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class FreeFounderFoodRecoveryTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void existing_food(GameTestHelper h){h.runAfterDelay(60,()->{try{
  var l=h.getLevel();var data=RemoteDevelopmentData.get(l);var r=data.all().getFirst();var owner=RemoteActivityTests.loginAtHome(l,r.owner());RemoteActivityService.tick(l);
  h.runAfterDelay(60,()->{try{
   var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),l.dimension());var c=colony.getCitizenManager().getCivilian(FoundingRoster.member(data,r.id(),FoundingRoster.Role.COURIER).orElseThrow().citizenId());c.updateEntityIfNecessary();var entity=(com.minecolonies.core.entity.citizen.EntityCitizen)c.getEntity().orElseThrow();var warehouse=dev.colonyhorizons.mayor.MayorLogistics.warehouse(l,r.id()).orElseThrow();var inv=dev.colonyhorizons.supply.WarehouseInventory.resolve(l,warehouse.getTileEntity()).inventory();
   var task=((com.minecolonies.core.colony.jobs.JobDeliveryman)c.getJob()).getCurrentTask();var carried=new ArrayList<net.minecraft.world.item.ItemStack>();for(int i=0;i<c.getInventory().getSlots();i++)carried.add(c.getInventory().getStackInSlot(i).copy());double initial=c.getSaturation();var outcome=RemoteCare.food(entity);h.assertTrue(c.getSaturation()>initial,"Separate warehouse portion restores hunger");h.assertTrue(task==((com.minecolonies.core.colony.jobs.JobDeliveryman)c.getJob()).getCurrentTask(),"Delivery task retained");for(int i=0;i<carried.size();i++)h.assertTrue(net.minecraft.world.item.ItemStack.matches(carried.get(i),c.getInventory().getStackInSlot(i)),"Delivery inventory untouched");
   System.out.println("FOOD_DIAGNOSTIC hunger="+c.getSaturation()+" pos="+entity.blockPosition()+" selection="+com.minecolonies.api.util.FoodUtils.checkForFoodInBuilding(c,null,warehouse)+" containers="+warehouse.getContainers()+" care="+outcome);for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(net.minecraft.world.item.Items.BREAD))System.out.println("FOOD_SLOT "+i+" count="+inv.getStackInSlot(i).getCount()+" canEat="+com.minecolonies.api.util.FoodUtils.canEat(inv.getStackInSlot(i),c.getHomeBuilding(),c.getWorkBuilding()));
   h.runAfterDelay(1200,()->{try{h.assertTrue(c.getSaturation()>0,"Native courier consumes existing local food after reload");RemoteActivityTests.connect(owner,false);ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"FreeFounderFoodRecovery",List.of(new ProbeResult("existing_food",ProbeResult.Outcome.PASS,"Existing warehouse food eaten without resource injection")));h.succeed();}catch(Throwable e){FreeFounderGrantTests.fail(h,new ArrayList<>(),e);}});
  }catch(Throwable e){FreeFounderGrantTests.fail(h,new ArrayList<>(),e);}});
 }catch(Throwable e){FreeFounderGrantTests.fail(h,new ArrayList<>(),e);}});}
}
