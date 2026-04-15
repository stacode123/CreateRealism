package net.Realism.trains;

import com.simibubi.create.content.trains.track.TrackPlacement;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.Interfaces.ITrackPlacementInterface;
import net.Realism.Interfaces.ITrackPlacementMixin;
import net.Realism.config.RealismConfig;
import net.Realism.mixin.mixinaccesors.PlacementInfoAccessor;
import net.createmod.catnip.data.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.GameType;

public class TrackOverlay {
    public static Pair<MutableComponent,MutableComponent> getText(PlacementInfoAccessor placement){
        if (placement == null) {
            return null;
        }
        if (placement.getCurve() == null) {
            return null;
        }
        boolean Straight = ((ITrackPlacementMixin) placement.getCurve()).isStraight();
        boolean Slope = ((ITrackPlacementMixin) placement.getCurve()).isSlope();
        float mxspeedP = (AllConfigs.server().trains.poweredTrainTopSpeed).getF() * 3.6f;
        float mxspeedU = (AllConfigs.server().trains.trainTopSpeed).getF() * 3.6f;
        float mxspeed = Math.max(mxspeedP, mxspeedU);
        double mphc = (RealismConfig.CLIENT.OverlayMPH.get() ? 0.621371192 : 1);
        double radius = placement.getCurve().getRadius();
        double handleLength = placement.getCurve().getHandleLength();
        if (mxspeed == 0) {
            mxspeed = 1;
        }
        int speedVal = (int) (getSpeed(radius)*mphc);
        int percentVal = (int) ((speedVal/mphc/mxspeed)*100);
        MutableComponent radiusText = Component.translatable("realism.overlay.speed_and_percent", speedVal, percentVal);
        if (RealismConfig.CLIENT.OverlayMPH.get()){
            radiusText = Component.translatable("realism.overlay.speed_and_percent_mph", speedVal, percentVal);
        }
        if (radius == 0) {
            int speedVal2 = (int) (getSpeed(((ITrackPlacementMixin) placement.getCurve()).getMinRadius())*mphc);
            int percentVal2 = (int) (speedVal2/mxspeed*100/mphc);
            radiusText = Component.translatable("realism.overlay.speed_and_percent", speedVal2, percentVal2);
            if (RealismConfig.CLIENT.OverlayMPH.get()){
                radiusText = Component.translatable("realism.overlay.speed_and_percent_mph", speedVal2, percentVal2);
            }
        }
        if (Straight) {
            return null;

        }
        if (Slope) {
            radiusText = Component.translatable("realism.overlay.speed_and_percent", Math.round(mxspeed), 100);
            if (RealismConfig.CLIENT.OverlayMPH.get()){
                radiusText = Component.translatable("realism.overlay.speed_and_percent_mph", Math.round(mxspeed*mphc), 100);
            }
        }

        ITrackPlacementInterface mixin = (ITrackPlacementInterface) TrackPlacement.cached;
        double grade = mixin.getGrade();
        MutableComponent gradeText = Component.translatable("realism.overlay.grade_percent", Math.round(grade * 100));
        return (Pair.of(radiusText,gradeText));


    }

    private static int getSpeed(double radius){
        if (radius < 160) {
            return (int) (Math.pow(radius,0.75) * 6.6);
        }
        else {
            return (int) (Math.sqrt(radius) * 23.5);
        }

    }
}
