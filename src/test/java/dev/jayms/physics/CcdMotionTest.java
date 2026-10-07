package dev.jayms.physics;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verify travelled distance, not only final velocity, after contact. */
class CcdMotionTest {
    private RigidBody box(Vector3f position, Vector3f halfSize, float mass) {
        RigidBody body = new RigidBody(position, halfSize, mass);
        body.friction = 0;
        body.restitution = 0;
        return body;
    }

    @Test void frictionlessFloorRetainsFullTangentialTravel() {
        PhysicsWorld world = new PhysicsWorld();
        world.bodies.add(box(new Vector3f(0, -.5f, 0), new Vector3f(30, .5f, 30), 0));
        RigidBody slider = box(new Vector3f(0, .5f, 0), new Vector3f(.5f), 1);
        slider.velocity.x = 10;
        world.bodies.add(slider);
        for (int i = 0; i < 60; i++) world.step(1f / 60);
        assertEquals(10, slider.position.x, .002, "One second at 10 blocks/s on a frictionless floor");
        assertEquals(10, slider.velocity.x, .001);
        assertEquals(.5, slider.position.y, .002);
    }

    @Test void diagonalWallRetainsMotionAfterMidStepHit() {
        PhysicsWorld world = new PhysicsWorld();
        world.gravity.zero();
        RigidBody wall = box(new Vector3f(4.65f, 1, 0), new Vector3f(.5f, 3, 20), 0);
        RigidBody slider = box(new Vector3f(0, 1, 0), new Vector3f(.5f), 1);
        slider.velocity.set(10, 0, 6);
        world.bodies.add(wall);
        world.bodies.add(slider);
        for (int i = 0; i < 60; i++) world.step(1f / 60);
        assertEquals(3.65, slider.position.x, .002);
        assertEquals(6, slider.position.z, .002, "Wall contact must not discard tangential time");
        assertEquals(0, slider.velocity.x, .001);
        assertEquals(6, slider.velocity.z, .001);
        assertFalse(slider.bounds().overlaps(wall.bounds()));
    }

    @Test void reboundUsesRemainingTimeInTheSameSubstep() {
        PhysicsWorld world = new PhysicsWorld();
        world.gravity.zero();
        RigidBody wall = box(new Vector3f(1.4f, 0, 0), new Vector3f(.1f, 2, 2), 0);
        RigidBody ball = box(new Vector3f(0, 0, 0), new Vector3f(.5f), 1);
        wall.restitution = ball.restitution = 1;
        ball.velocity.x = 120;
        world.bodies.add(wall);
        world.bodies.add(ball);
        world.step(1f / 120);
        assertEquals(.6, ball.position.x, .002, "Impact at .8 blocks then rebound .2 blocks");
        assertEquals(-120, ball.velocity.x, .001);
        assertFalse(ball.bounds().overlaps(wall.bounds()));
    }

    @Test void remainingSweepFindsSecondWallAfterSliding() {
        PhysicsWorld world = new PhysicsWorld();
        world.gravity.zero();
        RigidBody xWall = box(new Vector3f(1.4f, 0, 0), new Vector3f(.1f, 3, 3), 0);
        RigidBody zWall = box(new Vector3f(0, 0, 1.5f), new Vector3f(3, 3, .1f), 0);
        RigidBody slider = box(new Vector3f(), new Vector3f(.5f), 1);
        slider.velocity.set(240, 0, 240);
        world.bodies.add(xWall);
        world.bodies.add(zWall);
        world.bodies.add(slider);
        world.step(1f / 120);
        assertEquals(.8, slider.position.x, .002);
        assertEquals(.9, slider.position.z, .002);
        assertEquals(0, slider.velocity.length(), .001);
        assertFalse(slider.bounds().overlaps(xWall.bounds()));
        assertFalse(slider.bounds().overlaps(zWall.bounds()));
    }

    @Test void equalMassImpactAdvancesBothBodiesForRemainingTime() {
        PhysicsWorld world = new PhysicsWorld();
        world.gravity.zero();
        RigidBody a = box(new Vector3f(), new Vector3f(.5f), 1);
        RigidBody b = box(new Vector3f(1.5f, 0, 0), new Vector3f(.5f), 1);
        a.velocity.x = 120;
        a.restitution = b.restitution = 1;
        world.bodies.add(a);
        world.bodies.add(b);
        world.step(1f / 120);
        assertEquals(.5, a.position.x, .002);
        assertEquals(2, b.position.x, .002);
        assertEquals(0, a.velocity.x, .001);
        assertEquals(120, b.velocity.x, .001);
    }
}
