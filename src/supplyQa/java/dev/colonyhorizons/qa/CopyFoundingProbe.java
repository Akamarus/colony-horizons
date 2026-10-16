package dev.colonyhorizons.qa;

import dev.colonyhorizons.remote.*;
import dev.colonyhorizons.remote.RemoteModels.*;
import dev.colonyhorizons.mayor.*;
import dev.colonyhorizons.menu.*;
import dev.colonyhorizons.network.SupplyMenuPayloads.*;
import dev.colonyhorizons.supply.*;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Opt-in QA only: existing owner and citizens, explicitly prepared ground/finite stock in a COPY. */
@EventBusSubscriber(modid="colonyhorizons_qa")
public final class CopyFoundingProbe {
 private static final boolean FREE=System.getProperty("colonyhorizons.copyFounders","legacy").equals("free-four");
 private static final Map<Integer,String> originalJobs=new HashMap<>();private static final Map<Integer,Long> originalHomes=new HashMap<>();
 public static volatile boolean done;private static boolean started;private static int ticks;private static UUID settlement;private static Map<Integer,UUID> originals;private static int originalBuildings;private static Map<BlockPos,Integer> homeBuildings;private static long startedAt;
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){
  if(!Boolean.getBoolean("colonyhorizons.copyFounding")||done)return;var server=event.getServer();if(server.getPlayerList().getPlayers().isEmpty())return;var owner=server.getPlayerList().getPlayers().getFirst();if(owner.tickCount<100)return;
  try{if(!started){started=true;var path=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString();require(path.contains("copied-colony-client")&&path.contains("copy-founding"),"Refuse any world outside the disposable copy runner");setup(owner);return;}if(++ticks%20!=0)return;observe(owner);}catch(Throwable failure){System.out.println("COPY_FOUNDING_FAIL "+failure);failure.printStackTrace();server.halt(false);done=true;}
 }
 private static void setup(ServerPlayer owner)throws Exception{
  var l=owner.serverLevel();l.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);var colony=IColonyManager.getInstance().getColonyByDimension(1,l.dimension());require(colony!=null&&owner.getUUID().equals(colony.getPermissions().getOwner()),"Actual single-player owner profile must match the copied colony");
  var data=RemoteDevelopmentData.get(l);originals=new LinkedHashMap<>();homeBuildings=new LinkedHashMap<>();
  var baseline=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("qa-founding-baseline.dat");
  if(data.all().isEmpty()){
   if(FREE){int extra=0;for(var c:colony.getCitizenManager().getCitizens()){c.setPaused(true);if(!c.isChild()&&c.getJob()==null){
    var pos=colony.getCenter().offset((extra++%4)*4,35,(extra/4)*4);var bp=com.ldtteam.structurize.storage.StructurePacks.getBlueprint("Arcology Horizons","fundamentals/builder1.blueprint",l.registryAccess());require(l.getBlockState(pos).isAir(),"QA workplace is empty");require(ModBlocks.blockHutBuilder.setup(owner,l,pos,bp,com.ldtteam.structurize.api.RotationMirror.NONE,true,"Arcology Horizons","fundamentals/builder1.blueprint"),"QA native Home workplace");require(colony.getServerBuildingManager().getBuilding(pos).getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(c),"Every original adult holds a native job");
   }}}
   for(var c:colony.getCitizenManager().getCitizens())originals.put(c.getId(),c.getUUID());for(var b:colony.getServerBuildingManager().getBuildings().values())homeBuildings.put(b.getPosition(),b.getBuildingLevel());
   var proof=new CompoundTag();var people=new ListTag();originals.forEach((id,uuid)->{var entry=new CompoundTag();entry.putInt("id",id);entry.putUUID("uuid",uuid);var c=colony.getCitizenManager().getCivilian(id);String job=c.getJob()==null?"none":c.getJob().getClass().getName()+":"+c.getWorkBuilding().getPosition();long home=c.getHomeBuilding()==null?Long.MIN_VALUE:c.getHomeBuilding().getPosition().asLong();originalJobs.put(id,job);originalHomes.put(id,home);entry.putString("job",job);entry.putLong("home",home);people.add(entry);});proof.put("people",people);var huts=new ListTag();homeBuildings.forEach((pos,level)->{var entry=new CompoundTag();entry.putLong("pos",pos.asLong());entry.putInt("level",level);huts.add(entry);});proof.put("huts",huts);NbtIo.write(proof,baseline);
  }else{
   var proof=NbtIo.read(baseline);require(proof!=null,"Independent pre-founding baseline required on reload");for(var entry:proof.getList("people",10)){var t=(CompoundTag)entry;originals.put(t.getInt("id"),t.getUUID("uuid"));originalJobs.put(t.getInt("id"),t.getString("job"));originalHomes.put(t.getInt("id"),t.getLong("home"));}for(var entry:proof.getList("huts",10)){var t=(CompoundTag)entry;homeBuildings.put(BlockPos.of(t.getLong("pos")),t.getInt("level"));}
  }
  originalBuildings=homeBuildings.size();startedAt=l.getGameTime();
  if(!data.all().isEmpty()){require(data.all().size()==1,"Exactly one copied founding record");settlement=data.all().getFirst().id();require(data.find(settlement).orElseThrow().phase()==Phase.OPERATING,"Saved copied outpost operating");System.out.println("COPY_FOUNDING_RELOAD identities="+originals.size()+" buildings="+originalBuildings);observe(owner);return;}
  require(!originals.isEmpty()&&originalBuildings>0,"Existing colony baseline");
  var warehouse=colony.getServerBuildingManager().getBuildings().values().stream().filter(b->b instanceof BuildingWareHouse&&b.isBuilt()).map(b->(BuildingWareHouse)b).findFirst().orElseThrow();
  owner.connection.teleport(warehouse.getPosition().getX()+0.5,warehouse.getPosition().getY()+2,warehouse.getPosition().getZ()+0.5,0,0);
  // Disable automatic Home hiring in this disposable experiment and release five real adults.
  colony.getCitizenManager().getCitizens().forEach(c->c.setPaused(true));
  var party=FREE?List.<ICitizenData>of():colony.getCitizenManager().getCitizens().stream().filter(c->!c.isChild()).sorted(Comparator.comparingInt(ICitizenData::getId)).limit(5).toList();require(FREE||party.size()==5,"Five existing adults for legacy transfer");
  for(var c:party){c.updateEntityIfNecessary();if(c.getWorkBuilding()!=null)c.getWorkBuilding().getFirstModuleOccurance(WorkerBuildingModule.class).removeCitizen(c);require(c.getJob()==null&&c.getWorkBuilding()==null,"Native unassignment frees founder");}
  int cx=((colony.getCenter().getX()+2048)>>4)<<4,cz=(colony.getCenter().getZ()>>4)<<4;
  int y=64;for(int x=cx-48;x<=cx+63;x++)for(int z=cz-48;z<=cz+63;z++){l.getChunk(x>>4,z>>4);y=Math.max(y,l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));}require(y<220,"Bounded test terrain height");
  for(int x=cx-48;x<=cx+63;x++)for(int z=cz-48;z<=cz+63;z++){int ground=l.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)-1;for(int v=ground;v<y;v++)l.setBlock(new BlockPos(x,v,z),Blocks.DIRT.defaultBlockState(),2);for(int v=y;v<y+24;v++)l.setBlock(new BlockPos(x,v,z),Blocks.AIR.defaultBlockState(),2);}
  var center=new BlockPos(cx,y,cz);var ids=party.stream().map(ICitizenData::getId).toList();var request=new SiteRequest(1,"Copied Colony Forestry",center,new BoundingBox(cx-48,l.getMinBuildHeight(),cz-48,cx+63,l.getMaxBuildHeight()-1,cz+63),warehouse.getPosition(),ids,FREE?0:ids.getFirst(),5,new Budget(Map.of(),Map.of()),FREE?FoundingRoster.Policy.FREE_FOUR_V1:FoundingRoster.Policy.LEGACY_SELECTED);var draft=RemoteSitePlanner.review(owner,request);require(draft.blockers().isEmpty(),"Prepared copy site: "+draft.blockers());var temporary=new Development(draft.id(),owner.getUUID(),draft,Mode.APPROVAL,Phase.PROPOSED,0);
  var stock=new HashMap<>(FoundingPlacement.bill(l,temporary));FoundingSupplies.bill(request).forEach((k,n)->stock.merge(k,n,Integer::sum));FoundingConstructionSupplies.operatingReserve().forEach((k,n)->stock.merge(k,n,Integer::sum));for(var plan:draft.buildings())MayorUpgrades.bill(l,plan,plan.blueprint()).forEach((k,n)->stock.merge(k,n,Integer::sum));stock.merge(new ItemKey(new ItemStack(Items.BREAD)),192,Integer::sum);RemoteCare.starterMedicines().forEach((k,n)->stock.merge(k,24,Integer::sum));
  // Keep every existing item. New QA racks and a finite preapproval preload are explicitly logged.
  for(int i=0;i<16;i++){var pos=warehouse.getPosition().offset(i%4*2,20,i/4*2);require(l.getBlockState(pos).isAir(),"QA rack location must be empty");l.setBlockAndUpdate(pos,ModBlocks.blockRack.defaultBlockState());warehouse.registerBlockPosition(l.getBlockState(pos),pos,l);}
  var inventory=WarehouseInventory.resolve(l,warehouse.getTileEntity()).inventory();for(var item:stock.entrySet())require(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(inventory,item.getKey().stack().copyWithCount(item.getValue()),false).isEmpty(),"Finite QA warehouse capacity");
  var form=new CompoundTag();form.putString("name","Copied Colony Forestry");form.putLong("home",warehouse.getPosition().asLong());form.putLong("center",center.asLong());if(!FREE)form.putIntArray("citizens",ids);form.putInt("cap",5);var budget=new ListTag();stock.forEach((item,n)->{var t=new CompoundTag();t.put("item",item.stack().save(l.registryAccess()));t.putInt("allowance",n);t.putInt("reserve",0);budget.add(t);});form.put("budget",budget);
  var session=SupplyMenuSessions.open(owner,1);var review=SupplyMenuSessions.handle(owner,new MenuRequest(session.session(),1,Action.REVIEW_REMOTE,1,0,0,0,"",List.of(),new UUID(0,0),0,0,0,form));require(review.ok()&&review.data().getCompound("remoteReview").getBoolean("ready"),"Live copied owner menu review: "+review.message());
  var approved=SupplyMenuSessions.handle(owner,new MenuRequest(session.session(),2,Action.APPROVE_REMOTE,1,0,0,0,"",List.of(),new UUID(0,0),0,0,0,form));require(approved.ok(),"Live copied owner founding approval: "+approved.message());settlement=data.all().getFirst().id();for(var c:party)c.setPaused(false);
  l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,l.getServer());l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());serverCommand(owner,"tick sprint 260000t");
  System.out.println("COPY_FOUNDING_APPROVED originalCitizens="+originals+" originalBuildings="+originalBuildings+" center="+center+" preparedGround=true finitePreload="+stock.values().stream().mapToInt(Integer::intValue).sum()+" qaRacks=16");
 }
 private static void observe(ServerPlayer owner)throws Exception{var l=owner.serverLevel();var data=RemoteDevelopmentData.get(l);var r=data.find(settlement).orElseThrow();var colony=IColonyManager.getInstance().getColonyByDimension(1,l.dimension());for(var e:originals.entrySet())require(colony.getCitizenManager().getCivilian(e.getKey())!=null&&colony.getCitizenManager().getCivilian(e.getKey()).getUUID().equals(e.getValue()),"Original citizen UUID preserved: "+e.getKey());for(var e:homeBuildings.entrySet()){var b=colony.getServerBuildingManager().getBuilding(e.getKey());require(b!=null&&b.getBuildingLevel()==e.getValue(),"Original Home hut and level preserved");}require(r.phase()!=Phase.RECOVERY_NEEDED,"Copied founding must not require recovery");
  if(r.phase()!=Phase.OPERATING){if(ticks%1000==0)System.out.println("COPY_FOUNDING_PROGRESS phase="+r.phase()+" anchors="+data.anchorReceipts(settlement).size()+" orders="+data.construction(settlement));require(l.getGameTime()-startedAt<250000,"Copied founding timed out");return;}
  if(FREE){require(colony.getCitizenManager().getCurrentCitizenCount()==originals.size()+4,"Exactly four added citizens");require(FoundingRoster.resolve(data,settlement).stream().noneMatch(m->originals.containsValue(m.entityId())),"No Home citizen taken");for(var e:originals.entrySet()){var c=colony.getCitizenManager().getCivilian(e.getKey());String job=c.getJob()==null?"none":c.getJob().getClass().getName()+":"+c.getWorkBuilding().getPosition();long home=c.getHomeBuilding()==null?Long.MIN_VALUE:c.getHomeBuilding().getPosition().asLong();require((originalJobs.get(e.getKey()).equals("none")||originalJobs.get(e.getKey()).equals(job))&&originalHomes.get(e.getKey())==home,"Home job and housing preserved id="+e.getKey()+" before="+originalJobs.get(e.getKey())+"/"+originalHomes.get(e.getKey())+" after="+job+"/"+home);}}
  require(MayorService.available(l,settlement)&&MayorLogistics.ready(l,settlement),"Original Mayor and local courier active");require(data.journey(settlement).orElseThrow().state()==FoundingTravel.State.ARRIVED,"Protected founding journey finished");for(var plan:r.draft().buildings()){var building=colony.getServerBuildingManager().getBuilding(plan.anchor());require(building!=null&&building.isBuilt()&&building.getBuildingLevel()==1&&FoundingPlacement.isPaidAnchor(l,plan),"Ten original native new buildings completed");}require(data.construction(settlement).size()==(FREE?8:10)&&data.construction(settlement).values().stream().allMatch(e->e.state()==ConstructionJournal.State.COMPLETE),"Exactly ten completed operations");
  serverCommand(owner,"tick sprint stop");data.checkpoint(l);NativeFoundingCheckpoint.save(l,colony);l.getServer().halt(false);System.out.println("COPY_FOUNDING_PASS originalCitizenUUIDs="+originals.size()+" currentNativePopulation="+colony.getCitizenManager().getCitizens().size()+" nativeBuildings="+colony.getServerBuildingManager().getBuildings().size()+" founders=5 huts=10 phase="+r.phase());done=true;
 }
 private static void serverCommand(ServerPlayer owner,String command){owner.server.getCommands().performPrefixedCommand(owner.server.createCommandSourceStack(),command);}
 private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
