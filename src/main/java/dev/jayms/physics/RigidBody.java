package dev.jayms.physics;

import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Box body with linear/angular velocity and diagonal box inertia in local coordinates. */
public final class RigidBody {
    public final Vector3f position,halfSize,velocity=new Vector3f(),force=new Vector3f(),angularVelocity=new Vector3f(),torque=new Vector3f();
    public final Quaternionf rotation=new Quaternionf();
    public final Vector3f inverseInertia=new Vector3f(),previous=new Vector3f();
    public final float inverseMass;
    public float friction=.6f,restitution=.1f,quietTime;
    public int layer=1,mask=-1,material=3;
    public boolean trigger,sleeping,kinematic;
    public RigidBody(Vector3f position,Vector3f halfSize,float mass) {
        if(!position.isFinite()||!halfSize.isFinite()||halfSize.x<=0||halfSize.y<=0||halfSize.z<=0||!Float.isFinite(mass)||mass<0)throw new IllegalArgumentException("Invalid body");
        this.position=new Vector3f(position);this.halfSize=new Vector3f(halfSize);inverseMass=mass==0?0:1/mass;
        if(mass>0)inverseInertia.set(3/(mass*(halfSize.y*halfSize.y+halfSize.z*halfSize.z)),3/(mass*(halfSize.x*halfSize.x+halfSize.z*halfSize.z)),3/(mass*(halfSize.x*halfSize.x+halfSize.y*halfSize.y)));
    }
    public Aabb bounds() {
        Matrix3f m=new Matrix3f().set(rotation);
        float x=Math.abs(m.m00())*halfSize.x+Math.abs(m.m10())*halfSize.y+Math.abs(m.m20())*halfSize.z;
        float y=Math.abs(m.m01())*halfSize.x+Math.abs(m.m11())*halfSize.y+Math.abs(m.m21())*halfSize.z;
        float z=Math.abs(m.m02())*halfSize.x+Math.abs(m.m12())*halfSize.y+Math.abs(m.m22())*halfSize.z;
        return new Aabb(position.x-x,position.y-y,position.z-z,position.x+x,position.y+y,position.z+z);
    }
    public boolean interacts(RigidBody b){return (mask&b.layer)!=0&&(b.mask&layer)!=0;}
    public void wake(){sleeping=false;quietTime=0;}
    public void impulse(Vector3f impulse){if(inverseMass>0&&!kinematic){velocity.fma(inverseMass,impulse);wake();}}
    /** Apply R * I_local^-1 * R^T to a world-space torque or angular impulse. */
    public Vector3f inverseInertiaWorld(Vector3f worldVector) {
        Vector3f local=rotation.transformInverse(new Vector3f(worldVector));
        return rotation.transform(local.mul(inverseInertia));
    }
    public void impulseAt(Vector3f impulse,Vector3f point){impulse(impulse);if(inverseMass>0&&!kinematic)angularVelocity.add(inverseInertiaWorld(new Vector3f(point).sub(position).cross(impulse)));}
    public Vector3f anchor(Vector3f local){return rotation.transform(new Vector3f(local)).add(position);}
}
