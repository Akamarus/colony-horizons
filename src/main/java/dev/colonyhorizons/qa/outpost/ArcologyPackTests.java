package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.integration.ArcologyCatalog;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.*;
import java.util.*;
@PrefixGameTestTemplate(false)
public final class ArcologyPackTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=1200)
 public static void inspect_actual_pack(GameTestHelper h) throws Exception {
  var rows=new ArrayList<ProbeResult>();
  try {
   var root=Path.of(System.getProperty("colonyhorizons.arcologyPath"));
   var result=ArcologyCatalog.inspect(root,h.getLevel().registryAccess());
   h.assertTrue(result.entries().size()==25,"Expected 25 required Arcology level blueprints, got "+result.entries().size()+" "+result.diagnostics());
   pass(rows,"required_files_present","25 actual files");
   h.assertTrue(result.entries().stream().allMatch(e->e.size().getX()>0 && e.size().getY()>0 && e.size().getZ()>0 && e.sha256().length()==64 && e.unresolvedBlocks().isEmpty()),"Decode/palette failures: "+result);
   pass(rows,"native_blueprint_decode","Native loader and original palette registry validation");
   h.assertTrue(result.entries().stream().allMatch(e->e.hutId().startsWith("minecolonies:blockhut")),"Required anchors must resolve native huts: "+result);
   h.assertTrue(result.diagnostics().stream().allMatch(s->s.equals("Missing optional route terminal blueprint: gatehouse")),"Unexpected compatibility diagnostics: "+result.diagnostics());
   pass(rows,"required_hut_anchors","Native hut at primary anchor");
   h.assertTrue(!ArcologyCatalog.inspect(root.resolve("missing"),h.getLevel().registryAccess()).diagnostics().isEmpty(),"Missing pack must report");
   pass(rows,"missing_pack_reported","No fallback pack");
   var temp=Files.createTempDirectory("arcology-invalid");
   try {
    Files.writeString(temp.resolve("pack.json"),"{\"name\":\"Arcology\"}");
    Files.createDirectories(temp.resolve("fundamentals"));
    Files.writeString(temp.resolve("fundamentals/builder1.blueprint"),"invalid");
    h.assertTrue(ArcologyCatalog.inspect(temp,h.getLevel().registryAccess()).diagnostics().stream().anyMatch(s->s.contains("builder1.blueprint")),"Invalid file must identify path");
   } finally {
    Files.deleteIfExists(temp.resolve("fundamentals/builder1.blueprint"));
    Files.deleteIfExists(temp.resolve("fundamentals"));Files.deleteIfExists(temp.resolve("pack.json"));Files.delete(temp);
   }
   pass(rows,"invalid_blueprint_reported","Malformed content reported");
   boolean gatehouse;try(var files=Files.walk(root)){gatehouse=files.anyMatch(p->p.getFileName().toString().matches("gatehouse[1-5]\\.blueprint"));}
   h.assertTrue(result.diagnostics().contains("Missing optional route terminal blueprint: gatehouse")!=gatehouse,"Gatehouse availability matches actual pack");
   pass(rows,"terminal_availability_reported",gatehouse?"Terminal blueprint found":"Optional terminal absent");
   Files.writeString(Path.of(System.getProperty("colonyhorizons.probeOutput")).resolve("catalog.txt"),result.toString());
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Arcology",rows);h.succeed();
  } catch(Throwable e) { rows.add(new ProbeResult("exception",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"Arcology",rows);throw e; }
 }
 private static void pass(List<ProbeResult> r,String id,String evidence){r.add(new ProbeResult(id,ProbeResult.Outcome.PASS,evidence));}
}
