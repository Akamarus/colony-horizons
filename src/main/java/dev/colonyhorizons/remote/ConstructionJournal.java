// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Native order custody, independent of native order numbering across settlements. */
public final class ConstructionJournal {
 private ConstructionJournal(){}
 public enum State { INTENT, QUEUED, COMPLETE, RECOVERY }
 public record Entry(UUID building,int target,State state,int nativeOrder){
  public Entry{Objects.requireNonNull(building);Objects.requireNonNull(state);if(target<1||target>5||nativeOrder<0||(state==State.QUEUED||state==State.COMPLETE)&&nativeOrder==0||state==State.INTENT&&nativeOrder!=0)throw new IllegalArgumentException("Invalid native construction journal");}
  public UUID operation(){return UUID.nameUUIDFromBytes((building+":"+target).getBytes(StandardCharsets.UTF_8));}
  public Entry state(State next){return new Entry(building,target,next,nativeOrder);}
 }
}
