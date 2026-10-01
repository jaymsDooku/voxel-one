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
        assertTrue(PlayerAnimation.attack(.25f) > .99f);
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
        var pose = new Protocol.Pose(1, 8, 5, 8, 25, -10, 5, .7f, false, .4f, Blocks.FLOWER_POT);
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
                new Protocol.Pose(1, 8, 5, 8, 0, 0, 0, 0, false, 0, Blocks.FLOWER_POT), 100000000);
        assertEquals(1, remote.sample(150000000).swingProgress());
        assertEquals(0, remote.sample(200000000).swingProgress());
        assertEquals(Blocks.FLOWER_POT, remote.sample(200000000).heldItem());
    }
}
