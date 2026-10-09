package dev.jayms.render;

import dev.jayms.net.atmosphere.AtmosphereConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig.Vec;

/** Bounded double precision midpoint reference. Original RGB single-scattering model.
 * Directions are unit vectors, positions and ray distances are planet-relative metres. */
public final class AtmosphereReference {
    private AtmosphereReference() {}
    public record Interval(double near,double far) {}
    public record Sample(Vec transmittance,Vec radiance) {}
    /** Stable quadratic roots using q and c/q. Null means a miss. Tangencies have equal roots. */
    public static Interval sphere(Vec p,Vec direction,double radius) {
        if(!Double.isFinite(radius)||radius<=0)throw new IllegalArgumentException("Invalid radius");
        Vec d=direction.unit();double b=p.dot(d),r=p.length();
        double c=(r-radius)*(r+radius),disc=b*b-c;
        double tolerance=8*Math.ulp(Math.max(b*b,Math.abs(c)));
        if(disc < -tolerance)return null;
        double root=Math.sqrt(Math.max(0,disc)),q=-b-Math.copySign(root,b);
        double a=q==0 ? -b : q, z=q==0 ? -b : c/q;
        if(a==0)a=0;if(z==0)z=0;
        return new Interval(Math.min(a,z),Math.max(a,z));
    }
    public static Interval path(AtmosphereConfig c,Vec p,Vec d,double distance) {
        if(!Double.isFinite(distance)||distance<0)throw new IllegalArgumentException("Invalid path length");
        if(p.length()<c.radius()-.001)return null;
        var shell=sphere(p,d,c.radius()+c.height());
        if(shell==null)return null;
        double near=Math.max(0,shell.near),far=Math.min(distance,shell.far);
        var ground=sphere(p,d,c.radius());
        if(ground!=null && ground.far>0 && ground.near>=-.001)far=Math.min(far,Math.max(0,ground.near));
        return far>near ? new Interval(near,far) : null;
    }
    public static Vec density(AtmosphereConfig c,double altitude) {
        if(altitude<0||altitude>c.height()||!c.enabled())return Vec.gray(0);
        return new Vec(Math.exp(-altitude/c.molecularScale()),Math.exp(-altitude/c.aerosolScale()),
            Math.max(0,1-Math.abs(altitude-c.absorptionCentre())/c.absorptionWidth()));
    }
    public static Vec extinction(AtmosphereConfig c,Vec density) {
        return c.molecular().mul(density.x()).add(c.aerosolExtinction().mul(density.y())).add(c.absorption().mul(density.z()));
    }
    private static void samples(int n) {if(n<1||n>4096)throw new IllegalArgumentException("Samples outside 1..4096");}
    private static Vec expNegative(Vec depth) {return new Vec(Math.exp(-depth.x()),Math.exp(-depth.y()),Math.exp(-depth.z()));}
    private static Vec product(Vec a,Vec b) {return new Vec(a.x()*b.x(),a.y()*b.y(),a.z()*b.z());}
    public static Vec opticalDepth(AtmosphereConfig c,Vec p,Vec direction,double distance,int n) {
        samples(n);Vec d=direction.unit();var interval=path(c,p,d,distance);
        if(interval==null||!c.enabled())return Vec.gray(0);
        double step=(interval.far-interval.near)/n;Vec depth=Vec.gray(0);
        for(int i=0;i<n;i++)depth=depth.add(extinction(c,density(c,p.add(d.mul(interval.near+(i+.5)*step)).length()-c.radius())).mul(step));
        return depth;
    }
    public static Vec transmittance(AtmosphereConfig c,Vec p,Vec d,double distance,int n) {
        return expNegative(opticalDepth(c,p,d,distance,n));
    }
    public static Vec sunlight(AtmosphereConfig c,Vec p,Vec direction,int n) {
        Vec d=direction.unit();var ground=sphere(p,d,c.radius());
        if(p.length()<c.radius() || ground!=null && ground.far>0 && ground.near>=-.001)return Vec.gray(0);
        return transmittance(c,p,d,2*(c.radius()+c.height()),n);
    }
    public static Sample integrate(AtmosphereConfig c,Vec p,Vec direction,Vec sunDirection,double distance,int n,int sunSamples) {
        samples(n);samples(sunSamples);Vec d=direction.unit(),sun=sunDirection.unit();var interval=path(c,p,d,distance);
        if(interval==null||!c.enabled())return new Sample(Vec.gray(1),Vec.gray(0));
        double mu=Math.max(-1,Math.min(1,d.dot(sun))),g=c.anisotropy();
        double phaseR=3*(1+mu*mu)/(16*Math.PI);
        double phaseM=(1-g*g)/(4*Math.PI*Math.pow(1+g*g-2*g*mu,1.5));
        double step=(interval.far-interval.near)/n;Vec depth=Vec.gray(0),radiance=Vec.gray(0);
        for(int i=0;i<n;i++) {
            Vec position=p.add(d.mul(interval.near+(i+.5)*step));
            Vec rho=density(c,position.length()-c.radius()),sigma=extinction(c,rho);
            Vec source=c.molecular().mul(rho.x()*phaseR).add(c.aerosolScattering().mul(rho.y()*phaseM));
            Vec visibility=product(expNegative(depth.add(sigma.mul(step*.5))),sunlight(c,position,sun,sunSamples));
            radiance=radiance.add(product(product(source,visibility),c.solarIrradiance()).mul(step));
            depth=depth.add(sigma.mul(step));
        }
        return new Sample(expNegative(depth),radiance);
    }

    /** Original isotropic closure reference. Angular midpoint/Fibonacci integration estimates
     * the repeat-scattering probability; 0.95 caps feedback for finite conservative workloads.
     * This is an approximation, not a full spectral or higher-order transport solver. */
    public static Vec multiple(AtmosphereConfig c,double altitude,double sunCosine,int directions,int n,int sunSamples) {
        if(!Double.isFinite(altitude)||altitude<0||altitude>c.height()||!Double.isFinite(sunCosine)||sunCosine<-1||sunCosine>1)
            throw new IllegalArgumentException("Invalid multiple-scattering coordinate");
        if(directions<4||directions>256)throw new IllegalArgumentException("Directions outside 4..256");samples(n);samples(sunSamples);
        if(!c.enabled())return Vec.gray(0);
        Vec p=new Vec(0,c.radius()+Math.max(1,altitude),0),sun=new Vec(Math.sqrt(1-sunCosine*sunCosine),sunCosine,0);
        Vec meanLight=Vec.gray(0),meanFeedback=Vec.gray(0);
        for(int j=0;j<directions;j++) {
            double y=1-2*(j+.5)/directions,phi=j*2.39996323;
            Vec d=new Vec(Math.sqrt(1-y*y)*Math.cos(phi),y,Math.sqrt(1-y*y)*Math.sin(phi));
            Sample single=integrate(c,p,d,sun,2*(c.radius()+c.height()),n,sunSamples);
            Vec light=single.radiance(),feedback=Vec.gray(0),viewT=Vec.gray(1);
            Interval range=path(c,p,d,2*(c.radius()+c.height()));
            if(range!=null){double step=(range.far-range.near)/n;
                for(int i=0;i<n;i++){
                    Vec q=p.add(d.mul(range.near+(i+.5)*step)),rho=density(c,q.length()-c.radius()),sigma=extinction(c,rho);
                    Vec scatter=c.molecular().mul(rho.x()).add(c.aerosolScattering().mul(rho.y())),stepT=expNegative(sigma.mul(step));
                    Vec fraction=new Vec((1-stepT.x())*scatter.x()/Math.max(1e-11,sigma.x()),(1-stepT.y())*scatter.y()/Math.max(1e-11,sigma.y()),(1-stepT.z())*scatter.z()/Math.max(1e-11,sigma.z()));
                    feedback=feedback.add(product(viewT,fraction));viewT=product(viewT,stepT);
                }
                Interval ground=sphere(p,d,c.radius());
                if(ground!=null&&ground.near>0&&ground.near<=range.far+1){Vec q=p.add(d.mul(ground.near)),up=q.unit();
                    Vec bounced=product(product(c.groundAlbedo(),c.solarIrradiance()),sunlight(c,q.add(up.mul(2)),sun,sunSamples)).mul(Math.max(0,up.dot(sun))/Math.PI);
                    light=light.add(product(single.transmittance(),bounced));feedback=feedback.add(product(viewT,c.groundAlbedo()));
                }
            }
            meanLight=meanLight.add(light.mul(1.0/directions));meanFeedback=meanFeedback.add(feedback.mul(1.0/directions));
        }
        return new Vec(meanLight.x()/(1-Math.min(.95,meanFeedback.x())),meanLight.y()/(1-Math.min(.95,meanFeedback.y())),meanLight.z()/(1-Math.min(.95,meanFeedback.z())));
    }
}
