package dev.jayms.building;

import dev.jayms.net.Blocks;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BreakwaterHouseTest {
    @Test void authoredRecipeHasAWalkInLobbyRecessedWindowsAndLitTower() throws Exception {
        var source = Files.readString(Path.of("assets/buildings/breakwater-house.vbuild"));
        var house = BuildingCompiler.compile(source);
        assertEquals("breakwater-house", house.name());
        assertEquals(52, house.width());
        assertEquals(44, house.height());
        assertEquals(38, house.depth());

        int[][][] material = new int[house.width()][house.height()][house.depth()];
        int[][][] color = new int[house.width()][house.height()][house.depth()];
        for (var edit : house.edits()) {
            int size = 2 >> edit.depth();
            int x = edit.x()*2+edit.ix()*size;
            int y = edit.y()*2+edit.iy()*size;
            int z = edit.z()*2+edit.iz()*size;
            for (int dx=0; dx<size; dx++) for (int dy=0; dy<size; dy++)
                for (int dz=0; dz<size; dz++) {
                    material[x+dx][y+dy][z+dz] = Blocks.material(edit.type());
                    color[x+dx][y+dy][z+dz] = edit.color();
                }
        }

        // The porch and lobby share one floor level and the door stays unobstructed.
        for (int z=1; z<=15; z++) {
            assertNotEquals(Blocks.AIR, material[34][0][z], "floor at z="+z);
            for (int x=31; x<=36; x++) for (int y=1; y<=6; y++)
                assertEquals(Blocks.AIR, material[x][y][z], "clear entry at "+x+","+y+","+z);
        }
        assertEquals(Blocks.BRICKS, material[34][0][19], "terrazzo inset");
        assertEquals(Blocks.AIR, material[24][4][5], "north-facing window recess");
        assertEquals(Blocks.GLASS, material[24][4][6], "north-facing glazing");
        assertEquals(Blocks.AIR, material[1][11][19], "west porthole recess");
        assertEquals(Blocks.GLASS, material[2][11][19], "west porthole glazing");
        assertEquals(Blocks.AIR, material[2][6][6], "rounded corner leaves empty space");
        assertEquals(Blocks.LED, material[12][43][15], "spire light");
        assertEquals(0xdb815e, color[12][43][15], "spire accent");
    }
}
