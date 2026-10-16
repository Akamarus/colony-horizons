package dev.colonyhorizons.integration;

import com.google.gson.JsonParser;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtil;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only inspection of user-installed Arcology assets; never converts source files. */
public final class ArcologyCatalog {
 public record Entry(String relativePath,String sha256,String hutId,int level,BlockPos size,BlockPos anchor,List<String> unresolvedBlocks) {
  public Entry { unresolvedBlocks=List.copyOf(unresolvedBlocks); }
 }
 public record Inspection(String packName,List<Entry> entries,List<String> diagnostics) {
  public Inspection { entries=List.copyOf(entries);diagnostics=List.copyOf(diagnostics); }
 }
 public static Inspection inspect(Path root,HolderLookup.Provider registries) {return inspect(root,registries,null);}
 public static Inspection inspect(Path root,HolderLookup.Provider registries,dev.colonyhorizons.remote.ProductionProfile profile) {
  var entries=new ArrayList<Entry>();var diagnostics=new ArrayList<String>();
  String name="";
  try {
   root=root.toRealPath();
   name=JsonParser.parseString(Files.readString(root.resolve("pack.json"))).getAsJsonObject().get("name").getAsString();
   if(!name.equals("Arcology") && !name.equals("Arcology Horizons"))throw new IllegalArgumentException("Expected Arcology or Arcology Horizons pack");
  }catch(Exception e){return new Inspection(name,entries,List.of("Cannot read Arcology pack: "+e.getMessage()));}
  var roles=new HashMap<>(Map.of("fundamentals/builder","minecolonies:blockhutbuilder",
   "fundamentals/lumberjack","minecolonies:blockhutlumberjack",
   "fundamentals/citizen","minecolonies:blockhutcitizen",
   "craftsmanship/storage/warehouse","minecolonies:blockhutwarehouse",
   "craftsmanship/storage/deliveryman","minecolonies:blockhutdeliveryman"));
  if(profile!=null)roles.put(profile.blueprintPrefix(),"minecolonies:blockhut"+profile.hutRole());
  for(var role:new TreeMap<>(roles).entrySet())for(int level=1;level<=5;level++){
   String relative=role.getKey()+level+".blueprint";
   try {
    var path=root.resolve(relative).toRealPath();
    if(!path.startsWith(root))throw new IllegalArgumentException("Blueprint escapes pack root");
    var bytes=Files.readAllBytes(path);
    var tag=NbtIo.readCompressed(path,NbtAccounter.create(64L*1024*1024));
    var unresolved=new ArrayList<String>();
    for(var element:tag.getList("palette",Tag.TAG_COMPOUND)){
     String block=((CompoundTag)element).getString("Name");
     var id=ResourceLocation.tryParse(block);
     if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))unresolved.add(block);
    }
    var bp=BlueprintUtil.readBlueprintFromNBT(tag,registries);
    if(bp==null || bp.getSizeX()<=0 || bp.getSizeY()<=0 || bp.getSizeZ()<=0)throw new IllegalArgumentException("Invalid native blueprint dimensions");
    var anchor=bp.getPrimaryBlockOffset();
    if(anchor.getX()<0 || anchor.getY()<0 || anchor.getZ()<0 || anchor.getX()>=bp.getSizeX() || anchor.getY()>=bp.getSizeY() || anchor.getZ()>=bp.getSizeZ())throw new IllegalArgumentException("Invalid native anchor");
    String hut=BuiltInRegistries.BLOCK.getKey(bp.getBlockState(anchor).getBlock()).toString();
    if(!hut.equals(role.getValue()))diagnostics.add(relative+": expected "+role.getValue()+" at anchor, found "+hut);
    entries.add(new Entry(relative,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),hut,level,new BlockPos(bp.getSizeX(),bp.getSizeY(),bp.getSizeZ()),anchor,unresolved));
    if(!unresolved.isEmpty())diagnostics.add(relative+": unresolved original palette "+unresolved);
   }catch(Exception e){diagnostics.add(relative+": "+e.getClass().getSimpleName()+": "+e.getMessage());}
  }
  try(var paths=Files.walk(root)){
   if(paths.noneMatch(p->p.getFileName().toString().matches("gatehouse[1-5]\\.blueprint")))
    diagnostics.add("Missing optional route terminal blueprint: gatehouse");
  }catch(Exception e){diagnostics.add("Cannot inspect terminal assets: "+e.getMessage());}
  return new Inspection(name,entries,diagnostics);
 }
}
