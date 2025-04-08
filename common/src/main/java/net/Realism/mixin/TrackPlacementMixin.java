// common/src/main/java/net/Realism/mixin/TrackPlacementMixin.java
package net.Realism.mixin;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackPlacement;
import net.Realism.Interfaces.ITrackPlacementInterface;
import net.Realism.mixinaccesors.PlacementInfoAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = TrackPlacement.PlacementInfo.class)
public class TrackPlacementMixin implements ITrackPlacementInterface, PlacementInfoAccessor {
    @Shadow
    Vec3 end1;

    @Shadow
    Vec3 end2;

    @Shadow
    private BezierConnection curve;

    @Override
    public double getGrade() {
        if (end1 == null || end2 == null) {
            return 0;
        }

        double deltaY = end2.y - end1.y;
        double deltaX = end2.x - end1.x;
        double deltaZ = end2.z - end1.z;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (horizontalDistance == 0) {
            return 0;
        }

        return deltaY / horizontalDistance;
    }

    @Override
    public BezierConnection getCurve() {
        return curve;
    }


}