package net.Realism.trains;

import com.simibubi.create.infrastructure.config.AllConfigs;
import net.Realism.config.RealismConfig;

/** Shared client and server limits for editable train settings. */
public final class TrainSettingsLimits {
    private TrainSettingsLimits() {}

    public static double maxCustomAcceleration() {
        return RealismConfig.COMMON.AllowBiggerValuesTrains.get()
                ? 50.0
                : AllConfigs.server().trains.trainAcceleration.getF();
    }

    public static TrainSettings sanitize(TrainSettings settings) {
        TrainSettings sanitized = settings.copy();
        double acceleration = Double.isFinite(sanitized.customAcceleration) ? sanitized.customAcceleration : 1.0;
        sanitized.customAcceleration = Math.max(0, Math.min(maxCustomAcceleration(), acceleration));
        return sanitized;
    }
}
