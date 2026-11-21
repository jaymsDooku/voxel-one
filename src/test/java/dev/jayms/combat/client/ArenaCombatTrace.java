package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.practice.PracticeMatch.Fighter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic JSONL benchmark. Pass --samples for every physics tick and presentation frame. */
public final class ArenaCombatTrace {
    private static final int HIT = 8, END = 32;
    private static final long TICK = 50_000_000;
    private static final double FLOOR = BasaltCourtyard.FLOOR_Y + 1;
    private static final Vec3 START = new Vec3(.5, FLOOR, .5);
    private static final int[] JITTER_MS = {0, 6, 28, 3, 17, 34, 1, 12};

    public static void main(String[] args) {
        run(List.of(args).contains("--samples"), true);
    }

    record Run(double physicsError, double sprintError, List<Replay> replays) {}
    record Replay(int fps, int phase, Metrics local, Metrics remote) {}

    static Run run(boolean samples, boolean report) {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, ArenaMotionTimeline.TERRAIN);
        if (!engine.join(1, START.add(-2, 0, 0), 0) || !engine.join(2, START, 180))
            throw new IllegalStateException("Cannot spawn duel");
        var packets = new ArrayList<List<Fighter>>();
        var truth = new ArrayList<Vec3>();
        var paper = new PaperTravel();
        double maxError = 0;
        for (int tick = 0; tick <= END; tick++) {
            if (tick == HIT && !engine.submit(1, new Intent(0, 0, 0, 0, 0, false, false, true, false, 0)))
                throw new IllegalStateException("Attack rejected");
            if (tick > 0) engine.tick();
            var victim = engine.snapshot(2);
            if (tick == HIT && victim.impulseTick() != HIT) throw new IllegalStateException("Attack missed");
            truth.add(victim.feet());
            packets.add(List.of(Fighter.fromCombat(engine.snapshot(1), "attacker", tick == HIT, true, 1, "", tick, tick),
                    Fighter.fromCombat(victim, "victim", false, true, 2, "", tick, tick)));
            if (tick > HIT) paper.advance();
            double error = victim.feet().minus(paper.point()).length();
            maxError = Math.max(maxError, error);
            if (samples) print("{\"kind\":\"tick\",\"tick\":%d,\"engine\":%s,\"paper\":%s,\"errorBlocks\":%.9f}", tick, xyz(victim.feet()), xyz(paper.point()), error);
        }
        if (report) print("{\"kind\":\"physics\",\"ticks\":%d,\"baseLift\":0.36,\"baseHorizontalPush\":%.6f,\"maxPositionErrorBlocks\":%.9f}",
                truth.size(), CombatRules.NO_DEBUFF.knockback(), maxError);
        var replays = new ArrayList<Replay>();
        for (int fps : List.of(60, 144)) {
            replays.add(replay(packets, truth, fps, -1, samples, report));
            for (int phase = 0; phase < JITTER_MS.length; phase++) replays.add(replay(packets, truth, fps, phase, samples, report));
        }
        double sprintError = sprintTrajectory(samples);
        if (report) print("{\"kind\":\"sprintPhysics\",\"ticks\":%d,\"baseLift\":0.36,\"sprintHorizontalBonus\":%.6f,\"maxPositionErrorBlocks\":%.9f}",
                END + 1, CombatRules.NO_DEBUFF.sprintKnockback(), sprintError);
        if (maxError > .00001 || sprintError > .00001) throw new AssertionError("Arena profile diverged from Paper travel by " + Math.max(maxError, sprintError));
        return new Run(maxError, sprintError, List.copyOf(replays));
    }

    // PaperSpigot 1.7.10 EntityLiving.a(Entity,float,double,double), e(float,float) and e().
    // Use the arena horizontal profile and lift with the independent Paper travel equations.
    // Stationary target already carries the previous grounded tick's gravity when hit after travel.
    private static final class PaperTravel {
        double x = START.x(), y = FLOOR, vx = CombatRules.NO_DEBUFF.knockback(), vy = .36 + (-.08 * (double).98F) / 2;
        boolean grounded = true;
        Vec3 point() { return new Vec3(x, y, START.z()); }
        void advance() {
            if (Math.abs(vx) < .005) vx = 0;
            if (Math.abs(vy) < .005) vy = 0;
            float drag = grounded ? .6F * .91F : .91F;
            x += vx;
            grounded = y + vy < FLOOR;
            y = Math.max(FLOOR, y + vy);
            if (grounded) vy = 0;
            vy = (vy - .08) * (double).98F;
            vx *= drag;
        }
    }

    // Both fighters step forward on the hit tick. The victim releases forward on the next
    // tick, so knockback retains half of its already-dragged walking velocity.
    private static double sprintTrajectory(boolean samples) {
        var engine = new CombatEngine(CombatRules.NO_DEBUFF, ArenaMotionTimeline.TERRAIN);
        engine.join(1, START.add(-2, 0, 0), 0);
        engine.join(2, START, 0);
        var paper = new PaperTravel();
        double maxError = 0;
        for (int tick = 0; tick <= END; tick++) {
            if (tick == HIT) {
                engine.submit(1, new Intent(0, 0, 0, 1, 0, false, true, true, false, 0));
                engine.submit(2, new Intent(0, 0, 0, 1, 0, false, false, false, false, 0));
                float friction = .6F * .91F;
                float acceleration = .1F * (.16277136F / (friction * friction * friction));
                double forward = .98F * acceleration;
                paper.x += forward;
                paper.vx = forward * friction / 2 + CombatRules.NO_DEBUFF.knockback() + CombatRules.NO_DEBUFF.sprintKnockback();
                paper.vy += .1;
            }
            if (tick == HIT + 1) engine.submit(2, new Intent(1, 0, 0, 0, 0, false, false, false, false, 0));
            if (tick > HIT) paper.advance();
            if (tick > 0) engine.tick();
            var victim = engine.snapshot(2);
            if (tick == HIT && victim.impulseTick() != HIT) throw new AssertionError("Sprint hit missed");
            double error = victim.feet().minus(paper.point()).length();
            maxError = Math.max(maxError, error);
            if (samples) print("{\"kind\":\"sprintTick\",\"tick\":%d,\"engine\":%s,\"paper\":%s,\"errorBlocks\":%.9f}",
                    tick, xyz(victim.feet()), xyz(paper.point()), error);
        }
        return maxError;
    }
    private static Replay replay(List<List<Fighter>> packets, List<Vec3> truth, int fps, int phase, boolean samples, boolean report) {
        var timeline = new ArenaMotionTimeline();
        int next = 0;
        double maxGap = 0;
        var local = new Metrics();
        var remote = new Metrics();
        for (int frame = 0; frame <= END * fps / 20; frame++) {
            long now = Math.round(frame * 1e9 / fps);
            while (next < packets.size() && arrival(next, phase) <= now) {
                if (phase < 0 || (next + phase) % 9 != 0 || next == 0)
                    timeline.observe(next, packets.get(next), arrival(next, phase));
                next++;
            }
            var cursor = timeline.frame(now);
            Vec3 camera = timeline.sampleLocal(2, cursor.local(), now, false, true).point();
            var model = timeline.sample(2, cursor.remote());
            Vec3 avatar = new Vec3(model.x(), model.y(), model.z());
            // A common one-tick-delayed authority curve gives a fixed motion window for both roles.
            double t = Math.max(0, now / (double)TICK - 1);
            int left = (int)t;
            Vec3 delta = truth.get(left + 1).minus(truth.get(left));
            Vec3 reference = truth.get(left).add(delta.scale(t - left));
            boolean moving = delta.length() > .005 && t > HIT;
            local.measure(camera, reference, moving, now);
            remote.measure(avatar, reference, moving, now);
            maxGap = Math.max(maxGap, camera.minus(avatar).length());
            if (samples) print("{\"kind\":\"frame\",\"fps\":%d,\"phase\":%d,\"ns\":%d,\"local\":%s,\"remote\":%s,\"reference\":%s}", fps, phase, now, xyz(camera), xyz(avatar), xyz(reference));
        }
        if (report) {
            local.report("local", fps, phase, maxGap);
            remote.report("remote", fps, phase, maxGap);
        }
        return new Replay(fps, phase, local, remote);
    }

    private static long arrival(int tick, int phase) {
        return tick * TICK + (phase < 0 || tick == 0 ? 0 : JITTER_MS[(tick + phase) % JITTER_MS.length] * 1_000_000L);
    }

    static final class Metrics {
        Vec3 previous = START;
        int frames, movingFrames, pauses, run, longest;
        double onset = -1, maxError, squaredError, maxStep;
        void measure(Vec3 point, Vec3 reference, boolean moving, long now) {
            frames++;
            double error = point.minus(reference).length();
            double step = point.minus(previous).length();
            maxStep = Math.max(maxStep, step);
            squaredError += error * error;
            maxError = Math.max(maxError, error);
            if (onset < 0 && point.minus(START).length() > .001) onset = (now - HIT * TICK) / 1e6;
            if (moving) movingFrames++;
            if (moving && step < .00001) {
                pauses++;
                longest = Math.max(longest, ++run);
            } else run = 0;
            previous = point;
        }
        void report(String role, int fps, int phase, double gap) {
            if (onset < 0 || movingFrames == 0) throw new AssertionError("No visible trajectory");
            print("{\"kind\":\"presentation\",\"role\":\"%s\",\"fps\":%d,\"jitterPhase\":%d,\"frames\":%d,\"movingFrames\":%d,\"pausedFrames\":%d,\"longestPauseMs\":%.4f,\"onsetFromServerHitMs\":%.4f,\"rmsErrorBlocks\":%.6f,\"maxErrorBlocks\":%.6f,\"maxStepBlocks\":%.6f,\"maxLocalRemoteGapBlocks\":%.6f}",
                    role, fps, phase, frames, movingFrames, pauses, longest * 1000.0 / fps, onset, Math.sqrt(squaredError / frames), maxError, maxStep, gap);
        }
    }
    private static String xyz(Vec3 v) { return String.format(Locale.ROOT, "[%.8f,%.8f,%.8f]", v.x(), v.y(), v.z()); }
    private static void print(String format, Object... args) { System.out.printf(Locale.ROOT, format + "%n", args); }
}
