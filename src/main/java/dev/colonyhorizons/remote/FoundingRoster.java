// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;

import net.minecraft.nbt.*;
import java.util.*;

/** Immutable founding intent is separate from the native identities created for it. */
public final class FoundingRoster {
    private FoundingRoster() {}
    public enum Policy { LEGACY_SELECTED, FREE_FOUR_V1, RESOURCE_FOUR_V2 }
    public enum Role { MAYOR, BUILDER, COURIER, FORESTER, PRODUCER }
    public record Member(Role role,int citizenId,UUID entityId) {
        public Member { Objects.requireNonNull(role);Objects.requireNonNull(entityId);if(citizenId<=0)throw new IllegalArgumentException("Invalid founder identity"); }
    }
    public static boolean free(RemoteModels.SiteRequest request){return request.foundingPolicy()!=Policy.LEGACY_SELECTED;}
    public static Role productionRole(RemoteModels.SiteRequest request){return request.foundingPolicy()==Policy.RESOURCE_FOUR_V2?Role.PRODUCER:Role.FORESTER;}
    static void validatePolicy(RemoteModels.SiteRequest request,List<Member> roster){validate(roster);if(roster.stream().noneMatch(m->m.role()==productionRole(request)))throw new IllegalArgumentException("Founder role does not match founding policy");}
    public static boolean warehouseFirst(RemoteModels.Development r){return free(r.draft().request())||r.draft().request().citizenIds().size()==5;}
    public static List<Member> resolve(RemoteDevelopmentData data,UUID outpost) {
        var r=data.find(outpost).orElseThrow();var q=r.draft().request();
        if(free(q))return data.founderRoster(outpost);
        var party=data.approvedParty(outpost);var workers=q.citizenIds().stream().filter(id->id!=q.mayorId()).toList();var result=new ArrayList<Member>();
        for(int id:q.citizenIds())if(party.containsKey(id))result.add(new Member(id==q.mayorId()?Role.MAYOR:id==workers.getFirst()?Role.BUILDER:workers.size()==4&&id==workers.getLast()?Role.COURIER:Role.FORESTER,id,party.get(id)));
        return List.copyOf(result);
    }
    public static Optional<Member> member(RemoteDevelopmentData data,UUID outpost,Role role){return resolve(data,outpost).stream().filter(m->m.role()==role).findFirst();}
    public static List<Integer> citizenIds(RemoteDevelopmentData data,UUID outpost){
        var q=data.find(outpost).orElseThrow().draft().request();return free(q)?resolve(data,outpost).stream().map(Member::citizenId).toList():q.citizenIds();
    }
    public static int mayorId(RemoteDevelopmentData data,UUID outpost){var q=data.find(outpost).orElseThrow().draft().request();return free(q)?member(data,outpost,Role.MAYOR).map(Member::citizenId).orElse(0):q.mayorId();}
    static List<Member> validate(List<Member> roster){
        if(!roster.stream().map(Member::role).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of(Role.MAYOR,Role.BUILDER,Role.COURIER))||roster.size()!=4||roster.stream().map(Member::role).distinct().count()!=4||roster.stream().map(Member::citizenId).distinct().count()!=4||roster.stream().map(Member::entityId).distinct().count()!=4)throw new IllegalArgumentException("Four distinct founding roles and identities required");
        return roster.stream().sorted(Comparator.comparing(Member::role)).toList();
    }
    static ListTag save(List<Member> roster){var list=new ListTag();for(var m:roster){var t=new CompoundTag();t.putString("role",m.role().name());t.putInt("citizen",m.citizenId());t.putUUID("entity",m.entityId());list.add(t);}return list;}
    static List<Member> load(CompoundTag t){
        if(!t.contains("founderRoster",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid founder roster");
        var raw=t.getList("founderRoster",Tag.TAG_COMPOUND);if(raw.size()!=4)throw new IllegalArgumentException("Incomplete founder roster");var result=new ArrayList<Member>();
        for(var value:raw){var m=(CompoundTag)value;if(!m.contains("role",Tag.TAG_STRING)||!m.contains("citizen",Tag.TAG_INT)||!m.hasUUID("entity"))throw new IllegalArgumentException("Invalid founder receipt");result.add(new Member(Role.valueOf(m.getString("role")),m.getInt("citizen"),m.getUUID("entity")));}
        return validate(result);
    }
}
