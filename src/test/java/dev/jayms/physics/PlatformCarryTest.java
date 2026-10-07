package dev.jayms.physics;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlatformCarryTest {
    static CharacterController run(float vx,float vy,boolean ceiling) {
        PhysicsWorld w=new PhysicsWorld();
        RigidBody p=new RigidBody(new Vector3f(0,3,0),new Vector3f(3,.2f,3),0);p.kinematic=true;w.bodies.add(p);
        if(ceiling)w.bodies.add(new RigidBody(new Vector3f(0,5.4f,0),new Vector3f(3,.2f,3),0));
        CharacterController c=new CharacterController(new Vector3f(0,3.2f,0));c.step(w,1f/120,new Vector3f(),false);assertTrue(c.grounded);
        p.velocity.set(vx,vy,0);
        for(int i=0;i<120;i++){w.step(1f/120);c.step(w,1f/120,new Vector3f(),false);}
        return c;
    }
    @Test void risingPlatformCarries(){assertEquals(4.2,run(0,1,false).position.y,.002);}
    @Test void descendingPlatformCarries(){assertEquals(2.2,run(0,-1,false).position.y,.002);}
    @Test void horizontalPlatformCarries(){assertEquals(1,run(1,0,false).position.x,.002);}
    @Test void ceilingStillBlocksCarry(){assertTrue(run(0,1,true).position.y+1.8f<=5.201f);}
}
