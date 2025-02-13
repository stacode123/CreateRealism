package net.Realism.mixin;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.foundation.utility.Couple;
import net.Realism.Interfaces.TrackPlacementMixinStraight;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BezierConnection.class, remap = false)
public abstract class BezierConnectionMixin implements TrackPlacementMixinStraight {
    @Shadow
    public Couple<BlockPos> tePositions;
    @Shadow
    private boolean resolved;

    @Shadow
    private void resolve() {
    }

    @Shadow
    public double getRadius() {
        return radius;
    }

    @Shadow
    private double radius;

    @Override
    public boolean isStraight() {
        if (!resolved) {
            resolve();
        }
        if (this.getRadius() != 0) {
            return false;
        }
        BlockPos Pos1 = tePositions.getFirst();
        BlockPos Pos2 = tePositions.getSecond();
        return ((Math.abs(Pos1.getX() - Pos2.getX()) == 2 || Math.abs(Pos1.getZ() - Pos2.getZ()) == 2) || Math.abs(Pos1.getX() - Pos2.getX()) == Math.abs(Pos1.getZ() - Pos2.getZ())) && Pos1.getY() == Pos2.getY();
    }
}
