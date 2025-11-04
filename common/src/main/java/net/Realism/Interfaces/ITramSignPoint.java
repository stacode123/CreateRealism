package net.Realism.Interfaces;

import net.createmod.catnip.data.Couple;
import purplecreate.tramways.content.signs.TramSignPoint;

import java.util.Set;

public interface ITramSignPoint {
    Couple<Set<TramSignPoint.SignData>> getSides();

}
