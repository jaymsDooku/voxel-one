package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.PoseView;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatGeometry.PosePath;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.practice.PracticeMatch.Fighter;

import static dev.jayms.combat.practice.PracticeMatch.SWING_TICKS;

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
    private static final double RECONCILE_TICKS = 2;

    record Cursor(double tick, double fadeTick, double receiptTime) {
        Cursor(double tick, double fadeTick) { this(tick, fadeTick, Double.NaN); }
    }
    record Frame(Cursor local, Cursor remote) {}
    record LocalView(Vec3 point, Vec3 confirmedPoint, PoseView pose) {}
    record Presented(Fighter fighter, float x, float y, float z, float yaw, float pitch,
                     float walkPhase, float walkAmount, float hurt, float swingProgress, PoseView pose) {
        boolean visible() { return fighter.health() > 0 || hurt > 0; }
    }

    static final Arena TERRAIN = new Arena() {
        @Override public boolean solid(int x, int y, int z) { return BasaltCourtyard.solid(x, y, z); }
        @Override public boolean contains(Vec3 feet) { return BasaltCourtyard.contains(feet.x(), feet.y(), feet.z()); }
    };

    private record Cue(long tick, double received, long packetTick) {
        double age(Cursor cursor) {
            if (Double.isNaN(cursor.receiptTime)) return cursor.fadeTick - tick;
            if (cursor.receiptTime < received) return -1;
            return Math.max(cursor.tick - tick, packetTick - tick + (cursor.receiptTime - received));
        }
    }

    private record Sample(long tick, Fighter fighter, Vec3 point, Cue hurt, Cue swing, double stride, PosePath fromPrevious) {}
    private record ImpulsePreview(long tick, long receivedAt, double startTick, PosePath path) {}
    private record Reconciliation(long receivedAt, Vec3 offset) {}

    private static final class Track {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        Fighter latest;
        Cue hurtCue, swingCue;
        long confirmedSwing = -1, skippedSwing = -1;
        long confirmedHurt = -1, skippedHurt = -1;

        void skipStaleHistory() {
            samples.clear();
            // Repeated metadata after a gap must not restart an old cue.
            skippedHurt = confirmedHurt;
            skippedSwing = confirmedSwing;
        }
    }

    private final Map<Integer, Track> tracks = new HashMap<>();
    private long latestTick = -1, anchorNanos, lastFrameNanos;
    private double anchorTick, localTick, remoteTick, effectTick, deliveryJitter;
    private int localId;
    private long localEpoch = -1, localObservedTick = -1;
    private double localVisualTick;
    private long localRenderedAt;
    private Vec3 localVisualPoint;
    private ImpulsePreview impulsePreview;
    private Reconciliation reconciliation;

    void observe(long tick, List<Fighter> fighters, long now) {
        if (tick <= latestTick) return;
        boolean reanchor = latestTick < 0 || tick - latestTick > MAX_BRIDGE_TICKS
                || now - anchorNanos >= STALE_NANOS;
        if (reanchor) {
            tracks.values().forEach(Track::skipStaleHistory);
            lastFrameNanos = now;
            anchorTick = tick;
            deliveryJitter = 0;
            // A late packet can be older than the clock after a stall. Keep frame time from rewinding.
            localTick = latestTick < 0 ? tick - LOCAL_DELAY : Math.max(localTick, tick - LOCAL_DELAY);
            remoteTick = latestTick < 0 ? tick - 1 : Math.max(remoteTick, tick - 1);
            effectTick = latestTick < 0 ? localTick : Math.max(effectTick, localTick);
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
        // Keep one tick of history on clean delivery and up to half a tick more during jitter.
        double remoteDelay = 1 + Math.max(0, Math.min(.5, deliveryJitter - .12));
        double localTarget = Math.max(latestTick, estimated) - LOCAL_DELAY;
        double remoteTarget = estimated - remoteDelay;
        localTick = Math.max(localTick, Math.min(latestTick, localTarget));
        double elapsed = elapsedTicks(now, lastFrameNanos);
        lastFrameNanos = Math.max(now, lastFrameNanos);
        if (deliveryJitter > .12 && elapsed < 1) {
            // Pace clock corrections over several frames. As the buffer empties, spend
            // the last fraction of a confirmed tick more slowly until a packet arrives.
            double error = remoteTarget - (remoteTick + elapsed);
            double rate = Math.max(.75, Math.min(1.45, 1 + .8 * error));
            rate = Math.min(rate, Math.max(.12, (latestTick - remoteTick) * 1.9));
            remoteTick = Math.min(latestTick, remoteTick + elapsed * rate);
        } else {
            remoteTick = Math.max(remoteTick, Math.min(latestTick, remoteTarget));
        }
        double effectTarget = Math.max(latestTick, estimated);
        effectTick = Math.max(effectTick, Math.min(latestTick + Math.max(HURT_TICKS, SWING_TICKS), effectTarget));

        return new Frame(new Cursor(localTick, effectTick, now / NANOS_PER_TICK),
                new Cursor(remoteTick, effectTick, now / NANOS_PER_TICK));
    }

    Presented sample(int id, Cursor cursor) {
        var track = tracks.get(id);
        if (track == null || track.samples.isEmpty()) return null;
        Sample left = track.samples.peekFirst(), right = left;
        double tick = Math.max(cursor.tick, left.tick);
        Cue hurtCue = null, swingCue = null;
        for (Sample candidate : track.samples) {
            if (candidate.swing != null && candidate.swing.age(cursor) >= 0) swingCue = candidate.swing;
            if (candidate.hurt != null && candidate.hurt.age(cursor) >= 0) hurtCue = candidate.hurt;
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
        float hurt = hurtCue == null ? 0 : injuryStrength(hurtCue.age(cursor));
        float swingProgress = swingCue == null ? 1 : (float) Math.min(1, swingCue.age(cursor) / SWING_TICKS);
        return new Presented(discrete, (float) point.x(), (float) point.y(), (float) point.z(), yaw, pitch,
                (float) (stride % (2 * Math.PI)), amount, hurt, swingProgress, pose);
    }

    LocalView sampleLocal(int id, Cursor cursor, long now, boolean factions, boolean fighting) {
        Track track = tracks.get(id);
        if (track == null || track.samples.isEmpty()) return null;
        Sample latest = track.samples.getLast();
        long epoch = latest.fighter.motionEpoch();
        boolean continuous = fighting && id == localId && epoch == localEpoch
                && (localObservedTick == latest.tick || track.samples.size() > 1 && latest.fromPrevious != null && latest.tick - localObservedTick <= MAX_BRIDGE_TICKS);
        if (!continuous) {
            localId = id;
            localEpoch = epoch;
            localVisualTick = cursor.tick;
            localVisualPoint = null;
            impulsePreview = null;
            reconciliation = null;
        } else if (latest.tick != localObservedTick) {
            if (impulsePreview != null && latest.tick > impulsePreview.tick) {
                // Continue from the point the camera already reached as the server catches up.
                var confirmed = sample(id, new Cursor(localVisualTick, cursor.fadeTick));
                var offset = localVisualPoint.minus(point(confirmed));
                reconciliation = new Reconciliation(localRenderedAt, offset);
                impulsePreview = null;
            }
            if (latest.fighter.health() > 0 && latest.fighter.impulseTick() == latest.tick) {
                Fighter f = latest.fighter;
                Vec3 impulse = new Vec3(f.impulseX(), f.impulseY(), f.impulseZ());
                Vec3 end = CombatGeometry.clipMovement(TERRAIN, id, latest.point, impulse);
                PosePath route = allowProjection(latest.point, end, factions)
                        ? CombatGeometry.posePath(TERRAIN, latest.point, end, 1) : null;
                if (route != null && !latest.point.equals(end)) {
                    impulsePreview = new ImpulsePreview(latest.tick, now,
                            Math.min(latest.tick, Math.max(cursor.tick, localVisualTick)), route);
                }
            }
        }
        localObservedTick = latest.tick;

        if (impulsePreview != null && now - impulsePreview.receivedAt >= STALE_NANOS) {
            // A stalled connection cannot confirm the predicted endpoint. Ease back to the last pose.
            reconciliation = new Reconciliation(impulsePreview.receivedAt + STALE_NANOS,
                    localVisualPoint.minus(latest.point));
            impulsePreview = null;
            localVisualTick = latest.tick;
        }

        double visualTick = cursor.tick;
        double correctionFactor = 0;
        if (reconciliation != null) {
            correctionFactor = Math.max(0, 1 - elapsedTicks(now, reconciliation.receivedAt) / RECONCILE_TICKS);
            // Give back the preview lead gradually while advancing through confirmed motion.
            double recoveryTick = localVisualTick + .8 * elapsedTicks(now, localRenderedAt);
            visualTick = Math.max(visualTick, recoveryTick);
            if (correctionFactor == 0 && (cursor.tick >= recoveryTick || cursor.tick >= latest.tick)) reconciliation = null;
        }
        visualTick = Math.min(latest.tick, Math.max(localVisualTick, visualTick));
        if (impulsePreview != null) {
            double elapsed = elapsedTicks(now, impulsePreview.receivedAt);
            double approach = (impulsePreview.tick - impulsePreview.startTick) / 2;
            double runningTick = elapsed <= approach ? impulsePreview.startTick + 2 * elapsed
                    : impulsePreview.tick + Math.min(1, elapsed - approach);
            visualTick = Math.max(visualTick, runningTick);
        }
        Presented confirmed = sample(id, new Cursor(Math.min(latest.tick, visualTick), cursor.fadeTick));
        Vec3 confirmedPoint = point(confirmed);
        Vec3 visualPoint = impulsePreview != null && visualTick > impulsePreview.tick
                ? impulsePreview.path.at(visualTick - impulsePreview.tick) : confirmedPoint;
        if (reconciliation != null) {
            Vec3 candidate = visualPoint.add(reconciliation.offset.scale(correctionFactor));
            if (allowProjection(visualPoint, candidate, factions)
                    && CombatGeometry.posePath(TERRAIN, visualPoint, candidate, 1) != null
                    && (localVisualPoint == null || CombatGeometry.posePath(TERRAIN, localVisualPoint, candidate, 1) != null)) {
                visualPoint = candidate;
            }
        }
        localVisualTick = visualTick;
        localVisualPoint = visualPoint;
        localRenderedAt = now;
        return new LocalView(visualPoint, confirmedPoint, confirmed.pose);
    }

    private static boolean allowProjection(Vec3 start, Vec3 end, boolean factions) {
        // Combat-tagged fighters may only retreat from a claim, and claim permissions are server-side.
        return !factions || BasaltCourtyard.factionTerritory(start.z()) < 0
                && BasaltCourtyard.factionTerritory(end.z()) < 0;
    }

    private static Vec3 point(Presented shown) { return new Vec3(shown.x, shown.y, shown.z); }

    private static double elapsedTicks(long now, long since) { return Math.max(0, now - since) / NANOS_PER_TICK; }

    private void append(long tick, Fighter fighter) {
        var track = tracks.computeIfAbsent(fighter.id(), ignored -> new Track());
        Fighter prior = track.latest;
        if (prior != null && prior.motionEpoch() != fighter.motionEpoch()) {
            track.samples.clear();
            track.skippedSwing = Math.max(track.confirmedSwing, tick - 1);
            prior = null;
        }
        Sample previous = track.samples.peekLast();
        Vec3 next = new Vec3(fighter.x(), fighter.y(), fighter.z());
        long hurt = fighter.hurtTick() > 0 && fighter.hurtTick() <= tick ? fighter.hurtTick() : -1;
        if (prior != null && fighter.motionEpoch() == 0 && hurt < 0 && fighter.health() < prior.health()) hurt = tick;
        if (hurt > track.confirmedHurt) {
            track.confirmedHurt = hurt;
            track.hurtCue = new Cue(hurt, anchorNanos / NANOS_PER_TICK, tick);
        }
        if (hurt <= track.skippedHurt) hurt = -1;
        PosePath route = previous == null ? null : CombatGeometry.posePath(TERRAIN, previous.point, next, tick - previous.tick);
        double stride = route == null ? 0 : previous.stride
                + Math.hypot(next.x() - previous.point.x(), next.z() - previous.point.z()) * 2.5;
        long swing = fighter.swingTick();
        if (swing < 0 && fighter.swinging() && (prior == null || !prior.swinging())) swing = tick;
        if (swing > track.confirmedSwing && swing <= tick) {
            track.confirmedSwing = swing;
            track.swingCue = new Cue(swing, anchorNanos / NANOS_PER_TICK, tick);
        }
        swing = track.confirmedSwing > track.skippedSwing ? track.confirmedSwing : -1;
        track.samples.addLast(new Sample(tick, fighter, next, hurt < 0 ? null : track.hurtCue,
                swing < 0 ? null : track.swingCue, stride, route));
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
