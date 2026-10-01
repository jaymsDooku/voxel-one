package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.player.*;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.*;

class PlayerAnimationTest {
    @Test
    void classicRigHasFullWidthLimbsAndOpposingWalkCycle() {
        assertEquals(.5f, PlayerAnimation.HEAD);
        assertEquals(.5f, PlayerAnimation.BODY_WIDTH);
        assertEquals(.75f, PlayerAnimation.BODY_HEIGHT);
        assertEquals(.25f, PlayerAnimation.LIMB_WIDTH);
        assertEquals(.75f, PlayerAnimation.LIMB_HEIGHT);
        var pose = PlayerAnimation.pose(0, .6f, 1, false);
        assertEquals(-pose.rightArm(), pose.leftArm(), .00001f);
        assertEquals(-pose.rightLeg(), pose.leftLeg(), .00001f);
        assertEquals(-pose.rightArm() * 1.4f, pose.rightLeg(), .00001f);
        var opposite = PlayerAnimation.pose((float) Math.PI, .6f, 1, false);
        assertEquals(-pose.rightLeg(), opposite.rightLeg(), .00001f);
        assertEquals(0, PlayerAnimation.pose(10, 0, 1, false).rightArm());
        assertEquals(0, PlayerAnimation.attack(0));
        assertEquals(0, PlayerAnimation.attack(1));
        assertTrue(PlayerAnimation.attack(.5f) > .99f);
    }

    @Test
    void clickSwingCompletesAndRapidClicksDoNotPinTheArm() {
        World world = new World();
        world.addChunk(new ChunkPos(0, 0, 0), new Chunk());
        Player player = new Player(new Vector3f(8, 5, 8), 0, 0, new Camera());
        assertEquals(1, player.swingProgress());
        player.swing();
        assertEquals(0, player.swingProgress());
        player.step(world, .1f, 0, 0, false, false);
        float progress = player.swingProgress();
        player.swing();
        assertEquals(progress, player.swingProgress());
        player.step(world, .1f, 0, 0, false, false);
        player.step(world, .1f, 0, 0, false, false);
        assertEquals(1, player.swingProgress(), .0001);
        player.swing();
        assertEquals(0, player.swingProgress());
    }

    @Test
    void swingAndHeldItemRoundTripAndRemoteSwingDoesNotRewindAtReset() throws Exception {
        var pose =
                new Protocol.Pose(1, 8, 5, 8, 25, -10, 5, .7f, false, .4f, Blocks.FLOWER_POT, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        pose.write(new DataOutputStream(bytes));
        assertEquals(
                pose,
                Protocol.Pose.read(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertTrue(pose.valid());
        assertFalse(new Protocol.Pose(1, 8, 5, 8, 0, 0, 0, 0, false, Float.NaN, 0).valid());
        assertFalse(new Protocol.Pose(1, 8, 5, 8, 0, 0, 0, 0, false, 1.5f, 0).valid());
        var remote = new RemotePlayer("jayms");
        remote.accept(new Protocol.Pose(1, 8, 5, 8, 0, 0, 0, 0, false, 1, 0), 0);
        remote.accept(
                new Protocol.Pose(1, 8, 5, 8, 0, 0, 0, 0, false, 0, Blocks.FLOWER_POT, true),
                100000000);
        assertEquals(1, remote.sample(150000000).swingProgress());
        assertEquals(0, remote.sample(200000000).swingProgress());
        assertEquals(Blocks.FLOWER_POT, remote.sample(200000000).heldItem());
        assertFalse(remote.sample(150000000).placingSwing());
        assertTrue(remote.sample(200000000).placingSwing());
    }

    @Test
    void attackStrikesDownFromShoulderAndPlacementHasSmallerMotion() {
        var start = PlayerAnimation.pose(0, 0, 0, true, false);
        var middle = PlayerAnimation.pose(0, 0, .36f, true, false);
        var end = PlayerAnimation.pose(0, 0, .72f, true, false);
        assertEquals(Math.PI / 2, start.rightArm(), .0001);
        assertTrue(start.rightArm() > middle.rightArm());
        assertTrue(middle.rightArm() > end.rightArm());
        assertEquals(.3f, PlayerAnimation.pose(0, 0, 1, true, false).rightArm(), .0001);
        float placingRange =
                PlayerAnimation.pose(0, 0, 0, true, true).rightArm()
                        - PlayerAnimation.pose(0, 0, .72f, true, true).rightArm();
        assertTrue(placingRange < (start.rightArm() - end.rightArm()) / 3);
        float top = wristHeight(0, false),
                halfway = wristHeight(.36f, false),
                bottom = wristHeight(.72f, false);
        assertTrue(top > halfway && halfway > bottom, "First-person wrist must move downward");
        assertEquals(top, wristHeight(1, false), .0001);
        assertTrue(wristHeight(0, true) - wristHeight(.72f, true) < (top - bottom) / 2);
    }

    private float wristHeight(float progress, boolean placing) {
        return PlayerAnimation.firstPersonHand(0, 0, progress, placing)
                .transformPosition(new Vector3f(0, .75f, 0))
                .y;
    }

    @Test
    void placingSwingUsesItsOwnDurationAndCanReplaceAttack() {
        World world = new World();
        world.addChunk(new ChunkPos(0, 0, 0), new Chunk());
        Player player = new Player(new Vector3f(8, 5, 8), 0, 0, new Camera());
        player.swing();
        player.step(world, .05f, 0, 0, false, false);
        player.swing(true);
        assertTrue(player.placingSwing());
        assertTrue(player.pose(1).placingSwing());
        assertEquals(0, player.swingProgress());
        player.step(world, .1f, 0, 0, false, false);
        assertEquals(.4f, player.swingProgress(), .0001);
        player.swing(true);
        assertEquals(.4f, player.swingProgress(), .0001);
        player.step(world, .1f, 0, 0, false, false);
        player.step(world, .05f, 0, 0, false, false);
        assertEquals(1, player.swingProgress(), .0001);
        player.swing();
        assertFalse(player.placingSwing());
    }

    @Test
    void visibleHairLayerClearsEveryExteriorHeadPlane() {
        assertTrue(PlayerSkin.HAIR_WIDTH / 2 > PlayerAnimation.HEAD / 2);
        assertTrue(PlayerSkin.HAIR_BOTTOM + PlayerSkin.HAIR_HEIGHT > PlayerAnimation.HEAD);
        assertEquals(
                PlayerSkin.OUTER_INFLATION,
                PlayerSkin.HAIR_BOTTOM + PlayerSkin.HAIR_HEIGHT - PlayerAnimation.HEAD,
                .0001);
    }
}
