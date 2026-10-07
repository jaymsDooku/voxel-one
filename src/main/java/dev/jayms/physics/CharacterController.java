package dev.jayms.physics;

import org.joml.Vector3f;
import java.util.*;

/** Swept kinematic capsule proxy with slope limit, stairs and kinematic platform carry. */
public final class CharacterController {
    public final Vector3f position,velocity=new Vector3f(),groundNormal=new Vector3f();
    public float radius=.3f,height=1.8f,stepHeight=.6f,maxSlopeDegrees=50;
    public int layer=1,mask=-1;
    private Set<RigidBody> overlaps=Set.of(),entered=Set.of(),exited=Set.of();
    public Set<RigidBody> overlaps(){return overlaps;}public Set<RigidBody> entered(){return entered;}public Set<RigidBody> exited(){return exited;}
    public boolean grounded;private RigidBody platform;private final Vector3f platformPosition=new Vector3f();
    public CharacterController(Vector3f position){this.position=new Vector3f(position);}
    public Aabb bounds(){return new Aabb(position.x-radius,position.y,position.z-radius,position.x+radius,position.y+height,position.z+radius);}
    public void step(PhysicsWorld world,float dt,Vector3f desired,boolean jump) {
        if(dt<=0||dt>.1f||!Float.isFinite(dt)||!desired.isFinite())throw new IllegalArgumentException("Invalid character step");
        if(platform!=null&&world.bodies.contains(platform))move(world,new Vector3f(platform.position).sub(platformPosition),false);
        float response=1-(float)Math.exp(-20*dt);velocity.x+=(desired.x-velocity.x)*response;velocity.z+=(desired.z-velocity.z)*response;
        if(jump&&grounded){velocity.y=8;grounded=false;platform=null;}
        velocity.y+=world.gravity.y*dt;
        move(world,new Vector3f(velocity.x*dt,0,velocity.z*dt),grounded);
        var hit=move(world,new Vector3f(0,velocity.y*dt,0),false);
        grounded=hit!=null&&velocity.y<0&&hit.normal.y>=Math.cos(Math.toRadians(maxSlopeDegrees));
        if(hit!=null){velocity.y=0;groundNormal.set(hit.normal);}
        Set<RigidBody> next=new HashSet<>();for(var b:world.bodies)if(b.trigger&&(mask&b.layer)!=0&&(b.mask&layer)!=0&&bounds().overlaps(b.bounds()))next.add(b);
        Set<RigidBody> enter=new HashSet<>(next);enter.removeAll(overlaps);entered=Set.copyOf(enter);
        Set<RigidBody> exit=new HashSet<>(overlaps);exit.removeAll(next);exited=Set.copyOf(exit);overlaps=Set.copyOf(next);
        if(grounded&&hit.body.kinematic){platform=hit.body;platformPosition.set(platform.position);}else platform=null;
    }
    private record Contact(RigidBody body,Vector3f normal) {}
    private Contact move(PhysicsWorld world,Vector3f delta,boolean stairs) {
        Contact contact=null;
        for(int iteration=0;iteration<4&&delta.lengthSquared()>1e-12f;iteration++) {
            float fraction=1;RigidBody body=null;Vector3f normal=null;
            for(var b:world.bodies)if(!b.trigger&&(b.layer&mask)!=0&&(b.mask&layer)!=0){var h=bounds().sweep(b.bounds(),delta);if(h!=null&&h.time()<fraction){fraction=h.time();normal=h.normal();body=b;}}
            if(body==null){position.add(delta);break;}
            if(stairs&&normal.y==0&&canStep(world,delta)){return null;}
            position.fma(Math.max(0,fraction-.00001f),delta);contact=new Contact(body,normal);
            delta.mul(1-fraction);float inward=delta.dot(normal);if(inward<0)delta.fma(-inward,normal);stairs=false;
        }
        return contact;
    }
    private boolean canStep(PhysicsWorld w,Vector3f delta) {
        Vector3f start=new Vector3f(position);Vector3f up=new Vector3f(0,stepHeight+.001f,0);
        if(move(w,up,false)!=null){position.set(start);return false;}
        if(move(w,new Vector3f(delta),false)!=null){position.set(start);return false;}
        Contact landing=move(w,new Vector3f(0,-stepHeight-.03f,0),false);
        if(landing==null||landing.normal.y<.7f){position.set(start);return false;}return true;
    }
}
