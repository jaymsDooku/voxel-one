package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArenaMotionTimelineTest {
    @Test
    void stableLocalAndRemoteFramesBothInterpolateOneTickBehind() {
        var motion = new ArenaMotionTimeline();
        for (int i = 0; i < 4; i++) {
            motion.observe(100 + i, List.of(fighter(.24f * i, 0, i < 3 ? 179 : -179, .24f, 20, -1, 1)),
                    ms(i * 50));
        }
        var frame = motion.frame(ms(175));
        var remote = motion.sample(1, frame.remote());
        assertEquals(.60f, remote.x(), .0001f);
        assertEquals(180f, remote.yaw(), .0001f);
        assertEquals(.60f * 2.5f, remote.walkPhase(), .0001f);
        assertEquals(1, remote.walkAmount());
        assertEquals(.60f, motion.sample(1, frame.local()).x(), .0001f);

        motion.observe(104, List.of(fighter(.72f, 0, -179, 0, 20, -1, 1)), ms(200));
        assertEquals(.72f, motion.sample(1, motion.frame(ms(300)).remote()).x(), .0001f);
        var stationary = motion.sample(1, motion.frame(ms(650)).remote());
        assertEquals(.72f, stationary.x(), .0001f);
        assertEquals(0, stationary.walkAmount());
    }

    @Test
    void deliveryJitterBuffersOnlyTheRemoteLaneAndNeverRewindsIt() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(.24f, 0, 0, .24f, 20, -1, 1)), ms(100));
        var delayed = motion.frame(ms(100));
        assertTrue(delayed.remote().tick() < delayed.local().tick());
        assertTrue(delayed.local().tick() >= 100);
        motion.observe(102, List.of(fighter(.48f, 0, 0, .24f, 20, -1, 1)), ms(101));
        assertTrue(motion.frame(ms(101)).remote().tick() >= delayed.remote().tick());

        for (int i = 0; i < 18; i++)
            motion.observe(103 + i, List.of(fighter(.72f + i * .24f, 0, 0, .24f, 20, -1, 1)), ms(151 + i * 50));
        var stable = motion.frame(ms(1026));
        assertEquals(stable.local().tick(), stable.remote().tick(), .0001);
    }

    @Test
    void oneTickBridgeFollowsTheAxisMoveAroundArenaCover() {
        var motion = route(1);
        var corner = motion.sample(1, cursor(100.5));
        assertEquals(7.40f, corner.x(), .0001f);
        assertEquals(5.60f, corner.z(), .0001f);
        for (int i = 0; i <= 40; i++) {
            var pose = motion.sample(1, cursor(100 + i / 40.0));
            assertTrue(pose.x() < 7.70f || pose.z() < 5.70f, "the player body must stay outside the cover");
        }
    }

    @Test
    void risingAcrossTheTopOfCoverClearsHeightBeforeHorizontalMotion() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(7.68f, 74.8f, 7, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(8.20f, 75.22f, 7, 0, .52f, 20, -1, 1)), ms(50));

        var pose = motion.sample(1, cursor(100.5));
        assertEquals(75.22f, pose.y(), .0001f);
        assertEquals(7.73f, pose.x(), .0001f);
        for (int i = 1; i < 40; i++) {
            var frame = motion.sample(1, cursor(100 + i / 40.0));
            assertTrue(frame.x() <= 7.70f || frame.y() >= 75f);
        }
    }

    @Test
    void blockedBridgeAcrossMissingSnapshotsHoldsThenSnaps() {
        var motion = route(2);
        var held = motion.sample(1, cursor(101));
        assertEquals(8.30f, held.x());
        assertEquals(5.60f, held.z());
        assertEquals(7.40f, motion.sample(1, cursor(102)).x());
    }

    @Test
    void epochChangeSnapsEvenFromZeroAndIgnoresOutOfOrderPackets() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 0)), ms(0));
        motion.observe(101, List.of(fighter(.3f, 0, 0, 0, 20, -1, 1)), ms(50));
        motion.observe(100, List.of(fighter(2, 0, 0, 0, 20, -1, 0)), ms(60));
        var frame = motion.frame(ms(60));
        assertEquals(.3f, motion.sample(1, frame.local()).x());
        assertEquals(.3f, motion.sample(1, frame.remote()).x());
    }

    @Test
    void aStalledConnectionDoesNotMoveAnActorOrStrandTheCursorAheadOfRecovery() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(.24f, 0, 0, .24f, 20, -1, 1)), ms(50));
        assertEquals(.24f, motion.sample(1, motion.frame(ms(2000)).remote()).x());

        for (int i = 0; i < 4; i++) {
            motion.observe(102 + i, List.of(fighter(6 + i * .24f, 0, 0, .24f, 20, -1, 1)),
                    ms(2200 + i * 50));
        }
        assertEquals(6.60f, motion.sample(1, motion.frame(ms(2375)).remote()).x(), .0001f);
    }

    @Test
    void confirmedHurtWaitsForPresentationAndExpiresWithoutARefresh() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(50));
        assertEquals(0, motion.sample(1, motion.frame(ms(99)).remote()).hurt());
        motion.observe(102, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(100));
        assertEquals(1, motion.sample(1, motion.frame(ms(100)).remote()).hurt());
        assertEquals(2f / 3, motion.sample(1, motion.frame(ms(250)).remote()).hurt(), .0001f);
        assertEquals(0, motion.sample(1, motion.frame(ms(350)).remote()).hurt());
    }

    @Test
    void stallRecoveryDoesNotReplayAnExpiredHitButShowsNewConfirmedDamage() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(50));
        assertEquals(1, motion.sample(1, motion.frame(ms(100)).remote()).hurt());
        assertEquals(0, motion.sample(1, motion.frame(ms(420)).remote()).hurt());
        motion.observe(102, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(450));
        assertEquals(0, motion.sample(1, motion.frame(ms(450)).remote()).hurt());
        motion.observe(103, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(500));
        assertEquals(0, motion.sample(1, motion.frame(ms(525)).remote()).hurt());

        motion.observe(104, List.of(fighter(0, 0, 0, 0, 14, 104, 1)), ms(900));
        assertEquals(1, motion.sample(1, motion.frame(ms(900)).remote()).hurt());
    }

    @Test
    void secondHitRestartsTintAndFatalHitRemainsVisibleOnlyDuringFlash() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(50));
        motion.observe(104, List.of(fighter(0, 0, 0, 0, 15, 104, 1)), ms(200));
        motion.observe(106, List.of(fighter(0, 0, 0, 0, 0, 106, 1)), ms(300));
        assertTrue(motion.sample(1, cursor(103.5)).hurt() < 1);
        assertEquals(1, motion.sample(1, cursor(104)).hurt());
        var lethal = motion.sample(1, cursor(106));
        assertEquals(0, lethal.fighter().health());
        assertEquals(1, lethal.hurt());
        assertTrue(lethal.visible());
        assertFalse(motion.sample(1, new ArenaMotionTimeline.Cursor(106, 111)).visible());
    }

    @Test
    void olderMetadataOnlyFlashesOnAnObservedHealthDecrease() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, 0, 0)), ms(0));
        motion.observe(101, List.of(fighter(0, 0, 0, 0, 20, 0, 0)), ms(50));
        motion.observe(102, List.of(fighter(0, 0, 0, 0, 18, -1, 0)), ms(100));
        assertEquals(0, motion.sample(1, cursor(101.5)).hurt());
        assertEquals(1, motion.sample(1, cursor(102)).hurt());
    }

    @Test
    void hitCueMovesOnlyTheLocalCameraBeforeTheNextPositionSample() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.sampleLocal(1, motion.frame(ms(0)).local(), ms(0), false, true);
        motion.observe(101, List.of(withImpulse(.20f, 0, 1, 101, .90f, .40f, 0)), ms(50));
        motion.sampleLocal(1, motion.frame(ms(50)).local(), ms(50), false, true);

        long duringImpulse = ms(87) + 500_000;
        var frame = motion.frame(duringImpulse);
        var local = motion.sampleLocal(1, frame.local(), duringImpulse, false, true);
        assertEquals(.65, local.point().x(), .0001);
        assertEquals(73.20, local.point().y(), .0001);
        assertEquals(.20, local.confirmedPoint().x(), .0001);
        assertEquals(101, local.pose().first());
        assertEquals(101, local.pose().last());
        assertEquals(101, local.pose().tick());
        assertTrue(motion.sample(1, frame.remote()).x() <= .20f);

        var stalled = motion.sampleLocal(1, motion.frame(ms(190)).local(), ms(190), false, true);
        assertEquals(1.10, stalled.point().x(), .0001);
        assertEquals(101, stalled.pose().tick());
        var expired = motion.sampleLocal(1, motion.frame(ms(400)).local(), ms(400), false, true);
        assertEquals(expired.confirmedPoint(), expired.point());
        assertEquals(101, expired.pose().tick());
    }

    @Test
    void nextConfirmedSampleReconcilesWithoutReplayingTheCueOrJerkingBack() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.sampleLocal(1, motion.frame(ms(0)).local(), ms(0), false, true);
        motion.observe(101, List.of(withImpulse(.20f, 0, 1, 101, .90f, 0, 0)), ms(50));
        motion.sampleLocal(1, motion.frame(ms(50)).local(), ms(50), false, true);
        var before = motion.sampleLocal(1, motion.frame(ms(90)).local(), ms(90), false, true);

        motion.observe(102, List.of(withImpulse(1.05f, 0, 1, 101, .90f, 0, 0)), ms(100));
        var after = motion.sampleLocal(1, motion.frame(ms(100)).local(), ms(100), false, true);
        assertEquals(before.point().x(), after.point().x(), .0001);
        assertTrue(after.pose().last() <= 102);
        var caughtUp = motion.sampleLocal(1, motion.frame(ms(240)).local(), ms(240), false, true);
        assertEquals(1.05, caughtUp.point().x(), .0001);
        assertEquals(102, caughtUp.pose().tick());
    }

    @Test
    void hitCueClipsAgainstCoverAndNeverBridgesTeleportOrAStaleGap() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(7.60f, 7, 0, 0, 20, -1, 1)), ms(0));
        motion.sampleLocal(1, motion.frame(ms(0)).local(), ms(0), false, true);
        motion.observe(101, List.of(withImpulse(7.60f, 7, 1, 101, .90f, 0, 0)), ms(50));
        motion.sampleLocal(1, motion.frame(ms(50)).local(), ms(50), false, true);
        var clipped = motion.sampleLocal(1, motion.frame(ms(110)).local(), ms(110), false, true);
        assertEquals(7.70, clipped.point().x(), .0001);
        assertEquals(7.60, clipped.confirmedPoint().x(), .0001);

        motion.observe(102, List.of(withImpulse(2, 0, 2, 101, .90f, 0, 0)), ms(120));
        var teleported = motion.sampleLocal(1, motion.frame(ms(160)).local(), ms(160), false, true);
        assertEquals(2, teleported.point().x(), .0001);
        assertEquals(102, teleported.pose().tick());

        motion.observe(108, List.of(withImpulse(3, 0, 2, 108, .90f, 0, 0)), ms(500));
        var reanchored = motion.sampleLocal(1, motion.frame(ms(540)).local(), ms(540), false, true);
        assertEquals(3, reanchored.point().x(), .0001);
        assertEquals(108, reanchored.pose().tick());
    }

    @Test
    void factionCameraWaitsForServerToConfirmACrossClaimImpulse() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 11.40f, 0, 0, 20, -1, 1)), ms(0));
        motion.sampleLocal(1, motion.frame(ms(0)).local(), ms(0), true, true);
        motion.observe(101, List.of(withImpulse(0, 11.40f, 1, 101, 0, 0, .90f)), ms(50));
        motion.sampleLocal(1, motion.frame(ms(50)).local(), ms(50), true, true);
        var awaitingConfirmation = motion.sampleLocal(1, motion.frame(ms(110)).local(), ms(110), true, true);
        assertEquals(11.40, awaitingConfirmation.point().z(), .0001);
        assertEquals(awaitingConfirmation.confirmedPoint(), awaitingConfirmation.point());
    }

    private static ArenaMotionTimeline route(int gap) {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(8.30f, 5.60f, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(100 + gap, List.of(fighter(7.40f, 6.50f, 0, 0, 20, -1, 1)), ms(50 * gap));
        return motion;
    }

    private static Fighter fighter(float x, float z, float yaw, float vx, float health, long hurt, long epoch) {
        return fighter(x, 73, z, yaw, vx, health, hurt, epoch);
    }

    private static Fighter fighter(float x, float y, float z, float yaw, float vx, float health, long hurt, long epoch) {
        return new Fighter(1, "Fighter", x, y, z, yaw, 0, health, vx != 0, false, false, Item.SWORD,
                0, 0, 0, 0, -1, "", vx, 0, 0, hurt, epoch, -1, -1, 0, 0, 0);
    }

    private static Fighter withImpulse(float x, float z, long epoch, long tick, float vx, float vy, float vz) {
        return new Fighter(1, "Fighter", x, 73, z, 0, 0, 17, false, false, false, Item.SWORD,
                0, 0, 0, 0, -1, "", 0, 0, 0, tick, epoch, -1, tick, vx, vy, vz);
    }

    private static ArenaMotionTimeline.Cursor cursor(double tick) { return new ArenaMotionTimeline.Cursor(tick, tick); }

    private static long ms(int value) { return value * 1_000_000L; }
}
