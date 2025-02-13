package net.Realism.mixinaccesors;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TrackPlacement.PlacementInfo.class, remap = false)
public interface PlacementInfoAccessor {
    @Accessor
    BezierConnection getCurve();
}
