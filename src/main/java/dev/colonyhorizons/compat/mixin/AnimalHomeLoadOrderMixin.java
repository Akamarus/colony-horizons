package dev.colonyhorizons.compat.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.core.colony.AnimalData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Colony loads animal records before buildings; defer home lookup without losing the saved address. */
@Mixin(value=AnimalData.class,remap=false)
public abstract class AnimalHomeLoadOrderMixin {
 @Shadow @Final private IColony colony;
 @Shadow private IBuilding homeBuilding;
 @Unique private BlockPos colonyHorizons$pendingHome;

 @Inject(method="deserializeNBT",at=@At("RETURN"))
 private void colonyHorizons$rememberHome(HolderLookup.Provider provider,CompoundTag tag,CallbackInfo ci){
  colonyHorizons$pendingHome=null;
  if(homeBuilding==null&&tag.contains(NbtTagConstants.TAG_ANIMALHOME)){
   var pos=BlockPosUtil.read(tag,NbtTagConstants.TAG_ANIMALHOME);
   if(!pos.equals(BlockPos.ZERO))colonyHorizons$pendingHome=pos;
  }
 }
 @Inject(method="getHomeBuilding",at=@At("HEAD"))
 private void colonyHorizons$resolveHome(CallbackInfoReturnable<IBuilding> cir){colonyHorizons$resolvePending();}
 @Inject(method="serializeViewNetworkData",at=@At("HEAD"))
 private void colonyHorizons$resolveForClients(net.minecraft.network.RegistryFriendlyByteBuf buffer,CallbackInfo ci){colonyHorizons$resolvePending();}
 @Unique private void colonyHorizons$resolvePending(){
  if(colonyHorizons$pendingHome!=null){
   var home=colony.getServerBuildingManager().getBuilding(colonyHorizons$pendingHome);
   if(home!=null)((AnimalData)(Object)this).setHomeBuilding(home);
  }
 }
 @Inject(method="serializeNBT",at=@At("RETURN"))
 private void colonyHorizons$savePendingHome(HolderLookup.Provider provider,CallbackInfoReturnable<CompoundTag> cir){
  if(homeBuilding==null&&colonyHorizons$pendingHome!=null)BlockPosUtil.write(cir.getReturnValue(),NbtTagConstants.TAG_ANIMALHOME,colonyHorizons$pendingHome);
 }
 @Inject(method="setHomeBuilding",at=@At("HEAD"))
 private void colonyHorizons$explicitHome(IBuilding building,CallbackInfo ci){colonyHorizons$pendingHome=null;}
 @Inject(method="onRemoveBuilding",at=@At("HEAD"))
 private void colonyHorizons$removedHome(IBuilding building,CallbackInfo ci){
  if(colonyHorizons$pendingHome!=null&&colonyHorizons$pendingHome.equals(building.getID()))((AnimalData)(Object)this).setHomeBuilding(null);
 }
}
