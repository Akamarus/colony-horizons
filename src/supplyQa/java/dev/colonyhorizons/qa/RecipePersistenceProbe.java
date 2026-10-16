package dev.colonyhorizons.qa;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import dev.colonyhorizons.recipes.RecipeCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
/** Destructive fixture preparation is restricted to disposable copied worlds; never included in addon. */
@EventBusSubscriber(modid="colonyhorizons_qa")
public final class RecipePersistenceProbe {
 private static int ticks;private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(!Boolean.getBoolean("colonyhorizons.recipeRestart")||done)return;
  var server=event.getServer();var level=server.overworld();
  try{
   var root=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
   if(!root.toString().contains("recipe-copy"))throw new IllegalStateException("Disposable recipe-copy required");
   if(++ticks==1)for(int x=-17;x<=-13;x++)for(int z=-32;z<=-27;z++)level.setChunkForced(x,z,true);
   if(ticks<100)return;
   var colony=IColonyManager.getInstance().getColonyByDimension(1,level.dimension());
   if(colony==null)throw new IllegalStateException("Missing copied colony");
   var file=root.resolve("recipe-expected.dat");
   if(!java.nio.file.Files.exists(file)){
    var hut=colony.getServerBuildingManager().getBuildings().values().stream().filter(b->b instanceof com.minecolonies.core.colony.buildings.workerbuildings.BuildingSawmill&&b.getBuildingLevel()>0).findFirst().orElseThrow();
    var module=hut.getFirstModuleOccurance(AbstractCraftingBuildingModule.class);
    for(var token:List.copyOf(module.getRecipes()))module.removeRecipe(token);
    var entries=RecipeCatalog.entries(level,module).stream().filter(e->e.teachable()&&e.recipe().getGridSize()==2&&e.recipe().getInputs().stream().allMatch(options->options.size()==1)).toList();
    var manual=entries.get(0);var catalog=entries.stream().filter(e->!e.recipe().getPrimaryOutput().is(manual.recipe().getPrimaryOutput().getItem())).findFirst().orElseThrow();
    var storage=RecipeStorage.builder(RecipeCatalog.storage(level,manual,new int[0])).withGridSize(3).build();
    var token=IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
    check(module.addRecipe(token),"Manual teaching rejected");module.toggle(module.getRecipes().indexOf(token));
    RecipeCatalog.learn(level,module,catalog.key(),new int[0]);
    var tag=new CompoundTag();tag.putLong("hut",hut.getPosition().asLong());tag.putString("manual",manual.recipe().getRecipeId().toString());tag.putString("catalog",catalog.recipe().getRecipeId().toString());tag.putInt("count",module.getRecipes().size());tag.putString("tokens",module.getRecipes().toString());
    dev.colonyhorizons.remote.NativeFoundingCheckpoint.save(level,colony);NbtIo.write(tag,file);
    System.out.println("RECIPE_RESTART_SEED_PASS manual 3x3 disabled recipe and catalog recipe persisted through native save");
   }else{
    var tag=NbtIo.read(file);var hut=colony.getServerBuildingManager().getBuilding(BlockPos.of(tag.getLong("hut")));var module=hut.getFirstModuleOccurance(AbstractCraftingBuildingModule.class);
    check(module.getRecipes().size()==tag.getInt("count")&&module.getRecipes().toString().equals(tag.getString("tokens")),"Recipe tokens changed after process restart");
    for(String type:List.of("manual","catalog")){
     var entry=RecipeCatalog.entries(level,module).stream().filter(e->e.teachable()&&e.recipe().getRecipeId().toString().equals(tag.getString(type))).findFirst().orElseThrow();
     check(RecipeCatalog.known(module,RecipeCatalog.storage(level,entry,new int[0])),type+" no longer known");
     try{RecipeCatalog.learn(level,module,entry.key(),new int[0]);throw new AssertionError("Duplicate learned after restart");}catch(IllegalArgumentException expected){check(expected.getMessage().startsWith("Already learned"),"Wrong rejection");}
    }
    check(module.getRecipes().size()==tag.getInt("count"),"Duplicate changed recipe count");
    check(module.isDisabled(module.getRecipes().getFirst()),"Disabled manual recipe did not stay disabled");
    System.out.println("RECIPE_RESTART_RELOAD_PASS exact native tokens retained; manual and catalog duplicates blocked after full restart");
   }
  }catch(Throwable e){System.out.println("RECIPE_RESTART_FAIL "+e);e.printStackTrace();}
  done=true;server.halt(false);
 }
 private static void check(boolean value,String detail){if(!value)throw new IllegalStateException(detail);}
}

