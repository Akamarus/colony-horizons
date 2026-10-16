package dev.colonyhorizons.recipes;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class RecipeCatalogConfig {
 private static final ModConfigSpec.Builder B=new ModConfigSpec.Builder();
 public static final ModConfigSpec.BooleanValue ENABLED=B.comment("Allow learning supported hut recipes from the catalog without carrying ingredients. Normal recipe capacity still applies.").define("recipeCatalog.enabled",true);
 public static final ModConfigSpec SPEC=B.build();
 private RecipeCatalogConfig(){}
}
