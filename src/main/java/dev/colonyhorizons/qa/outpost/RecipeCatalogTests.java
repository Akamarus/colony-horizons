package dev.colonyhorizons.qa.outpost;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

@PrefixGameTestTemplate(false)
public final class RecipeCatalogTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void catalog(GameTestHelper h){
  for(int x=28;x<=35;x++)for(int z=-2;z<=3;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();
   SupplyTests.run(rows,"native_recipe_catalog",()->{
    try {
     var profile=new GameProfile(UUID.randomUUID(),"CatalogProbe");
     var fixture=OutpostProbeFixture.create(h.getLevel(),new BlockPos(480,16,0),profile.getId());
     var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=fixture;var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),profile);setup.owner=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());setup.owner.connection=fake.connection;
     var hut=setup.place("craftsmanship/carpentry/sawmill1.blueprint",new BlockPos(512,16,0),true);
     var module=hut.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule.class);
     var entries=dev.colonyhorizons.recipes.RecipeCatalog.entries(h.getLevel(),module);SupplyTests.check(!entries.isEmpty());
     var candidates=entries.stream().filter(e->e.teachable()&&!dev.colonyhorizons.recipes.RecipeCatalog.known(module,dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),e,new int[0]))).toList();SupplyTests.check(candidates.size()>10);
     SupplyTests.run(rows,"canonical_result_and_remainders",()->{
      var holder=h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cake")).orElseThrow();
      var generic=com.minecolonies.api.crafting.GenericRecipe.of(holder,h.getLevel());
      var altered=com.minecolonies.api.crafting.GenericRecipe.builder(generic).withOutput(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)).withAdditionalOutputs(List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD))).build();
      var storage=dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),new dev.colonyhorizons.recipes.RecipeCatalog.Entry("test",altered,"",true),new int[0]);
      SupplyTests.check(storage.getPrimaryOutput().is(net.minecraft.world.item.Items.CAKE));
      SupplyTests.check(storage.getSecondaryOutputs().stream().allMatch(i->i.is(net.minecraft.world.item.Items.BUCKET))&&storage.getSecondaryOutputs().stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum()==3);
      var invalid=com.minecolonies.api.crafting.GenericRecipe.builder(generic).withInputs(List.of(List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)))).build();
      reject(()->dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),new dev.colonyhorizons.recipes.RecipeCatalog.Entry("invalid",invalid,"",true),new int[0]));
     });
     SupplyTests.run(rows,"ingredient_variant_and_profession",()->{
      var holder=h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick")).orElseThrow();var generic=com.minecolonies.api.crafting.GenericRecipe.of(holder,h.getLevel());
      var options=generic.getInputs();int slot=java.util.stream.IntStream.range(0,options.size()).filter(i->options.get(i).size()>1).findFirst().orElseThrow();int[] choices=new int[options.size()];choices[slot]=1;
      var storage=dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),new dev.colonyhorizons.recipes.RecipeCatalog.Entry("variant",generic,"",true),choices);
      SupplyTests.check(storage.getPrimaryOutput().is(net.minecraft.world.item.Items.STICK)&&storage.getPrimaryOutput().getCount()==4);
      SupplyTests.check(storage.getCleanedInput().stream().anyMatch(i->net.minecraft.world.item.ItemStack.isSameItemSameComponents(i.getItemStack(),options.get(slot).get(1))));
      SupplyTests.check(entries.stream().noneMatch(e->e.recipe().getPrimaryOutput().is(net.minecraft.world.item.Items.DIAMOND_PICKAXE)));
     });
     SupplyTests.run(rows,"manually_taught_duplicate_rejected",()->{
      var entry=candidates.stream().filter(e->e.recipe().getGridSize()==2).findFirst().orElseThrow();
      var catalog=dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),entry,new int[0]);
      var manual=com.minecolonies.api.crafting.RecipeStorage.builder(catalog).withGridSize(3).build();
      var token=com.minecolonies.api.colony.IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(manual);
      SupplyTests.check(module.addRecipe(token));
      try{
       int count=module.getRecipes().size();
       SupplyTests.check(dev.colonyhorizons.recipes.RecipeCatalog.known(module,catalog));
       reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,entry.key(),new int[0]));
       SupplyTests.check(module.getRecipes().size()==count);
       module.toggle(module.getRecipes().indexOf(token));
       SupplyTests.check(dev.colonyhorizons.recipes.RecipeCatalog.known(module,catalog));
       reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,entry.key(),new int[0]));
       var differentYield=com.minecolonies.api.crafting.RecipeStorage.builder(catalog).withPrimaryOutput(catalog.getPrimaryOutput().copyWithCount(catalog.getPrimaryOutput().getCount()+1)).build();
       SupplyTests.check(!dev.colonyhorizons.recipes.RecipeCatalog.known(module,differentYield));
       var named=catalog.getPrimaryOutput().copy();named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Distinct"));
       SupplyTests.check(!dev.colonyhorizons.recipes.RecipeCatalog.known(module,com.minecolonies.api.crafting.RecipeStorage.builder(catalog).withPrimaryOutput(named).build()));

      }finally{module.removeRecipe(token);}
     });
     SupplyTests.run(rows,"manual_input_order_and_variants",()->{
      var entry=candidates.stream().filter(e->dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),e,new int[0]).getCleanedInput().size()>1).findFirst().orElseThrow();
      var catalog=dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),entry,new int[0]);
      var inputs=new ArrayList<>(catalog.getCleanedInput());Collections.reverse(inputs);
      var manual=com.minecolonies.api.crafting.RecipeStorage.builder(catalog).withInputs(inputs).withGridSize(3).build();
      var token=com.minecolonies.api.colony.IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(manual);
      SupplyTests.check(module.addRecipe(token));
      try{
       SupplyTests.check(dev.colonyhorizons.recipes.RecipeCatalog.known(module,catalog));
       inputs.set(0,new com.minecolonies.api.crafting.ItemStorage(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,inputs.getFirst().getAmount())));
       SupplyTests.check(!dev.colonyhorizons.recipes.RecipeCatalog.known(module,com.minecolonies.api.crafting.RecipeStorage.builder(catalog).withInputs(inputs).build()));
      }finally{module.removeRecipe(token);}
     });
     var first=candidates.getFirst();int before=((dev.colonyhorizons.compat.mixin.RecipeCapacityAccess)module).colonyHorizons$used();
     SupplyTests.run(rows,"inputless_native_teaching",()->{SupplyTests.check(setup.owner.getInventory().isEmpty());dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,first.key(),new int[0]);SupplyTests.check(setup.owner.getInventory().isEmpty());SupplyTests.check(((dev.colonyhorizons.compat.mixin.RecipeCapacityAccess)module).colonyHorizons$used()==before+1);});
     SupplyTests.run(rows,"duplicate_and_forged_rejected",()->{reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,first.key(),new int[0]));reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,"forged:diamond",new int[0]));});
     SupplyTests.run(rows,"invalid_ingredient_rejected",()->reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,candidates.get(1).key(),new int[]{Integer.MAX_VALUE})));
     SupplyTests.run(rows,"native_capacity_enforced",()->{var access=(dev.colonyhorizons.compat.mixin.RecipeCapacityAccess)module;for(var e:candidates){if(access.colonyHorizons$used()>=access.colonyHorizons$capacity())break;if(!dev.colonyhorizons.recipes.RecipeCatalog.known(module,dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),e,new int[0])))dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,e.key(),new int[0]);}SupplyTests.check(access.colonyHorizons$used()==access.colonyHorizons$capacity());var e=candidates.stream().filter(c->!dev.colonyhorizons.recipes.RecipeCatalog.known(module,dev.colonyhorizons.recipes.RecipeCatalog.storage(h.getLevel(),c,new int[0]))).findFirst().orElseThrow();reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,e.key(),new int[0]));});
     var forge=setup.place("craftsmanship/metallurgy/blacksmith1.blueprint",new BlockPos(544,16,0),true);
     var smith=forge.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule.class);
     SupplyTests.run(rows,"automatic_recipes_read_only",()->{var automatic=dev.colonyhorizons.recipes.RecipeCatalog.entries(h.getLevel(),smith).stream().filter(e->e.restriction().startsWith("Automatic recipe")).toList();SupplyTests.check(!automatic.isEmpty());for(var e:automatic){SupplyTests.check(!e.teachable());reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),smith,e.key(),new int[0]));}});
     SupplyTests.run(rows,"recipe_save_round_trip",()->{var saved=new net.minecraft.nbt.CompoundTag();var tokens=List.copyOf(module.getRecipes());module.serializeNBT(h.getLevel().registryAccess(),saved);module.deserializeNBT(h.getLevel().registryAccess(),saved);SupplyTests.check(tokens.equals(module.getRecipes()));});
     SupplyTests.run(rows,"config_disabled",()->{try{dev.colonyhorizons.recipes.RecipeCatalogConfig.ENABLED.set(false);reject(()->dev.colonyhorizons.recipes.RecipeCatalog.learn(h.getLevel(),module,first.key(),new int[0]));}finally{dev.colonyhorizons.recipes.RecipeCatalogConfig.ENABLED.set(true);}});
     SupplyTests.run(rows,"bounded_protocol_and_throttle",()->{
      var request=new dev.colonyhorizons.network.RecipeCatalogNetwork.Request(UUID.randomUUID(),1,fixture.colony().getID(),hut.getPosition(),module.getProducer().getRuntimeID(),0,"oak","",new int[0],false);
      var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{dev.colonyhorizons.network.RecipeCatalogNetwork.Request.CODEC.encode(buffer,request);var read=dev.colonyhorizons.network.RecipeCatalogNetwork.Request.CODEC.decode(buffer);SupplyTests.check(read.window().equals(request.window())&&read.query().equals("oak"));}finally{buffer.release();}
      reject(()->new dev.colonyhorizons.network.RecipeCatalogNetwork.Request(UUID.randomUUID(),0,0,net.minecraft.core.BlockPos.ZERO,0,0,"x".repeat(65),"",new int[0],false));
      setup.owner.setPos(512.5,16,0.5);var response=dev.colonyhorizons.network.RecipeCatalogNetwork.handle(setup.owner,request);SupplyTests.check(!response.data().isEmpty());
      var throttle=dev.colonyhorizons.network.RecipeCatalogNetwork.handle(setup.owner,request);SupplyTests.check(throttle.data().isEmpty()&&throttle.message().contains("wait"));
     });
     SupplyTests.run(rows,"permission_and_distance",()->{setup.owner.setPos(512.5,16,0.5);SupplyTests.check(dev.colonyhorizons.recipes.RecipeCatalog.require(setup.owner,fixture.colony().getID(),hut.getPosition(),module.getProducer().getRuntimeID())==module);var foreign=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"Visitor"),net.minecraft.server.level.ClientInformation.createDefault());foreign.setPos(512.5,16,0.5);reject(()->dev.colonyhorizons.recipes.RecipeCatalog.require(foreign,fixture.colony().getID(),hut.getPosition(),module.getProducer().getRuntimeID()));setup.owner.setPos(800,16,0);reject(()->dev.colonyhorizons.recipes.RecipeCatalog.require(setup.owner,fixture.colony().getID(),hut.getPosition(),module.getProducer().getRuntimeID()));});
    }catch(Exception e){throw new IllegalStateException("Recipe catalog behavior unavailable or incorrect: "+e,e);}
   });
   try{ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"RecipeCatalog",rows);}catch(Exception e){throw new RuntimeException(e);}
   h.assertTrue(rows.stream().allMatch(r->r.outcome()==ProbeResult.Outcome.PASS),rows.toString());h.succeed();
  });
 }
 private static void reject(Runnable r){try{r.run();throw new AssertionError("Forbidden catalog action accepted");}catch(IllegalArgumentException expected){}}
}
