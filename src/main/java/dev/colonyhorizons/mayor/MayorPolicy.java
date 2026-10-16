// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;

import dev.colonyhorizons.remote.RemoteModels.*;
import java.util.*;

/** Pure selection only. The coordinator must persist approval/reservation before dispatch. */
public final class MayorPolicy {
 private MayorPolicy(){}
 public enum Priority { OPERATING_SHORTAGE, EXPLICIT_TARGET, CONSTRUCTION, UPGRADE }
 public enum Action { NONE, PROPOSE, EXECUTE, BLOCKED }
 public record Candidate(UUID operation,Priority priority,boolean approved,boolean withinScope,Map<ItemKey,Integer> cost){
  public Candidate{
   Objects.requireNonNull(operation);Objects.requireNonNull(priority);cost=Map.copyOf(cost);
   if(cost.size()>128||cost.values().stream().anyMatch(n->n<=0||n>1_000_000))throw new IllegalArgumentException("Invalid Mayor cost");
  }
 }
 public record Decision(Action action,UUID operation,String reason){}
 public static int spendable(int stock,int homeReserve,int reserved){
  quantities(stock,homeReserve,reserved);return (int)Math.max(0,(long)stock-homeReserve-reserved);
 }
 public static int unmet(int target,int localStock,int incoming){
  quantities(target,localStock,incoming);return (int)Math.max(0,(long)target-localStock-incoming);
 }
 private static void quantities(int... values){for(int n:values)if(n<0)throw new IllegalArgumentException("Negative stock quantity");}
 public static Decision choose(Mode mode,List<Candidate> candidates,Map<ItemKey,Integer> available){
  Objects.requireNonNull(mode);if(candidates.size()>128||available.size()>128)throw new IllegalArgumentException("Mayor plan is too large");
  available.forEach((key,n)->{Objects.requireNonNull(key);quantities(n);});
  var identities=new HashSet<UUID>();for(var c:candidates)if(!identities.add(c.operation()))throw new IllegalArgumentException("Duplicate Mayor operation");
  // A blocked food/tool shortage must not let lower-priority expansion consume its supplies.
  var next=candidates.stream().min(Comparator.comparing(Candidate::priority).thenComparing(Candidate::operation));
  if(next.isEmpty())return new Decision(Action.NONE,null,"No work needed");var c=next.orElseThrow();
  if(!c.withinScope())return new Decision(Action.BLOCKED,c.operation(),"Outside approved development limits");
  if(mode==Mode.APPROVAL&&!c.approved())return new Decision(Action.PROPOSE,c.operation(),"Needs your approval");
  if(c.cost().entrySet().stream().anyMatch(e->e.getValue()>available.getOrDefault(e.getKey(),0)))return new Decision(Action.BLOCKED,c.operation(),"Needs supplies within the approved allowance and Home reserve");
  return new Decision(Action.EXECUTE,c.operation(),"Ready");
 }
}
