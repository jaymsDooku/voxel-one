package dev.jayms;

import dev.jayms.net.Terrain;
import dev.jayms.net.city.StressGrid;
import dev.jayms.render.LightVolume;
import dev.jayms.render.WorldLighting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StressGridLightingTest {
    @Test void lightingFallbackUsesFlatGridInsteadOfHiddenOriginalHills() throws Exception {
        var original=new Terrain(42); int chosen=4812, z=-8500;
        assertTrue(original.column(chosen,z).height()>40,"Synthetic hill lies above the grid");
        try(var world=new World(42);var lighting=new WorldLighting()) {
            world.terrain().stressGrid(StressGrid.standard());
            assertTrue(world.getLoadedChunks().isEmpty(),"Exercise only the worker's fallback terrain");
            LightVolume volume=null;long deadline=System.nanoTime()+60_000_000_000L;
            while(volume==null && System.nanoTime()<deadline) {
                volume=lighting.update(world,chosen,z); if(volume==null) Thread.sleep(20);
            }
            assertNotNull(volume,"Lighting worker completed");
            assertTrue(volume.sample(chosen,34,z)[2]>.4f,"Sky reaches air above the flat grid");
            assertTrue(volume.sample(chosen,31,z)[2]<.04f,"Ground below the grid stays opaque");
        }
    }
}
