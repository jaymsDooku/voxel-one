package dev.jayms;

import dev.jayms.net.mobile.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTerrainFixtureTest {
    @Test void portableSunContractPreservesDirectionAndNormalization() {
        for(double hour:new double[]{0,6,10,12,18,23.5}) {
            double angle=(hour-6)/24*Math.PI*2;
            double x=Math.cos(angle),y=Math.sin(angle),z=-.35;
            double length=Math.sqrt(x*x+y*y+z*z);
            var sun=MobileSnapshot.sunDirection(hour);
            assertEquals(x/length,sun.get(0),1e-15);
            assertEquals(y/length,sun.get(1),1e-15);
            assertEquals(z/length,sun.get(2),1e-15);
            assertEquals(1,sun.stream().mapToDouble(v->v*v).sum(),1e-15);
        }
    }
    @Test void nativeTerrainGoldenAssetMatchesAuthoritativeGenerator()throws Exception {
        var data=NativeTerrainFixtures.data();
        assertEquals(Json.write(data),Files.readString(Path.of("ios/Resources/terrain-fixtures.json")));
        var cases=(List<?>)data.get("cases");assertTrue(cases.size()>1000 && cases.size()<=12000);
        Set<Integer> types=new HashSet<>();for(var value:cases)types.add((Integer)((Map<?,?>)value).get("block"));
        assertTrue(types.containsAll(List.of(0,1,2,3,4,5,6,7,185,186)),"Golden samples cover ground, trees, water and mineral");
        var window=(Map<?,?>)data.get("window");assertEquals(17380,window.get("cellCount"));
        var clock=(List<?>)data.get("clock");assertEquals(6,clock.size());
        assertTrue(MobileSnapshot.sunDirection(0).get(1)<0);assertTrue(MobileSnapshot.sunDirection(12).get(1)>0);
        assertThrows(IllegalArgumentException.class,()->MobileSnapshot.sunDirection(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->MobileSnapshot.sunDirection(24));
    }
}
