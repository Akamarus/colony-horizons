package dev.colonyhorizons.qa;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;

import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.core.colony.crafting.RecipeAnalyzer;
import com.minecolonies.core.colony.crafting.GenericRecipeUtils;
import com.minecolonies.core.colony.buildings.modules.SimpleCraftingModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.Comparator;

/** Read-only registry/recipe export in a fresh disposable test world. */
@PrefixGameTestTemplate(false)
public final class ResourceAudit {
    private static JsonArray storageStacks(Iterable<ItemStorage> values, RegistryOps<JsonElement> ops){
        var rows=new JsonArray();
        for(var value:values){var stack=value.getItemStack();if(!stack.isEmpty())rows.add(ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());}
        return rows;
    }
    private static JsonArray stacks(Iterable<ItemStack> values, RegistryOps<JsonElement> ops){
        var rows=new JsonArray();for(var stack:values)if(!stack.isEmpty())rows.add(ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());return rows;
    }
    private static String typeName(CraftingType type){
        if(type.equals(ModCraftingTypes.SMALL_CRAFTING.get()))return "small_crafting";
        if(type.equals(ModCraftingTypes.LARGE_CRAFTING.get()))return "large_crafting";
        if(type.equals(ModCraftingTypes.SMELTING.get()))return "smelting";
        if(type.equals(ModCraftingTypes.BREWING.get()))return "brewing";
        if(type.equals(ModCraftingTypes.ARCHITECTS_CUTTER.get()))return "architects_cutter";
        return type.getClass().getName();
    }
    @GameTest(template="empty", templateNamespace="colonyhorizons", timeoutTicks=200)
    public static void exportProductionInputs(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();var root=new JsonObject();var items=new JsonArray();
        for(var item:BuiltInRegistries.ITEM){
            var row=new JsonObject();row.addProperty("id",BuiltInRegistries.ITEM.getKey(item).toString());row.addProperty("class",item.getClass().getName());row.addProperty("enabled",item.isEnabled(level.enabledFeatures()));
            var tags=new JsonArray();item.builtInRegistryHolder().tags().map(t->t.location().toString()).sorted().forEach(tags::add);row.add("tags",tags);items.add(row);
        }
        root.add("items",items);var recipes=new JsonArray();var ops=RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
        for(var holder:level.getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(h->h.id().toString())).toList()){
            var recipe=holder.value();var row=new JsonObject();row.addProperty("id",holder.id().toString());row.addProperty("type",BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString());row.addProperty("serializer",BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString());row.addProperty("class",recipe.getClass().getName());row.addProperty("special",recipe.isSpecial());
            var result=recipe.getResultItem(level.registryAccess());row.addProperty("result",BuiltInRegistries.ITEM.getKey(result.getItem()).toString());row.addProperty("count",result.getCount());
            if(!result.isEmpty())row.add("result_stack",ItemStack.CODEC.encodeStart(ops,result).getOrThrow());
            var ingredients=new JsonArray();for(var ingredient:recipe.getIngredients()){
                var alternatives=new JsonArray();for(var stack:ingredient.getItems())if(!stack.isEmpty())alternatives.add(ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());ingredients.add(alternatives);
            }
            row.add("ingredients",ingredients);recipes.add(row);
        }
        var compatibility=IMinecoloniesAPI.getInstance().getColonyManager().getCompatibilityManager();
        var compatibilityLists=new JsonObject();
        compatibilityLists.add("saplings",storageStacks(compatibility.getCopyOfSaplings(),ops));
        compatibilityLists.add("plantables",storageStacks(compatibility.getCopyOfPlantables(),ops));
        compatibilityLists.add("flowers",storageStacks(compatibility.getImmutableFlowers(),ops));
        compatibilityLists.add("smeltable_ores",storageStacks(compatibility.getSmeltableOres(),ops));
        compatibilityLists.add("compost_inputs",storageStacks(compatibility.getCompostInputs(),ops));
        compatibilityLists.addProperty("caveat","Compatibility classifications only, not independently sufficient evidence of autonomous acquisition. Plantables and saplings require world/site/seed prerequisites; smeltable ores require acquisition and fuel; compost inputs are consumed.");
        root.add("compatibility_lists",compatibilityLists);
        // Execute the same potential-teachability predicates as MineColonies RecipeAnalyzer.
        // This intentionally does not require a real colony or mutate learned recipes.
        var nativeRecipes=RecipeAnalyzer.buildVanillaRecipesMap(level.getRecipeManager(),level);
        var modules=new JsonArray();var candidates=new JsonArray();var errors=new JsonArray();var inherent=new JsonArray();var inherentErrors=new JsonArray();
        for(var key:BuildingEntry.getALlModuleProducers().keySet().stream().sorted().toList()){
            var module=BuildingEntry.produceModuleWithoutBuilding(key);
            if(!(module instanceof ICraftingBuildingModule crafting))continue;
            var moduleRow=new JsonObject();moduleRow.addProperty("module",key);moduleRow.addProperty("custom_recipe_key",crafting.getCustomRecipeKey());
            boolean privateOnly=module instanceof SimpleCraftingModule;
            moduleRow.addProperty("scope",privateOnly?"private_self_use":"public_worker_candidate");
            var supported=new JsonArray();for(var type:crafting.getSupportedCraftingTypes())supported.add(typeName(type));moduleRow.add("supported_types",supported);modules.add(moduleRow);
            try{
                // These are the same illustrative inherent routes used by RecipeAnalyzer/JEI.
                // Empty primary outputs and secondary display samples are not guaranteed yields.
                for(var recipe:crafting.getAdditionalRecipesForDisplayPurposesOnly(level)){
                    try{
                        var row=new JsonObject();row.addProperty("module",key);row.addProperty("custom_recipe_key",crafting.getCustomRecipeKey());row.addProperty("route_kind","inherent_ai_display_only");
                        if(recipe.getRecipeId()!=null)row.addProperty("recipe_id",recipe.getRecipeId().toString());
                        row.addProperty("result",BuiltInRegistries.ITEM.getKey(recipe.getPrimaryOutput().getItem()).toString());
                        if(!recipe.getPrimaryOutput().isEmpty())row.add("result_stack",ItemStack.CODEC.encodeStart(ops,recipe.getPrimaryOutput()).getOrThrow());
                        row.add("multi_outputs",stacks(recipe.getAllMultiOutputs(),ops));row.add("additional_outputs",stacks(recipe.getAdditionalOutputs(),ops));
                        var inputs=new JsonArray();for(var slot:recipe.getInputs())inputs.add(stacks(slot,ops));row.add("inputs",inputs);
                        row.addProperty("intermediate",BuiltInRegistries.BLOCK.getKey(recipe.getIntermediate()).toString());
                        if(recipe.getLootTable()!=null)row.addProperty("loot_table",recipe.getLootTable().location().toString());
                        if(recipe.getRequiredTool()!=null)row.addProperty("required_tool",recipe.getRequiredTool().getRegistryName().toString());
                        if(recipe.getRequiredEntity()!=null)row.addProperty("required_entity",BuiltInRegistries.ENTITY_TYPE.getKey(recipe.getRequiredEntity()).toString());
                        row.addProperty("level_sort",recipe.getLevelSort());
                        // Some restriction suppliers access client-only biome-name UI. Do not invoke on a server.
                        row.addProperty("restriction_note","Client tooltip suppliers omitted; inspect gathering map for biome/level/research constraints.");
                        inherent.add(row);
                    }catch(Exception ex){var error=new JsonObject();error.addProperty("module",key);error.addProperty("stage","serialize_inherent_recipe");error.addProperty("error",ex.toString());inherentErrors.add(error);}
                }
            }catch(Exception ex){var error=new JsonObject();error.addProperty("module",key);error.addProperty("stage","discover_inherent_recipes");error.addProperty("error",ex.toString());inherentErrors.add(error);}
            for(var entry:nativeRecipes.entrySet()){
                if(!crafting.canLearn(entry.getKey()))continue;
                for(var recipe:entry.getValue()){
                    try{
                        var safe=GenericRecipeUtils.filterInputs(recipe,crafting.getIngredientValidator());
                        if(!crafting.isRecipeCompatible(safe))continue;
                        var row=new JsonObject();row.addProperty("module",key);row.addProperty("custom_recipe_key",crafting.getCustomRecipeKey());row.addProperty("scope",privateOnly?"private_self_use":"public_worker_candidate");row.addProperty("crafting_type",typeName(entry.getKey()));
                        if(safe.getRecipeId()!=null)row.addProperty("recipe_id",safe.getRecipeId().toString());
                        row.addProperty("result",BuiltInRegistries.ITEM.getKey(safe.getPrimaryOutput().getItem()).toString());
                        if(!safe.getPrimaryOutput().isEmpty())row.add("result_stack",ItemStack.CODEC.encodeStart(ops,safe.getPrimaryOutput()).getOrThrow());
                        var inputs=new JsonArray();for(var slot:safe.getInputs()){
                            var alternatives=new JsonArray();for(var stack:slot)if(!stack.isEmpty())alternatives.add(ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());inputs.add(alternatives);
                        }
                        row.add("inputs",inputs);candidates.add(row);
                    }catch(Exception ex){
                        var error=new JsonObject();error.addProperty("module",key);error.addProperty("recipe",String.valueOf(recipe.getRecipeId()));error.addProperty("error",ex.toString());errors.add(error);
                    }
                }
            }
        }
        root.add("inherent_ai_display_recipes",inherent);root.add("inherent_ai_errors",inherentErrors);
        root.addProperty("inherent_ai_caveat","Native display-only descriptions, not executed production tests or guarantees. Loot-table routes require separate loot analysis; additional outputs may be illustrative samples when primary output is empty. Preconditions and acquisition chains are not proved by this export.");
        root.add("crafting_modules",modules);root.add("teachable_candidates",candidates);root.add("candidate_errors",errors);
        root.addProperty("candidate_caveat","Potential compatibility only: no colony level, research, learned capacity, ingredients or autonomous acquisition asserted. Private modules only craft for themselves. Native discovery omits special recipes, empty declared outputs and conversion failures: special crafting may still be taught from a concrete assembled grid. Custom recipes and inherent AI routes are separate from this candidate export. Brewing and DO outputs may differ by components.");
        root.add("recipes",recipes);root.addProperty("minecraft","1.21.1");root.addProperty("minecolonies","1.1.1403-1.21.1");
        var output=Path.of(System.getProperty("colonyhorizons.auditOutput"));Files.createDirectories(output);Files.writeString(output.resolve("runtime-registry.json"),new GsonBuilder().setPrettyPrinting().create().toJson(root));
        System.out.println("RESOURCE_AUDIT_EXPORTED items="+items.size()+" recipes="+recipes.size()+" candidates="+candidates.size()+" candidate_errors="+errors.size()+" inherent="+inherent.size()+" inherent_errors="+inherentErrors.size());if(!errors.isEmpty() || !inherentErrors.isEmpty())throw new IllegalStateException("Audit export contains unresolved runtime errors; see JSON");
        helper.succeed();
    }
}

