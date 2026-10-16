package dev.colonyhorizons.menu;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
public final class SupplyMenuModels {
 private SupplyMenuModels(){}
 public record Page<T>(List<T> rows,int page,boolean hasNext){public Page{rows=List.copyOf(rows);}}
 public static <T> Page<T> page(List<T> values,int page){
  if(page<0||page>Math.max(0,(values.size()-1)/16))throw new IllegalArgumentException("Page unavailable; refresh the list");
  int start=page*16;return new Page<>(values.subList(start,Math.min(start+16,values.size())),page,start+16<values.size());
 }
 public record HutRow(BlockPos position,String name,String district,String status,boolean eligible){}
 public record DistrictRow(UUID id,String name,String status,int huts,boolean paused){}
 public record RouteRow(UUID id,String source,String destination,ItemStack item,int target,int reserve,int batch,boolean paused,boolean disabled,String status,int cargo,int seconds,long delivered){
  public RouteRow{item=item.copy();} @Override public ItemStack item(){return item.copy();}
 }
 public record MenuSnapshot(int colony,String name,Page<HutRow> huts,Page<DistrictRow> districts,Page<RouteRow> routes,String problem){}
 public static String text(String value){return value.length()>256?value.substring(0,253)+"...":value;}
}

