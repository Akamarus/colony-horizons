// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import com.minecolonies.api.colony.*;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;

public final class SurveyorAccess {
 private SurveyorAccess(){}
 public static BuildingSurveyor require(ServerPlayer actor,int colonyId,BlockPos pos){
  if(!actor.blockPosition().closerThan(pos,16))throw new IllegalArgumentException("Open exploration at the Exploration Hut");
  return requireOwned(actor,colonyId,pos);
 }
 public static BuildingSurveyor requireOwned(ServerPlayer actor,int colonyId,BlockPos pos){
  var colony=IColonyManager.getInstance().getColonyByDimension(colonyId,actor.level().dimension());
  if(colony==null||!actor.getUUID().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only the colony owner can manage exploration");
  if(!actor.serverLevel().hasChunkAt(pos))throw new IllegalArgumentException("Open surveys at the Exploration Hut");
  var building=colony.getServerBuildingManager().getBuilding(pos);
  if(!(building instanceof BuildingSurveyor hut)||hut.getBuildingLevel()<1||hut.getTileEntity()==null||!actor.serverLevel().getBlockState(pos).is(SurveyorRegistration.HUT.get()))throw new IllegalArgumentException("Build a Exploration Hut first");return hut;
 }
 public static Optional<ICitizenData> worker(BuildingSurveyor hut){return hut.getFirstModuleOccurance(WorkerBuildingModule.class).getAssignedCitizen().stream().filter(c->!c.isChild()&&!c.isPaused()&&c.getJob() instanceof JobSurveyor&&c.getWorkBuilding()==hut&&c.getCitizenDiseaseHandler().getDisease()==null).findFirst();}
}
