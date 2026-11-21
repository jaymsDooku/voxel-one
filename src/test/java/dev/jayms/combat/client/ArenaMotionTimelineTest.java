package dev.jayms.combat.client;

import dev.jayms.Camera;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.player.PlayerDimensions;
import org.joml.Vector3f;
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
    void aLatePacketCannotMakeTheRemoteClockJumpOnReceipt() {
        var motion = new ArenaMotionTimeline();
        var actor = List.of(fighter(0, 0, 0, 0, 20, -1, 1));
        motion.observe(100, actor, ms(0));
        motion.observe(101, actor, ms(50));
        double before = motion.frame(ms(185)).remote().tick();
        motion.observe(103, actor, ms(186));
        double after = motion.frame(ms(186)).remote().tick();
        assertTrue(after > before);
        assertTrue(after - before <= 1.45 / 50 + 1e-9);
        assertEquals(after, motion.frame(ms(186)).remote().tick());
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
    void confirmedHurtShowsOnReceiptAndExpiresWithoutARefresh() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(50));
        var received = motion.frame(ms(50)).remote();
        assertTrue(received.tick() < 101);
        assertEquals(1, motion.sample(1, received).hurt());
        assertEquals(0, motion.sample(1, cursor(100)).hurt());
        motion.observe(102, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(100));
        assertEquals(1, motion.sample(1, motion.frame(ms(100)).remote()).hurt());
        assertEquals(.75f, motion.sample(1, motion.frame(ms(250)).remote()).hurt(), .0001f);
        assertEquals(.5f, motion.sample(1, motion.frame(ms(350)).remote()).hurt(), .0001f);
        assertEquals(0, motion.sample(1, motion.frame(ms(550)).remote()).hurt());
    }

    @Test
    void mutualTradeShowsIncomingAndOutgoingConfirmationTogetherAndFadesThemIndependently() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(trader(1, 20, 0, -1), trader(2, 20, 0, -1)), ms(0));
        motion.observe(101, List.of(trader(1, 17, 1, 101), trader(2, 17, 1, 101)), ms(50));
        var frame = motion.frame(ms(50));
        for (int id : List.of(1, 2)) {
            assertFeedback(1, 1, motion.hitFeedback(id, frame.local()));
            assertEquals(1, motion.sample(id, frame.remote()).hurt());
        }
        // A stronger hit inside protection deals extra damage without a new Paper hurt status.
        motion.observe(104, List.of(trader(1, 17, 2, 101), trader(2, 15, 1, 101)), ms(200));
        frame = motion.frame(ms(200));
        assertFeedback(.875f, 1, motion.hitFeedback(1, frame.local()));
        assertFeedback(.875f, .25f, motion.hitFeedback(2, frame.local()));
        assertEquals(.875f, motion.sample(2, frame.remote()).hurt());
        // A stalled pose stream can recover while the original reaction is still visible.
        motion.observe(105, List.of(trader(1, 17, 2, 101), trader(2, 15, 1, 101)), ms(450));
        frame = motion.frame(ms(450));
        assertFeedback(.25f, 0, motion.hitFeedback(1, frame.local()));
        assertEquals(.25f, motion.sample(2, frame.remote()).hurt());
        assertEquals(ArenaMotionTimeline.HitFeedback.NONE, motion.hitFeedback(2, motion.frame(ms(550)).local()));
    }

    private static Fighter trader(int id, float health, int hits, long hurtTick) {
        return new Fighter(id, "Trader", 0, 73, 0, 0, 0, health, false, false, false, Item.SWORD,
                hits, 0, 0, 0, -1, "", 0, 0, 0, hurtTick, 1, -1, -1, 0, 0, 0);
    }

    private static void assertFeedback(float received, float dealt, ArenaMotionTimeline.HitFeedback feedback) {
        assertEquals(received, feedback.received(), .0001f);
        assertEquals(dealt, feedback.dealt(), .0001f);
    }

    @Test
    void hurtRollTipsThenRecoversThroughAStallAndOnlyFreshFullHitsRestartIt() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(trader(1, 20, 0, -1)), ms(0));
        motion.observe(101, List.of(trader(1, 17, 0, 101)), ms(50));
        assertEquals(0, motion.hitFeedback(1, motion.frame(ms(50)).local()).rollRadians());
        float rising = motion.hitFeedback(1, motion.frame(ms(75)).local()).rollRadians();
        float peak = motion.hitFeedback(1, motion.frame(ms(130)).local()).rollRadians();
        assertTrue(rising < 0 && peak < rising);
        assertEquals(-Math.toRadians(6), peak, .0001);

        motion.observe(104, List.of(trader(1, 15, 1, 101)), ms(200));
        float afterProtectedHit = motion.hitFeedback(1, motion.frame(ms(200)).local()).rollRadians();
        assertTrue(peak < afterProtectedHit && afterProtectedHit < rising);
        float stalled = motion.hitFeedback(1, motion.frame(ms(450)).local()).rollRadians();
        assertTrue(afterProtectedHit < stalled && stalled < 0);
        motion.observe(105, List.of(trader(1, 15, 1, 101)), ms(450));
        assertEquals(stalled, motion.hitFeedback(1, motion.frame(ms(450)).local()).rollRadians());
        assertEquals(0, motion.hitFeedback(1, motion.frame(ms(550)).local()).rollRadians());

        motion.observe(112, List.of(trader(1, 12, 1, 112)), ms(600));
        assertEquals(rising, motion.hitFeedback(1, motion.frame(ms(625)).local()).rollRadians(), .0001);
    }

    @Test
    void hurtRollTiltsTheWorldAroundTheThreeBlockAimRay() {
        Camera camera = new Camera();
        camera.position().set(4.5f, 73.62f, -12.25f);
        camera.setYaw(37);
        camera.setPitch(-22);
        Vector3f direction = camera.getDirection();
        Vector3f target = direction.mul(3, new Vector3f()).add(camera.position());
        Vector3f offset = direction.cross(new Vector3f(0, 1, 0), new Vector3f()).normalize().add(target);
        float roll = (float) -Math.toRadians(6);
        var matrix = PracticeGame.practiceViewMatrix(camera, roll);
        Vector3f center = matrix.transformPosition(new Vector3f(target));
        assertEquals(0, center.x, .00002);
        assertEquals(0, center.y, .00002);
        assertEquals(-3, center.z, .00002);
        Vector3f edge = matrix.transformPosition(new Vector3f(offset));
        assertEquals(Math.cos(roll), edge.x, .00002);
        assertEquals(Math.sin(roll), edge.y, .00002);
        assertEquals(-3, edge.z, .00002);
        assertEquals(direction, camera.getDirection());
    }

    @Test
    void legacyLocalHurtTickSurvivesPacketStallWithoutAnObservedHealthChange() {
        var motion = new ArenaMotionTimeline();
        var fighters = List.of(fighter(0, 0, 0, 0, 20, -1, 0));
        motion.observe(100, fighters, ms(0), 1, -1);
        motion.observe(101, fighters, ms(50), 1, 101);
        assertFeedback(1, 0, motion.hitFeedback(1, motion.frame(ms(50)).local()));
        motion.observe(102, fighters, ms(350), 1, 101);
        assertFeedback(.5f, 0, motion.hitFeedback(1, motion.frame(ms(350)).local()));
    }

    @Test
    void stallRecoveryDoesNotReplayAnExpiredHitButShowsNewConfirmedDamage() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.observe(101, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(50));
        assertEquals(1, motion.sample(1, motion.frame(ms(100)).remote()).hurt());
        assertEquals(0, motion.sample(1, motion.frame(ms(600)).remote()).hurt());
        motion.observe(102, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(650));
        assertEquals(0, motion.sample(1, motion.frame(ms(650)).remote()).hurt());
        motion.observe(103, List.of(fighter(0, 0, 0, 0, 17, 101, 1)), ms(700));
        assertEquals(0, motion.sample(1, motion.frame(ms(725)).remote()).hurt());

        motion.observe(104, List.of(fighter(0, 0, 0, 0, 14, 104, 1)), ms(1100));
        assertEquals(1, motion.sample(1, motion.frame(ms(1100)).remote()).hurt());
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
        assertFalse(motion.sample(1, new ArenaMotionTimeline.Cursor(106, 116)).visible());
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
        assertEquals(.425, local.point().x(), .0001);
        assertEquals(73.10, local.point().y(), .0001);
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
    void targetedSprintClickReducesTheCurrentHorizontalCameraImpulseEvenWithoutHitConfirmation() {
        var predicted = incomingImpulse();
        var original = incomingImpulse();
        localFrame(predicted, 85);
        localFrame(original, 85);

        predicted.submitSprintInput(1, true, false, ms(85));
        assertEquals(localFrame(original, 90), localFrame(predicted, 90), "A swing into the air keeps momentum");
        assertNull(predicted.predictedSprintOffset());
        var before = localFrame(predicted, 90);
        predicted.submitSprintInput(1, true, true, ms(90));

        var normal = localFrame(original, 95);
        var slowed = localFrame(predicted, 95);
        assertNotNull(predicted.predictedSprintOffset());
        assertEquals((normal.point().x() - before.point().x()) * .6, slowed.point().x() - before.point().x(), .00001);
        assertEquals((normal.point().z() - before.point().z()) * .6, slowed.point().z() - before.point().z(), .00001);
        assertEquals(normal.point().y(), slowed.point().y());
        assertEquals(normal.pose(), slowed.pose());
        assertEquals(normal.confirmedPoint(), slowed.confirmedPoint());
    }

    @Test
    void authoritativeSlowdownStopsPredictionAndExplicitReleaseAllowsAnotherClick() {
        var motion = incomingImpulse();
        localFrame(motion, 85);
        motion.submitSprintInput(1, true, true, ms(85));
        localFrame(motion, 95);
        assertNotNull(motion.predictedSprintOffset());

        // The server already advanced using multiplied horizontal carry. Retire the preview
        // as soon as its new segment is displayed, before the 50 ms prediction timeout.
        motion.observe(102, List.of(withImpulse(.74f, .12f, 1, 101, .54f, .40f, .12f)), ms(100));
        motion.sampleLocal(1, cursor(101.25), ms(110), false, true);
        assertNull(motion.predictedSprintOffset());
        var settled = motion.sampleLocal(1, cursor(102), ms(211), false, true);
        assertEquals(settled.confirmedPoint(), settled.point());

        motion.submitSprintInput(1, true, true, ms(212));
        assertNull(motion.predictedSprintOffset(), "Held sprint cannot apply a second prediction");
        motion.submitSprintInput(1, false, false, ms(213));
        motion.submitSprintInput(1, true, true, ms(214));
        assertNotNull(motion.predictedSprintOffset(), "A released sprint permits the next prediction");
    }

    @Test
    void laterIncomingKnockbackReplacesTheOlderPredictedSlowdown() {
        var predicted = incomingImpulse();
        var original = incomingImpulse();
        localFrame(predicted, 85);
        localFrame(original, 85);
        predicted.submitSprintInput(1, true, true, ms(85));
        var previous = localFrame(predicted, 95);
        var originalPrevious = localFrame(original, 95);
        var offset = previous.point().minus(originalPrevious.point());
        var next = withImpulse(1.10f, .20f, 1, 102, .8f, .40f, .15f);
        predicted.observe(102, List.of(next), ms(100));
        original.observe(102, List.of(next), ms(100));
        var received = localFrame(predicted, 100);
        var originalReceived = localFrame(original, 100);
        assertEquals(offset.x(), received.point().x() - originalReceived.point().x(), .00001);
        assertNull(predicted.predictedSprintOffset());
        var advanced = localFrame(predicted, 120);
        var originalAdvanced = localFrame(original, 120);
        assertEquals(originalAdvanced.point().x() - originalReceived.point().x(), advanced.point().x() - received.point().x(), .00001);
        assertEquals(originalAdvanced.point().y(), advanced.point().y());
    }

    private static ArenaMotionTimeline incomingImpulse() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        localFrame(motion, 0);
        motion.observe(101, List.of(withImpulse(.20f, 0, 1, 101, .90f, .40f, .20f)), ms(50));
        localFrame(motion, 50);
        return motion;
    }

    private static ArenaMotionTimeline.LocalView localFrame(ArenaMotionTimeline motion, int millis) {
        long now = ms(millis);
        return motion.sampleLocal(1, motion.frame(now).local(), now, false, true);
    }

    @Test
    void aPlayerTakingKnockbackPicksFromTheRenderedEyeAndSendsOnlyConfirmedPoseTimes() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
        motion.sampleLocal(1, motion.frame(ms(0)).local(), ms(0), false, true);
        motion.observe(101, List.of(withImpulse(0, 0, 1, 101, .5f, 0, 0)), ms(50));
        motion.sampleLocal(1, motion.frame(ms(50)).local(), ms(50), false, true);
        long now = ms(87) + 500_000;
        var local = motion.sampleLocal(1, motion.frame(now).local(), now, false, true);
        assertEquals(.125, local.point().x() - local.confirmedPoint().x(), .0001);
        Camera camera = new Camera();
        camera.position().set((float) local.point().x(), (float) (local.point().y() + PlayerDimensions.EYE_HEIGHT), 0);
        camera.setPitch(0);

        camera.setYaw(180);
        assertEquals(0, PracticeGame.displayedTarget(camera, CombatRules.NO_DEBUFF, local.pose(),
                List.of(opponentAt(-3.35f))).targetId());
        camera.setYaw(0);
        var approaching = PracticeGame.displayedTarget(camera, CombatRules.NO_DEBUFF, local.pose(),
                List.of(opponentAt(3.45f)));
        assertEquals(2, approaching.targetId());
        assertEquals(local.pose(), approaching.attacker());
    }

    private static ArenaMotionTimeline.Presented opponentAt(float x) {
        var timeline = new ArenaMotionTimeline();
        var fighter = new Fighter(2, "Opponent", x, 73, 0, 0, 0, 20, false, false, false, Item.SWORD,
                0, 0, 0, 0, -1, "", 0, 0, 0, -1, 1, -1, -1, 0, 0, 0);
        timeline.observe(101, List.of(fighter), 0);
        return timeline.sample(2, cursor(101));
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
        double handoffStep = after.point().x() - before.point().x();
        assertTrue(handoffStep > .10 && handoffStep < .18, "camera should advance at near impulse speed through confirmation");
        var next = motion.sampleLocal(1, motion.frame(ms(110)).local(), ms(110), false, true);
        assertEquals(handoffStep, next.point().x() - after.point().x(), .005);
        assertTrue(after.pose().last() <= 102);
        var caughtUp = motion.sampleLocal(1, motion.frame(ms(240)).local(), ms(240), false, true);
        assertEquals(1.05, caughtUp.point().x(), .0001);
        assertEquals(102, caughtUp.pose().tick());
    }

    @Test
    void predictedImpulseAdvancesAtOneTickOfTravelPerServerTickAcrossRenderRates() {
        for (int fps : List.of(60, 144)) {
            var motion = new ArenaMotionTimeline();
            motion.observe(100, List.of(fighter(0, 0, 0, 0, 20, -1, 1)), ms(0));
            motion.sampleLocal(1, motion.frame(ms(0)).local(), ms(0), false, true);
            motion.observe(101, List.of(withImpulse(0, 0, 1, 101, .90f, 0, 0)), ms(50));
            motion.sampleLocal(1, motion.frame(ms(50)).local(), ms(50), false, true);
            // After the buffered half-tick approach, each frame consumes its share of the impulse.
            long interval = 1_000_000_000L / fps;
            var previous = motion.sampleLocal(1, motion.frame(ms(75)).local(), ms(75), false, true);
            for (long now = ms(75) + interval; now < ms(125); now += interval) {
                var current = motion.sampleLocal(1, motion.frame(now).local(), now, false, true);
                assertEquals(.90 * interval / ms(50), current.point().x() - previous.point().x(), .0001);
                assertEquals(101, current.pose().tick());
                previous = current;
            }
        }
    }

    @Test
    void hitCueClipsAgainstCoverAndNeverBridgesTeleportOrAStaleGap() {
        var motion = new ArenaMotionTimeline();
        motion.observe(100, List.of(fighter(7.60f, 7, 0, 0, 20, -1, 1)), ms(0));
        motion.sampleLocal(1, motion.frame(ms(0)).local(), ms(0), false, true);
        motion.observe(101, List.of(withImpulse(7.60f, 7, 1, 101, .90f, 0, 0)), ms(50));
        motion.sampleLocal(1, motion.frame(ms(50)).local(), ms(50), false, true);
        var clipped = motion.sampleLocal(1, motion.frame(ms(140)).local(), ms(140), false, true);
        assertEquals(7.70, clipped.point().x(), .0001);
        assertEquals(7.60, clipped.confirmedPoint().x(), .0001);

        motion.observe(102, List.of(withImpulse(2, 0, 2, 101, .90f, 0, 0)), ms(150));
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
