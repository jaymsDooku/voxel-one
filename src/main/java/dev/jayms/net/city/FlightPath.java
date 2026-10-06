package dev.jayms.net.city;

/** Great-circle routes in the world's x/east, z/north map coordinates (metres).
 * The current terrain is a planar map. Routes use a 6,371 km reference sphere,
 * then project back to that map. Height uses the terrain vertical coordinate system.
 * Climb and descent stay over cleared runways; cruise clears the terrain.
 */
public final class FlightPath {
    public static final double WORLD_RADIUS = 6_371_000;
    public record Sample(float x, float y, float z, float yaw, float pitch) {}
    private static double[] unit(double x, double z) {
        double lon = x / WORLD_RADIUS, lat = z / WORLD_RADIUS;
        return new double[]{Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
    }
    private static double[] point(double ax, double az, double bx, double bz, double t) {
        double[] a = unit(ax, az), b = unit(bx, bz);
        double dot = Math.max(-1, Math.min(1, a[0]*b[0]+a[1]*b[1]+a[2]*b[2]));
        double angle = Math.acos(dot);
        double[] v = new double[3];
        if (angle < 1e-7) {
            for (int i = 0; i < 3; i++) v[i] = a[i] + t * (b[i] - a[i]);
        } else if (Math.PI - angle < 1e-7) {
            // Antipodes have no unique route. Pick a stable orthogonal great circle.
            double[] axis = Math.abs(a[2]) < .9 ? new double[]{0,0,1} : new double[]{0,1,0};
            double projection = a[0]*axis[0]+a[1]*axis[1]+a[2]*axis[2], length = 0;
            for (int i = 0; i < 3; i++) { axis[i] -= projection * a[i]; length += axis[i]*axis[i]; }
            length = Math.sqrt(length);
            for (int i = 0; i < 3; i++) v[i] = a[i]*Math.cos(Math.PI*t)+axis[i]/length*Math.sin(Math.PI*t);
        } else {
            double wa = Math.sin((1-t)*angle)/Math.sin(angle), wb = Math.sin(t*angle)/Math.sin(angle);
            for (int i = 0; i < 3; i++) v[i] = wa*a[i]+wb*b[i];
        }
        double lon = Math.atan2(v[1], v[0]);
        double expected = ax / WORLD_RADIUS;
        lon += 2*Math.PI*Math.rint((expected-lon)/(2*Math.PI));
        return new double[]{lon*WORLD_RADIUS, Math.atan2(v[2], Math.hypot(v[0],v[1]))*WORLD_RADIUS};
    }
    private static double smooth(double t) { return t*t*(3-2*t); }
    private static double[] position(float ax, float ay, float az, float bx, float by, float bz, double t) {
        double cruise = Math.max(110, Math.max(ay, by) + 50);
        // Climb and descend over cleared runway land. Traverse all intervening terrain
        // at cruise height; a single low arc could cut into a nearby mountain.
        if (t < .2) {
            double u = smooth(t / .2);
            return new double[]{ax + 10*u, ay + (cruise-ay)*u, az};
        }
        if (t > .8) {
            double u = smooth((t-.8) / .2);
            return new double[]{bx - 10 + 10*u, cruise + (by-cruise)*u, bz};
        }
        var p = point(ax+10, az, bx-10, bz, (t-.2)/.6);
        return new double[]{p[0], cruise, p[1]};
    }
    public static Sample sample(float ax, float ay, float az, float bx, float by, float bz, double progress) {
        for (double n : new double[]{ax,ay,az,bx,by,bz,progress})
            if (!Double.isFinite(n)) throw new IllegalArgumentException("Invalid flight coordinates");
        if (Math.abs(az) > Math.PI*WORLD_RADIUS/2 || Math.abs(bz) > Math.PI*WORLD_RADIUS/2)
            throw new IllegalArgumentException("Flight latitude outside world");
        double t = Math.max(0,Math.min(1,progress));
        var p = position(ax,ay,az,bx,by,bz,t);
        var prev = position(ax,ay,az,bx,by,bz,Math.max(0,t-.001));
        var next = position(ax,ay,az,bx,by,bz,Math.min(1,t+.001));
        return new Sample((float)p[0], (float)p[1], (float)p[2],
                (float)Math.toDegrees(Math.atan2(next[2]-prev[2],next[0]-prev[0])),
                (float)Math.toDegrees(Math.atan2(next[1]-prev[1],Math.hypot(next[0]-prev[0],next[2]-prev[2]))));
    }
    private FlightPath() {}
}
