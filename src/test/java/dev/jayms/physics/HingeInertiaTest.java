package dev.jayms.physics;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HingeInertiaTest {
    @Test void unequalInertiasConserveMomentum(){
        var a=new RigidBody(new Vector3f(),new Vector3f(1),1);var b=new RigidBody(new Vector3f(),new Vector3f(3),1);
        var h=new Constraints.Hinge(a,b,new Vector3f(),new Vector3f(),new Vector3f(0,1,0));a.angularVelocity.x=30;
        float before=a.angularVelocity.x/a.inverseInertia.x+b.angularVelocity.x/b.inverseInertia.x;
        for(int i=0;i<10;i++)h.solve(1f/120);
        assertEquals(before,a.angularVelocity.x/a.inverseInertia.x+b.angularVelocity.x/b.inverseInertia.x,.0001);
        assertEquals(a.angularVelocity.x,b.angularVelocity.x,.0001);
    }
    @Test void oppositeAxesAlign(){
        var a=new RigidBody(new Vector3f(),new Vector3f(1),0);var b=new RigidBody(new Vector3f(),new Vector3f(1),1);b.rotation.rotateX((float)Math.PI);
        var h=new Constraints.Hinge(a,b,new Vector3f(),new Vector3f(),new Vector3f(0,1,0));for(int i=0;i<10;i++)h.solve(1f/120);
        assertTrue(a.rotation.transform(new Vector3f(0,1,0)).dot(b.rotation.transform(new Vector3f(0,1,0)))>.999f);
    }
    @Test void freeAxisSpinIsPreserved(){var a=new RigidBody(new Vector3f(),new Vector3f(1),0);var b=new RigidBody(new Vector3f(),new Vector3f(3),1);b.angularVelocity.y=4;new Constraints.Hinge(a,b,new Vector3f(),new Vector3f(),new Vector3f(0,1,0)).solve(.01f);assertEquals(4,b.angularVelocity.y,.0001);}
}
