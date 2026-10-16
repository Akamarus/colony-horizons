// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;

/** Single paid block transaction. The coordinator supplies approved work and verifies native builder reach. */
public final class SitePreparationWork {
    private SitePreparationWork(){}
    public static Attempt prepare(ServerLevel level,ServerPlayer owner,SitePreparationPlan.Operation op,IItemHandlerModifiable inventory) {
        if(!op.original().getFluidState().isEmpty())throw new IllegalArgumentException("Water requires its approved containment plan");
        return prepare(level,owner,op,inventory,()->{});
    }
    public static Attempt prepareContained(ServerLevel level,ServerPlayer owner,SitePreparationPlan plan,int cursor,IItemHandlerModifiable inventory){
        if(!plan.approved())throw new IllegalArgumentException("Unapproved containment");
        return prepare(level,owner,plan.operations().get(cursor),inventory,()->plan.validateWater(level,cursor));
    }
    private static Attempt prepare(ServerLevel level,ServerPlayer owner,SitePreparationPlan.Operation op,IItemHandlerModifiable inventory,Runnable containment){
        containment.run();validate(level,owner,op);var before=new ArrayList<ItemStack>();var after=new ArrayList<ItemStack>();
        for(int i=0;i<inventory.getSlots();i++){before.add(inventory.getStackInSlot(i).copy());after.add(inventory.getStackInSlot(i).copy());}
        if(op.kind()==SitePreparationPlan.Kind.FILL) {
            int slot=-1;for(int i=0;i<after.size();i++)if(ItemStack.isSameItemSameComponents(after.get(i),new ItemStack(Items.DIRT))&&inventory.extractItem(i,1,true).getCount()==1){slot=i;break;}
            if(slot<0)throw new IllegalStateException("Builder needs dirt for the foundation");after.get(slot).shrink(1);
        }
        var tool=ItemStack.EMPTY;int toolSlot=-1;var state=op.original();
        if(!state.isAir()&&requiresTool(state)) {
            for(int i=0;i<after.size();i++)if(suitable(after.get(i),state)&&inventory.extractItem(i,1,true).getCount()==1){toolSlot=i;tool=after.get(i);break;}
            if(toolSlot<0)throw new IllegalStateException("Builder needs a usable tool for this terrain");
        }
        var drops=state.isAir()?List.<ItemStack>of():Block.getDrops(state,level,op.pos(),null,owner,tool);
        if(toolSlot>=0&&tool.isDamageableItem())tool.hurtAndBreak(1,level,null,item->{});
        for(var drop:drops) {
            var rest=drop.copy();
            for(int i=0;i<after.size()&&!rest.isEmpty();i++) {
                var current=after.get(i);if(!inventory.isItemValid(i,rest)||!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,rest))continue;
                int amount=Math.min(rest.getCount(),Math.max(0,Math.min(inventory.getSlotLimit(i),rest.getMaxStackSize())-current.getCount()));
                if(amount>0){after.set(i,rest.copyWithCount(current.getCount()+amount));rest.shrink(amount);}
            }
            if(!rest.isEmpty())throw new IllegalStateException("Builder needs inventory space for recovered materials");
        }
        return new Attempt(level,owner,op,inventory,before,after,containment);
    }
    /** Separate paid preparation tools preserve the construction/operating reserve. */
    public static Map<RemoteModels.ItemKey,Integer> toolBill(SitePreparationPlan plan){
        var uses=new HashMap<Item,Integer>();
        for(var op:plan.operations())if(!op.original().isAir()&&requiresTool(op.original())){
            var item=List.of(Items.STONE_AXE,Items.STONE_PICKAXE,Items.STONE_SHOVEL).stream().filter(i->suitable(new ItemStack(i),op.original())).findFirst().orElseThrow(()->new IllegalArgumentException("Terrain requires an unsupported preparation tool"));
            uses.merge(item,1,Math::addExact);
        }
        var result=new HashMap<RemoteModels.ItemKey,Integer>();uses.forEach((item,count)->{int durability=new ItemStack(item).getMaxDamage();result.put(new RemoteModels.ItemKey(new ItemStack(item)),(count+durability-1)/durability);});return Map.copyOf(result);
    }
    private static boolean requiresTool(BlockState s){return s.is(BlockTags.MINEABLE_WITH_AXE)||s.is(BlockTags.MINEABLE_WITH_PICKAXE)||s.is(BlockTags.MINEABLE_WITH_SHOVEL)||s.requiresCorrectToolForDrops();}
    private static boolean suitable(ItemStack tool,BlockState s) {
        if(tool.isEmpty()||tool.getCount()!=1||tool.isDamageableItem()&&tool.getDamageValue()>=tool.getMaxDamage())return false;
        if(s.is(BlockTags.MINEABLE_WITH_AXE)&&!(tool.getItem() instanceof AxeItem)||s.is(BlockTags.MINEABLE_WITH_SHOVEL)&&!(tool.getItem() instanceof ShovelItem)||s.is(BlockTags.MINEABLE_WITH_PICKAXE)&&!(tool.getItem() instanceof PickaxeItem))return false;
        return !s.requiresCorrectToolForDrops()||tool.isCorrectToolForDrops(s);
    }
    private static void validate(ServerLevel level,ServerPlayer owner,SitePreparationPlan.Operation op) {
        var p=op.pos();
        if(owner.serverLevel()!=level||!level.hasChunkAt(p)||!level.getWorldBorder().isWithinBounds(p)||!level.mayInteract(owner,p)
            ||level.getBlockEntity(p)!=null||!level.getBlockState(p).equals(op.original())||!op.original().getFluidState().isEmpty()&&!(op.kind()==SitePreparationPlan.Kind.FILL&&SitePreparationPlan.water(op.original()))
            ||!op.original().isAir()&&!SitePreparationPlan.natural(op.original())&&!SitePreparationPlan.water(op.original()))throw new IllegalStateException("Terrain changed or is protected; review this block");
        var claim=IColonyManager.getInstance().getClaimData(level.dimension(),new ChunkPos(p));
        if(claim!=null&&claim.getOwningColony()!=0){var c=IColonyManager.getInstance().getColonyByDimension(claim.getOwningColony(),level.dimension());if(c==null||!owner.getUUID().equals(c.getPermissions().getOwner()))throw new IllegalStateException("Terrain belongs to another colony");}
        if(op.kind()==SitePreparationPlan.Kind.FILL) {
            if(!op.target().equals(Blocks.DIRT.defaultBlockState())||!level.hasChunkAt(p.below())||!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)||!level.getFluidState(p.below()).isEmpty())throw new IllegalStateException("Dirt foundation needs solid dry support");
            if(!level.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(p),e->!e.isSpectator()&&!e.isRemoved()).isEmpty())throw new IllegalStateException("Wait for the foundation space to be clear");
            var event=new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(),level,p),level.getBlockState(p.below()),owner){
                @Override public BlockState getPlacedBlock(){return op.target();}
                @Override public BlockState getState(){return op.target();}
            };
            NeoForge.EVENT_BUS.post(event);if(event.isCanceled())throw new IllegalStateException("Foundation placement is protected");
        }else if(!op.target().isAir()||op.original().isAir())throw new IllegalArgumentException("Invalid clearing operation");
        if(!op.original().isAir()){var event=new BlockEvent.BreakEvent(level,p,op.original(),owner);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())throw new IllegalStateException("Terrain clearing is protected");}
    }
    public static final class Attempt {
        private final ServerLevel level;private final ServerPlayer owner;private final SitePreparationPlan.Operation op;private final IItemHandlerModifiable inventory;
        private final List<ItemStack> before,after;private final Runnable containment;private boolean attempted;
        private Attempt(ServerLevel level,ServerPlayer owner,SitePreparationPlan.Operation op,IItemHandlerModifiable inventory,List<ItemStack> before,List<ItemStack> after,Runnable containment){this.containment=containment;this.level=level;this.owner=owner;this.op=op;this.inventory=inventory;this.before=before;this.after=after;}
        public void commit(Runnable durableIntent,Runnable flushNative,Runnable durableReceipt) {
            if(attempted)throw new IllegalStateException("Terrain operation already attempted");attempted=true;
            containment.run();validate(level,owner,op);checkInventory();durableIntent.run();
            // The journal remains pending after any later failure, including a partially saved native inventory.
            containment.run();validate(level,owner,op);containment.run();checkInventory();
            if(!level.setBlock(op.pos(),op.target(),Block.UPDATE_ALL))throw new IllegalStateException("Terrain block change failed");
            for(int i=0;i<after.size();i++)if(!ItemStack.matches(before.get(i),after.get(i)))inventory.setStackInSlot(i,after.get(i).copy());
            flushNative.run();durableReceipt.run();
        }
        private void checkInventory(){if(inventory.getSlots()!=before.size())throw new IllegalStateException("Builder inventory changed");for(int i=0;i<before.size();i++)if(!ItemStack.matches(before.get(i),inventory.getStackInSlot(i)))throw new IllegalStateException("Builder supplies changed");}
    }
}
