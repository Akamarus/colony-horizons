// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import dev.colonyhorizons.integration.ArcologyCatalog;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.supply.NativeSupplyCheckpoint;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Paid unfinished hut anchors only. Full structures remain native builder work. */
public final class FoundingPlacement {
    private FoundingPlacement() {}
    private static final String OPERATION="colonyhorizons:founding_anchor";
    public static boolean isPaidAnchor(ServerLevel level,BuildingPlan plan){return level.hasChunkAt(plan.anchor())&&level.getBlockEntity(plan.anchor()) instanceof TileEntityColonyBuilding hut&&hut.getPersistentData().hasUUID(OPERATION)&&hut.getPersistentData().getUUID(OPERATION).equals(plan.operationId());}
    public record Receipt(UUID operation,Map<ItemKey,Integer> spent,boolean complete) {
        public Receipt {Objects.requireNonNull(operation);spent=Map.copyOf(spent);if(spent.isEmpty()||spent.size()>8||spent.values().stream().anyMatch(n->n<=0||n>64))throw new IllegalArgumentException("Invalid paid anchor receipt");}
    }
    private record Materials(BlockState anchor,List<BlockPos> supports,Map<ItemKey,Integer> bill) {}
    public static Map<ItemKey,Integer> bill(ServerLevel level,UUID development) {
        return bill(level,RemoteDevelopmentData.get(level).find(development).orElseThrow());
    }
    public static Map<ItemKey,Integer> bill(ServerLevel level,Development r) {
        var result=new HashMap<ItemKey,Integer>();
        validatePack(level,r);
        for(var plan:r.draft().buildings())materials(level,r,plan).bill().forEach((k,n)->result.merge(k,n,Math::addExact));
        return Map.copyOf(result);
    }
    public static Map<ItemKey,Integer> remaining(ServerLevel level,UUID development) {
        var data=RemoteDevelopmentData.get(level);if(data.foundingRefunded(development))return Map.of();if(data.refundPending(development))throw new IllegalStateException("Uncertain refund requires recovery");var cargo=FoundingCargo.load(data.foundingCargo(development).orElseThrow(()->new IllegalStateException("Founding supplies not paid")),level.registryAccess());
        var result=new HashMap<>(cargo.items());
        for(var receipt:data.anchorReceipts(development).values())for(var entry:receipt.spent().entrySet()) {
            int left=result.getOrDefault(entry.getKey(),0)-entry.getValue();if(left<0)throw new IllegalStateException("Anchor receipts exceed paid custody");
            if(left==0)result.remove(entry.getKey());else result.put(entry.getKey(),left);
        }
        for(var entry:data.starterDelivery(development).map(FoundingSupplies.Delivery::spent).orElse(Map.of()).entrySet()){int left=result.getOrDefault(entry.getKey(),0)-entry.getValue();if(left<0)throw new IllegalStateException("Starter supplies exceed paid custody");if(left==0)result.remove(entry.getKey());else result.put(entry.getKey(),left);}
        for(var entry:data.bootstrapSpent(development).entrySet()){int left=result.getOrDefault(entry.getKey(),0)-entry.getValue();if(left<0)throw new IllegalStateException("Bootstrap spending exceeds custody");if(left==0)result.remove(entry.getKey());else result.put(entry.getKey(),left);}
        return Map.copyOf(result);
    }
    public static void validatePack(ServerLevel level,Development r) {
        var name=r.draft().buildings().getFirst().pack();var pack=StructurePacks.getStructurePack(name);
        if(pack==null)throw new IllegalArgumentException("Approved style pack is unavailable");
        var current=ArcologySettlementLayout.plan(r.id(),r.draft().request(),ArcologyCatalog.inspect(pack.getPath(),level.registryAccess(),r.draft().request().surveyOrigin().map(SurveyOrigin::profile).orElse(null)));
        if(!current.equals(r.draft().buildings()))throw new IllegalArgumentException("Approved blueprints changed; review the settlement again");
    }
    private static Materials materials(ServerLevel level,Development r,BuildingPlan plan) {
        if(plan.transform()!=com.ldtteam.structurize.api.RotationMirror.NONE || plan.targetLevel()!=1)throw new IllegalArgumentException("Unsupported founding transform or level");
        var blueprint=StructurePacks.getBlueprint(plan.pack(),plan.blueprint(),level.registryAccess());
        var state=blueprint.getBlockState(blueprint.getPrimaryBlockOffset());
        if(!(state.getBlock() instanceof AbstractBlockHut<?>) || state.getBlock().asItem()==Items.AIR)throw new IllegalArgumentException("Blueprint has no payable native hut");
        var bill=new HashMap<ItemKey,Integer>();bill.put(new ItemKey(new ItemStack(state.getBlock())),1);var supports=new ArrayList<BlockPos>();
        int depth=plan.anchor().getY()-r.draft().request().center().getY();if(depth<0||depth>1)throw new IllegalArgumentException("Unsupported founding support depth");
        for(int i=1;i<=depth;i++)supports.add(plan.anchor().below(i));
        if(depth>0)bill.put(new ItemKey(new ItemStack(Items.DIRT)),depth);
        return new Materials(state,List.copyOf(supports),Map.copyOf(bill));
    }
    public static void place(ServerPlayer actor,UUID development,UUID operation) {
        var level=actor.serverLevel();var data=RemoteDevelopmentData.get(level);var r=data.find(development).orElseThrow();
        var colony=IColonyManager.getInstance().getColonyByDimension(r.draft().request().colonyId(),level.dimension());
        if(!actor.getUUID().equals(r.owner())||colony==null||!r.owner().equals(colony.getPermissions().getOwner())||!colony.getPermissions().hasPermission(actor,Action.PLACE_HUTS))throw new SecurityException("Only the current owner may place founding huts");
        if(r.phase()!=Phase.STAGING || !data.hasClaimJournal(development))throw new IllegalStateException("Founding is not staging");
        var plan=r.draft().buildings().stream().filter(b->b.operationId().equals(operation)).findFirst().orElseThrow(()->new IllegalArgumentException("Unapproved building operation"));
        var prior=data.anchorReceipts(development).get(operation);
        if(prior!=null){if(!prior.complete())throw new IllegalStateException("Interrupted placement requires recovery; no replay");return;}
        validatePack(level,r);var material=materials(level,r,plan);var available=remaining(level,development);
        for(var e:material.bill().entrySet())if(available.getOrDefault(e.getKey(),0)<e.getValue())throw new IllegalStateException("Missing paid founding hut or support");
        var hutBlock=(AbstractBlockHut<?>)material.anchor().getBlock();
        if(!colony.getServerBuildingManager().canPlaceAt(hutBlock,plan.anchor(),actor))throw new IllegalArgumentException("Native hut placement requirements not met");
        var research=colony.getResearchManager();var effect=research.getResearchEffectIdFrom(hutBlock);
        if(com.minecolonies.api.IMinecoloniesAPI.getInstance().getGlobalResearchTree().getResearchForEffect(effect)!=null && research.getResearchEffects().getEffectStrength(effect)<=0)throw new IllegalArgumentException("Hut research is not unlocked");
        // Revalidate the full selected upgrade envelope, not merely the hut block.
        var v=plan.upgradeVolume();
        for(var pos:BlockPos.betweenClosed(v.minX(),v.minY(),v.minZ(),v.maxX(),v.maxY(),v.maxZ())) {
            var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(pos));
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||claim==null||claim.getOwningColony()!=colony.getID()||!data.confirmedClaims(development).contains(new ChunkPos(pos)))throw new IllegalArgumentException("Approved site is unavailable or ownership changed");
            if(level.getBlockEntity(pos)!=null || !level.getFluidState(pos).isEmpty() || !level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())throw new IllegalArgumentException("Building area is obstructed");
            if(pos.getY()==v.minY() && !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))throw new IllegalArgumentException("Building floor is unsupported");
        }
        var receipt=new Receipt(operation,material.bill(),false);
        data.putAnchorReceipt(development,receipt);data.checkpoint(level);
        try {
            for(var pos:material.supports())if(!level.setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState()))throw new IllegalStateException("Paid support placement failed");
            if(!level.setBlockAndUpdate(plan.anchor(),material.anchor()))throw new IllegalStateException("Paid hut placement failed");
            hutBlock.onBlockPlacedByBuildTool(level,plan.anchor(),material.anchor(),actor,new ItemStack(hutBlock),plan.transform(),plan.pack(),plan.blueprint());
            var building=colony.getServerBuildingManager().getBuilding(plan.anchor());
            if(building==null||building.getBuildingLevel()!=0||!(level.getBlockEntity(plan.anchor()) instanceof TileEntityColonyBuilding hut))throw new IllegalStateException("Native unfinished hut registration failed");
            hut.getPersistentData().putUUID(OPERATION,operation);hut.setChanged();
            NativeSupplyCheckpoint.flushAndVerify(level,List.of(hut));NativeFoundingCheckpoint.save(level,colony);
            data.putAnchorReceipt(development,new Receipt(operation,material.bill(),true));data.checkpoint(level);
        }catch(RuntimeException failure){var current=data.find(development).orElseThrow();data.transition(development,current.revision(),Phase.RECOVERY_NEEDED);try{data.checkpoint(level);}catch(RuntimeException save){failure.addSuppressed(save);}throw failure;}
    }
}
