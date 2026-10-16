package dev.colonyhorizons.qa.outpost;
import java.nio.file.*;
import java.util.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** QA-only timings; transaction durability semantics are unchanged. */
@EventBusSubscriber(modid="colonyhorizons")
public final class SupplyTiming {
    private static boolean active;private static long tickStart,checkpointStart;
    private static final List<Long> ticks=new ArrayList<>(),checkpoints=new ArrayList<>();
    public static void begin(){ticks.clear();checkpoints.clear();active=true;}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event){if(active)tickStart=System.nanoTime();}
    @SubscribeEvent public static void post(ServerTickEvent.Post event){if(active&&tickStart!=0)ticks.add(System.nanoTime()-tickStart);}
    public static void checkpointBegin(){if(active)checkpointStart=System.nanoTime();}
    public static void checkpointEnd(){if(active&&checkpointStart!=0){checkpoints.add(System.nanoTime()-checkpointStart);checkpointStart=0;}}
    public static void finish(Path output)throws Exception {
        active=false;var report=new LinkedHashMap<String,Object>();report.put("server_tick_ms",stats(ticks));report.put("checkpoint_ms",stats(checkpoints));
        report.put("processors",Runtime.getRuntime().availableProcessors());report.put("max_heap_bytes",Runtime.getRuntime().maxMemory());
        Files.writeString(output.resolve("supply-timings.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static Map<String,Object> stats(List<Long> samples){
        var sorted=new ArrayList<>(samples);Collections.sort(sorted);var result=new LinkedHashMap<String,Object>();result.put("count",sorted.size());
        if(!sorted.isEmpty()){result.put("p50",sorted.get((sorted.size()-1)/2)/1e6);result.put("p95",sorted.get((int)Math.floor((sorted.size()-1)*0.95))/1e6);result.put("max",sorted.getLast()/1e6);result.put("over_250ms",sorted.stream().filter(n->n>250000000L).count());}return result;
    }
}
