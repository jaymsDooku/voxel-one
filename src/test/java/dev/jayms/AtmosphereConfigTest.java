package dev.jayms;

import dev.jayms.net.atmosphere.AtmosphereConfig;
import dev.jayms.net.atmosphere.AtmosphereConfig.Vec;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereConfigTest {
    @Test void rejectsProfilesBelowGpuPrecisionAndUnsafeOrigins()throws Exception{
        assertThrows(IllegalArgumentException.class,()->AtmosphereConfig.earth().withThickness(.001));
        for(int index:new int[]{4,23,25}){
            var bytes=new ByteArrayOutputStream();AtmosphereConfig.earth().write(new DataOutputStream(bytes));
            byte[] payload=bytes.toByteArray();java.nio.ByteBuffer.wrap(payload).putDouble(5+index*8,index==4?1e13:1);
            assertThrows(IOException.class,()->AtmosphereConfig.read(new DataInputStream(new ByteArrayInputStream(payload))));
        }
    }
    @Test void profilesAreImmutableFiniteAndBounded() {
        var earth=AtmosphereConfig.earth();
        assertEquals(4.44e-6,earth.aerosolExtinction().x());
        assertEquals(13.32e-6,AtmosphereConfig.hazy().aerosolExtinction().x(),1e-15);
        assertFalse(AtmosphereConfig.airless().enabled());
        assertEquals(50_000,AtmosphereConfig.thin().height());
        assertThrows(IllegalArgumentException.class,()->earth.withHaze(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->earth.withThickness(0));
        assertThrows(IllegalArgumentException.class,()->new Vec(Double.POSITIVE_INFINITY,0,0));
    }
    @Test void tangentUnitsSupportNegativeLargeCoordinatesAndRebasedOrigins() {
        var c=AtmosphereConfig.earth();
        assertEquals(new Vec(0,c.radius(),0),c.planetPosition(0,24,0));
        assertEquals(new Vec(-1,c.radius()+100_000,2),c.planetPosition(-1,100_024,2));
        var rebased=new AtmosphereConfig(c.enabled(),c.radius(),c.height(),c.metresPerBlock(),c.seaLevel(),
            new Vec(1e9,-1e9,-1e9),c.up(),c.molecular(),c.aerosolScattering(),c.aerosolExtinction(),c.absorption(),
            c.molecularScale(),c.aerosolScale(),c.absorptionCentre(),c.absorptionWidth(),c.anisotropy(),c.groundAlbedo(),c.solarIrradiance(),c.solarRadius());
        assertEquals(c.planetPosition(.125,24.5,-.25),rebased.planetPosition(1e9+.125,-1e9+24.5,-1e9-.25));
    }
    @Test void versionedSnapshotRoundTripAndLegacyClockMigration()throws Exception {
        var profile=AtmosphereConfig.hazy();
        var game=new GameConfig(true,false,999,23.5,profile);
        var bytes=new ByteArrayOutputStream();CityFrame.empty(game).write(new DataOutputStream(bytes));
        var restored=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(game,restored.config());
        bytes.reset();var old=new GameConfig(true,true,700,3.5);
        CityFrame.empty(old).write(new DataOutputStream(bytes),16);
        var migrated=CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),16);
        assertEquals(old,migrated.config());assertEquals(3.5,migrated.config().startHour());
        assertThrows(IOException.class,()->CityFrame.empty(game).write(new DataOutputStream(new ByteArrayOutputStream()),16));
    }
    @Test void unknownAndMalformedProfilesFailClosed()throws Exception {
        var b=new ByteArrayOutputStream();var o=new DataOutputStream(b);o.writeInt(999);
        assertThrows(IOException.class,()->AtmosphereConfig.read(new DataInputStream(new ByteArrayInputStream(b.toByteArray()))));
        b.reset();AtmosphereConfig.earth().write(o);byte[] bytes=b.toByteArray();
        // Radius starts after version and enabled flag.
        for(int i=5;i<13;i++)bytes[i]=(byte)0xff;
        assertThrows(IOException.class,()->AtmosphereConfig.read(new DataInputStream(new ByteArrayInputStream(bytes))));
    }
}
