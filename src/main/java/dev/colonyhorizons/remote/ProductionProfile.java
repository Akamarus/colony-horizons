// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import dev.colonyhorizons.surveyor.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFlorist;

/** Exact intended production. A profile is not proof of an unlocked or operational producer. */
public record ProductionProfile(ResourceLocation target,SurveyResource.Profile kind) {
    public ProductionProfile {
        var resource=SurveyResourceCatalog.find(target).orElseThrow(()->new IllegalArgumentException("Unsupported resource target"));
        if(kind!=resource.profile())throw new IllegalArgumentException("Producer does not match the requested resource");
        if(kind==SurveyResource.Profile.SEEDS&&!target.equals(ResourceLocation.withDefaultNamespace("wheat_seeds")))
            throw new IllegalArgumentException("Fruit-to-seed production is not configured for this target");
    }
    public static ProductionProfile forTarget(ResourceLocation target){return new ProductionProfile(target,SurveyResourceCatalog.find(target).orElseThrow(()->new IllegalArgumentException("Unsupported resource target")).profile());}
    public String hutRole(){return switch(kind){case FORESTRY->"lumberjack";case FLOWERS->"florist";case SEEDS->"farmer";default->"plantation";};}
    public ResourceLocation jobId(){return ResourceLocation.fromNamespaceAndPath("minecolonies",hutRole().equals("plantation")?"planter":hutRole());}
    public String blueprintPrefix(){return (kind==SurveyResource.Profile.FORESTRY?"fundamentals/":"agriculture/horticulture/")+hutRole();}
    public String fieldBlueprint(){return switch(kind){case SEEDS->"infrastructure/fields/wooden field.blueprint";case VINES->"infrastructure/plantationfields/vine1.blueprint";case KELP->"infrastructure/plantationfields/kelp1.blueprint";case SEAGRASS->"infrastructure/plantationfields/seagrass1.blueprint";default->"";};}
    public boolean aquatic(){return kind==SurveyResource.Profile.KELP||kind==SurveyResource.Profile.SEAGRASS;}
    public ResourceLocation starterItem(){
        if(kind!=SurveyResource.Profile.FORESTRY||!target.getPath().endsWith("_log"))return target;
        String wood=target.getPath().substring(0,target.getPath().length()-4);
        return ResourceLocation.withDefaultNamespace(wood.equals("mangrove")?"mangrove_propagule":wood+"_sapling");
    }
    /** Finite seed and tool stock; callers debit this exact bill before delivery. */
    public java.util.Map<RemoteModels.ItemKey,Integer> starterSupplies(){
        var items=new java.util.HashMap<RemoteModels.ItemKey,Integer>();
        java.util.function.BiConsumer<net.minecraft.world.item.Item,Integer> add=(item,n)->items.put(new RemoteModels.ItemKey(new net.minecraft.world.item.ItemStack(item)),n);
        add.accept(BuiltInRegistries.ITEM.get(starterItem()),8);
        switch(kind){
            case FORESTRY->{add.accept(net.minecraft.world.item.Items.STONE_AXE,1);add.accept(net.minecraft.world.item.Items.STONE_HOE,1);add.accept(net.minecraft.world.item.Items.SHEARS,1);}
            case FLOWERS->{add.accept(net.minecraft.world.item.Items.SHEARS,1);add.accept(com.minecolonies.api.items.ModItems.compost,32);}
            case SEEDS->add.accept(net.minecraft.world.item.Items.STONE_HOE,1);
            case VINES->add.accept(net.minecraft.world.item.Items.SHEARS,1);
            case SEAGRASS->{add.accept(net.minecraft.world.item.Items.SHEARS,1);add.accept(net.minecraft.world.item.Items.BONE_MEAL,32);}
            case KELP->{}
        }
        return java.util.Map.copyOf(items);
    }
    public int minimumLevel(){
        if(kind!=SurveyResource.Profile.FLOWERS)return 1;
        var item=BuiltInRegistries.ITEM.get(target);
        for(int level=1;level<=5;level++)if(BuildingFlorist.getPlantablesForBuildingLevel(level).stream().anyMatch(s->s.getItem()==item))return level;
        throw new IllegalArgumentException("Native florist cannot grow the requested flower");
    }
    public CompoundTag save(){var t=new CompoundTag();t.putInt("version",1);t.putString("target",target.toString());t.putString("kind",kind.name());return t;}
    public static ProductionProfile load(CompoundTag t){
        if(t.getInt("version")!=1||!t.contains("target",Tag.TAG_STRING)||!t.contains("kind",Tag.TAG_STRING))throw new IllegalArgumentException("Unknown production profile");
        return new ProductionProfile(ResourceLocation.parse(t.getString("target")),SurveyResource.Profile.valueOf(t.getString("kind")));
    }
}
