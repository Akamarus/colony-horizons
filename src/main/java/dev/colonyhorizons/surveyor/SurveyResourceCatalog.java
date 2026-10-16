// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import java.util.*;
import static dev.colonyhorizons.surveyor.SurveyResource.Tool.*;
import static dev.colonyhorizons.surveyor.SurveyResource.Profile.*;

public final class SurveyResourceCatalog {
 private SurveyResourceCatalog(){}
 private static ResourceLocation id(String name){return ResourceLocation.withDefaultNamespace(name);}
 // Lazy initialization happens after vanilla registries exist, never during addon class loading.
 private static final class Entries {
  static final Map<ResourceLocation,SurveyResource> ALL=build();
  static Map<ResourceLocation,SurveyResource> build(){
   var result=new LinkedHashMap<ResourceLocation,SurveyResource>();
   for(String wood:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry")){
    add(result,wood+"_log",AXE,FORESTRY,wood+"_log");
    add(result,wood.equals("mangrove")?"mangrove_propagule":wood+"_sapling",HAND,FORESTRY,wood.equals("mangrove")?"mangrove_propagule":wood+"_leaves");
   }
   for(String flower:List.of("dandelion","poppy","blue_orchid","allium","azure_bluet","red_tulip","orange_tulip","white_tulip","pink_tulip","oxeye_daisy","cornflower","lily_of_the_valley","sunflower","lilac","rose_bush","peony"))add(result,flower,HAND,FLOWERS,flower);
   add(result,"wheat_seeds",HAND,SEEDS,"short_grass","tall_grass","fern","large_fern");
   // Wild fruit must be processed through its actual vanilla recipe before it becomes seed stock.
   add(result,"pumpkin",HAND,SEEDS,"pumpkin");add(result,"melon_slice",HAND,SEEDS,"melon");
   add(result,"vine",SHEARS,VINES,"vine");add(result,"kelp",HAND,KELP,"kelp","kelp_plant");
   add(result,"seagrass",SHEARS,SEAGRASS,"seagrass","tall_seagrass");
   return Collections.unmodifiableMap(result);
  }
  static void add(Map<ResourceLocation,SurveyResource> map,String name,SurveyResource.Tool tool,SurveyResource.Profile profile,String... blocks){
   var sources=new HashSet<Block>();for(String b:blocks){if(!BuiltInRegistries.BLOCK.containsKey(id(b)))throw new IllegalStateException("Unknown survey block "+b);sources.add(BuiltInRegistries.BLOCK.get(id(b)));}
   if(!BuiltInRegistries.ITEM.containsKey(id(name)))throw new IllegalStateException("Unknown survey item "+name);
   map.put(id(name),new SurveyResource(id(name),sources,tool,profile));
  }
 }
 public static Optional<SurveyResource> find(ResourceLocation item){return Optional.ofNullable(Entries.ALL.get(item));}
 public static List<SurveyResource> all(){return List.copyOf(Entries.ALL.values());}
}
