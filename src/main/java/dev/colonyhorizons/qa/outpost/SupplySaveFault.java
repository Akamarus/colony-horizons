package dev.colonyhorizons.qa.outpost;

import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

/** One-shot QA fault, never armed in ordinary gameplay. */
@EventBusSubscriber(modid="colonyhorizons")
public final class SupplySaveFault {
 static ChunkPos target;
 @SubscribeEvent public static void save(ChunkDataEvent.Save event){
  if(target!=null&&target.equals(event.getChunk().getPos())){
   target=null;throw new IllegalStateException("SUPPLY_QA_INJECTED_CHUNK_SAVE_FAILURE");
  }
 }
}
