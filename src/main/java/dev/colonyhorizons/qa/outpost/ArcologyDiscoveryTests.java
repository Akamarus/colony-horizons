package dev.colonyhorizons.qa.outpost;

import com.ldtteam.structurize.storage.StructurePacks;
import net.minecraft.gametest.framework.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;

/** Native server category discovery; deliberately not a claim of client menu/preview testing. */
@PrefixGameTestTemplate(false)
public final class ArcologyDiscoveryTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=2400)
 public static void category_discovery(GameTestHelper h){
  h.runAfterDelay(40,()->{var rows=new ArrayList<ProbeResult>();var output=Path.of(System.getProperty("colonyhorizons.probeOutput"));try{
   String name="Arcology Horizons";var root=Path.of(System.getProperty("colonyhorizons.arcologyPath")).toRealPath();
   var meta=StructurePacks.getStructurePack(name);
   check(meta!=null&&!meta.isDisabled(),"Pack absent or disabled");
   check(meta.getPath().toRealPath().equals(root),"Native loader selected a different pack copy");
   check(StructurePacks.getPackMetas().stream().filter(m->m.getName().equals(name)).count()==1,"Duplicate pack identity");
   check(meta.getModList().contains("colonyhorizons")&&meta.getModList().stream().allMatch(id->ModList.get().isLoaded(id)),"Missing required compatibility dependency");
   check(meta.getAuthors().contains("BlockyUniverse")&&Files.isRegularFile(root.resolve("icon.png")),"Pack credit/icon missing");
   rows.add(new ProbeResult("pack_metadata",ProbeResult.Outcome.PASS,"Native server automatically discovered the isolated Arcology Horizons pack, enabled once with its installed dependencies, original author credit and icon. No manual pack registration."));
   var pending=new ArrayDeque<String>();pending.add("");var visited=new HashSet<String>();var discovered=new TreeSet<String>();var trace=new ArrayList<String>();
   while(!pending.isEmpty()){
    var category=pending.remove();if(!visited.add(category))continue;check(visited.size()<100,"Category traversal cycle");
    var children=StructurePacks.getCategoriesFuture(name,category).get();
    for(var child:children){
     check(child.packMeta.getName().equals(name),"Cross-pack category");
     String path=child.subPath.replace('\\','/');trace.add(category+" -> "+path+" terminal="+child.isTerminal+" current="+child.isCurrent);
     if(!child.isCurrent)pending.add(path);
    }
    for(var bp:StructurePacks.getBlueprintsFuture(name,category,h.getLevel().registryAccess()).get()){
     check(bp.getPackName().equals(name),"Cross-pack blueprint");
     var file=bp.getFilePath().resolve(bp.getFileName()+".blueprint").toRealPath();
     check(file.startsWith(root),"Blueprint outside pack");
     String relative=root.relativize(file).toString().replace('\\','/');
     check(discovered.add(relative),"Duplicate category blueprint: "+relative);
    }
   }
   var expected=new TreeSet<String>();try(var files=Files.walk(root)){files.filter(p->p.toString().endsWith(".blueprint")).forEach(p->expected.add(root.relativize(p).toString().replace('\\','/')));}
   check(expected.size()==305,"Complete candidate must contain all305 blueprints");
   var missing=new TreeSet<>(expected);missing.removeAll(discovered);var extra=new TreeSet<>(discovered);extra.removeAll(expected);
   check(missing.isEmpty()&&extra.isEmpty(),"Category coverage mismatch; missing="+missing+" extra="+extra);
   Files.write(output.resolve("pack-category-discovery.txt"),trace);
   rows.add(new ProbeResult("all_blueprints_browsable",ProbeResult.Outcome.PASS,"Native recursive category/blueprint enumeration reached all305 distinct on-disk blueprints across "+visited.size()+" categories; no omitted or duplicate assets. Server backend enumeration only, not client grouping, previews or placement clicks."));
   for(var path:List.of("military/stable1.blueprint","military/stable5.blueprint","military/gatehouse1.blueprint","military/gatehouse3.blueprint","fundamentals/simplequarry1.blueprint","fundamentals/mediumquarry1.blueprint","infrastructure/plantationfields/seapickle1.blueprint","infrastructure/plantationfields/seagrass1.blueprint"))
    check(discovered.contains(path),"Added building missing from browsing: "+path);
   rows.add(new ProbeResult("extensions_browsable",ProbeResult.Outcome.PASS,"New stable, gatehouse, quarry and aquatic field entries are discoverable through native pack categories. Blueprint work behavior is covered by separate suites."));
   ProbeReport.write(output,"ArcologyDiscovery",rows);h.succeed();
  }catch(Throwable e){try{rows.add(new ProbeResult("discovery_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(output,"ArcologyDiscovery",rows);}catch(Exception ignored){}h.fail(e.toString());}});
 }
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
