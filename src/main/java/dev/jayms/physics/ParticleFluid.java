package dev.jayms.physics;

import java.util.*;
import org.joml.Vector3f;

/** Symmetric SPH pressure and viscosity with a compact smoothing kernel. Fixed bounded substeps. */
public final class ParticleFluid {
    public final List<Vector3f> positions=new ArrayList<>(),velocities=new ArrayList<>();
    public float radius=1,mass=1,restDensity=10,stiffness=20,viscosity=.1f;
    public void add(Vector3f position){if(positions.size()>=2048)throw new IllegalStateException("Particle limit");positions.add(new Vector3f(position));velocities.add(new Vector3f());}
    public float[] densities() {
        if(radius<=0||mass<=0)throw new IllegalStateException("Positive smoothing radius and mass required");
        float[] rho=new float[positions.size()];
        double poly=315/(64*Math.PI*Math.pow(radius,9));
        for(int i=0;i<rho.length;i++)for(var p:positions){float q=radius*radius-positions.get(i).distanceSquared(p);if(q>0)rho[i]+=mass*(float)(poly*q*q*q);}
        return rho;
    }
    public void step(float dt,Vector3f gravity,float floor) {
        if(!Float.isFinite(dt)||dt<0||dt>.1f)throw new IllegalArgumentException("Invalid SPH step");
        int steps=Math.max(1,(int)Math.ceil(dt/.004f));float h=dt/steps;
        for(int s=0;s<steps;s++) {
            float[] density=densities();Vector3f[] acceleration=new Vector3f[density.length];
            for(int i=0;i<density.length;i++)acceleration[i]=new Vector3f(gravity);
            float grad=(float)(45/(Math.PI*Math.pow(radius,6)));
            for(int i=0;i<density.length;i++)for(int j=i+1;j<density.length;j++) {
                Vector3f d=new Vector3f(positions.get(i)).sub(positions.get(j));float distance=d.length();
                if(distance<1e-6f||distance>=radius)continue;
                float pi=Math.max(0,stiffness*(density[i]-restDensity)),pj=Math.max(0,stiffness*(density[j]-restDensity));
                Vector3f a=d.div(distance).mul(mass*(pi/(density[i]*density[i])+pj/(density[j]*density[j]))*grad*(radius-distance)*(radius-distance));
                a.add(new Vector3f(velocities.get(j)).sub(velocities.get(i)).mul(viscosity*mass*grad*(radius-distance)/(density[i]*density[j])));
                acceleration[i].add(a);acceleration[j].sub(a);
            }
            for(int i=0;i<density.length;i++) {
                velocities.get(i).fma(h,acceleration[i]);positions.get(i).fma(h,velocities.get(i));
                if(positions.get(i).y<floor){positions.get(i).y=floor;velocities.get(i).y=Math.max(0,-velocities.get(i).y*.2f);}
            }
        }
    }
}
