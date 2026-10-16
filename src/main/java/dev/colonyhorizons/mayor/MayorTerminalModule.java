// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.mayor;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;

/** Separate appointment slot at an approved paid staging hut; never auto-hires. */
public final class MayorTerminalModule extends WorkerBuildingModule {
 private boolean restoringNativeJob;
 public MayorTerminalModule(){super(MayorJobRegistration.MAYOR.get(),Skill.Knowledge,Skill.Intelligence,true,b->MayorService.isStation(b)?1:0);setHiringMode(HiringMode.MANUAL);}
 @Override public boolean assignCitizen(ICitizenData citizen){
  if(citizen==null)return false;
  // Native colonies deserialize while their world is null. Rebind only an existing
  // saved Mayor at this exact workplace; this cannot create a new appointment.
  boolean restore=getBuilding().getColony().getWorld()==null&&citizen.getColony()==getBuilding().getColony()
   &&citizen.getJob() instanceof JobMayor&&getBuilding().getPosition().equals(citizen.getJob().getBuildingPos());
  if(!restore&&!MayorService.mayAssign(getBuilding(),citizen))return false;
  if(hasAssignedCitizen(citizen))return citizen.getJob() instanceof JobMayor&&citizen.getJob().getWorkModule()==this;
  restoringNativeJob=restore;
  try{return super.assignCitizen(citizen);}finally{restoringNativeJob=false;}
 }
 @Override public int getModuleMax(){return restoringNativeJob?1:super.getModuleMax();}
 @Override public void onColonyTick(IColony colony){} // Owner-authorized appointments only.
 @Override public void setHiringMode(HiringMode mode){super.setHiringMode(HiringMode.MANUAL);}
 @Override public HiringMode getHiringMode(){return HiringMode.MANUAL;}
}
