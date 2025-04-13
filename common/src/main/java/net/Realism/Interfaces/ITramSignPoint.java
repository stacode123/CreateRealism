package net.Realism.Interfaces;

import com.simibubi.create.foundation.utility.Couple;
import purplecreate.tramways.content.signs.TramSignPoint;

import java.util.Set;

public interface ITramSignPoint {
    Couple<Set<TramSignPoint.SignData>> getSides();

}
