package dev.jayms.physics;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WheelContactTest {
    @Test void airborneSpinIsConserved(){var w=new VehicleForces.Wheel();w.angularSpeed=7;var f=w.force(100,0,-10,0,0,.8f,7200,1f/120);assertEquals(7,w.angularSpeed,.0001);assertEquals(0,f.length());}
    @Test void zeroNormalLoadHasNoTraction(){var w=new VehicleForces.Wheel();w.angularSpeed=7;var f=w.force(.8f,0,-10,0,0,.8f,0,1f/120);assertEquals(7,w.angularSpeed,.0001);assertEquals(0,f.x);assertTrue(f.y>0);}
    @Test void airborneDriveStillSpinsWheel(){var w=new VehicleForces.Wheel();w.force(100,0,0,12,0,.8f,7200,.01f);assertTrue(w.angularSpeed>0);}
    @Test void unevenRotatedSupportsProduceAngularResponse()throws Exception {
        try(var s=new PhysicsScene()){
            s.physics.bodies.clear();s.physics.joints.clear();s.physics.gravity.zero();s.vehicle.position.set(8,3,8);s.vehicle.rotation.rotateY(.4f);s.physics.bodies.add(s.vehicle);
            Vector3f mount=s.vehicle.rotation.transform(new Vector3f(-.8f,0,-1.3f)).add(s.vehicle.position);
            s.physics.bodies.add(new RigidBody(new Vector3f(mount.x,2.3f,mount.z),new Vector3f(.2f,.1f,.2f),0));
            s.step(1f/120,0,new Vector3f(),false);
            assertTrue(s.vehicle.angularVelocity.length()>.001f);assertTrue(s.vehicle.velocity.y>0);
        }
    }
}
