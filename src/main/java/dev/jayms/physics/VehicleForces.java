package dev.jayms.physics;

import org.joml.Vector3f;

/** Forces in SI units. Four independent wheel contacts drive suspension, traction and wheel spin. */
public final class VehicleForces {
    public static final class Wheel {
        public float radius=.4f, restLength=.6f, stiffness=18000, damping=1800;
        public float angularSpeed, rotation, previousCompression;
        public Vector3f force(float groundDistance,float verticalSpeed,float forwardSpeed,
                float driveTorque,float brakeTorque,float friction,float normalLoad,float dt) {
            if(dt<=0||radius<=0) return new Vector3f();
            float compression=Math.max(0,restLength+radius-groundDistance);
            float support=Math.max(0,stiffness*compression-damping*verticalSpeed);
            float slip=angularSpeed*radius-forwardSpeed;
            float availableLoad=compression>0?Math.min(support,Math.max(0,normalLoad)):0;
            float limit=Math.max(0,friction)*availableLoad;
            float traction=Math.max(-limit,Math.min(limit,slip*1200));
            float inertia=12*radius*radius;
            angularSpeed+=(driveTorque-traction*radius-Math.signum(angularSpeed)*Math.min(brakeTorque,Math.abs(angularSpeed)*inertia/dt))*dt/inertia;
            rotation+=angularSpeed*dt;previousCompression=compression;
            return compression>0?new Vector3f(traction,support,0):new Vector3f();
        }
    }
    public static Vector3f buoyancy(float bottom,float top,float waterHeight,float volume,float density,float gravity) {
        if(top<=bottom||volume<0||density<0) throw new IllegalArgumentException("Invalid buoyancy geometry");
        float fraction=Math.max(0,Math.min(1,(waterHeight-bottom)/(top-bottom)));
        return new Vector3f(0,density*volume*gravity*fraction,0);
    }
    public static Vector3f dragLift(Vector3f velocity,Vector3f wind,float density,float area,float drag,float lift,Vector3f up) {
        Vector3f relative=new Vector3f(velocity).sub(wind);float speed=relative.length();
        if(speed<1e-6f) return new Vector3f();
        Vector3f direction=relative.div(speed);
        Vector3f liftDirection=new Vector3f(up).sub(new Vector3f(direction).mul(up.dot(direction)));
        if(liftDirection.lengthSquared()>1e-8f) liftDirection.normalize();
        float q=.5f*density*speed*speed*area;
        return direction.mul(-drag*q).fma(lift*q,liftDirection);
    }
    public static Vector3f explosion(Vector3f position,Vector3f origin,float radius,float impulse) {
        Vector3f d=new Vector3f(position).sub(origin);float distance=d.length();
        if(radius<=0||distance>=radius) return new Vector3f();
        if(distance<1e-6f) d.set(0,1,0);else d.div(distance);
        return d.mul(impulse*(1-distance/radius));
    }
    private VehicleForces() {}
}
