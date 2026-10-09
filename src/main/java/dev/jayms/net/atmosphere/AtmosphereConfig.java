package dev.jayms.net.atmosphere;

import java.io.*;
import java.util.Objects;

/** Immutable world-owned profile. All lengths are metres; optical coefficients are per metre.
 * Artistic presets approximate RGB spectra, not climate or oxygen simulation. */
public record AtmosphereConfig(boolean enabled, double radius, double height,
        double metresPerBlock, double seaLevel, Vec origin, Vec up,
        Vec molecular, Vec aerosolScattering, Vec aerosolExtinction, Vec absorption,
        double molecularScale, double aerosolScale, double absorptionCentre,
        double absorptionWidth, double anisotropy, Vec groundAlbedo,
        Vec solarIrradiance, double solarRadius) {
    public static final int VERSION = 1;
    public record Vec(double x, double y, double z) {
        public Vec {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite atmosphere vector");
        }
        public double dot(Vec b) { return x*b.x+y*b.y+z*b.z; }
        public double length() { return Math.hypot(Math.hypot(x,y),z); }
        public Vec add(Vec b) { return new Vec(x+b.x,y+b.y,z+b.z); }
        public Vec sub(Vec b) { return new Vec(x-b.x,y-b.y,z-b.z); }
        public Vec mul(double s) { return new Vec(x*s,y*s,z*s); }
        public Vec cross(Vec b) { return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x); }
        public Vec unit() { double n=length(); if(n<1e-12)throw new IllegalArgumentException("Zero direction");return mul(1/n); }
        public double component(int i) { return i==0?x:i==1?y:z; }
        public static Vec gray(double v) { return new Vec(v,v,v); }
    }
    public AtmosphereConfig {
        range(radius,1_000,1e9,"planet radius");range(height,1,1e7,"atmosphere height");
        if(height>radius)throw new IllegalArgumentException("Atmosphere exceeds planet radius");
        range(metresPerBlock,.001,1e6,"block metres");range(seaLevel,-1e9,1e9,"sea level");
        Objects.requireNonNull(origin);Objects.requireNonNull(up);
        if(Math.abs(up.length()-1)>1e-9)throw new IllegalArgumentException("Local up must be unit length");
        coefficient(molecular);coefficient(aerosolScattering);coefficient(aerosolExtinction);coefficient(absorption);
        for(int i=0;i<3;i++)if(aerosolScattering.component(i)>aerosolExtinction.component(i))
            throw new IllegalArgumentException("Aerosol extinction below scattering");
        range(molecularScale,1,height,"molecular scale height");range(aerosolScale,1,height,"aerosol scale height");
        range(absorptionCentre,0,height,"absorption centre");range(absorptionWidth,1,height,"absorption width");
        range(anisotropy,-.95,.95,"aerosol anisotropy");
        vectorRange(groundAlbedo,0,1,"ground albedo");vectorRange(solarIrradiance,0,100,"solar irradiance");
        range(solarRadius,.00001,.05,"solar angular radius");
    }
    private static void range(double v,double lo,double hi,String name) {
        if(!Double.isFinite(v)||v<lo||v>hi)throw new IllegalArgumentException("Invalid "+name);
    }
    private static void vectorRange(Vec v,double lo,double hi,String name) {
        Objects.requireNonNull(v);for(int i=0;i<3;i++)range(v.component(i),lo,hi,name);
    }
    private static void coefficient(Vec v) { vectorRange(v,0,.01,"optical coefficient"); }
    public static AtmosphereConfig earth() {
        return new AtmosphereConfig(true,6_360_000,100_000,1,24,new Vec(0,0,0),new Vec(0,1,0),
            new Vec(5.8e-6,13.5e-6,33.1e-6),Vec.gray(3.996e-6),Vec.gray(4.44e-6),
            new Vec(.65e-6,1.881e-6,.085e-6),8_000,1_200,25_000,15_000,.76,
            Vec.gray(.1),Vec.gray(18),.004675);
    }
    public static AtmosphereConfig thin() { return earth().withHaze(.25).withThickness(.5); }
    public static AtmosphereConfig hazy() { return earth().withHaze(3); }
    public static AtmosphereConfig airless() { return earth().withEnabled(false); }
    public static AtmosphereConfig preset(String name) {
        return switch(name) {case "earth"->earth();case "thin"->thin();case "hazy"->hazy();case "airless","off"->airless();
            default->throw new IllegalArgumentException("Atmosphere preset must be earth, thin, hazy, airless or off");};
    }
    public AtmosphereConfig withEnabled(boolean value) {
        return copy(value,height,molecularScale,aerosolScale,absorptionCentre,absorptionWidth,aerosolScattering,aerosolExtinction);
    }
    public AtmosphereConfig withHaze(double factor) {
        range(factor,0,20,"haze multiplier");
        return copy(enabled,height,molecularScale,aerosolScale,absorptionCentre,absorptionWidth,
            aerosolScattering.mul(factor),aerosolExtinction.mul(factor));
    }
    public AtmosphereConfig withThickness(double factor) {
        range(factor,.1,5,"thickness multiplier");
        return copy(enabled,height*factor,molecularScale*factor,aerosolScale*factor,
            absorptionCentre*factor,absorptionWidth*factor,aerosolScattering,aerosolExtinction);
    }
    private AtmosphereConfig copy(boolean e,double h,double r,double m,double o,double w,Vec ms,Vec me) {
        return new AtmosphereConfig(e,radius,h,metresPerBlock,seaLevel,origin,up,molecular,ms,me,absorption,
            r,m,o,w,anisotropy,groundAlbedo,solarIrradiance,solarRadius);
    }
    /** Surface anchor = origin + world Y seaLevel. X/Z form an orthonormal tangent plane.
     * Virtual centre is radius metres below that anchor. Subtract origin before scaling. */
    public Vec planetPosition(double x,double y,double z) {
        Vec east = Math.abs(up.y)<.99 ? new Vec(0,1,0).cross(up).unit() : new Vec(0,0,1).cross(up).unit().mul(-1);
        Vec north=east.cross(up);
        return east.mul((x-origin.x)*metresPerBlock).add(up.mul(radius+(y-origin.y-seaLevel)*metresPerBlock))
            .add(north.mul((z-origin.z)*metresPerBlock));
    }
    public void write(DataOutput out) throws IOException {
        out.writeInt(VERSION);out.writeBoolean(enabled);
        for(double v:new double[]{radius,height,metresPerBlock,seaLevel})out.writeDouble(v);
        for(Vec v:new Vec[]{origin,up,molecular,aerosolScattering,aerosolExtinction,absorption})writeVec(out,v);
        for(double v:new double[]{molecularScale,aerosolScale,absorptionCentre,absorptionWidth,anisotropy})out.writeDouble(v);
        writeVec(out,groundAlbedo);writeVec(out,solarIrradiance);out.writeDouble(solarRadius);
    }
    private static void writeVec(DataOutput out,Vec v)throws IOException {out.writeDouble(v.x);out.writeDouble(v.y);out.writeDouble(v.z);}
    private static Vec readVec(DataInput in)throws IOException {return new Vec(in.readDouble(),in.readDouble(),in.readDouble());}
    public static AtmosphereConfig read(DataInput in)throws IOException {
        if(in.readInt()!=VERSION)throw new IOException("Unsupported atmosphere version");
        try{return new AtmosphereConfig(in.readBoolean(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),
            readVec(in),readVec(in),readVec(in),readVec(in),readVec(in),readVec(in),in.readDouble(),in.readDouble(),
            in.readDouble(),in.readDouble(),in.readDouble(),readVec(in),readVec(in),in.readDouble());}
        catch(IllegalArgumentException|NullPointerException e){throw new IOException("Invalid atmosphere profile",e);}
    }
}
