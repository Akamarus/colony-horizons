package dev.colonyhorizons.qa.outpost;
import com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
/** Prebuilt shaft traversal isolates path geometry from excavation sequencing. */
@PrefixGameTestTemplate(false)
public final class ArcologyQuarryAccessTests {
 @GameTest(template="empty",templateNamespace="colonyhorizons",timeoutTicks=15000)
 public static void shaft_access(GameTestHelper h){
  for(int x=28;x<=39;x++)for(int z=-2;z<=6;z++)h.getLevel().setChunkForced(x,z,true);
  h.runAfterDelay(40,()->{var f=new ArcologyQuarryTests.Run(h);f.afterSetup=r->new Run(r).start();f.start();});
 }
 static final class Run {
  final ArcologyQuarryTests.Run f;final List<ProbeResult> rows=new ArrayList<>();final BlockPos[][] route=new BlockPos[2][2];final int[] leg={0,0};final boolean[] arrived={false,false};final Vec3[] previous=new Vec3[2];int ticks;
  Run(ArcologyQuarryTests.Run f){this.f=f;}
  void start(){try{
   for(int i=0;i<2;i++){
    var worker=f.workers.get(i);worker.setPaused(true);
    for(var cell:f.completeShafts.get(i).entrySet())f.level.setBlock(cell.getKey(),cell.getValue(),18);
    var bottom=f.completeShafts.get(i).entrySet().stream().filter(x->x.getValue().is(Blocks.LADDER)).map(Map.Entry::getKey).min(Comparator.comparingInt(BlockPos::getY)).orElseThrow().east();
    var top=f.quarries.get(i).getPosition().north(3);route[i]=new BlockPos[]{bottom,top};
    var entity=(EntityCitizen)worker.getEntity().orElseThrow();entity.setPos(top.getX()+0.5,top.getY(),top.getZ()+0.5);previous[i]=entity.position();final int n=i;
    entity.getCitizenAI().addTransition(new AIOneTimeEventTarget<IState>(() -> {if(leg[n]<2&&!arrived[n])arrived[n]=EntityNavigationUtils.walkToPos(entity,route[n][leg[n]],1,true);return entity.getCitizenAI().getState();}){public boolean shouldRemove(){return leg[n]>=2;}});
   }schedule();
  }catch(Throwable e){fail(e);}}
  void schedule(){f.h.runAfterDelay(20,()->{try{tick();}catch(Throwable e){fail(e);}});}
  void tick()throws Exception{
   ticks+=20;for(int i=0;i<2;i++){
    var e=f.workers.get(i).getEntity().orElseThrow();check(e.isAlive()&&previous[i].distanceTo(e.position())<=12,"Death or recovery teleport");previous[i]=e.position();if(leg[i]>=2)continue;
    if(arrived[i]){check(e.blockPosition().distSqr(route[i][leg[i]])<=4,"False shaft arrival");arrived[i]=false;leg[i]++;e.getNavigation().stop();
     if(leg[i]==2){rows.add(new ProbeResult(f.names[i]+"_shaft_access",ProbeResult.Outcome.PASS,"Native citizen traversed prebuilt full-depth shaft from exterior to lowest ladder landing and returned to exterior with recovery disabled. Exact target range, no intermediate repositioning. Does not prove excavation sequence or hauling. ticks="+ticks));ProbeReport.write(f.output,"ArcologyQuarryAccess",rows);}
    }
    if(ticks%400==0)java.nio.file.Files.writeString(f.output.resolve("shaft-access-diagnostics.txt"),f.names[i]+" tick="+ticks+" leg="+leg[i]+" pos="+e.position()+" target="+route[i][Math.min(leg[i],1)]+"\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
   }
   if(leg[0]==2&&leg[1]==2){f.h.succeed();return;}check(ticks<12000,"Full shaft route timeout; see diagnostics");schedule();
  }
  void check(boolean b,String text){if(!b)throw new IllegalStateException(text);}
  void fail(Throwable e){try{rows.add(new ProbeResult("shaft_access_failure",ProbeResult.Outcome.FAIL,e.toString()));ProbeReport.write(f.output,"ArcologyQuarryAccess",rows);}catch(Exception ignored){}f.h.fail(e.toString());}
 }
}
