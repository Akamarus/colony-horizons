// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.surveyor;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
public final class GatheringCatalog {
 private GatheringCatalog(){}
 public static Optional<GatheringOffer> find(ResourceLocation target){return SurveyResourceCatalog.find(target).filter(r->!Set.of("pumpkin","melon_slice").contains(r.item().getPath())).map(r->{String p=target.getPath();int count=p.endsWith("_log")||p.equals("kelp")?16:p.endsWith("_sapling")||p.equals("mangrove_propagule")?4:8;int wear=r.tool()==SurveyResource.Tool.AXE?16:r.tool()==SurveyResource.Tool.SHEARS?8:0;return new GatheringOffer(target,count,6000,2,r.tool(),wear);});}
 public static List<GatheringOffer> all(){return SurveyResourceCatalog.all().stream().flatMap(r->find(r.item()).stream()).toList();}
}
