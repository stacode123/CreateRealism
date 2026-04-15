package net.Realism.mixin;

import com.simibubi.create.content.trains.track.BezierConnection;
import net.Realism.Interfaces.ITrackPlacementMixin;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BezierConnection.class)
public abstract class BezierConnectionMixin implements ITrackPlacementMixin {
    @Final
    @Shadow
    public Couple<BlockPos> bePositions;
    @Final
    @Shadow
    public Couple<Vec3> starts;
    @Final
    @Shadow
    public Couple<Vec3> normals;
    @Final
    @Shadow
    public Couple<Vec3> axes;

    @Shadow
    public abstract double getRadius();

    @Shadow
    public abstract double getHandleLength();

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

    @Override
    public double getMinRadius() {
        double directRadius = getRadius();
        if (directRadius > 0.0 && Double.isFinite(directRadius)) {
            return directRadius;
        }

        Vec3 p0 = starts.getFirst();
        Vec3 p3 = starts.getSecond();

        double h = Math.max(getHandleLength(), 1e-9);
        Vec3 p1 = p0.add(axes.getFirst().normalize().scale(h));
        Vec3 p2 = p3.add(axes.getSecond().normalize().scale(h));

        // 1) Coarse scan to bracket curvature maxima
        final int samples = 96;
        double bestT = 0.0;
        double bestK = 0.0;

        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double k = curvatureAt(p0, p1, p2, p3, t);
            if (k > bestK) {
                bestK = k;
                bestT = t;
            }
        }

        // 2) Refine around the best sample (golden section search on curvature)
        double dt = 1.0 / samples;
        double a = Math.max(0.0, bestT - 2.0 * dt);
        double b = Math.min(1.0, bestT + 2.0 * dt);
        double maxK = maximizeCurvature(p0, p1, p2, p3, a, b, 20);

        if (!Double.isFinite(maxK) || maxK <= 1e-12) {
            return 1e9; // effectively straight / extremely large radius
        }

        double rMin = 1.0 / maxK;
        return Double.isFinite(rMin) && rMin > 0.0 ? rMin : 1e9;
    }

    @Unique
    private static double maximizeCurvature(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double a, double b, int iters) {
        // Golden-section search maximizing kappa(t)
        final double gr = (Math.sqrt(5.0) - 1.0) * 0.5;
        double c = b - gr * (b - a);
        double d = a + gr * (b - a);
        double fc = curvatureAt(p0, p1, p2, p3, c);
        double fd = curvatureAt(p0, p1, p2, p3, d);

        for (int i = 0; i < iters; i++) {
            if (fc < fd) {
                a = c;
                c = d;
                fc = fd;
                d = a + gr * (b - a);
                fd = curvatureAt(p0, p1, p2, p3, d);
            } else {
                b = d;
                d = c;
                fd = fc;
                c = b - gr * (b - a);
                fc = curvatureAt(p0, p1, p2, p3, c);
            }
        }

        double mid = 0.5 * (a + b);
        double fm = curvatureAt(p0, p1, p2, p3, mid);
        return Math.max(fm, Math.max(fc, fd));
    }
    @Unique
    private static double curvatureAt(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        Vec3 d1 = bezierD1(p0, p1, p2, p3, t);
        Vec3 d2 = bezierD2(p0, p1, p2, p3, t);

        double speed = d1.length();
        if (speed <= 1e-12 || !Double.isFinite(speed)) {
            return 0.0;
        }

        double num = d1.cross(d2).length();
        double den = speed * speed * speed;
        if (den <= 1e-18 || !Double.isFinite(num) || !Double.isFinite(den)) {
            return 0.0;
        }

        double k = num / den;
        return Double.isFinite(k) && k > 0.0 ? k : 0.0;
    }

    @Unique
    private static Vec3 bezierD1(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        Vec3 a = p1.subtract(p0).scale(3.0 * u * u);
        Vec3 b = p2.subtract(p1).scale(6.0 * u * t);
        Vec3 c = p3.subtract(p2).scale(3.0 * t * t);
        return a.add(b).add(c);
    }

    @Unique
    private static Vec3 bezierD2(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        Vec3 a = p2.subtract(p1.scale(2.0)).add(p0).scale(6.0 * (1.0 - t));
        Vec3 b = p3.subtract(p2.scale(2.0)).add(p1).scale(6.0 * t);
        return a.add(b);
    }

}

