package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.remote.*;
import com.minecolonies.core.entity.ai.minimal.EntityAISickTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.datalistener.model.Disease;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import java.util.*;
final class RemoteIllnessAssertions {
 static void run(GameTestHelper h,RemoteTestEnvironment env,List<ProbeResult> rows)throws Exception{
  var citizen=env.home.colony().getCitizenManager().getCivilian(env.draft.request().mayorId());var entity=(EntityCitizen)citizen.getEntity().orElseThrow();
  var plan=env.draft.buildings().getFirst();var hut=(com.minecolonies.core.tileentities.TileEntityColonyBuilding)h.getLevel().getBlockEntity(plan.anchor());
  var disease=new Disease(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("colonyhorizons","qa_illness"),net.minecraft.network.chat.Component.literal("Test illness"),1,List.of(new com.minecolonies.api.crafting.ItemStorage(new ItemStack(Items.CARROT))));
  var field=citizen.getCitizenDiseaseHandler().getClass().getDeclaredField("disease");field.setAccessible(true);field.set(citizen.getCitizenDiseaseHandler(),disease);
  hut.getInventory().setStackInSlot(0,new ItemStack(Items.CARROT,2));
  var ai=new EntityAISickTask(entity);var search=EntityAISickTask.class.getDeclaredMethod("searchHospital");search.setAccessible(true);
  entity.setPos(plan.anchor().getX()+15.5,plan.anchor().getY(),plan.anchor().getZ()+0.5);search.invoke(ai);h.assertTrue(count(citizen.getInventory(),Items.CARROT)==0&&count(hut.getInventory(),Items.CARROT)==2,"No medicine teleports from distant local store");pass(rows,"care_requires_local_arrival");
  entity.setPos(plan.anchor().getX()+1.5,plan.anchor().getY(),plan.anchor().getZ()+0.5);h.assertTrue(search.invoke(ai)==EntityAISickTask.DiseaseState.APPLY_CURE,"Sick founder retrieves local cure instead of looping on unavailable Home hospital");h.assertTrue(count(citizen.getInventory(),Items.CARROT)==1&&count(hut.getInventory(),Items.CARROT)==1,"Exactly one finite medicine transferred");pass(rows,"care_finite_pickup");
  search.invoke(ai);h.assertTrue(count(hut.getInventory(),Items.CARROT)==1,"Repeat recovery does not take extra medicine");pass(rows,"care_pickup_idempotent");
  var apply=EntityAISickTask.class.getDeclaredMethod("applyCure");apply.setAccessible(true);for(int i=0;i<400&&citizen.getCitizenDiseaseHandler().isSick();i++)apply.invoke(ai);h.assertTrue(!citizen.getCitizenDiseaseHandler().isSick()&&count(citizen.getInventory(),Items.CARROT)==0,"Native treatment consumes the medicine and cures");pass(rows,"care_native_cure");
  for(int slot=0;slot<citizen.getInventory().getSlots();slot++)if(citizen.getInventory().getStackInSlot(slot).is(Items.BREAD))citizen.getInventory().setStackInSlot(slot,ItemStack.EMPTY);
  hut.getInventory().setStackInSlot(1,new ItemStack(Items.BREAD,8));citizen.setSaturation(0);
  var eating=new com.minecolonies.core.entity.ai.minimal.EntityAIEatTask(entity);var findFood=eating.getClass().getDeclaredMethod("searchRestaurant");findFood.setAccessible(true);
  h.assertTrue(findFood.invoke(eating)==com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState.EAT,"Hungry remote resident can collect local food without a Home restaurant");h.assertTrue(count(citizen.getInventory(),Items.BREAD)+count(hut.getInventory(),Items.BREAD)==8,"Food conserved during recovery");pass(rows,"care_hunger_recovery");
  dev.colonyhorizons.mayor.MayorService.appoint(env.owner,env.draft.id(),citizen.getId());
  var jobless=env.home.colony().getCitizenManager().getCivilian(env.draft.request().citizenIds().getLast());h.assertTrue(jobless.getWorkBuilding()==null,"Unemployed founder fixture");
  field.set(jobless.getCitizenDiseaseHandler(),disease);hut.getInventory().setStackInSlot(0,ItemStack.EMPTY);
  citizen.setPaused(false);RemoteCare.tick(h.getLevel(),env.draft.id());
  h.assertTrue(hut.getBuilding().getOpenRequests(jobless.getId()).stream().anyMatch(q->q.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable d&&d.matches(new ItemStack(Items.CARROT))),"Jobless sick founder has a local native medicine request");pass(rows,"care_jobless_request");
  int requests=hut.getBuilding().getOpenRequests(jobless.getId()).size();RemoteCare.tick(h.getLevel(),env.draft.id());h.assertTrue(hut.getBuilding().getOpenRequests(jobless.getId()).size()==requests,"Repeated care review reuses request");pass(rows,"care_jobless_request_reused");
  var oldRequest=hut.getBuilding().getOpenRequests(jobless.getId()).iterator().next().getId();
  hut.getBuilding().overruleNextOpenRequestOfCitizenWithStack(jobless,new ItemStack(Items.CARROT));hut.getInventory().setStackInSlot(0,new ItemStack(Items.CARROT));
  var joblessEntity=(EntityCitizen)jobless.getEntity().orElseThrow();joblessEntity.setPos(plan.anchor().getX()+1.5,plan.anchor().getY(),plan.anchor().getZ()+0.5);RemoteCare.recover(joblessEntity);
  h.assertTrue(hut.getBuilding().getCompletedRequestsOfCitizenOrBuilding(jobless).stream().noneMatch(q->q.getId().equals(oldRequest)),"Self-care retires fulfilled jobless request");
  var joblessAi=new EntityAISickTask(joblessEntity);for(int i=0;i<400&&jobless.getCitizenDiseaseHandler().isSick();i++)apply.invoke(joblessAi);field.set(jobless.getCitizenDiseaseHandler(),disease);RemoteCare.tick(h.getLevel(),env.draft.id());
  h.assertTrue(hut.getBuilding().getOpenRequests(jobless.getId()).stream().anyMatch(q->!q.getId().equals(oldRequest)&&q.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable d&&d.matches(new ItemStack(Items.CARROT))),"Next illness gets a fresh request after exhausted medicine");pass(rows,"care_jobless_second_illness");
  hut.getInventory().setStackInSlot(0,new ItemStack(Items.CARROT));
  field.set(citizen.getCitizenDiseaseHandler(),disease);var data=RemoteDevelopmentData.get(h.getLevel());var r=data.find(env.draft.id()).orElseThrow();data.transition(r.id(),r.revision(),RemoteModels.Phase.PAUSED);search.invoke(ai);h.assertTrue(count(hut.getInventory(),Items.CARROT)==1,"Paused outpost cannot withdraw medicine");pass(rows,"care_pause_guard");
 }
 static int count(net.neoforged.neoforge.items.IItemHandler inventory,Item item){int n=0;for(int i=0;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).is(item))n+=inventory.getStackInSlot(i).getCount();return n;}
 static void pass(List<ProbeResult> rows,String id){rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native illness, finite local medicine and no Home path"));}
}
