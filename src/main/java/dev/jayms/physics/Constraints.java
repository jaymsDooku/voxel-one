package dev.jayms.physics;

import org.joml.Vector3f;

/** Iterative positional joints. Broken joints stop applying corrections and expose their state. */
public final class Constraints {
    public interface Joint {default void beginStep() {} void solve(float dt);boolean broken();}
    public static final class Distance implements Joint {
        public final RigidBody a,b;public final float rest,compliance;public final boolean rope;
        public float breakForce=Float.POSITIVE_INFINITY;private boolean broken;private float lambda;
        public Distance(RigidBody a,RigidBody b,float rest,float compliance,boolean rope){if(rest<0||compliance<0)throw new IllegalArgumentException("Invalid joint");this.a=a;this.b=b;this.rest=rest;this.compliance=compliance;this.rope=rope;}
        public boolean broken(){return broken;}
        public void beginStep(){lambda=0;}
        public void solve(float dt){
            if(broken)return;
            Vector3f d=new Vector3f(b.position).sub(a.position);
            float length=d.length(),error=length-rest;
            if(length<1e-7f)return;
            float wa=a.kinematic?0:a.inverseMass,wb=b.kinematic?0:b.inverseMass;
            float alpha=compliance/(dt*dt),sum=wa+wb+alpha;if(wa+wb==0)return;
            float next=lambda-(error+alpha*lambda)/sum;
            if(rope)next=Math.min(0,next); // A cable carries tension only.
            float change=next-lambda;
            if(Math.abs(next)/(dt*dt)>breakForce){broken=true;return;}
            lambda=next;d.div(length);
            a.position.fma(-wa*change,d);b.position.fma(wb*change,d);
            // Keep the corrected pose and velocity on the same trajectory. Without
            // this update gravity accumulates and falsely appears as growing load.
            a.velocity.fma(-wa*change/dt,d);b.velocity.fma(wb*change/dt,d);
            if(change!=0){if(wa>0&&a.sleeping)a.wake();if(wb>0&&b.sleeping)b.wake();}
        }
    }

    public static final class Spring implements Joint {
        public final RigidBody a,b;public final float rest,stiffness,damping;public float breakForce=Float.POSITIVE_INFINITY;private boolean broken;
        public Spring(RigidBody a,RigidBody b,float rest,float stiffness,float damping){this.a=a;this.b=b;this.rest=rest;this.stiffness=stiffness;this.damping=damping;}
        public boolean broken(){return broken;}
        public void solve(float dt) {
            if(broken)return;Vector3f d=new Vector3f(b.position).sub(a.position);float length=d.length();if(length<1e-7f)return;d.div(length);
            float f=(length-rest)*stiffness+new Vector3f(b.velocity).sub(a.velocity).dot(d)*damping;
            if(Math.abs(f)>breakForce){broken=true;return;}a.impulse(new Vector3f(d).mul(f*dt));b.impulse(d.mul(-f*dt));
        }
    }
    /** Anchor coincidence and aligned hinge axes leave exactly rotation about the hinge free. */
    public static final class Hinge implements Joint {
        public final RigidBody a,b;private final Vector3f anchorA,anchorB,axisA,axisB;
        public float breakForce=Float.POSITIVE_INFINITY;private boolean broken;private final Vector3f reaction=new Vector3f();
        public Hinge(RigidBody a,RigidBody b,Vector3f anchorA,Vector3f anchorB,Vector3f axis){this.a=a;this.b=b;this.anchorA=new Vector3f(anchorA);this.anchorB=new Vector3f(anchorB);this.axisA=new Vector3f(axis).normalize();this.axisB=new Vector3f(axis).normalize();}
        public boolean broken(){return broken;}
        public void beginStep(){reaction.zero();}
        public void solve(float dt) {
            if(broken)return;float wa=a.kinematic?0:a.inverseMass,wb=b.kinematic?0:b.inverseMass,sum=wa+wb;if(sum==0)return;
            for(int anchorIteration=0;anchorIteration<8;anchorIteration++){
            Vector3f ra=a.rotation.transform(new Vector3f(anchorA)),rb=b.rotation.transform(new Vector3f(anchorB));
            Vector3f error=new Vector3f(b.position).add(rb).sub(a.position).sub(ra);
            // Anchor response includes the lever arm and world-space angular inertia.
            var effective=new org.joml.Matrix3f(
                response(new Vector3f(1,0,0),ra,rb,wa,wb),
                response(new Vector3f(0,1,0),ra,rb,wa,wb),
                response(new Vector3f(0,0,1),ra,rb,wa,wb));
            Vector3f correction=effective.invert().transform(new Vector3f(error));
            Vector3f nextReaction=new Vector3f(reaction).add(correction);
            if(nextReaction.length()/(dt*dt)>breakForce){broken=true;return;}
            reaction.set(nextReaction);
            applyAnchor(a,ra,correction,wa,dt);
            applyAnchor(b,rb,new Vector3f(correction).negate(),wb,dt);
            if(error.lengthSquared()>0){if(wa>0&&a.sleeping)a.wake();if(wb>0&&b.sleeping)b.wake();}
            }
            Vector3f aa=a.rotation.transform(new Vector3f(axisA)),bb=b.rotation.transform(new Vector3f(axisB));
            Vector3f cross=new Vector3f(bb).cross(aa);float sine=cross.length(),dot=bb.dot(aa);
            if(sine>1e-6f||dot<0){
                if(sine>1e-6f)cross.div(sine);else cross.set(tangent(aa));
                Vector3f impulse=projectedImpulse(cross.mul((float)Math.atan2(sine,dot)),aa,wa,wb);
                if(wa>0){var angular=a.inverseInertiaWorld(impulse).negate();a.rotation.integrate(1,angular.x,angular.y,angular.z).normalize();}
                if(wb>0){var angular=b.inverseInertiaWorld(impulse);b.rotation.integrate(1,angular.x,angular.y,angular.z).normalize();}
            }
            aa=a.rotation.transform(new Vector3f(axisA));
            Vector3f impulse=projectedImpulse(new Vector3f(b.angularVelocity).sub(a.angularVelocity),aa,wa,wb);
            if(wa>0)a.angularVelocity.add(a.inverseInertiaWorld(impulse));
            if(wb>0)b.angularVelocity.sub(b.inverseInertiaWorld(impulse));
        }
        private Vector3f tangent(Vector3f axis){return new Vector3f(axis).cross(Math.abs(axis.x)<.8f?new Vector3f(1,0,0):new Vector3f(0,1,0)).normalize();}
        private Vector3f angularResponse(Vector3f impulse,float wa,float wb){
            Vector3f result=new Vector3f();if(wa>0)result.add(a.inverseInertiaWorld(impulse));if(wb>0)result.add(b.inverseInertiaWorld(impulse));return result;
        }
        // Two constrained angular directions; spin around the hinge remains free.
        private Vector3f projectedImpulse(Vector3f target,Vector3f axis,float wa,float wb){
            Vector3f u=tangent(axis),v=new Vector3f(axis).cross(u),ku=angularResponse(u,wa,wb),kv=angularResponse(v,wa,wb);
            float xx=u.dot(ku),xy=u.dot(kv),yy=v.dot(kv),det=xx*yy-xy*xy;
            if(det<=1e-12f)return new Vector3f();
            float x=target.dot(u),y=target.dot(v);
            return u.mul((yy*x-xy*y)/det).fma((xx*y-xy*x)/det,v);
        }
        private Vector3f response(Vector3f direction,Vector3f ra,Vector3f rb,float wa,float wb) {
            Vector3f result=new Vector3f(direction).mul(wa+wb);
            if(wa>0)result.add(a.inverseInertiaWorld(new Vector3f(ra).cross(direction)).cross(ra));
            if(wb>0)result.add(b.inverseInertiaWorld(new Vector3f(rb).cross(direction)).cross(rb));
            return result;
        }
        private void applyAnchor(RigidBody body,Vector3f arm,Vector3f correction,float weight,float dt) {
            if(weight==0)return;
            Vector3f angular=body.inverseInertiaWorld(new Vector3f(arm).cross(correction));
            body.position.fma(weight,correction);body.velocity.fma(weight/dt,correction);
            body.rotation.integrate(1,angular.x,angular.y,angular.z).normalize();
            body.angularVelocity.fma(1/dt,angular);
        }
    }
    private Constraints() {}
}
