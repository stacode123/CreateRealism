package net.Realism.compat;

import com.simibubi.create.content.trains.entity.Train;
import net.Realism.Interfaces.ITramSignPoint;
import net.Realism.RealismExpectPlatform;
import net.Realism.RealismMod;
import net.Realism.mixin.mixinaccesors.TramSignDataAccessor;
import net.Realism.trains.SignalFinder;
import net.Realism.trains.etcs.ETCS;
import net.createmod.catnip.data.Couple;
import net.minecraft.nbt.CompoundTag;
import purplecreate.tramways.content.signs.TramSignPoint;
import purplecreate.tramways.content.signs.demands.SignDemand;
import purplecreate.tramways.content.signs.demands.TemporaryEndSignDemand;
import purplecreate.tramways.content.signs.demands.TemporarySpeedSignDemand;

import java.util.*;

public class TramwaysCompat {
    private static final boolean tramwaysLoaded = RealismExpectPlatform.isModLoaded("tramways");


    public static List<ETCS.SpeedLimit> processTramSigns(SignalFinder.SignalScanResult s, double Mspeed, Train train) {

        try {
            // All Tramways-specific code moved here
            // This is only accessed through reflection when the mod is loaded
            return TramwaysCompat.processTramSignsImpl(s,Mspeed,train);
        } catch (Throwable e) {
            RealismMod.LOGGER.error("Failed to process tram signs", e);
            return new ArrayList<>();
        }
    }

    // Static initialization ensures this only runs if Tramways exists

    public static List<ETCS.SpeedLimit> processTramSignsImpl(SignalFinder.SignalScanResult s, double maxSpeed,Train train) {
        List<ETCS.SpeedLimit> cachedSpeedLimits = new ArrayList<>();
        try {

            cachedSpeedLimits = new ArrayList<>();
            boolean first = true;
            int LastLimit = 300;
            for (SignalFinder.TramSignInfo sign : s.getTramSigns()) {
                if (sign == null || sign.getSign() == null) continue;
                Couple<Set<TramSignPoint.SignData>> sides = null;
                if (sign.getSign() instanceof ITramSignPoint) {
                    sides = ((ITramSignPoint)sign.getSign()).getSides();
                } else {
                    // Use reflection as a fallback
                    try {
                        java.lang.reflect.Field sidesField = TramSignPoint.class.getDeclaredField("sides");
                        sidesField.setAccessible(true);
                        sides = (Couple<Set<TramSignPoint.SignData>>) sidesField.get(sign.getSign());
                    } catch (Exception e) {
                        RealismMod.LOGGER.error("Error accessing sides field: " + e.getMessage());
                    }
                }

                if (sides == null) continue;
                CompoundTag tag = null;
                SignDemand demand = null;

                //noinspection ReassignedVariable
                if (sides.get(sign.getPrimary()) == null) continue;
                for (TramSignPoint.SignData signD : new HashSet<>(sides.get(sign.getPrimary()))) {
                    if (signD == null) continue;
                    if (!(signD instanceof TramSignDataAccessor accessor)) {
                        try {
                            java.lang.reflect.Field demandFieldExtra = TramSignPoint.SignData.class.getDeclaredField("demandExtra");
                            java.lang.reflect.Field demandField = TramSignPoint.SignData.class.getDeclaredField("demand");
                            demandFieldExtra.setAccessible(true);
                            demandField.setAccessible(true);
                            tag = (CompoundTag) demandFieldExtra.get(signD);
                            demand = (SignDemand) demandField.get(signD);

                        } catch (Exception e) {
                            RealismMod.LOGGER.error("Error accessing DemandExtra field: " + e.getMessage());
                        }
                    } else {


                        demand = accessor.getDemand();
                        if (demand == null) continue;

                        tag = accessor.getDemandExtra();
                    }
                    if (tag != null && tag.contains("Throttle")) {
                        double signSpeedLimit = tag.getInt("Throttle");
                        signSpeedLimit = (signSpeedLimit / 100) * maxSpeed;
                        cachedSpeedLimits.add(new ETCS.SpeedLimit(sign.getDistance(), signSpeedLimit));
                        if (demand instanceof TemporarySpeedSignDemand && !first) {
                            LastLimit = (int) cachedSpeedLimits.get(cachedSpeedLimits.size()-2).speedLimit();
                        } else if (demand instanceof TemporarySpeedSignDemand) {
//
                            double speed = train.maxSpeed()*20;
                            LastLimit = (int) (train.throttle*speed*3.6);
                        }
                        first= false;}
                    if (demand instanceof TemporaryEndSignDemand && !first) {
                        cachedSpeedLimits.add(new ETCS.SpeedLimit(sign.getDistance(), LastLimit));}
                    else if (demand instanceof TemporaryEndSignDemand && first) {
                       double tempSpeedLimit = 300.0;
                        try {
                            // Fix: Look for the field in the train's class, not the interface class
                            java.lang.reflect.Field tempSPeedLimitField = train.getClass().getDeclaredField("tramways$storedPermanent");
                            tempSPeedLimitField.setAccessible(true);
                            tempSpeedLimit = (double) tempSPeedLimitField.get(train);
                        }
                        catch (Exception e) {
                            RealismMod.LOGGER.error("Error accessing tempSpeedLimit field: " + e.getMessage());
                        }
                        cachedSpeedLimits.add(new ETCS.SpeedLimit(sign.getDistance(),tempSpeedLimit*3.6*train.maxSpeed()*20));
                    }
                    first = false;
                }
            }
        } catch (Exception e) {
            RealismMod.LOGGER.error("Error processing tram signs: " + e.getMessage(), e);
        }
        return cachedSpeedLimits;
    }

    public static boolean isLoaded() {
        return tramwaysLoaded;
    }

    public static Object createTramSignInfo(UUID signId, double distance, Object signType, boolean primary) {
        if (!tramwaysLoaded) return null;
        try {
            return TramwaysCompatImpl.createTramSignInfo(signId, distance, signType, primary);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isTramSignPoint(Object obj) {
        if (!tramwaysLoaded) return false;
        try {
            return TramwaysCompatImpl.isTramSignPoint(obj);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPrimary(Object tramSign, Object node) {
        if (!tramwaysLoaded) return false;
        try {
            return TramwaysCompatImpl.isPrimary(tramSign, node);
        } catch (Exception e) {
            return false;
        }
    }

    // Only loaded if Tramways exists
    private static class TramwaysCompatImpl {
        static Object createTramSignInfo(UUID signId, double distance, Object signType, boolean primary) {
            return new net.Realism.trains.SignalFinder.TramSignInfo(
                    signId, distance, signType, primary);
        }

        static boolean isTramSignPoint(Object obj) {
            return obj instanceof purplecreate.tramways.content.signs.TramSignPoint;
        }

        static boolean isPrimary(Object tramSign, Object node) {
            return ((purplecreate.tramways.content.signs.TramSignPoint)tramSign).isPrimary((com.simibubi.create.content.trains.graph.TrackNode)node);
        }
    }
}