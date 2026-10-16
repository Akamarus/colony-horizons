package dev.colonyhorizons.recipes;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.crafting.*;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.crafting.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Canonical recipes are resolved on the server; clients never provide recipe outputs or stacks. */
public final class RecipeCatalog {
 private RecipeCatalog(){}
 public record Entry(String key,IGenericRecipe recipe,String restriction,boolean teachable){}
 private record Cached(Object manager,Map<com.minecolonies.api.crafting.registry.CraftingType,List<IGenericRecipe>> recipes){}
 private static final Map<ServerLevel,Cached> CACHE=new WeakHashMap<>();
 public static void clear(){CACHE.clear();}
 public static AbstractCraftingBuildingModule require(ServerPlayer player,int colonyId,BlockPos hut,int moduleId){
  if(!RecipeCatalogConfig.ENABLED.get())throw new IllegalArgumentException("Recipe catalog is disabled by the server.");
  if(!player.serverLevel().hasChunkAt(hut)||!player.blockPosition().closerThan(hut,16))throw new IllegalArgumentException("Open the catalog near the hut.");
  var colony=IColonyManager.getInstance().getColonyByDimension(colonyId,player.level().dimension());
  if(colony==null||!colony.getPermissions().hasPermission(player,Action.MANAGE_HUTS))throw new IllegalArgumentException("You cannot manage this hut.");
  var building=colony.getServerBuildingManager().getBuilding(hut);
  if(building==null||building.getBuildingLevel()<1||building.getTileEntity()==null||!(building.getModule(moduleId) instanceof AbstractCraftingBuildingModule module))throw new IllegalArgumentException("This crafting hut is unavailable.");
  return module;
 }
 public static List<Entry> entries(ServerLevel level,AbstractCraftingBuildingModule module){
  var cached=CACHE.get(level);
  if(cached==null||cached.manager()!=level.getRecipeManager()){cached=new Cached(level.getRecipeManager(),RecipeAnalyzer.buildVanillaRecipesMap(level.getRecipeManager(),level));CACHE.put(level,cached);}
  var customs=CustomRecipeManager.getInstance().getRecipes(module.getCustomRecipeKey());
  var result=new LinkedHashMap<String,Entry>();
  for(var recipe:RecipeAnalyzer.findRecipes(cached.recipes(),module,level)){
   if(recipe.getPrimaryOutput().isEmpty())continue;
   boolean automatic=customs.stream().anyMatch(c->Objects.equals(c.getRecipeStorage().getRecipeSource(),recipe.getRecipeId())&&recipe.getRecipeId()!=null);
   String restrictions=String.join("; ",recipe.getRestrictions().get().stream().map(c->c.getString()).toList());
   boolean teachable=!automatic&&recipe.getLootTable()==null&&recipe.getRequiredEntity()==null&&recipe.getInputs().size()<=9
    &&!recipe.getInputs().isEmpty()&&recipe.getInputs().stream().allMatch(s->s.isEmpty()||s.stream().anyMatch(i->!i.isEmpty()))
    &&recipe.getRecipeId()!=null&&level.getRecipeManager().byKey(recipe.getRecipeId()).map(h->h.value() instanceof CraftingRecipe&&h.value().canCraftInDimensions(3,3)||h.value() instanceof SmeltingRecipe).orElse(false)
    &&!(module instanceof AbstractCraftingBuildingModule.Domum);
   String key=String.valueOf(recipe.getRecipeId())+"/"+Integer.toUnsignedString(Objects.hash(recipe.getPrimaryOutput().toString(),recipe.getInputs().toString(),recipe.getGridSize()),16);
   result.putIfAbsent(key,new Entry(key,recipe,automatic?"Automatic recipe"+(restrictions.isEmpty()?"":" - "+restrictions):teachable?restrictions:"Special recipe; use its native teaching controls",teachable));
  }
  return result.values().stream().sorted(Comparator.comparing((Entry e)->e.recipe().getPrimaryOutput().getHoverName().getString()).thenComparing(Entry::key)).toList();
 }
 public static IRecipeStorage storage(ServerLevel level,Entry entry,int[] choices){
  if(!entry.teachable())throw new IllegalArgumentException(entry.restriction());
  var recipe=entry.recipe();var inputs=new ArrayList<ItemStorage>();
  if(choices.length>recipe.getInputs().size())throw new IllegalArgumentException("Invalid ingredient choices.");
  for(int i=0;i<recipe.getInputs().size();i++){
   var options=recipe.getInputs().get(i);if(options.isEmpty())continue;
   int index=i<choices.length?choices[i]:0;
   if(index<0||index>=options.size()||options.get(index).isEmpty())throw new IllegalArgumentException("Invalid ingredient choice.");
   inputs.add(new ItemStorage(options.get(index).copy()));
  }
  var holder=level.getRecipeManager().byKey(recipe.getRecipeId()).orElseThrow(()->new IllegalArgumentException("Recipe changed; refresh the catalog."));
  ItemStack output;List<ItemStack> remainders;
  if(holder.value() instanceof CraftingRecipe canonical){
   int width=canonical instanceof ShapedRecipe shaped?shaped.getWidth():3;
   int height=canonical instanceof ShapedRecipe shaped?shaped.getHeight():3;
   var grid=new ArrayList<ItemStack>(Collections.nCopies(width*height,ItemStack.EMPTY));
   var selected=new ArrayList<>(inputs.stream().map(i->i.getItemStack().copy()).toList());
   // MineColonies omits its reusable build tool from taught recipe costs.
   var buildTool=com.ldtteam.structurize.items.ModItems.buildTool.get();
   for(var ingredient:canonical.getIngredients())if(ingredient.getItems().length>0&&ingredient.getItems()[0].is(buildTool))selected.add(new ItemStack(buildTool));
   if(!arrange(canonical.getIngredients(),0,selected,grid,new int[]{20000}))throw new IllegalArgumentException("These ingredients do not form this recipe: "+recipe.getRecipeId());
   var input=CraftingInput.of(width,height,grid);
   if(!canonical.matches(input,level))throw new IllegalArgumentException("These ingredients do not form this recipe: "+recipe.getRecipeId());
   output=canonical.assemble(input,level.registryAccess());remainders=canonical.getRemainingItems(input);
  }else if(holder.value() instanceof SmeltingRecipe canonical){
   if(inputs.size()!=1||inputs.getFirst().getAmount()!=1)throw new IllegalArgumentException("Unsupported furnace ingredients.");
   var input=new SingleRecipeInput(inputs.getFirst().getItemStack().copy());
   if(!canonical.matches(input,level))throw new IllegalArgumentException("Invalid furnace ingredient.");
   output=canonical.assemble(input,level.registryAccess());remainders=canonical.getRemainingItems(input);
  }else throw new IllegalArgumentException("Use native teaching for this special recipe.");
  if(output.isEmpty())throw new IllegalArgumentException("This selection has no crafting result.");
  // Manual recipes count towards the native capacity; automatic source IDs are deliberately absent.
  return RecipeStorage.builder().withInputs(inputs).withPrimaryOutput(output)
   .withSecondaryOutputs(remainders.stream().filter(i->!i.isEmpty()&&!i.is(com.ldtteam.structurize.items.ModItems.buildTool.get())).map(ItemStack::copy).toList()).withGridSize(recipe.getGridSize())
   .withIntermediate(recipe.getIntermediate()).withRequiredTool(recipe.getRequiredTool()).build();
 }
 private static boolean arrange(List<Ingredient> ingredients,int slot,List<ItemStack> remaining,List<ItemStack> grid,int[] budget){
  if(--budget[0]<0)return false;
  if(slot==ingredients.size())return remaining.stream().allMatch(ItemStack::isEmpty);
  var ingredient=ingredients.get(slot);
  if(ingredient.isEmpty())return arrange(ingredients,slot+1,remaining,grid,budget);
  for(var stack:remaining)if(!stack.isEmpty()&&ingredient.test(stack)){
   var unit=stack.copyWithCount(1);stack.shrink(1);grid.set(slot,unit);
   if(arrange(ingredients,slot+1,remaining,grid,budget))return true;
   stack.grow(1);grid.set(slot,ItemStack.EMPTY);
  }
  return false;
 }

 public static boolean known(AbstractCraftingBuildingModule module,IRecipeStorage storage){
  var manager=IColonyManager.getInstance().getRecipeManager();return module.getRecipes().stream().map(manager.getRecipes()::get).anyMatch(storage::equals);
 }
 public static void learn(ServerLevel level,AbstractCraftingBuildingModule module,String key,int[] choices){
  if(!RecipeCatalogConfig.ENABLED.get())throw new IllegalArgumentException("Recipe catalog is disabled by the server.");
  var entry=entries(level,module).stream().filter(e->e.key().equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("Recipe changed; refresh the catalog."));
  var storage=storage(level,entry,choices);
  if(known(module,storage))throw new IllegalArgumentException("Already learned. Use the hut recipe list to enable it if disabled.");
  if(!module.isRecipeCompatible(GenericRecipe.builder(storage).build()))throw new IllegalArgumentException("This hut cannot learn that recipe.");
  var token=IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
  if(!module.addRecipe(token))throw new IllegalArgumentException("Recipe limit reached or recipe locked. Upgrade the hut or free a recipe slot.");
 }
}

