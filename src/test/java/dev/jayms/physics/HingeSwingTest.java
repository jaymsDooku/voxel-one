package dev.jayms.physics;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HingeSwingTest {
    private PhysicsWorld fixture(float gravity) {
        var w=new PhysicsWorld();w.gravity.set(0,-gravity,0);
        var a=new RigidBody(new Vector3f(0,5,0),new Vector3f(.1f),0);
        var b=new RigidBody(new Vector3f(2,5,0),new Vector3f(.4f),1);
        a.mask=b.mask=0;w.bodies.add(a);w.bodies.add(b);
        w.joints.add(new Constraints.Hinge(a,b,new Vector3f(),new Vector3f(-2,0,0),new Vector3f(0,0,1)));return w;
    }
    private void checkAnchor(PhysicsWorld w){assertTrue(w.bodies.get(1).anchor(new Vector3f(-2,0,0)).distance(w.bodies.get(0).position)<.01f);}
    @Test void gravitySwingsOffsetLoad(){var w=fixture(24);for(int i=0;i<120;i++)w.step(1f/120);var b=w.bodies.get(1);assertTrue(b.position.y<4.99f,"position="+b.position+" rotation="+b.rotation);assertTrue(Math.abs(b.rotation.z)>.1f);checkAnchor(w);}
    @Test void centreImpulseProducesSwing(){var w=fixture(0);w.bodies.get(1).impulse(new Vector3f(0,-1,0));for(int i=0;i<60;i++)w.step(1f/120);assertTrue(w.bodies.get(1).angularVelocity.z<-.1f);checkAnchor(w);}
    @Test void staticAnchorDoesNotMove(){var w=fixture(24);for(int i=0;i<120;i++)w.step(1f/120);assertEquals(new Vector3f(0,5,0),w.bodies.get(0).position);assertEquals(0,w.bodies.get(0).angularVelocity.length());}
}
