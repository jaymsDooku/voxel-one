package dev.jayms;

import dev.jayms.render.AtmosphereReference;
import dev.jayms.net.atmosphere.AtmosphereConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig.Vec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereReferenceTest {
    private final AtmosphereConfig c=AtmosphereConfig.earth();
    @Test void multipleClosureIsFiniteNonnegativeVacuumAndAlbedoResponsive() {
        assertEquals(Vec.gray(0),AtmosphereReference.multiple(AtmosphereConfig.airless(),100,0,16,32,32));
        for(double mu:new double[]{-.04,0,.8}) {
            Vec value=AtmosphereReference.multiple(c,100,mu,16,64,128);
            assertTrue(value.x()>=0&&value.y()>=0&&value.z()>=0);
        }
        Vec dark=AtmosphereReference.multiple(c.withGroundAlbedo(Vec.gray(0)),100,.8,32,128,128);
        Vec bright=AtmosphereReference.multiple(c.withGroundAlbedo(Vec.gray(.4)),100,.8,32,128,128);
        assertTrue(bright.x()>dark.x()&&bright.y()>dark.y()&&bright.z()>dark.z());
    }
    @Test void intersectionsIncludeInsideSurfaceOutsideGrazingAndMiss() {
        assertEquals(new AtmosphereReference.Interval(-10,10),AtmosphereReference.sphere(Vec.gray(0),new Vec(1,0,0),10));
        assertEquals(new AtmosphereReference.Interval(-20,0),AtmosphereReference.sphere(new Vec(10,0,0),new Vec(1,0,0),10));
        assertEquals(new AtmosphereReference.Interval(10,30),AtmosphereReference.sphere(new Vec(20,0,0),new Vec(-1,0,0),10));
        var grazing=AtmosphereReference.sphere(new Vec(10,0,0),new Vec(0,1,0),10);
        assertEquals(0,grazing.near(),0);assertEquals(0,grazing.far(),0);
        assertNull(AtmosphereReference.sphere(new Vec(11,0,0),new Vec(0,1,0),10));
        assertEquals(1,AtmosphereReference.sphere(new Vec(1e9+1,0,0),new Vec(-1,0,0),1e9).near(),1e-7);
    }
    @Test void clipsSpaceEntryAndGroundAndPlanetBlocksSun() {
        Vec p=new Vec(0,c.radius()+200_000,0),down=new Vec(0,-1,0);
        var interval=AtmosphereReference.path(c,p,down,500_000);
        assertEquals(100_000,interval.near(),1e-8);assertEquals(200_000,interval.far(),1e-8);
        assertEquals(Vec.gray(0),AtmosphereReference.sunlight(c,new Vec(0,c.radius()+2,0),down,64));
        assertTrue(AtmosphereReference.sunlight(c,new Vec(0,c.radius()+2,0),new Vec(0,1,0),128).x()>0);
    }
    @Test void vacuumBoundsAndMonotonicOpticalDepth() {
        Vec p=new Vec(0,c.radius()+10,0),up=new Vec(0,1,0);
        assertEquals(Vec.gray(1),AtmosphereReference.transmittance(AtmosphereConfig.airless(),p,up,100_000,64));
        var a=AtmosphereReference.opticalDepth(c,p,up,1000,256);var b=AtmosphereReference.opticalDepth(c,p,up,10_000,256);
        for(int i=0;i<3;i++){assertTrue(b.component(i)>a.component(i));double t=AtmosphereReference.transmittance(c,p,up,100_000,256).component(i);assertTrue(t>=0&&t<=1);}
        assertThrows(IllegalArgumentException.class,()->AtmosphereReference.transmittance(c,p,up,1,0));
    }
    @Test void rotationalConsistencyAndFiniteTwilightSpaceRadiance() {
        Vec p=new Vec(0,c.radius()+100,0),d=new Vec(1,.02,0).unit(),sun=new Vec(1,-.01,0).unit();
        var a=AtmosphereReference.integrate(c,p,d,sun,1e7,256,128);
        var b=AtmosphereReference.integrate(c,new Vec(p.y(),0,0),new Vec(d.y(),d.x(),0),new Vec(sun.y(),sun.x(),0),1e7,256,128);
        for(int i=0;i<3;i++){assertEquals(a.radiance().component(i),b.radiance().component(i),1e-12);assertTrue(a.radiance().component(i)>=0);}
        var space=AtmosphereReference.integrate(c,new Vec(0,c.radius()+120_000,0),new Vec(1,-.2,0).unit(),new Vec(0,1,0),1e7,256,128);
        assertTrue(space.radiance().length()>=0);
    }
    @Test void controlledVerticalIntegrationConvergesAtDocumentedTolerance() {
        Vec p=new Vec(0,c.radius()+20,0),up=new Vec(0,1,0),sun=new Vec(1,1,0).unit();
        var a=AtmosphereReference.integrate(c,p,up,sun,1e7,256,128);var b=AtmosphereReference.integrate(c,p,up,sun,1e7,1024,512);
        for(int i=0;i<3;i++)assertEquals(b.radiance().component(i),a.radiance().component(i),Math.max(.002,b.radiance().component(i)*.02));
    }
}
