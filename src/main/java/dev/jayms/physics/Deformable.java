package dev.jayms.physics;

import java.util.*;
import org.joml.Vector3f;

/** Particle PBD / XPBD. Pinned particles have zero inverse mass. Lambdas reset each time step. */
public final class Deformable {
    public static final class Particle {
        public final Vector3f position,velocity=new Vector3f(),previous=new Vector3f();
        public float inverseMass;
        public Particle(Vector3f p,float inverseMass) {position=new Vector3f(p);this.inverseMass=inverseMass;}
    }
    public static final class Link {
        public final int a,b;public final float rest,compliance;public float lambda;public boolean broken;
        public float breakStrain=Float.POSITIVE_INFINITY;
        public Link(int a,int b,float rest,float compliance) {this.a=a;this.b=b;this.rest=rest;this.compliance=compliance;}
    }
    public final List<Particle> particles=new ArrayList<>();
    public final List<Link> links=new ArrayList<>();
    public void connect(int a,int b,float compliance) {links.add(new Link(a,b,particles.get(a).position.distance(particles.get(b).position),compliance));}
    public void step(float dt,Vector3f gravity,int iterations,float floor,boolean xpbd) {
        if(!Float.isFinite(dt)||dt<=0||dt>.1f||iterations<1)throw new IllegalArgumentException("Invalid deformable step");
        for(Particle p:particles){p.previous.set(p.position);if(p.inverseMass>0){p.velocity.fma(dt,gravity);p.position.fma(dt,p.velocity);}}
        for(Link c:links)c.lambda=0;
        for(int it=0;it<iterations;it++) {
            for(Link c:links) {
                if(c.broken)continue;Particle a=particles.get(c.a),b=particles.get(c.b);
                Vector3f delta=new Vector3f(b.position).sub(a.position);float distance=delta.length();
                if(distance<1e-7f)continue;
                if(Math.abs(distance-c.rest)>c.rest*c.breakStrain){c.broken=true;continue;}
                float alpha=xpbd?c.compliance/(dt*dt):0,denom=a.inverseMass+b.inverseMass+alpha;
                if(denom==0)continue;float change=(-(distance-c.rest)-alpha*c.lambda)/denom;c.lambda+=change;
                delta.div(distance);a.position.fma(-a.inverseMass*change,delta);b.position.fma(b.inverseMass*change,delta);
            }
            for(Particle p:particles)if(p.inverseMass>0)p.position.y=Math.max(floor,p.position.y);
        }
        for(Particle p:particles)if(p.inverseMass>0)p.velocity.set(p.position).sub(p.previous).div(dt).mul(.995f);
    }
    public static Deformable cloth(int width,int height,float spacing,Vector3f origin,float compliance) {
        if(width<2||height<2||(long)width*height>4096||spacing<=0)throw new IllegalArgumentException("Invalid cloth");
        Deformable cloth=new Deformable();
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)cloth.particles.add(new Particle(new Vector3f(origin).add(x*spacing,-y*spacing,0),y==0?0:1));
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int i=y*width+x;if(x+1<width)cloth.connect(i,i+1,compliance);if(y+1<height)cloth.connect(i,i+width,compliance);
            if(x+1<width&&y+1<height){cloth.connect(i,i+width+1,compliance);cloth.connect(i+1,i+width,compliance);}
            if(x+2<width)cloth.connect(i,i+2,compliance*2);if(y+2<height)cloth.connect(i,i+width*2,compliance*2);
        }
        return cloth;
    }
    /** Tetrahedron edges constrain a volumetric soft body rather than a surface sheet. */
    public static Deformable softBody(Vector3f origin,float size,float compliance) {
        if(size<=0||compliance<0)throw new IllegalArgumentException("Invalid soft body");
        Deformable body=new Deformable();
        for(Vector3f p:List.of(new Vector3f(),new Vector3f(size,0,0),new Vector3f(size/2,0,size),new Vector3f(size/2,size,size/2)))body.particles.add(new Particle(p.add(origin),1));
        for(int a=0;a<4;a++)for(int b=a+1;b<4;b++)body.connect(a,b,compliance);
        return body;
    }
}
