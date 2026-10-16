// SPDX-License-Identifier: GPL-3.0-only
package dev.colonyhorizons.remote;
import com.minecolonies.api.colony.*;

/** Creates native adult data only; protected travel owns entity release. */
public final class NativeFounderFactory {
 private NativeFounderFactory(){}
 public static ICitizenData register(IColony colony){
  var citizen=colony.getCitizenManager().createAndRegisterCivilianData();
  long women=colony.getCitizenManager().getCitizens().stream().filter(c->c!=citizen&&c.isFemale()).count();
  citizen.setGenderAndGenerateName(women<(colony.getCitizenManager().getCurrentCitizenCount()-1)/2.0);
  if(citizen.isChild())throw new IllegalStateException("Native founding registration was not an adult");
  return citizen;
 }
}
