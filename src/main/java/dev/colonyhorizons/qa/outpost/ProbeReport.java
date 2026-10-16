package dev.colonyhorizons.qa.outpost;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.List;
public final class ProbeReport {
    public static void write(Path directory, String suite, List<ProbeResult> results) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(suite+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
    }
}
