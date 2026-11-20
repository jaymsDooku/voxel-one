package dev.jayms.building;

import dev.jayms.Chunk;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.net.WorldVoxels;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OctreeBuildingTest {
    @Test void allFiveDepthsRoundTripThroughNativeStorage() {
        var building = BuildingCompiler.compile("""
                building depths 80 32 32 grid16
                part pieces 0 0 0 80 32 32
                box STONE 0 0 0 16 16 16
                box WOOD 16 0 0 24 8 8
                box GLASS 32 0 0 36 4 4
                box PLANKS 48 0 0 50 2 2
                box LED:114477 64 0 0 65 1 1
                end
                """);
        assertEquals(16,building.unitsPerBlock());
        assertEquals(4096+512+64+8+1,building.occupied());
        assertEquals(5,building.edits().size());
        for (int i=0;i<5;i++) assertEquals(i,building.edits().get(i).depth());
        var chunk = new Chunk();
        building.edits().forEach(chunk::apply);
        assertEquals(Blocks.STONE,chunk.material(15,15,15));
        assertEquals(Blocks.GLASS,chunk.material(35,3,3));
        assertEquals(Blocks.AIR,chunk.material(36,3,3));
        assertEquals(0x114477,WorldVoxels.lightColor(chunk.value(64,0,0)));
        assertEquals(Blocks.AIR,chunk.material(65,0,0));
    }

    @Test void largeSparseEnvelopeAndCutsAcrossTilesDoNotExpandTheWholeGrid() {
        var building = BuildingCompiler.compile("""
                building sparse 4096 4096 4096 grid16
                part edge 240 0 0 272 16 16
                box STONE 240 0 0 272 16 16
                box AIR 255 0 0 257 1 1
                box LED:aa4411 256 0 0 257 1 1
                end
                part far 4080 4080 4080 4096 4096 4096
                box WOOD 4080 4080 4080 4096 4096 4096
                end
                """);
        assertEquals(3*4096-1,building.occupied());
        assertTrue(building.edits().size()<70);
        assertEquals(Blocks.AIR,materialAt(building,255,0,0));
        assertEquals(Blocks.LED,materialAt(building,256,0,0));
        assertEquals(Blocks.STONE,materialAt(building,257,0,0));
        assertEquals(Blocks.WOOD,materialAt(building,4095,4095,4095));
        var endpoint=building.edits().get(building.edits().size()-1);
        assertEquals(255,endpoint.x());
        assertEquals(255,endpoint.y());
        assertEquals(255,endpoint.z());
        assertThrows(IllegalArgumentException.class,()->BuildingCompiler.compile(
                "building too-wide 4097 16 16 grid16\npart sample 0 0 0 16 16 16\nbox STONE 0 0 0 16 16 16\nend"));
    }

    @Test void fineCurvesAreSymmetricAndFineBudgetFailsCleanly() {
        var building = BuildingCompiler.compile("""
                building arch 16 16 16 grid16
                part test 0 0 0 16 16 16
                cylinder GLASS z 1 1 2 11 11 3
                end
                """);
        assertEquals(80,building.occupied());
        assertEquals(Blocks.AIR,materialAt(building,1,1,2));
        assertEquals(Blocks.GLASS,materialAt(building,5,5,2));
        assertEquals(Blocks.GLASS,materialAt(building,6,6,2));
        var error=assertThrows(IllegalArgumentException.class,()->BuildingCompiler.compile("""
                building budget 1024 1024 1024 grid16
                part whole 0 0 0 1024 1024 1024
                box STONE 1 1 1 1024 1024 1024
                end
                """));
        assertTrue(error.getMessage().contains("Line 3"));
        assertTrue(error.getMessage().contains("octree fill-work budget"));
    }

    @Test void cedarEntryUsesFineGlassPegsAndAnOpenDoor() throws Exception {
        var path = Path.of("assets/buildings/cedar-court-entry.vbuild");
        var source = BuildingApi.readRecipe(path);
        var building = BuildingCompiler.compile(source);
        assertEquals(building,BuildingCompiler.compile(source));
        assertEquals(Set.of(0,1,2,3,4),building.edits().stream().map(Protocol.Edit::depth).collect(Collectors.toSet()));
        assertEquals(Blocks.AIR,materialAt(building,80,48,49));
        assertEquals(Blocks.GLASS,materialAt(building,38,27,49));
        assertEquals(Blocks.WOOD,materialAt(building,44,27,48));
        assertEquals(Blocks.LED,materialAt(building,36,47,49));
        assertEquals(Blocks.STONE,materialAt(building,24,72,45));
    }

    @Test void westminsterRecipeMeetsItsMassingPlan() throws Exception {
        var building = BuildingCompiler.compile(
                BuildingApi.readRecipe(Path.of("assets/buildings/westminster.vbuild")));
        assertEquals(150*16,building.width());
        assertEquals(51*16,building.height());
        assertEquals(76*16,building.depth());
        assertEquals(Set.of(0,1,2,3,4),building.edits().stream().map(Protocol.Edit::depth).collect(Collectors.toSet()));
        assertEquals(Blocks.SAND,materialAt(building,136,47*16,312)); // Elizabeth finial
        assertEquals(Blocks.STONE,materialAt(building,1192,45*16,600)); // Central spire
        assertEquals(Blocks.SAND,materialAt(building,135*16,48*16,45*16)); // Victoria turret
        assertEquals(Blocks.SNOW,materialAt(building,130,27*16,213)); // river clock face
        assertEquals(Blocks.SNOW,materialAt(building,130,27*16,410)); // opposing clock face
        assertEquals(Blocks.SNOW,materialAt(building,38,27*16,310));
        assertEquals(Blocks.SNOW,materialAt(building,234,27*16,310));
        for (int x : new int[]{50,95}) {
            assertEquals(Blocks.STONE,materialAt(building,x*16,0,43*16));
            for (int y=1;y<51;y++)
                assertEquals(Blocks.AIR,materialAt(building,x*16,y*16,43*16));
        }
        assertEquals(Blocks.STONE,materialAt(building,20*16,0,62*16));
        assertEquals(Blocks.AIR,materialAt(building,20*16,5*16,62*16));
        assertEquals(Blocks.AIR,materialAt(building,30*16,3*16,57*16));
        assertEquals(Blocks.STONE,materialAt(building,20*16,15*16,62*16));
    }

    private static int materialAt(BuildingCompiler.Building building,int x,int y,int z) {
        for (var edit : building.edits()) {
            int side=16>>edit.depth();
            int ex=edit.x()*16+edit.ix()*side, ey=edit.y()*16+edit.iy()*side, ez=edit.z()*16+edit.iz()*side;
            if (ex<=x && x<ex+side && ey<=y && y<ey+side && ez<=z && z<ez+side)
                return Blocks.material(edit.type());
        }
        return Blocks.AIR;
    }
}
