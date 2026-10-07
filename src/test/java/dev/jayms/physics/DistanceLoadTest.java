package dev.jayms.physics;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DistanceLoadTest {
    private PhysicsWorld hanging(float gravity,float limit,float compliance) {
        PhysicsWorld w=new PhysicsWorld();w.gravity.set(0,-gravity,0);
        RigidBody anchor=new RigidBody(new Vector3f(0,3,0),new Vector3f(.1f),0);
        RigidBody load=new RigidBody(new Vector3f(0,2,0),new Vector3f(.1f),1);
        w.bodies.add(anchor);w.bodies.add(load);var rope=new Constraints.Distance(anchor,load,1,compliance,true);rope.breakForce=limit;w.joints.add(rope);return w;
    }
    @Test void sustainedSubLimitLoadDoesNotBreakOrAccumulateVelocity() {
        var w=hanging(24,100,0);for(int i=0;i<600;i++)w.step(1f/120);
        assertFalse(w.joints.get(0).broken());assertEquals(2,w.bodies.get(1).position.y,.002);assertEquals(0,w.bodies.get(1).velocity.y,.002);
    }
    @Test void overloadBreaksAndFalls() {
        var w=hanging(120,100,0);w.step(1f/120);assertTrue(w.joints.get(0).broken());assertTrue(w.bodies.get(1).velocity.y<0);
    }
    @Test void loadThresholdIsStableAcrossTimestepsAndIterations() {
        for(float dt:new float[]{1f/60,1f/120,1f/240})for(int iterations:new int[]{1,10,20}) {
            var w=hanging(24,30,0);w.iterations=iterations;for(int i=0;i<Math.round(2/dt);i++)w.step(dt);assertFalse(w.joints.get(0).broken(),"dt="+dt+" iterations="+iterations);
            var overloaded=hanging(36,30,0);overloaded.iterations=iterations;overloaded.step(dt);assertTrue(overloaded.joints.get(0).broken());
        }
    }
    @Test void compliantRopeSustainsLoadWithoutFalseBreakage() {
        var w=hanging(24,100,.0001f);for(int i=0;i<600;i++)w.step(1f/120);assertFalse(w.joints.get(0).broken());assertEquals(1.9976,w.bodies.get(1).position.y,.001);assertEquals(0,w.bodies.get(1).velocity.y,.002);
    }
    private PhysicsWorld hinged(float gravity) {
        var w=hanging(gravity,100,0);w.joints.clear();
        var hinge=new Constraints.Hinge(w.bodies.get(0),w.bodies.get(1),new Vector3f(0,-1,0),new Vector3f(),new Vector3f(0,0,1));hinge.breakForce=100;w.joints.add(hinge);return w;
    }
    @Test void hingeSustainsSubLimitLoadWithoutVelocityGrowth() {
        var w=hinged(24);for(int i=0;i<600;i++)w.step(1f/120);assertFalse(w.joints.get(0).broken());assertEquals(2,w.bodies.get(1).position.y,.002);assertEquals(0,w.bodies.get(1).velocity.y,.002);
    }
    @Test void hingeOverloadBreaks() {var w=hinged(120);w.step(1f/120);assertTrue(w.joints.get(0).broken());}
    @Test void slackRopeDoesNotResistInwardMotion() {
        var w=hanging(0,100,0);var load=w.bodies.get(1);load.position.y=2.5f;load.velocity.y=1;w.step(1f/120);assertEquals(1,load.velocity.y,.0001);assertFalse(w.joints.get(0).broken());
    }
}
