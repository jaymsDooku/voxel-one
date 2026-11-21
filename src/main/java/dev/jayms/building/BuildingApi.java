package dev.jayms.building;

import dev.jayms.net.mobile.Json;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared, read-only compilation boundary for agent tools and the local Asset Manager. */
public final class BuildingApi {
    public static final int MAX_RECIPE_BYTES = 256 * 1024;
    public static final String JSON_TYPE = "application/json; charset=utf-8";
    public static final String SCHEMA = """
            {
              "schemaVersion": 1,
              "contentType": "text/plain; charset=utf-8",
              "maxRecipeBytes": %d,
              "units": "half blocks",
              "grid16": {
                "schemaVersion": 2,
                "header": "building ID WIDTH HEIGHT DEPTH grid16",
                "unitsPerBlock": %d,
                "maxAxis": %d,
                "maxOctreeWork": %d,
                "monument": {"suffix":"monument","maxAxis":%d,"maxOutputEdits":%d,"maxOctreeWork":%d},
                "largeMonument": {"suffix":"monument large","maxAxis":%d,"maxOutputEdits":%d,"maxOctreeWork":%d},
                "editDepths": [0, 1, 2, 3, 4],
                "occupied": "Count in sixteenth-block cells"
              },
              "maxAxis": %d,
              "maxVolume": %d,
              "maxLineCharacters": %d,
              "maxOperations": %d,
              "maxOperationVolume": %d,
              "maxOutputEdits": %d,
              "bounds": "Inclusive minimum, exclusive maximum; all coordinates are local to the building.",
              "comments": "Blank lines and # comments are ignored.",
              "ordering": "Parts and operations are applied in source order; later operations replace earlier voxels.",
              "commands": [
                "building ID WIDTH HEIGHT DEPTH [grid16 [monument [large]]]",
                "part ID X0 Y0 Z0 X1 Y1 Z1",
                "box MATERIAL X0 Y0 Z0 X1 Y1 Z1",
                "cylinder MATERIAL AXIS X0 Y0 Z0 X1 Y1 Z1",
                "masonry MATERIAL RUN_AXIS STONE_LENGTH COURSE_HEIGHT JOINT PHASE X0 Y0 Z0 X1 Y1 Z1",
                "row COUNT DX DY DZ",
                "endrow",
                "end"
              ],
              "axes": ["x", "y", "z"],
              "materials": ["AIR", "GRASS", "DIRT", "STONE", "SAND", "SNOW", "WOOD", "LEAVES", "PLANKS", "BRICKS", "GLASS", "LED:rrggbb"],
              "editFields": ["x", "y", "z", "type", "depth", "ix", "iy", "iz", "color"]
            }
            """.formatted(MAX_RECIPE_BYTES,BuildingCompiler.FINE_UNITS_PER_BLOCK,BuildingCompiler.MAX_FINE_DIMENSION,
                    BuildingCompiler.MAX_OCTREE_WORK,BuildingCompiler.MONUMENT_AXIS,BuildingCompiler.MONUMENT_EDITS,
                    BuildingCompiler.MONUMENT_WORK,BuildingCompiler.LARGE_MONUMENT_AXIS,BuildingCompiler.LARGE_MONUMENT_EDITS,
                    BuildingCompiler.MONUMENT_WORK,BuildingCompiler.MAX_DIMENSION,BuildingCompiler.MAX_VOLUME,
                    BuildingCompiler.MAX_LINE_CHARS,BuildingCompiler.MAX_OPERATIONS,
                    BuildingCompiler.MAX_WORK,BuildingCompiler.MAX_EDITS);

    private BuildingApi() {}

    public static BuildingCompiler.Building compile(InputStream input) throws IOException {
        return BuildingCompiler.compile(readRecipe(input));
    }

    public static String readRecipe(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return readRecipe(input);
        }
    }

    private static String readRecipe(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_RECIPE_BYTES + 1);
        if (bytes.length > MAX_RECIPE_BYTES) throw new RecipeTooLarge();
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Building recipe must be valid UTF-8", e);
        }
    }

    /** Writes rows incrementally. The caller retains ownership of the output stream. */
    public static void writeJson(BuildingCompiler.Building building, OutputStream output) throws IOException {
        var writer = new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));
        boolean fine = building.unitsPerBlock() == BuildingCompiler.FINE_UNITS_PER_BLOCK;
        writer.write("{\"schemaVersion\":" + (fine ? 2 : 1) + ",\"name\":" + Json.write(building.name())
                + ",\"dimensions\":{\"width\":" + building.width() + ",\"height\":"
                + building.height() + ",\"depth\":" + building.depth() + "},\"occupied\":"
                + building.occupied() + ",\"editCount\":" + building.edits().size());
        if (fine) {
            int[] editsByDepth = new int[5];
            for (var edit : building.edits()) editsByDepth[edit.depth()]++;
            writer.write(",\"unitsPerBlock\":" + building.unitsPerBlock()
                    + ",\"editsByDepth\":" + java.util.Arrays.toString(editsByDepth));
        }
        writer.write(",\"editFields\":[\"x\",\"y\",\"z\",\"type\",\"depth\",\"ix\",\"iy\",\"iz\",\"color\"],\"edits\":[");
        boolean first = true;
        for (var edit : building.edits()) {
            if (!first) writer.write(',');
            first = false;
            writer.write("[" + edit.x() + "," + edit.y() + "," + edit.z() + "," + edit.type()
                    + "," + edit.depth() + "," + edit.ix() + "," + edit.iy() + "," + edit.iz()
                    + "," + edit.color() + "]");
        }
        writer.write("]}\n");
        writer.flush();
    }

    public static void run(String[] args) throws IOException {
        run(args,System.out);
    }

    static void run(String[] args, OutputStream output) throws IOException {
        if (args.length != 1 || args[0].isBlank())
            throw new IllegalArgumentException("Usage: --building-compile RECIPE_FILE");
        writeJson(BuildingCompiler.compile(readRecipe(Path.of(args[0]))),output);
    }

    public static final class RecipeTooLarge extends IOException {
        private RecipeTooLarge() { super("Building recipe exceeds " + MAX_RECIPE_BYTES + " bytes"); }
    }
}
