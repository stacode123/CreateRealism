package net.Realism.mixin;

import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.Realism.trains.RouteGuidance;
import net.Realism.trains.RouteGuidanceManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Manual route guidance steering. Only kicks in while no schedule destination is active
 * ({@code destination == null}) so Create's own autonomous navigation is never affected.
 * Throttle/speed are left entirely to the player.
 */
@Mixin(value = Navigation.class, remap = false)
public abstract class NavigationMixin {

    @Shadow
    public Train train;

    @Shadow
    public GlobalStation destination;

    @Inject(method = "control", at = @At("HEAD"), cancellable = true)
    private void realism$routeGuidanceControl(TravellingPoint mp, CallbackInfoReturnable<TravellingPoint.ITrackSelector> cir) {
        if (this.destination != null) return;

        RouteGuidance guidance = RouteGuidanceManager.getForTrain(this.train);
        if (guidance == null || !guidance.valid) return;

        TravellingPoint.ITrackSelector fallback = mp.steer(this.train.manualSteer, new Vec3(0, 1, 0));
        cir.setReturnValue(RouteGuidanceManager.createSteeringSelector(this.train, guidance, fallback));
    }
}
