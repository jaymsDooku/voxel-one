package dev.jayms.render;

import org.joml.Vector3f;

/** Analytic single scattering in kilometres: wavelength-dependent Rayleigh plus Mie aerosols. */
public final class Atmosphere {
    private Atmosphere() {}
    public static Vector3f radiance(Vector3f ray, Vector3f sun, float daylight, float ambient) {
        double mu=Math.max(-1,Math.min(1,ray.dot(sun)));
        double air=1/Math.sqrt(Math.max(.0025,Math.max(0,ray.y)*Math.max(0,ray.y)));
        double sunAir=1/Math.sqrt(Math.max(.0025,Math.max(0,sun.y)*Math.max(0,sun.y)));
        double rayleigh=3*(1+mu*mu)/(16*Math.PI),g=.76;
        double mie=(1-g*g)/(4*Math.PI*Math.pow(1+g*g-2*g*mu,1.5));
        float[] beta={.0058f,.0135f,.0331f};float[] values=new float[3];
        for(int i=0;i<3;i++) {
            double optical=beta[i]*8+.004*1.2;
            double scatter=(1-Math.exp(-optical*air))*(beta[i]*8*rayleigh+.004*1.2*mie)/optical;
            values[i]=(float)(scatter*Math.exp(-optical*sunAir)*18*daylight+.006*ambient);
        }
        float disc=(float)Math.pow(Math.max(0,mu),2048)*8*daylight;
        return new Vector3f(values[0]+disc,values[1]+disc*.9f,values[2]+disc*.7f);
    }
}
