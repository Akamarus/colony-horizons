// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.qa.outpost;
import dev.colonyhorizons.surveyor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import java.util.*;
record ExplorationFixture(OutpostProbeFixture colony,ServerPlayer owner,BuildingSurveyor hut,com.minecolonies.api.colony.ICitizenData worker,UUID discovery){
 static ExplorationFixture create(ServerLevel l,BlockPos center)throws Exception{
  var f=OutpostProbeFixture.create(l,center,UUID.randomUUID());var owner=RemoteActivityTests.loginAtHome(l,f.ownerId());owner.setPos(f.center().getX(),f.center().getY(),f.center().getZ());var pos=f.center().offset(0,0,4);var block=SurveyorRegistration.HUT.get();var bp=new com.ldtteam.structurize.blueprints.v1.Blueprint((short)1,(short)1,(short)1,l.registryAccess());bp.addBlockState(BlockPos.ZERO,block.defaultBlockState());bp.setCachePrimaryOffset(BlockPos.ZERO);if(!block.setup(owner,l,pos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/surveyor1.blueprint"))throw new IllegalStateException("Native hut setup");var hut=(BuildingSurveyor)f.colony().getServerBuildingManager().getBuilding(pos);hut.setBuildingLevel(1);var worker=f.createCitizen();hut.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(worker);var inv=hut.getTileEntity().getInventory();inv.setStackInSlot(0,new ItemStack(Items.BREAD,4));var target=ResourceLocation.withDefaultNamespace("kelp");
  // The real-discovery path is covered separately. Seed one already returned discovery for this trip fixture.
  var sd=SurveySavedData.get(l);var sr=SurveyRecord.create(UUID.randomUUID(),f.colony().getID(),owner.getUUID(),pos,worker.getId(),worker.getUUID(),target);sd.add(sr);var searching=sr.next(SurveyRecord.State.SEARCHING);sd.replace(sr,searching);var source=pos.offset(512,0,0);var op=UUID.randomUUID();sd.putSamples(sr.id(),sd.samples(sr.id()).prepare(op,source));sd.recordEvidence(new DiscoveryEvidence(sr.id(),sr.colony(),sr.owner(),l.dimension().location(),ResourceLocation.withDefaultNamespace("ocean"),target,source));sd.putSamples(sr.id(),sd.samples(sr.id()).collected(op,1));var returning=searching.next(SurveyRecord.State.RETURNING);sd.replace(searching,returning);sd.writeCargo(sr.id(),new SurveyCargo(true,ItemStack.EMPTY,List.of()).save(l.registryAccess()));sd.replace(returning,returning.next(SurveyRecord.State.COMPLETE));sd.checkpoint(l);
  return new ExplorationFixture(f,owner,hut,worker,sr.id());
 }
 UUID start(boolean repeat){return GatheringService.start(owner,colony.colony().getID(),hut.getPosition(),ResourceLocation.withDefaultNamespace("kelp"),repeat);}
}
