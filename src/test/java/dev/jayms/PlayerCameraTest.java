package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.player.Player;
import dev.jayms.player.Player.CameraView;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerCameraTest {
    private World emptyWorld() {
        World world = new World();
        world.addChunk(new ChunkPos(0, 0, 0), new Chunk());
        return world;
    }

    @Test
    void cyclePlacesCameraBehindThenInFrontAndReturnsToEyes() {
        World world = emptyWorld();
        Camera camera = new Camera();
        Player player = new Player(new Vector3f(8, 5, 8), 35, -20, camera);
        Vector3f eye = player.eyePosition(), forward = player.facingDirection();
        player.updateCamera(world);
        assertEquals(CameraView.FIRST_PERSON, player.cameraView());
        assertFalse(player.thirdPerson());
        assertTrue(camera.position().distance(eye) < .0001f);
        for (int cycle = 0; cycle < 2; cycle++) {
            player.toggleView();
            player.updateCamera(world);
            assertEquals(CameraView.THIRD_PERSON, player.cameraView());
            assertTrue(player.thirdPerson());
            assertTrue(camera.position().distance(new Vector3f(eye).fma(-4, forward)) < .0001f);
            assertTrue(camera.getDirection().distance(forward) < .0001f);
            player.toggleView();
            player.updateCamera(world);
            assertEquals(CameraView.FRONT, player.cameraView());
            assertTrue(player.thirdPerson());
            assertTrue(camera.position().distance(new Vector3f(eye).fma(4, forward)) < .0001f);
            assertTrue(camera.getDirection().distance(new Vector3f(forward).negate()) < .0001f);
            player.toggleView();
            player.updateCamera(world);
            assertEquals(CameraView.FIRST_PERSON, player.cameraView());
            assertFalse(player.thirdPerson());
            assertTrue(camera.position().distance(eye) < .0001f);
            assertTrue(camera.getDirection().distance(forward) < .0001f);
        }
    }

    @Test
    void frontViewPreservesMouseAimMovementAndNetworkPose() {
        World world = emptyWorld();
        Player first = new Player(new Vector3f(8, 5, 8), 0, 0, new Camera());
        Camera camera = new Camera();
        Player front = new Player(new Vector3f(8, 5, 8), 0, 0, camera);
        front.toggleView();
        front.toggleView();
        first.look(25, -15);
        front.look(25, -15);
        first.toggleFlight();
        front.toggleFlight();
        for (int i = 0; i < 10; i++) {
            first.step(world, 1f / 60, 1, 1, false, false);
            front.step(world, 1f / 60, 1, 1, false, false);
            first.updateCamera(world);
            front.updateCamera(world);
        }
        assertTrue(front.position().distance(first.position()) < .0001f);
        assertTrue(front.facingDirection().distance(first.facingDirection()) < .0001f);
        assertEquals(first.pose(1), front.pose(1));
        assertTrue(camera.getDirection().dot(front.facingDirection()) < -.999f);
    }

    @Test
    void terrainPullsBothExternalCamerasInBeforeTheWall() {
        World world = emptyWorld();
        world.setBlock(5, 6, 8, 3);
        world.setBlock(11, 6, 8, 3);
        Camera camera = new Camera();
        Player player = new Player(new Vector3f(8.5f, 5, 8.5f), 0, 0, camera);
        player.toggleView();
        player.updateCamera(world);
        assertTrue(camera.position().x > 6 && camera.position().x < 8.5f);
        assertTrue(camera.getDirection().x > .999f);
        player.toggleView();
        player.updateCamera(world);
        assertTrue(camera.position().x > 8.5f && camera.position().x < 11);
        assertTrue(camera.getDirection().x < -.999f);
    }
}
