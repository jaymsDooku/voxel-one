package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.PoseView;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatGeometry.PosePath;
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
    private static final double LOCAL_DELAY = 1;
    private static final double HURT_TICKS = 5;

    record Cursor(double tick, double fadeTick) {}
    record Frame(Cursor local, Cursor remote) {}
    record Presented(Fighter fighter, float x, float y, float z, float yaw, float pitch,
                     float walkPhase, float walkAmount, float hurt, PoseView pose) {
        boolean visible() { return fighter.health() > 0 || hurt > 0; }
    }

    private static final Arena TERRAIN = new Arena() {
        @Override public boolean solid(int x, int y, int z) { return BasaltCourtyard.solid(x, y, z); }
        @Override public boolean contains(Vec3 feet) { return BasaltCourtyard.contains(feet.x(), feet.y(), feet.z()); }
    };

    private record Sample(long tick, Fighter fighter, Vec3 point, long hurtTick, double stride, PosePath fromPrevious) {}

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
        Vec3 point = left.point;
        PoseView pose = new PoseView(left.tick, left.tick, left.tick);
        float yaw = discrete.yaw(), pitch = discrete.pitch(), amount = walkAmount(discrete);
        double stride = left.stride;
        if (left != right && right.fromPrevious != null && left.tick < tick) {
            double progress = Math.min(1, (tick - left.tick) / (right.tick - left.tick));
            point = right.fromPrevious.at(progress);
            pose = new PoseView(left.tick, right.tick, tick);
            yaw += (float) (Math.IEEEremainder(right.fighter.yaw() - yaw, 360) * progress);
            pitch += (float) ((right.fighter.pitch() - pitch) * progress);
            stride += (right.stride - stride) * progress;
            amount += (float) ((walkAmount(right.fighter) - amount) * progress);
        }
        // The pose holds at the freshest sample during a stall. Visual feedback keeps expiring.
        float hurt = hurtTick < 0 ? 0 : injuryStrength(Math.max(tick, cursor.fadeTick) - hurtTick);
        return new Presented(discrete, (float) point.x(), (float) point.y(), (float) point.z(), yaw, pitch,
                (float) (stride % (2 * Math.PI)), amount, hurt, pose);
    }

    private void append(long tick, Fighter fighter) {
        var track = tracks.computeIfAbsent(fighter.id(), ignored -> new Track());
        Fighter prior = track.latest;
        if (prior != null && prior.motionEpoch() != fighter.motionEpoch()) {
            track.samples.clear();
            prior = null;
        }
        Sample previous = track.samples.peekLast();
        Vec3 next = new Vec3(fighter.x(), fighter.y(), fighter.z());
        long hurt = fighter.hurtTick() > 0 && fighter.hurtTick() <= tick ? fighter.hurtTick() : -1;
        if (prior != null && fighter.motionEpoch() == 0 && hurt < 0 && fighter.health() < prior.health()) hurt = tick;
        track.confirmedHurt = Math.max(track.confirmedHurt, hurt);
        if (hurt <= track.skippedHurt) hurt = -1;
        PosePath route = previous == null ? null : CombatGeometry.posePath(TERRAIN, previous.point, next, tick - previous.tick);
        double stride = route == null ? 0 : previous.stride
                + Math.hypot(next.x() - previous.point.x(), next.z() - previous.point.z()) * 2.5;
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
}
