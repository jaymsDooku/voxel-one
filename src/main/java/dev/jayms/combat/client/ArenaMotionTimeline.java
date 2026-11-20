package dev.jayms.combat.client;

import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.practice.PracticeMatch.Fighter;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Presentation of confirmed arena snapshots on a monotonic 20 Hz clock. */
final class ArenaMotionTimeline {
    private static final double NANOS_PER_TICK = 50_000_000;
    private static final int MAX_SAMPLES = 12;
    private static final int MAX_BRIDGE_TICKS = 3;
    private static final long STALE_NANOS = 250_000_000;
    private static final double BODY_RADIUS = .31, BODY_HEIGHT = 1.8, CONTACT_EPSILON = .0001;
    private static final double LOCAL_DELAY = 1;
    private static final double HURT_TICKS = 5;

    record Cursor(double tick, double fadeTick) {}
    record Frame(Cursor local, Cursor remote) {}
    record Presented(Fighter fighter, float x, float y, float z, float yaw, float pitch,
                     float walkPhase, float walkAmount, float hurt) {
        boolean visible() { return fighter.health() > 0 || hurt > 0; }
    }

    private record Point(double x, double y, double z) {
        static Point of(Fighter fighter) { return new Point(fighter.x(), fighter.y(), fighter.z()); }
        Point blend(Point next, double t) {
            return new Point(x + (next.x - x) * t, y + (next.y - y) * t, z + (next.z - z) * t);
        }
        double distance(Point point) {
            double dx = x - point.x, dy = y - point.y, dz = z - point.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private record Route(Point[] points, double length) {
        static Route between(Point left, Point right, long gap) {
            double distance = left.distance(right);
            if (gap > MAX_BRIDGE_TICKS || distance > 1.75 * gap
                    || !BasaltCourtyard.contains(left.x, left.y, left.z)
                    || !BasaltCourtyard.contains(right.x, right.y, right.z)) return null;
            if (distance == 0 || clearSweep(left, right)) return new Route(new Point[]{left, right}, distance);
            if (gap != 1) return null;
            // The simulation clips Y, then X, then Z. Follow that route where the straight path hits terrain.
            var y = new Point(left.x, right.y, left.z);
            var x = new Point(right.x, right.y, left.z);
            if (!clearSweep(left, y) || !clearSweep(y, x) || !clearSweep(x, right)) return null;
            return new Route(new Point[]{left, y, x, right}, left.distance(y) + y.distance(x) + x.distance(right));
        }

        Point at(double t) {
            double travel = length * t;
            for (int i = 1; i < points.length; i++) {
                Point from = points[i - 1], to = points[i];
                double segment = from.distance(to);
                if (segment > 0 && travel <= segment) return from.blend(to, travel / segment);
                travel -= segment;
            }
            return points[points.length - 1];
        }
    }

    private record Sample(long tick, Fighter fighter, Point point, long hurtTick, double stride, Route fromPrevious) {}

    private static final class Track {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        Fighter latest;
        long confirmedHurt = -1, skippedHurt = -1;

        void skipStaleHistory() {
            samples.clear();
            // Reanchoring can move effect time backward, so previously observed hits must not replay.
            skippedHurt = confirmedHurt;
        }
    }

    private final Map<Integer, Track> tracks = new HashMap<>();
    private long latestTick = -1, anchorNanos;
    private double anchorTick, localTick, remoteTick, localFade, remoteFade, deliveryJitter;

    void observe(long tick, List<Fighter> fighters, long now) {
        if (tick <= latestTick) return;
        boolean reanchor = latestTick < 0 || tick - latestTick > MAX_BRIDGE_TICKS
                || now - anchorNanos >= STALE_NANOS;
        if (reanchor) {
            tracks.values().forEach(Track::skipStaleHistory);
            anchorTick = tick;
            deliveryJitter = 0;
            // A late packet can be older than the clock after a stall. Keep frame time from rewinding.
            localTick = latestTick < 0 ? tick - LOCAL_DELAY : Math.max(localTick, tick - LOCAL_DELAY);
            remoteTick = latestTick < 0 ? tick - 1 : Math.max(remoteTick, tick - 1);
            localFade = localTick;
            remoteFade = remoteTick;
        } else {
            double intervalError = Math.abs((now - anchorNanos) / NANOS_PER_TICK - (tick - latestTick));
            deliveryJitter += (Math.min(2, intervalError) - deliveryJitter) * .2;
            double projected = clock(now);
            double correction = Math.max(-.15, Math.min(.35, tick - projected));
            anchorTick = Math.max(tick - .75, Math.min(tick + .75, projected + correction));
        }
        anchorNanos = now;
        latestTick = tick;
        tracks.keySet().removeIf(id -> fighters.stream().noneMatch(fighter -> fighter.id() == id));
        for (Fighter fighter : fighters) append(tick, fighter);
    }

    Frame frame(long now) {
        double estimated = clock(now);
        // Reserve a second remote tick only for jitter. Local movement never adds more than one.
        double remoteDelay = 1 + Math.max(0, Math.min(1, (deliveryJitter - .12) * 2.5));
        double localTarget = Math.max(latestTick, estimated) - LOCAL_DELAY;
        double remoteTarget = estimated - remoteDelay;
        localTick = Math.max(localTick, Math.min(latestTick, localTarget));
        remoteTick = Math.max(remoteTick, Math.min(latestTick, remoteTarget));
        localFade = Math.max(localFade, Math.min(latestTick + HURT_TICKS, localTarget));
        remoteFade = Math.max(remoteFade, Math.min(latestTick + HURT_TICKS, remoteTarget));
        return new Frame(new Cursor(localTick, localFade), new Cursor(remoteTick, remoteFade));
    }

    Presented sample(int id, Cursor cursor) {
        var track = tracks.get(id);
        if (track == null || track.samples.isEmpty()) return null;
        Sample left = track.samples.peekFirst(), right = left;
        double tick = Math.max(cursor.tick, left.tick);
        long hurtTick = -1;
        for (Sample candidate : track.samples) {
            if (candidate.hurtTick <= tick) hurtTick = Math.max(hurtTick, candidate.hurtTick);
            if (candidate.tick <= tick) left = candidate;
            if (right.tick < tick) right = candidate;
        }
        Fighter discrete = left.fighter;
        Point point = left.point;
        float yaw = discrete.yaw(), pitch = discrete.pitch(), amount = walkAmount(discrete);
        double stride = left.stride;
        if (left != right && right.fromPrevious != null && left.tick < tick) {
            double progress = Math.min(1, (tick - left.tick) / (right.tick - left.tick));
            point = right.fromPrevious.at(progress);
            yaw += (float) (Math.IEEEremainder(right.fighter.yaw() - yaw, 360) * progress);
            pitch += (float) ((right.fighter.pitch() - pitch) * progress);
            stride += (right.stride - stride) * progress;
            amount += (float) ((walkAmount(right.fighter) - amount) * progress);
        }
        // The pose holds at the freshest sample during a stall. Visual feedback keeps expiring.
        float hurt = hurtTick < 0 ? 0 : injuryStrength(Math.max(tick, cursor.fadeTick) - hurtTick);
        return new Presented(discrete, (float) point.x, (float) point.y, (float) point.z, yaw, pitch,
                (float) (stride % (2 * Math.PI)), amount, hurt);
    }

    private void append(long tick, Fighter fighter) {
        var track = tracks.computeIfAbsent(fighter.id(), ignored -> new Track());
        Fighter prior = track.latest;
        if (prior != null && prior.motionEpoch() != fighter.motionEpoch()) {
            track.samples.clear();
            prior = null;
        }
        Sample previous = track.samples.peekLast();
        Point next = Point.of(fighter);
        long hurt = fighter.hurtTick() > 0 && fighter.hurtTick() <= tick ? fighter.hurtTick() : -1;
        if (prior != null && fighter.motionEpoch() == 0 && hurt < 0 && fighter.health() < prior.health()) hurt = tick;
        track.confirmedHurt = Math.max(track.confirmedHurt, hurt);
        if (hurt <= track.skippedHurt) hurt = -1;
        Route route = previous == null ? null : Route.between(previous.point, next, tick - previous.tick);
        double stride = route == null ? 0 : previous.stride
                + Math.hypot(next.x - previous.point.x, next.z - previous.point.z) * 2.5;
        track.samples.addLast(new Sample(tick, fighter, next, hurt, stride, route));
        if (track.samples.size() > MAX_SAMPLES) track.samples.removeFirst();
        track.latest = fighter;
    }

    private double clock(long now) { return anchorTick + Math.max(0, now - anchorNanos) / NANOS_PER_TICK; }

    private static float walkAmount(Fighter fighter) {
        double speed = Math.hypot(fighter.vx(), fighter.vz());
        if (!Double.isFinite(speed) || speed == 0) return fighter.moving() ? 1 : 0;
        return (float) Math.min(1, speed / .22);
    }

    private static float injuryStrength(double age) {
        if (age < 0 || age >= HURT_TICKS) return 0;
        return (float) Math.min(1, (HURT_TICKS - age) / (HURT_TICKS - 2));
    }

    private static boolean clearSweep(Point left, Point right) {
        int minX = (int) Math.floor(Math.min(left.x, right.x) - BODY_RADIUS);
        int maxX = (int) Math.floor(Math.max(left.x, right.x) + BODY_RADIUS);
        int minY = (int) Math.floor(Math.min(left.y, right.y));
        int maxY = (int) Math.floor(Math.max(left.y, right.y) + BODY_HEIGHT);
        int minZ = (int) Math.floor(Math.min(left.z, right.z) - BODY_RADIUS);
        int maxZ = (int) Math.floor(Math.max(left.z, right.z) + BODY_RADIUS);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (BasaltCourtyard.solid(x, y, z) && crossesBody(left, right, x, y, z)) return false;
                }
            }
        }
        return true;
    }

    private static boolean crossesBody(Point left, Point right, int x, int y, int z) {
        // Minkowski-expanded cell. Find the overlap of its three entry/exit intervals.
        Range sx = sweepAxis(left.x, right.x, x - BODY_RADIUS, x + 1 + BODY_RADIUS);
        Range sy = sweepAxis(left.y, right.y, y - BODY_HEIGHT, y + 1);
        Range sz = sweepAxis(left.z, right.z, z - BODY_RADIUS, z + 1 + BODY_RADIUS);
        return Math.max(0, Math.max(sx.entry, Math.max(sy.entry, sz.entry)))
                < Math.min(1, Math.min(sx.exit, Math.min(sy.exit, sz.exit)));
    }

    private record Range(double entry, double exit) {}

    private static Range sweepAxis(double from, double to, double min, double max) {
        min += CONTACT_EPSILON;
        max -= CONTACT_EPSILON;
        if (from == to) return from > min && from < max ? new Range(0, 1) : new Range(1, 0);
        double a = (min - from) / (to - from), b = (max - from) / (to - from);
        return new Range(Math.min(a, b), Math.max(a, b));
    }
}
