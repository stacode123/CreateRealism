package net.Realism.mixin;

import com.simibubi.create.content.trains.track.BezierConnection;
import net.Realism.Interfaces.ITrackPlacementMixin;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BezierConnection.class)
public abstract class BezierConnectionMixin implements ITrackPlacementMixin {
    @Final
    @Shadow
    public Couple<BlockPos> bePositions;

    @Shadow
    public abstract double getRadius();

    @Override
    public boolean isStraight() {

        if (this.getRadius() != 0) {
            return false;
        }
        BlockPos Pos1 = bePositions.getFirst();
        BlockPos Pos2 = bePositions.getSecond();
        return ((Math.abs(Pos1.getX() - Pos2.getX()) == 2 || Math.abs(Pos1.getZ() - Pos2.getZ()) == 2) || Math.abs(Pos1.getX() - Pos2.getX()) == Math.abs(Pos1.getZ() - Pos2.getZ())) && Pos1.getY() == Pos2.getY();
    }

    @Override
    public boolean isSlope() {
        BlockPos Pos1 = bePositions.getFirst();
        BlockPos Pos2 = bePositions.getSecond();
        return (Pos1.getZ() == Pos2.getZ() || Pos1.getX() == Pos2.getX()) && Pos1.getY() != Pos2.getY();
    }
}
