package dev.jayms.physics;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContactTriggerTest {
    @Test void slowImpactWakesAndKeepsImpulse(){
        var w=new PhysicsWorld();w.gravity.zero();var a=new RigidBody(new Vector3f(-1.001f,0,0),new Vector3f(.5f),1);var b=new RigidBody(new Vector3f(),new Vector3f(.5f),1);a.velocity.x=.2f;b.sleeping=true;w.bodies.add(a);w.bodies.add(b);w.step(1f/120);assertFalse(b.sleeping);assertEquals(.1,b.velocity.x,.001);assertEquals(.2,a.velocity.x+b.velocity.x,.001);
    }
    private PhysicsWorld crossing(float speed){var w=new PhysicsWorld();w.gravity.zero();var a=new RigidBody(new Vector3f(-1,0,0),new Vector3f(.05f),1);a.velocity.x=speed;var t=new RigidBody(new Vector3f(),new Vector3f(.1f,1,1),0);t.trigger=true;w.bodies.add(a);w.bodies.add(t);return w;}
    @Test void completeCrossingKeepsBothEvents(){var w=crossing(10);w.step(.25f);assertEquals(1,w.entered().size());assertEquals(1,w.exited().size());assertTrue(w.overlaps().isEmpty());w.step(.01f);assertTrue(w.entered().isEmpty());assertTrue(w.exited().isEmpty());}
    @Test void fastSweptCrossingKeepsBothEvents(){var w=crossing(1000);w.step(1f/120);assertEquals(1,w.entered().size());assertEquals(1,w.exited().size());assertTrue(w.overlaps().isEmpty());}
    @Test void grazingCrossingHasNoEvents(){var w=crossing(1000);w.bodies.get(0).halfSize.y=.125f;w.bodies.get(0).position.y=1.125f;w.step(1f/120);assertTrue(w.entered().isEmpty());assertTrue(w.exited().isEmpty());}
    @Test void maskedCrossingHasNoEvents(){var w=crossing(1000);w.bodies.get(0).mask=0;w.step(1f/120);assertTrue(w.entered().isEmpty());assertTrue(w.exited().isEmpty());}
    @Test void wallBeforeTriggerDoesNotCreateCrossing(){var w=crossing(1000);var wall=new RigidBody(new Vector3f(-.5f,0,0),new Vector3f(.1f,1,1),0);w.bodies.add(wall);w.step(1f/120);assertTrue(w.entered().isEmpty());assertTrue(w.exited().isEmpty());}
}
