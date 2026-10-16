// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.jobs.JobBuilder;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.function.Consumer;

/** Executes one approved operation. The founding coordinator must persist the supplied journal writer. */
public final class SitePreparationService {
    private SitePreparationService(){}
    public static RemoteModels.StepResult advance(ServerLevel level,ServerPlayer owner,ICitizenData builder,
            SitePreparationPlan plan,SitePreparationJournal journal,Consumer<SitePreparationJournal> durableWriter) {
        var colony=builder.getColony();
        if(colony.getWorld()!=level||owner.serverLevel()!=level||!owner.getUUID().equals(colony.getPermissions().getOwner()))throw new SecurityException("Only the colony owner may authorize terrain work");
        if(!plan.approved()||journal.total()!=plan.operations().size()||!journal.fingerprint().equals(plan.fingerprint()))throw new IllegalArgumentException("Approved terrain plan changed");
        if(journal.pending())return waiting("Interrupted terrain work requires recovery");
        if(journal.cancelled())return waiting("Terrain preparation cancelled; completed work retained");
        if(journal.finished())return waiting("Terrain preparation complete");
        if(builder.isChild()||builder.isPaused()||builder.getCitizenDiseaseHandler().getDisease()!=null
            ||!(builder.getJob() instanceof JobBuilder)||!(builder.getWorkBuilding() instanceof AbstractBuildingStructureBuilder hut)
            ||hut.getTileEntity()==null||hut.getWorkOrder()!=null||colony.getTravellingManager().getTravellingTargetFor(builder.getId()).isPresent())return waiting("Waiting for the assigned healthy builder");
        var entity=builder.getEntity().filter(e->e.isAlive()&&!e.isRemoved()).orElse(null);
        if(entity==null||entity.level()!=level)return waiting("Waiting for the builder to arrive");
        var op=plan.operations().get(journal.cursor());
        if(!level.hasChunkAt(op.pos()))return waiting("Waiting for surveyed terrain to load");
        boolean standingInFill=op.kind()==SitePreparationPlan.Kind.FILL&&entity.getBoundingBox().intersects(new AABB(op.pos()));
        if(standingInFill||!reachable(level,entity,entity.position(),op.pos())) {
            if(!entity.blockPosition().closerThan(op.pos(),64))return waiting("Builder is too far from terrain work");
            var nav=(com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate)entity.getNavigation();
            net.minecraft.core.BlockPos stand=null;
            if(nav.getPathResult()!=null&&!nav.getPathResult().failedToReachDestination()&&nav.getPathResult().getJob() instanceof com.minecolonies.core.entity.pathfinding.pathjobs.IDestinationPathJob path
                &&standing(level,builder,entity,path.getDestination(),op.pos()))stand=path.getDestination();
            if(stand==null){double nearest=Double.MAX_VALUE;
                for(int y=-3;y<=3;y++)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
                    var candidate=op.pos().offset(x,y,z);double height=candidate.getY()-entity.blockPosition().getY();double distance=entity.position().distanceToSqr(Vec3.atBottomCenterOf(candidate))+16*height*height;
                    if(distance<nearest&&standing(level,builder,entity,candidate,op.pos())){stand=candidate;nearest=distance;}
                }
            }
            if(stand==null)return waiting("Builder needs supported access to this terrain");
            if(nav.isDone()||nav.getPathResult()==null||!com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation.isJobFor(nav.getPathResult().getJob(),stand))
                nav.setPathJob(new com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation(level,
                    com.minecolonies.core.entity.pathfinding.PathfindingUtils.prepareStart(entity),stand,
                    (int)entity.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),entity),stand,1.0,true);
            return waiting("Builder walking to a safe work position");
        }
        var attempt=SitePreparationPlan.water(op.original())?SitePreparationWork.prepareContained(level,owner,plan,journal.cursor(),builder.getInventory()):SitePreparationWork.prepare(level,owner,op,builder.getInventory());var pending=journal.begin(journal.cursor());
        entity.getNavigation().stop();
        attempt.commit(()->durableWriter.accept(pending),()->{builder.markDirty(0);NativeFoundingCheckpoint.save(level,colony);},()->durableWriter.accept(pending.complete(journal.cursor())));
        return new RemoteModels.StepResult(op.kind()==SitePreparationPlan.Kind.FILL?"Builder placing dirt foundation":"Builder clearing terrain",java.util.List.of(),true);
    }
    private static boolean standing(ServerLevel level,ICitizenData builder,com.minecolonies.api.entity.citizen.AbstractEntityCitizen entity,
            net.minecraft.core.BlockPos stand,net.minecraft.core.BlockPos target){
        if(stand==null||stand.equals(target)||stand.above().equals(target)||!RemoteWorkerLocality.permits(builder,stand)
            ||!level.hasChunkAt(stand)||!level.getWorldBorder().isWithinBounds(stand)||level.isOutsideBuildHeight(stand.above())
            ||!level.isEmptyBlock(stand)||!level.isEmptyBlock(stand.above())||!level.getFluidState(stand.below()).isEmpty()
            ||!SitePreparationPlan.natural(level.getBlockState(stand.below()))
            ||!level.getBlockState(stand.below()).isFaceSturdy(level,stand.below(),net.minecraft.core.Direction.UP))return false;
        if(stand.equals(entity.blockPosition())&&(!reachable(level,entity,entity.position(),target)
            ||entity.getBoundingBox().intersects(new AABB(target))))return false;
        // Native navigation reaches a block, not an exact centered coordinate. Require margin
        // across that cell so a small arrival offset cannot leave the worker behind the cliff.
        for(double x:new double[]{0.1,0.9})for(double z:new double[]{0.1,0.9})
            if(!reachable(level,entity,new Vec3(stand.getX()+x,stand.getY(),stand.getZ()+z),target))return false;
        return true;
    }
    private static boolean reachable(ServerLevel level,com.minecolonies.api.entity.citizen.AbstractEntityCitizen entity,Vec3 feet,net.minecraft.core.BlockPos target){
        if(feet.distanceToSqr(Vec3.atCenterOf(target))>16)return false;
        var hit=level.clip(new ClipContext(feet.add(0,entity.getEyeHeight(),0),Vec3.atCenterOf(target),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(target);
    }
    private static RemoteModels.StepResult waiting(String message){return new RemoteModels.StepResult(message,java.util.List.of(),false);}
}
