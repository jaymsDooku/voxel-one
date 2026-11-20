package dev.jayms.combat;

/** Immutable finite vector. Distances are world blocks; velocities are blocks per tick. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) > 1e9)
            throw new IllegalArgumentException("Invalid vector");
    }
    public Vec3 add(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
    public Vec3 subtract(Vec3 b) { return new Vec3(x - b.x, y - b.y, z - b.z); }
    public Vec3 scale(double n) { return new Vec3(x * n, y * n, z * n); }
    public double dot(Vec3 b) { return x * b.x + y * b.y + z * b.z; }
    public double length() { return Math.sqrt(dot(this)); }
    public Vec3 unit() {
        double n = length();
        if (n < 1e-9) throw new IllegalArgumentException("Zero direction");
        return scale(1 / n);
    }
    public double distance(Vec3 b) { return subtract(b).length(); }
}
