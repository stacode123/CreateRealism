package net.Realism.content.simulator.core;

/**
 * Minimal immutable 3D vector so the simulator core stays free of Minecraft
 * classes (and therefore JUnit-testable without a game).
 */
public record SimVec(double x, double y, double z) {

    public static final SimVec ZERO = new SimVec(0, 0, 0);

    public double dot(SimVec other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public SimVec negate() {
        return new SimVec(-x, -y, -z);
    }

    public SimVec normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-9)
            return ZERO;
        return new SimVec(x / length, y / length, z / length);
    }
}
