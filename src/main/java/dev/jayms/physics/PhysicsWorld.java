package dev.jayms.physics;

import java.util.*;
import org.joml.Vector3f;

/** Fixed-step broadphase, swept box motion and sequential normal/friction impulses. */
public final class PhysicsWorld {
    public final List<RigidBody> bodies=new ArrayList<>();
    public final List<Constraints.Joint> joints=new ArrayList<>();
    public final Vector3f gravity=new Vector3f(0,-24,0),wind=new Vector3f();
    public int iterations=10;
    private final SpatialGrid<RigidBody> grid=new SpatialGrid<>(2);
    public record Overlap(RigidBody a,RigidBody b) {}
    private Set<Overlap> overlaps=Set.of(),entered=Set.of(),exited=Set.of();
    public Set<Overlap> overlaps(){return overlaps;}public Set<Overlap> entered(){return entered;}public Set<Overlap> exited(){return exited;}
    public void step(float dt) {
        if(!Float.isFinite(dt)||dt<0||dt>.25f)throw new IllegalArgumentException("Physics time must be 0..0.25 seconds");
        if(dt==0)return;int steps=Math.max(1,(int)Math.ceil(dt/(1f/120)));float h=dt/steps;
        entered=new LinkedHashSet<>();exited=new LinkedHashSet<>();
        for(int s=0;s<steps;s++) substep(h);
        entered=Set.copyOf(entered);exited=Set.copyOf(exited);
        for(RigidBody b:bodies){b.force.zero();b.torque.zero();}
    }
    private float weight(RigidBody b){return b.kinematic?0:b.inverseMass;}
    private void substep(float h) {
        for(RigidBody b:bodies) {
            b.previous.set(b.position);
            if(weight(b)>0&&(b.force.lengthSquared()>0||b.torque.lengthSquared()>0))b.wake();
            if(weight(b)>0&&!b.sleeping) {
                b.velocity.fma(h,gravity).fma(h*b.inverseMass,b.force);
                b.angularVelocity.add(b.inverseInertiaWorld(b.torque).mul(h));
                b.rotation.integrate(h,b.angularVelocity.x,b.angularVelocity.y,b.angularVelocity.z).normalize();
            }
        }
        for(var joint:joints)joint.beginStep();
        sweepMotion(h);
        for(int iteration=0;iteration<iterations;iteration++) {
            grid.rebuild(bodies,RigidBody::bounds);
            Map<RigidBody,Integer> ids=new IdentityHashMap<>();for(int i=0;i<bodies.size();i++)ids.put(bodies.get(i),i);
            for(RigidBody a:bodies)for(RigidBody b:grid.query(a.bounds().expanded(.002f))) {
                if(ids.get(b)<=ids.get(a)||a.trigger||b.trigger||!a.interacts(b)||!a.bounds().expanded(.002f).overlaps(b.bounds()))continue;
                Vector3f normal=a.bounds().penetrationNormal(b.bounds());float depth=a.bounds().penetration(b.bounds(),normal),sum=weight(a)+weight(b);if(sum==0)continue;
                float correction=Math.max(0,depth-.0001f)/sum*.8f;
                a.position.fma(correction*weight(a),normal);b.position.fma(-correction*weight(b),normal);
                resolveVelocity(a,b,normal);
            }
            for(var joint:joints)if(!(joint instanceof Constraints.Spring))joint.solve(h);
        }
        for(var joint:joints)if(joint instanceof Constraints.Spring)joint.solve(h);
        Set<Overlap> current=new LinkedHashSet<>();
        for(int i=0;i<bodies.size();i++)for(int j=i+1;j<bodies.size();j++){
            var a=bodies.get(i);var b=bodies.get(j);if((a.trigger||b.trigger)&&a.interacts(b)&&a.bounds().overlaps(b.bounds()))current.add(new Overlap(a,b));
        }
        recordOverlaps(Set.copyOf(current));
        for(RigidBody b:bodies)if(weight(b)>0&&!b.trigger) {
            if(b.velocity.lengthSquared()<.015f&&b.angularVelocity.lengthSquared()<.015f&&b.force.lengthSquared()==0&&b.torque.lengthSquared()==0) {
                b.quietTime+=h;if(b.quietTime>.75f){b.sleeping=true;b.velocity.zero();b.angularVelocity.zero();}
            } else {b.quietTime=0;b.sleeping=false;}
        }
    }
    private boolean moving(RigidBody b) {
        return !b.sleeping && (weight(b)>0 || b.kinematic);
    }
    private Vector3f motionVelocity(RigidBody b) {
        return moving(b)?new Vector3f(b.velocity):new Vector3f();
    }
    /** Advance every body on the same clock so impulses affect only time after contact. */
    private void sweepMotion(float h) {
        float remaining=h;
        Map<RigidBody,Integer> ids=new IdentityHashMap<>();
        for(int i=0;i<bodies.size();i++)ids.put(bodies.get(i),i);
        // A finite contact budget keeps pathological piles bounded. Unconsumed time is
        // held at the last safe pose rather than advanced through unchecked obstacles.
        for(int contact=0;contact<256 && remaining>0;contact++) {
            final float duration=remaining;
            grid.rebuild(bodies,b->b.bounds().swept(motionVelocity(b).mul(duration)));
            float fraction=1;RigidBody first=null,second=null;Vector3f normal=null;
            for(RigidBody a:bodies)for(RigidBody b:grid.query(a.bounds().swept(motionVelocity(a).mul(duration)))) {
                if(ids.get(b)<=ids.get(a)||a.trigger||b.trigger||!a.interacts(b)
                        ||weight(a)+weight(b)==0||!moving(a)&&!moving(b))continue;
                Vector3f relative=motionVelocity(a).sub(motionVelocity(b)).mul(duration);
                var hit=a.bounds().sweep(b.bounds(),relative);
                if(hit!=null&&hit.time()<=fraction&&relative.dot(hit.normal())<-1e-8f) {
                    fraction=hit.time();first=a;second=b;normal=hit.normal();
                }
            }
            float elapsed=remaining*fraction;
            recordSweptTriggers(elapsed);
            for(RigidBody b:bodies)if(moving(b))b.position.fma(elapsed,b.velocity);
            remaining-=elapsed;
            if(first==null)break;
            // Normal-only clearance absorbs floating-point TOI roundoff without
            // shortening tangential travel or consuming rebound time.
            float sum=weight(first)+weight(second);
            first.position.fma(.000001f*weight(first)/sum,normal);
            second.position.fma(-.000001f*weight(second)/sum,normal);
            resolveVelocity(first,second,normal);
        }
    }
    private void recordOverlaps(Set<Overlap> current) {
        for(var pair:current)if(!overlaps.contains(pair))entered.add(pair);
        for(var pair:overlaps)if(!current.contains(pair))exited.add(pair);
        overlaps=current;
    }
    /** Follow actual CCD segments, so an obstacle cannot create a false trigger crossing. */
    private void recordSweptTriggers(float elapsed) {
        Set<Overlap> start=new LinkedHashSet<>(),end=new LinkedHashSet<>();
        for(int i=0;i<bodies.size();i++)for(int j=i+1;j<bodies.size();j++) {
            var a=bodies.get(i);var b=bodies.get(j);
            if(!(a.trigger||b.trigger)||!a.interacts(b))continue;
            var pair=new Overlap(a,b);var ab=a.bounds();var bb=b.bounds();
            Vector3f da=motionVelocity(a).mul(elapsed),db=motionVelocity(b).mul(elapsed);
            boolean inside=ab.overlaps(bb),outsideEnd=!ab.translate(da).overlaps(bb.translate(db));
            if(inside)start.add(pair);if(!outsideEnd)end.add(pair);
            if(!inside&&outsideEnd&&sweptOverlap(ab,bb,new Vector3f(da).sub(db))){entered.add(pair);exited.add(pair);}
        }
        recordOverlaps(start);recordOverlaps(end);
    }
    private boolean sweptOverlap(Aabb a,Aabb b,Vector3f delta) {
        float enter=0,exit=1;
        float[] amin={a.x0(),a.y0(),a.z0()},amax={a.x1(),a.y1(),a.z1()};
        float[] bmin={b.x0(),b.y0(),b.z0()},bmax={b.x1(),b.y1(),b.z1()};
        for(int axis=0;axis<3;axis++){
            float v=delta.get(axis);
            if(Math.abs(v)<1e-9f){if(amax[axis]<=bmin[axis]||amin[axis]>=bmax[axis])return false;continue;}
            float t0=(bmin[axis]-amax[axis])/v,t1=(bmax[axis]-amin[axis])/v;
            enter=Math.max(enter,Math.min(t0,t1));exit=Math.min(exit,Math.max(t0,t1));
        }
        return enter<exit; // A grazing touch has no positive overlap interval.
    }
    private void resolveVelocity(RigidBody a,RigidBody b,Vector3f normal) {
        float wa=weight(a),wb=weight(b),sum=wa+wb;if(sum==0)return;
        Vector3f relative=new Vector3f(a.velocity).sub(b.velocity);float vn=relative.dot(normal);if(vn>=0)return;
        float restitution=Math.abs(vn)<.5f?0:Math.max(a.restitution,b.restitution);
        float impulse=-(1+restitution)*vn/sum;
        if(wa*impulse>1e-7f&&a.sleeping)a.wake();
        if(wb*impulse>1e-7f&&b.sleeping)b.wake();
        a.velocity.fma(wa*impulse,normal);b.velocity.fma(-wb*impulse,normal);
        Vector3f tangent=relative.sub(new Vector3f(normal).mul(vn));float speed=tangent.length();
        if(speed>1e-6f){tangent.div(speed);float friction=Math.min(speed/sum,impulse*(float)Math.sqrt(a.friction*b.friction));a.velocity.fma(-wa*friction,tangent);b.velocity.fma(wb*friction,tangent);}
    }
    public record RayHit(RigidBody body,float distance,Vector3f normal) {}
    public RayHit raycast(Vector3f origin,Vector3f direction,float distance,int mask) {
        if(!origin.isFinite()||!direction.isFinite()||direction.lengthSquared()<1e-12f||!Float.isFinite(distance)||distance<0)throw new IllegalArgumentException("Invalid ray");
        Vector3f normalized=new Vector3f(direction).normalize();RayHit best=null;
        for(var b:bodies)if((mask&b.layer)!=0){var hit=b.bounds().ray(origin,normalized,distance);if(hit!=null&&(best==null||hit.time()*distance<best.distance))best=new RayHit(b,hit.time()*distance,hit.normal());}
        return best;
    }
    public void explode(Vector3f origin,float radius,float impulse){for(var b:bodies)b.impulse(VehicleForces.explosion(b.position,origin,radius,impulse));}
}
