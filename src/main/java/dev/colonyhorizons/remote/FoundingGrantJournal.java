// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import java.util.*;

/** A creation intent cannot be retried after restart without its exact native receipt. */
public record FoundingGrantJournal(State state,List<FoundingRoster.Member> receipts,Optional<FoundingRoster.Role> intent,boolean consumed,Set<UUID> originalCitizens,List<BlockPos> returnTargets,int returned,FoundingRoster.Role productionRole) {
 public FoundingGrantJournal(State state,List<FoundingRoster.Member> receipts,Optional<FoundingRoster.Role> intent,boolean consumed,Set<UUID> originalCitizens){this(state,receipts,intent,consumed,originalCitizens,List.of(),0,FoundingRoster.Role.FORESTER);}
 public FoundingGrantJournal(State state,List<FoundingRoster.Member> receipts,Optional<FoundingRoster.Role> intent,boolean consumed,Set<UUID> originalCitizens,List<BlockPos> returnTargets,int returned){this(state,receipts,intent,consumed,originalCitizens,returnTargets,returned,FoundingRoster.Role.FORESTER);}
 public static FoundingGrantJournal reserved(RemoteModels.SiteRequest request,Set<UUID> originals){return new FoundingGrantJournal(State.RESERVED,List.of(),Optional.empty(),false,originals,List.of(),0,FoundingRoster.productionRole(request));}
 private static FoundingRoster.Role role(int index,FoundingRoster.Role producer){return switch(index){case 0->FoundingRoster.Role.MAYOR;case 1->FoundingRoster.Role.BUILDER;case 2->FoundingRoster.Role.COURIER;case 3->producer;default->throw new IllegalArgumentException("Invalid grant index");};}
 public enum State { RESERVED, CREATING, REGISTERED, ARRIVED, RECOVERY_REQUIRED }
 public FoundingGrantJournal {
  if(productionRole!=FoundingRoster.Role.FORESTER&&productionRole!=FoundingRoster.Role.PRODUCER)throw new IllegalArgumentException("Invalid producer role");
  Objects.requireNonNull(state);receipts=List.copyOf(receipts);intent=Objects.requireNonNull(intent);originalCitizens=Set.copyOf(originalCitizens);returnTargets=returnTargets.stream().map(BlockPos::immutable).toList();
  if(returned<0||returned>returnTargets.size()||!returnTargets.isEmpty()&&(returnTargets.size()!=receipts.size()||new HashSet<>(returnTargets).size()!=returnTargets.size()||intent.isPresent()))throw new IllegalArgumentException("Invalid return journal");
  if(receipts.size()>4||originalCitizens.size()>10000||receipts.stream().map(FoundingRoster.Member::citizenId).distinct().count()!=receipts.size()||receipts.stream().map(FoundingRoster.Member::entityId).distinct().count()!=receipts.size())throw new IllegalArgumentException("Invalid grant receipts");
  for(int i=0;i<receipts.size();i++)if(receipts.get(i).role()!=role(i,productionRole)||originalCitizens.contains(receipts.get(i).entityId()))throw new IllegalArgumentException("Invalid grant role or reused native identity");
  if(intent.isPresent()&&(receipts.size()==4||intent.orElseThrow()!=role(receipts.size(),productionRole))||!consumed&&(state!=State.RESERVED||!receipts.isEmpty()||intent.isPresent())||state==State.RESERVED&&consumed||(state==State.REGISTERED||state==State.ARRIVED)&&(receipts.size()!=4||intent.isPresent()))throw new IllegalArgumentException("Invalid grant state");
 }
 public FoundingGrantJournal begin(){if(intent.isPresent()||receipts.size()==4||state==State.RECOVERY_REQUIRED)throw new IllegalStateException("Grant requires recovery");return new FoundingGrantJournal(State.CREATING,receipts,Optional.of(role(receipts.size(),productionRole)),true,originalCitizens,returnTargets,returned,productionRole);}
 public FoundingGrantJournal receipt(FoundingRoster.Member member){if(intent.isEmpty()||member.role()!=intent.orElseThrow())throw new IllegalStateException("Missing creation intent");var next=new ArrayList<>(receipts);next.add(member);return new FoundingGrantJournal(next.size()==4?State.REGISTERED:State.CREATING,next,Optional.empty(),true,originalCitizens,returnTargets,returned,productionRole);}
 public FoundingGrantJournal recovery(){return new FoundingGrantJournal(State.RECOVERY_REQUIRED,receipts,intent,consumed,originalCitizens,returnTargets,returned,productionRole);}
 public FoundingGrantJournal returning(List<BlockPos> targets){return new FoundingGrantJournal(state,receipts,intent,consumed,originalCitizens,targets,returned,productionRole);}
 public FoundingGrantJournal returnedOne(){return new FoundingGrantJournal(state,receipts,intent,consumed,originalCitizens,returnTargets,returned+1,productionRole);}
 public CompoundTag save(){var t=new CompoundTag();if(productionRole==FoundingRoster.Role.PRODUCER)t.putString("productionRole",productionRole.name());t.putString("state",state.name());t.putBoolean("consumed",consumed);t.put("receipts",FoundingRoster.save(receipts));intent.ifPresent(role->t.putString("intent",role.name()));var originals=new ListTag();originalCitizens.stream().sorted().forEach(id->originals.add(NbtUtils.createUUID(id)));t.put("originals",originals);if(!returnTargets.isEmpty()){t.putLongArray("returnTargets",returnTargets.stream().mapToLong(BlockPos::asLong).toArray());t.putInt("returned",returned);}return t;}
 public static FoundingGrantJournal load(CompoundTag t){
  if(!t.contains("state",Tag.TAG_STRING)||!t.contains("consumed",Tag.TAG_BYTE)||!t.contains("receipts",Tag.TAG_LIST)||!t.contains("originals",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid grant journal");
  var raw=t.getList("receipts",Tag.TAG_COMPOUND);if(raw.size()!=((ListTag)t.get("receipts")).size())throw new IllegalArgumentException("Invalid receipt type");var members=new ArrayList<FoundingRoster.Member>();for(var v:raw){var m=(CompoundTag)v;if(!m.contains("role",Tag.TAG_STRING)||!m.contains("citizen",Tag.TAG_INT)||!m.hasUUID("entity"))throw new IllegalArgumentException("Invalid grant receipt");members.add(new FoundingRoster.Member(FoundingRoster.Role.valueOf(m.getString("role")),m.getInt("citizen"),m.getUUID("entity")));}
  var ids=new HashSet<UUID>();var originals=t.getList("originals",Tag.TAG_INT_ARRAY);if(originals.size()!=((ListTag)t.get("originals")).size())throw new IllegalArgumentException("Invalid native identities");for(var id:originals)if(!ids.add(NbtUtils.loadUUID(id)))throw new IllegalArgumentException("Duplicate original identity");
  if(t.contains("returnTargets")!=t.contains("returned")||t.contains("returnTargets")&&(!t.contains("returnTargets",Tag.TAG_LONG_ARRAY)||!t.contains("returned",Tag.TAG_INT)||t.getLongArray("returnTargets").length==0))throw new IllegalArgumentException("Invalid return journal fields");
  if(t.contains("productionRole")&&!t.contains("productionRole",Tag.TAG_STRING))throw new IllegalArgumentException("Invalid producer role type");
  if(t.contains("intent")&&!t.contains("intent",Tag.TAG_STRING))throw new IllegalArgumentException("Invalid creation intent");
  return new FoundingGrantJournal(State.valueOf(t.getString("state")),members,t.contains("intent")?Optional.of(FoundingRoster.Role.valueOf(t.getString("intent"))):Optional.empty(),t.getBoolean("consumed"),ids,t.contains("returnTargets")?Arrays.stream(t.getLongArray("returnTargets")).mapToObj(BlockPos::of).toList():List.of(),t.getInt("returned"),t.contains("productionRole")?FoundingRoster.Role.valueOf(t.getString("productionRole")):FoundingRoster.Role.FORESTER);
 }
}
