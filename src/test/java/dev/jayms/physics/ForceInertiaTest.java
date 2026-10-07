package dev.jayms.physics;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForceInertiaTest {
    private RigidBody box(float mass) {
        return new RigidBody(new Vector3f(),new Vector3f(1,2,3),mass);
    }
    private PhysicsWorld world(RigidBody body) {
        PhysicsWorld world=new PhysicsWorld();world.gravity.zero();world.bodies.add(body);return world;
    }
    @Test void forceWakesSleepingBodyBeforeIntegration() {
        RigidBody body=box(1);body.sleeping=true;body.force.x=120;
        world(body).step(1f/120);
        assertEquals(1,body.velocity.x,.00001);assertFalse(body.sleeping);
        assertEquals(0,body.force.length());assertEquals(1f/120,body.position.x,.00001);
    }
    @Test void torqueWakesSleepingRotatedBody() {
        RigidBody body=box(1);body.rotation.rotateZ((float)Math.PI/2);body.sleeping=true;body.torque.x=120;
        world(body).step(1f/120);
        assertEquals(.3,body.angularVelocity.x,.00001);assertEquals(0,body.angularVelocity.y,.00001);
        assertFalse(body.sleeping);assertEquals(0,body.torque.length());
    }
    @Test void pointImpulseUsesWorldInverseInertia() {
        RigidBody body=box(1);body.rotation.rotateZ((float)Math.PI/2);
        body.impulseAt(new Vector3f(0,0,1),new Vector3f(0,1,0));
        assertEquals(.3,body.angularVelocity.x,.00001);assertEquals(0,body.angularVelocity.y,.00001);
        assertEquals(1,body.velocity.z,.00001);
    }
    @Test void unrotatedInertiaAndCentreImpulseRemainCorrect() {
        RigidBody body=box(1);
        body.impulseAt(new Vector3f(0,0,1),new Vector3f(0,1,0));
        assertEquals(3f/13,body.angularVelocity.x,.00001);
        Vector3f angular=new Vector3f(body.angularVelocity);
        body.impulseAt(new Vector3f(1,0,0),body.position);
        assertEquals(angular,body.angularVelocity);
    }
    @Test void obliqueRotationProducesOffDiagonalAngularResponse() {
        RigidBody body=box(1);body.rotation.rotateZ((float)Math.PI/4);
        body.impulseAt(new Vector3f(0,0,1),new Vector3f(0,1,0));
        assertEquals((3f/13+.3f)/2,body.angularVelocity.x,.00001);
        assertEquals((3f/13-.3f)/2,body.angularVelocity.y,.00001);
    }
    @Test void SmallAppliedTorquePreventsImmediateResleep() {
        RigidBody body=box(1);body.sleeping=true;body.quietTime=1;body.torque.x=.001f;
        world(body).step(1f/120);
        assertFalse(body.sleeping);assertTrue(body.angularVelocity.x>0);
    }
    @Test void unloadedSleepingAndImmovableBodiesStayStill() {
        RigidBody sleeping=box(1);sleeping.sleeping=true;world(sleeping).step(1f/120);assertTrue(sleeping.sleeping);
        for(boolean kinematic:new boolean[]{false,true}) {
            RigidBody body=box(kinematic?1:0);body.kinematic=kinematic;body.sleeping=true;
            body.force.set(120,0,0);body.torque.set(120,0,0);
            body.impulseAt(new Vector3f(0,0,1),new Vector3f(0,1,0));world(body).step(1f/120);
            assertEquals(0,body.velocity.length());assertEquals(0,body.angularVelocity.length());assertTrue(body.sleeping);
        }
    }
}
