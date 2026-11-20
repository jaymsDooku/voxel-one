package dev.jayms.building;

import dev.jayms.Chunk;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.net.WorldVoxels;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildingCompilerTest {
    @Test void uniformCellsCoalesceAndPartOrderCarvesAndRepaints() {
        String recipe = """
                # All dimensions and positions use half-blocks.
                building breakwater 4 2 2
                part shell 0 0 0 4 2 2
                  box STONE 0 0 0 4 2 2
                end
                part facade 2 0 0 4 2 2
                  box AIR 2 0 0 4 2 2
                  box GLASS 2 0 0 3 1 1
                  box LED:1234AB 3 1 1 4 2 2
                end
                """;
        var building = BuildingCompiler.compile(recipe);
        assertEquals(building, BuildingCompiler.compile(recipe));
        assertEquals("breakwater", building.name());
        assertEquals(10, building.occupied());
        assertEquals(List.of(new Protocol.Edit(0, 0, 0, Blocks.STONE),
                new Protocol.Edit(1, 0, 0, Blocks.piece(Blocks.GLASS, 1), 1, 0, 0, 0),
                new Protocol.Edit(1, 0, 0, Blocks.piece(Blocks.LED, 1), 1, 1, 1, 1, 0x1234ab)),
                building.edits());
        assertTrue(building.edits().stream().allMatch(Protocol.Edit::valid));
        assertThrows(UnsupportedOperationException.class, () -> building.edits().clear());

        Chunk chunk = new Chunk();
        building.edits().forEach(chunk::apply);
        assertEquals(Blocks.STONE, chunk.getBlock(0, 0, 0));
        assertEquals(Blocks.PARTIAL, chunk.getBlock(1, 0, 0));
        var region = chunk.cell(1, 0, 0);
        assertEquals(Blocks.GLASS, WorldVoxels.decode(region.get(0, 0, 0)));
        assertEquals(Blocks.AIR, WorldVoxels.decode(region.get(15, 0, 0)));
        assertEquals(0x1234ab, WorldVoxels.lightColor(region.get(15, 15, 15)));
    }

    @Test void ellipticalPortholesFollowAxisAndFitOddFootprint() {
        for (String axis : List.of("x", "y", "z")) {
            var building = BuildingCompiler.compile("""
                    building porthole 5 5 5
                    part wall 0 0 0 5 5 5
                      box BRICKS 0 0 0 5 5 5
                      cylinder AIR %s 0 0 0 5 5 5
                    end
                    """.formatted(axis));
            // A 5x5 circle retains 21 planes, leaving only the 4 cut corners at each cross-section.
            assertEquals(20, building.occupied());
            assertEquals(20, building.edits().size());
            assertTrue(building.edits().stream().allMatch(Protocol.Edit::valid));
        }
    }

    @Test void invalidOperationsReportTheirLineAndPartCannotClaimOutsideEnvelope() {
        var message = rejects("""
                building house 12 12 12
                part wall 0 0 0 2 6 12
                box STONE 0 0 0 3 6 12
                end
                """);
        assertTrue(message.startsWith("Line 3:"));
        assertTrue(message.contains("enclosing bounds"));
        assertTrue(rejects("""
                building house 12 12 12
                part wall 0 0 0 2 6 12
                end
                part wall 0 0 0 2 6 12
                end
                """).contains("Duplicate part ID"));
        assertTrue(rejects("""
                building house 12 12 12
                part wall 0 0 0 2 6 12
                box LED:ffffff00 0 0 0 2 6 12
                end
                """).contains("LED:rrggbb"));
        assertTrue(rejects("""
                building house 12 12 12
                part wall 0 0 0 2 6 12
                box STONE 0 0 0 2 6 12
                """).contains("Part must end"));
    }

    @Test void budgetRejectsLargeVolumesAndRepeatedOverwrites() {
        assertTrue(rejects("building oversized 128 128 128").contains("volume"));
        String header = """
                building layered 120 100 120
                part large 0 0 0 120 100 120
                """;
        String paint = "box STONE 0 0 0 120 100 120\n";
        assertTrue(rejects(header + paint.repeat(6) + "end").contains("fill-work budget"));
        String nearCell = """
                building tiny 2 2 2
                part tiny 0 0 0 2 2 2
                """;
        assertTrue(rejects(nearCell + "box STONE 0 0 0 2 2 2\n".repeat(8001) + "end")
                .contains("operation or fill-work budget"));
        assertTrue(rejects("x".repeat(BuildingCompiler.MAX_SOURCE_CHARS+1)).contains("characters"));
    }

    @Test void buildRejectsExcessiveFragmentationButAllowsMergingTheSameOccupiedVolume() {
        String header = "building facade 100 100 100\npart enclosure 0 0 0 100 100 100\n";
        assertEquals(125_000, BuildingCompiler.compile(header+"box STONE 0 0 0 100 100 100\nend").edits().size());
        var stripes = new StringBuilder(header);
        for (int x=0; x<100; x+=2)
            stripes.append("box STONE ").append(x).append(" 0 0 ").append(x+1).append(" 100 100\n");
        assertTrue(rejects(stripes.append("end").toString()).contains("placement edits"));
    }

    private static String rejects(String recipe) {
        return assertThrows(IllegalArgumentException.class, () -> BuildingCompiler.compile(recipe)).getMessage();
    }
}
