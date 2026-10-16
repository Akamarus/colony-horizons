package dev.colonyhorizons.qa.outpost;
import com.minecolonies.core.colony.AnimalData;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.constant.NbtTagConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Path;
import java.util.*;

/** Native AnimalData contracts when saved home precedes building deserialization. */
@PrefixGameTestTemplate(false)
public final class ArcologyAnimalHomeTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=3000)
 public static void home_load_order(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=5;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var f=new ArcologyStableTests.Run(h);f.afterSetup=r->{try{
   var level=h.getLevel();var colony=r.fixture.colony();var lookup=level.registryAccess();var pos=new BlockPos(576,16,40);check(colony.getServerBuildingManager().getBuilding(pos)==null,"Future home already loaded");
   var tag=new CompoundTag();tag.putInt(NbtTagConstants.TAG_ID,77);tag.putUUID(NbtTagConstants.TAG_UUID,UUID.randomUUID());BlockPosUtil.write(tag,NbtTagConstants.TAG_ANIMALHOME,pos);
   var network=AnimalData.loadAnimalFromNBT(colony,tag,lookup);var delayed=AnimalData.loadAnimalFromNBT(colony,tag,lookup);var cleared=AnimalData.loadAnimalFromNBT(colony,tag,lookup);var reassigned=AnimalData.loadAnimalFromNBT(colony,tag,lookup);var removed=AnimalData.loadAnimalFromNBT(colony,tag,lookup);
   check(delayed.getHomeBuilding()==null,"Unavailable home unexpectedly resolved");check(BlockPosUtil.read(delayed.serializeNBT(lookup),NbtTagConstants.TAG_ANIMALHOME).equals(pos),"Unresolved saved home discarded during serialization");
   cleared.setHomeBuilding(null);reassigned.setHomeBuilding(r.stable);
   var setup=new ArcologyUpgradeTests.Run(h);setup.fixture=r.fixture;var home=setup.place("military/stable1.blueprint",pos,true);
   removed.onRemoveBuilding(home);
   delayed.clearDirty();check(delayed.getHomeBuilding()==home,"Saved home not resolved after building registration");check(delayed.isDirty(),"Resolved home not marked dirty for existing clients");
   var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),lookup);try{network.serializeViewNetworkData(buffer);check(buffer.readBoolean()&&buffer.readBlockPos().equals(pos),"Initial network snapshot lost available saved home");}finally{buffer.release();}
   check(cleared.getHomeBuilding()==null&&BlockPosUtil.read(cleared.serializeNBT(lookup),NbtTagConstants.TAG_ANIMALHOME).equals(BlockPos.ZERO),"Explicit clear resurrected old home");
   check(reassigned.getHomeBuilding()==r.stable&&BlockPosUtil.read(reassigned.serializeNBT(lookup),NbtTagConstants.TAG_ANIMALHOME).equals(r.stable.getPosition()),"Explicit reassignment lost");
   check(removed.getHomeBuilding()==null&&BlockPosUtil.read(removed.serializeNBT(lookup),NbtTagConstants.TAG_ANIMALHOME).equals(BlockPos.ZERO),"Removed pending home resurrected");
   var rows=new ArrayList<ProbeResult>();for(String id:List.of("pending_home_saved","home_resolves_after_building","explicit_home_clear","explicit_home_reassign","pending_home_removed","resolved_home_dirty","network_home_resolved"))rows.add(new ProbeResult(id,ProbeResult.Outcome.PASS,"Native AnimalData load before home registration; unavailable reference survives serialization, resolves after real building registration, and respects explicit clear/reassignment/removal."));
   ProbeReport.write(Path.of(System.getProperty("colonyhorizons.probeOutput")),"ArcologyAnimalHome",rows);h.succeed();
  }catch(Throwable e){h.fail(e.toString());}};f.start();});
 }
 static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
