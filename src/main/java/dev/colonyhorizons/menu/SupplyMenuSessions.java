package dev.colonyhorizons.menu;

import java.util.*;

import net.minecraft.server.level.ServerPlayer;

import net.minecraft.nbt.CompoundTag;

import net.minecraft.resources.ResourceLocation;

import dev.colonyhorizons.network.SupplyMenuPayloads.*;

public final class SupplyMenuSessions {

 private static final Map<UUID,Session> SESSIONS=new HashMap<>();

 private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();

 private static final class Session{

  UUID id=UUID.randomUUID();int colony;ResourceLocation dimension;long lastTick,lastNumber,windowTick,refreshTick=Long.MIN_VALUE/2;int actions;

  net.minecraft.core.BlockPos surveyHut;
  MenuRequest lastRequest;MenuReply lastReply;OutpostMenuPlanner.DistrictDraft draft;
  dev.colonyhorizons.remote.RemoteModels.SiteDraft remoteDraft;dev.colonyhorizons.remote.RemoteModels.Mode remoteMode;CompoundTag remoteChoices,mayor,remoteReview;dev.colonyhorizons.mayor.MayorProposals.Entry upgrade;dev.colonyhorizons.remote.ExpansionProposal expansion;UUID expansionSettlement;

  Session(ServerPlayer p,int c){colony=c;dimension=p.level().dimension().location();lastTick=p.serverLevel().getGameTime();windowTick=lastTick;}

 }

 private SupplyMenuSessions(){}

 public static void close(UUID actor){SESSIONS.remove(actor);}public static void clear(){SESSIONS.clear();}

 public static MenuReply open(ServerPlayer actor,int colony){

  SupplyMenuService.owned(actor,colony);var s=new Session(actor,colony);var reply=snapshot(actor,s,0,0,0,0,"");SESSIONS.put(actor.getUUID(),s);return reply;

 }

 public static MenuReply handle(ServerPlayer actor,MenuRequest r){

  try{

   if(r.action()==Action.OPEN){var prior=SESSIONS.get(actor.getUUID());long now=actor.serverLevel().getGameTime();if(prior!=null&&now-prior.lastTick<20)return error(r,"Please wait before reopening");if(!r.remote().contains("surveyHut",4))throw new IllegalArgumentException("Open this menu at the Exploration Hut");var hut=net.minecraft.core.BlockPos.of(r.remote().getLong("surveyHut"));dev.colonyhorizons.surveyor.SurveyorAccess.require(actor,r.colony(),hut);var s=new Session(actor,r.colony());s.surveyHut=hut;var opened=snapshot(actor,s,0,0,0,0,"");SESSIONS.put(actor.getUUID(),s);var data=opened.data();data.putUUID("opening",r.session());return new MenuReply(opened.session(),0,true,opened.message(),data);}

   var s=SESSIONS.get(actor.getUUID());long tick=actor.serverLevel().getGameTime();

   if(s==null||!s.id.equals(r.session())||s.colony!=r.colony()||!s.dimension.equals(actor.level().dimension().location())||tick-s.lastTick>12000)throw new IllegalArgumentException("Menu expired; reopen it from the Exploration Hut");

   // Closing only discards this actor's session; stale views and throttling must not trap it.
   if(r.action()==Action.CLOSE){close(actor.getUUID());return new MenuReply(s.id,r.number(),true,"",new CompoundTag());}

   SupplyMenuService.owned(actor,s.colony);
   if(s.surveyHut!=null)dev.colonyhorizons.surveyor.SurveyorAccess.require(actor,s.colony,s.surveyHut);

   if(r.equals(s.lastRequest))return s.lastReply;

   if(r.number()<=s.lastNumber)throw new IllegalArgumentException("This action is outdated; refresh the menu");

   if(tick-s.windowTick>=20){s.windowTick=tick;s.actions=0;}

   if(++s.actions>4)throw new IllegalArgumentException("Please wait before trying again");

   if(r.action()==Action.REFRESH&&tick-s.refreshTick<20)throw new IllegalArgumentException("Please wait before refreshing");

   s.lastTick=tick;s.lastNumber=r.number();s.lastRequest=r;s.lastReply=error(r,"Action interrupted; refresh before retrying");

   // Validate the requested view before any mutation; stale pages cannot create work.

   snapshot(actor,s,r.number(),r.hutPage(),r.districtPage(),r.routePage(),"");

   String message="";

   switch(r.action()){
    case START_GATHERING -> {if(s.surveyHut==null)throw new IllegalArgumentException("Open the Exploration Hut");dev.colonyhorizons.surveyor.GatheringService.start(actor,s.colony,s.surveyHut,ResourceLocation.parse(r.remote().getString("target")),r.remote().getBoolean("repeat"));message="Gathering started";}
    case RECALL_GATHERING -> {if(s.surveyHut==null)throw new IllegalArgumentException("Open the Exploration Hut");var expedition=dev.colonyhorizons.surveyor.GatheringSavedData.get(actor.serverLevel()).find(r.selected()).orElseThrow(()->new IllegalArgumentException("Unknown expedition"));if(!expedition.hut().equals(s.surveyHut)||expedition.colony()!=s.colony)throw new IllegalArgumentException("Open the expedition's hut");dev.colonyhorizons.surveyor.GatheringService.recall(actor,r.selected(),r.remote().getLong("revision"));message="Explorer recalled";}
    case START_SURVEY -> {if(s.surveyHut==null)throw new IllegalArgumentException("Open the Exploration Hut");dev.colonyhorizons.surveyor.SurveyService.start(actor,s.colony,s.surveyHut,ResourceLocation.parse(r.remote().getString("target")));message="Survey started";}
    case CANCEL_SURVEY -> {if(s.surveyHut==null)throw new IllegalArgumentException("Open the Exploration Hut");dev.colonyhorizons.surveyor.SurveyService.cancel(actor,r.selected(),r.remote().getLong("revision"));message="Explorer recalled";}

    case REFRESH -> {s.refreshTick=tick;if(s.mayor!=null)s.mayor=RemoteMenuService.mayor(actor,s.colony,s.mayor.getUUID("id"));}
    case REMOTE_OPTIONS -> s.remoteChoices=RemoteMenuService.choices(actor,s.colony,r.remote());
    case REVIEW_REMOTE -> {s.remoteDraft=null;s.remoteReview=null;s.remoteDraft=RemoteMenuService.review(actor,s.colony,r.remote());s.remoteMode=r.remote().getBoolean("automatic")?dev.colonyhorizons.remote.RemoteModels.Mode.AUTOMATIC:dev.colonyhorizons.remote.RemoteModels.Mode.APPROVAL;s.remoteReview=RemoteMenuService.reviewView(actor,s.remoteDraft);message="Review the site and founding supplies";}
    case APPROVE_REMOTE -> {var result=RemoteMenuService.approve(actor,s.colony,s.remoteDraft,s.remoteMode);if(!result.blockers().isEmpty())throw new IllegalArgumentException(String.join("; ",result.blockers()));s.mayor=RemoteMenuService.mayor(actor,s.colony,s.remoteDraft.id());s.remoteDraft=null;s.remoteReview=null;message="Founding approved";}
    case REVIEW_EXPANSION -> {if(s.mayor==null||!s.mayor.getUUID("id").equals(r.selected()))throw new IllegalArgumentException("Open the Mayor page first");s.upgrade=null;s.expansion=dev.colonyhorizons.remote.RemoteExpansion.review(actor,r.selected(),r.remote().getString("role"));s.expansionSettlement=r.selected();message="Review expansion";}
    case APPROVE_EXPANSION,REJECT_EXPANSION -> {if(s.expansion==null||!s.expansion.plan().operationId().equals(r.selected()))throw new IllegalArgumentException("Review this expansion first");dev.colonyhorizons.remote.RemoteExpansion.decide(actor,s.expansionSettlement,r.selected(),s.expansion.revision(),r.action()==Action.APPROVE_EXPANSION);s.mayor=RemoteMenuService.mayor(actor,s.colony,s.expansionSettlement);s.expansion=null;message="Expansion decision saved";}
    case REVIEW_UPGRADE -> {if(s.mayor==null||!s.mayor.getUUID("id").equals(r.selected()))throw new IllegalArgumentException("Open the Mayor page first");s.expansion=null;s.upgrade=dev.colonyhorizons.mayor.MayorUpgrades.review(actor,r.selected(),r.remote().getUUID("building"));message="Review upgrade";}
    case APPROVE_UPGRADE,REJECT_UPGRADE -> {if(s.upgrade==null||!s.upgrade.id().equals(r.selected()))throw new IllegalArgumentException("Review this upgrade first");dev.colonyhorizons.mayor.MayorUpgrades.decide(actor,s.upgrade.settlement(),s.upgrade.id(),s.upgrade.revision(),r.action()==Action.APPROVE_UPGRADE);s.mayor=RemoteMenuService.mayor(actor,s.colony,s.upgrade.settlement());s.upgrade=null;message="Upgrade decision saved";}
    case VIEW_MAYOR -> s.mayor=RemoteMenuService.mayor(actor,s.colony,r.selected());
    case PAUSE_REMOTE,RESUME_REMOTE -> {if(s.mayor==null||!s.mayor.getUUID("id").equals(r.selected()))throw new IllegalArgumentException("Open the Mayor page first");RemoteMenuService.pause(actor,s.colony,r.selected(),s.mayor.getLong("revision"),r.action()==Action.PAUSE_REMOTE);s.mayor=RemoteMenuService.mayor(actor,s.colony,r.selected());message="Outpost updated";}

    case REVIEW_OUTPOST -> {s.draft=null;s.draft=OutpostMenuPlanner.review(actor,s.colony,r.name(),new HashSet<>(r.huts()));message="Review all included huts before activating";}

    case CREATE_OUTPOST -> {if(s.draft==null)throw new IllegalArgumentException("Review the outpost first");OutpostMenuPlanner.activate(actor,s.colony,s.draft);s.draft=null;message="Outpost activated";}

    case PAUSE_OUTPOST,RESUME_OUTPOST -> SupplyMenuService.pauseOutpost(actor,s.colony,r.selected(),r.action()==Action.PAUSE_OUTPOST);

    case CREATE_ROUTE -> {if(r.huts().size()!=2)throw new IllegalArgumentException("Choose source and destination huts");SupplyRouteActions.create(actor,s.colony,r.huts().get(0),r.huts().get(1),r.target(),r.reserve(),r.batch());message="Supply route created";}

    case PAUSE_ROUTE,RESUME_ROUTE,STOP_ROUTE -> SupplyRouteActions.change(actor,s.colony,r.selected(),r.action()==Action.PAUSE_ROUTE?SupplyRouteActions.RouteAction.PAUSE:r.action()==Action.RESUME_ROUTE?SupplyRouteActions.RouteAction.RESUME:SupplyRouteActions.RouteAction.STOP);

    default -> throw new IllegalArgumentException("Unsupported action");

   }

   MenuReply reply;

   try{reply=snapshot(actor,s,r.number(),r.hutPage(),r.districtPage(),r.routePage(),message);}

   catch(RuntimeException refreshFailure){

    if(r.action()==Action.REFRESH||r.action()==Action.REVIEW_OUTPOST)throw refreshFailure;

    // A committed action must never be reported as failed because its refreshed view failed.

    LOG.warn("Supply menu action committed, but refresh failed",refreshFailure);

    reply=new MenuReply(s.id,r.number(),true,message.isEmpty()?"Action saved. Reopen the menu to refresh.":message,new CompoundTag());

   }

   s.lastReply=reply;return reply;

  }catch(IllegalArgumentException|SecurityException e){return cacheError(actor,r,e.getMessage());}

   catch(RuntimeException e){LOG.error("Supply menu action failed",e);return cacheError(actor,r,"Supply data needs attention; ask the server operator to check the log");}

 }

 private static MenuReply cacheError(ServerPlayer actor,MenuRequest r,String message){var reply=error(r,message);var s=SESSIONS.get(actor.getUUID());if(s!=null&&r.equals(s.lastRequest))s.lastReply=reply;return reply;}

 private static MenuReply error(MenuRequest r,String message){return new MenuReply(r.session(),r.number(),false,message,new CompoundTag());}

 private static MenuReply snapshot(ServerPlayer actor,Session s,long number,int hp,int dp,int rp,String message){

  var t=SupplyMenuWire.write(SupplyMenuService.snapshot(actor,s.colony,hp,dp,rp),actor.registryAccess());
  if(s.surveyHut!=null)t.put("survey",SurveyMenuService.view(actor,s.colony,s.surveyHut));

  for(var tag:t.getList("districts",10)){var row=(CompoundTag)tag;row.putBoolean("remote",dev.colonyhorizons.remote.RemoteDevelopmentData.get(actor.serverLevel()).find(row.getUUID("id")).filter(r->r.owner().equals(actor.getUUID())&&r.draft().request().colonyId()==s.colony).isPresent());}
  if(s.remoteChoices!=null)t.put("remoteChoices",s.remoteChoices.copy());if(s.mayor!=null)t.put("mayor",s.mayor.copy());if(s.remoteReview!=null)t.put("remoteReview",s.remoteReview.copy());
  if(s.expansion!=null){var e=s.expansion;var u=new CompoundTag();u.putBoolean("expansion",true);u.putUUID("id",e.plan().operationId());u.putUUID("settlement",s.expansionSettlement);u.putString("title","New "+(e.plan().role().equals("home")?"home":"forester hut"));u.putString("state",e.state().name());u.putString("details","At "+e.plan().anchor().toShortString()+" inside approved land. Native builder; warehouse supplies. No additional workers are recruited.");var costs=new net.minecraft.nbt.ListTag();e.cost().forEach((item,count)->{var row=new CompoundTag();row.putString("text",count+" "+item.stack().getHoverName().getString());costs.add(row);});u.put("costs",costs);t.put("upgrade",u);}
  if(s.upgrade!=null){var entry=s.upgrade;var u=new CompoundTag();u.putUUID("id",entry.id());u.putUUID("settlement",entry.settlement());var plan=dev.colonyhorizons.remote.RemoteDevelopmentData.get(actor.serverLevel()).buildings(entry.settlement()).stream().filter(p->p.operationId().equals(entry.building())).findFirst().orElseThrow();u.putString("title",plan.role()+" - level "+entry.from()+" to "+entry.target());u.putString("state",entry.state().name());u.putString("details","Hut at "+plan.anchor().toShortString()+". Local builder; existing allowance and Home reserves apply.");var costs=new net.minecraft.nbt.ListTag();entry.cost().forEach((item,count)->{var row=new CompoundTag();row.putString("text",count+" "+item.stack().getHoverName().getString());costs.add(row);});u.put("costs",costs);t.put("upgrade",u);}
  if(s.draft!=null){var d=new CompoundTag();d.putString("name",s.draft.record().name());d.putInt("huts",s.draft.record().buildings().size());d.putInt("chunks",s.draft.record().approvedChunks().size());var positions=new net.minecraft.nbt.ListTag();for(var b:s.draft.record().buildings().values()){var p=new CompoundTag();p.putLong("pos",b.anchor().asLong());var nativeBuilding=SupplyMenuService.owned(actor,s.colony).getServerBuildingManager().getBuilding(b.anchor());p.putString("name",nativeBuilding==null?"Unavailable hut":SupplyMenuModels.text(nativeBuilding.getCustomName().isBlank()?net.minecraft.network.chat.Component.translatable(nativeBuilding.getBuildingDisplayName()).getString():nativeBuilding.getCustomName()));positions.add(p);}d.put("buildings",positions);t.put("draft",d);}

  var reply=new MenuReply(s.id,number,true,message,t);var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{MenuReply.CODEC.encode(buf,reply);}finally{buf.release();}return reply;

 }

}
