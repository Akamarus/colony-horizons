package dev.colonyhorizons.supply;
import java.io.IOException;
/** Bounded retry boundary for an idempotent atomic snapshot write. */
public final class SupplyFileWriter {
    private SupplyFileWriter(){}
    @FunctionalInterface public interface Write {void run()throws IOException;}
    public static void write(Write action)throws IOException {
        for(int attempt=0;;attempt++) {
            try {action.run();return;}
            catch(java.nio.file.AccessDeniedException locked) {
                if(attempt>=3)throw locked;
                // Windows scanners can briefly hold the destination during atomic replacement.
                // Retry the identical snapshot only; never advance custody before a successful write.
                try {Thread.sleep(20L<<attempt);}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Snapshot retry interrupted",interrupted);}
            }
        }
    }
}
