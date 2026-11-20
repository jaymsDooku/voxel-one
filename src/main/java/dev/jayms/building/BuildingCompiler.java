package dev.jayms.building;

import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Turns bounded, ordered half-block recipes into the engine's canonical placement edits. */
public final class BuildingCompiler {
    public static final int MAX_SOURCE_CHARS = 262_144;
    public static final int MAX_LINE_CHARS = 512;
    public static final int MAX_DIMENSION = 128;
    public static final int MAX_VOLUME = 1_500_000;
    public static final int MAX_OPERATIONS = 8_000;
    public static final int MAX_WORK = 8_000_000;
    public static final int MAX_EDITS = 160_000;

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,47}");
    private static final Pattern RGB = Pattern.compile("[0-9a-fA-F]{6}");
    private static final Map<String, Integer> MATERIALS = Map.ofEntries(
            Map.entry("AIR", Blocks.AIR), Map.entry("GRASS", Blocks.GRASS),
            Map.entry("DIRT", Blocks.DIRT), Map.entry("STONE", Blocks.STONE),
            Map.entry("SAND", Blocks.SAND), Map.entry("SNOW", Blocks.SNOW),
            Map.entry("WOOD", Blocks.WOOD), Map.entry("LEAVES", Blocks.LEAVES),
            Map.entry("PLANKS", Blocks.PLANKS), Map.entry("BRICKS", Blocks.BRICKS),
            Map.entry("GLASS", Blocks.GLASS));

    private BuildingCompiler() {}

    /** Dimensions and occupied-cell count are in half-block units. Edits are local to the building. */
    public record Building(String name, int width, int height, int depth,
                           List<Protocol.Edit> edits, int occupied) {
        public Building {
            edits = List.copyOf(edits);
        }
    }

    public static Building compile(String source) {
        if (source == null || source.length() > MAX_SOURCE_CHARS)
            throw new IllegalArgumentException("Recipe must contain at most " + MAX_SOURCE_CHARS + " characters");

        Grid grid = null;
        Bounds part = null;
        Set<String> parts = new HashSet<>();
        int operationCount = 0, work = 0, lineNumber = 0, partLine = 0;
        for (String rawLine : source.split("\\R", -1)) {
            lineNumber++;
            try {
                if (rawLine.length() > MAX_LINE_CHARS)
                    throw new IllegalArgumentException("Line exceeds " + MAX_LINE_CHARS + " characters");
                int comment = rawLine.indexOf('#');
                String line = (comment < 0 ? rawLine : rawLine.substring(0, comment)).strip();
                if (line.isEmpty()) continue;
                String[] words = line.split("\\s+");
                if (grid == null) {
                    if (!words[0].equals("building"))
                        throw new IllegalArgumentException("Expected 'building ID WIDTH HEIGHT DEPTH'");
                    grid = header(words);
                    continue;
                }
                switch (words[0]) {
                    case "part" -> {
                        requireLength(words, 8, "part ID X0 Y0 Z0 X1 Y1 Z1");
                        if (part != null) throw new IllegalArgumentException("Close the current part with 'end' first");
                        requireId(words[1]);
                        if (!parts.add(words[1])) throw new IllegalArgumentException("Duplicate part ID " + words[1]);
                        part = bounds(words, 2, grid.bounds());
                        partLine = lineNumber;
                    }
                    case "end" -> {
                        requireLength(words, 1, "end");
                        if (part == null) throw new IllegalArgumentException("No open part to end");
                        part = null;
                    }
                    case "box", "cylinder" -> {
                        boolean cylinder = words[0].equals("cylinder");
                        requireLength(words, cylinder ? 9 : 8, cylinder
                                ? "cylinder MATERIAL AXIS X0 Y0 Z0 X1 Y1 Z1"
                                : "box MATERIAL X0 Y0 Z0 X1 Y1 Z1");
                        if (part == null) throw new IllegalArgumentException("Place operations inside a part");
                        int material = material(words[1]);
                        Axis axis = cylinder ? axis(words[2]) : null;
                        Bounds paint = bounds(words, cylinder ? 3 : 2, part);
                        work += paint.volume();
                        if (++operationCount > MAX_OPERATIONS || work > MAX_WORK)
                            throw new IllegalArgumentException("Recipe exceeds the operation or fill-work budget");
                        grid.paint(paint, material, axis);
                    }
                    default -> throw new IllegalArgumentException("Unknown command " + words[0]);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        if (grid == null) throw new IllegalArgumentException("Missing building header");
        if (part != null) throw new IllegalArgumentException("Line " + partLine + ": Part must end with 'end'");
        if (operationCount == 0) throw new IllegalArgumentException("Recipe must contain at least one drawing operation");
        return grid.build();
    }

    private static Grid header(String[] words) {
        requireLength(words, 5, "building ID WIDTH HEIGHT DEPTH");
        requireId(words[1]);
        int w = dimension(words[2]), h = dimension(words[3]), d = dimension(words[4]);
        var bounds = new Bounds(0, 0, 0, w, h, d);
        if (bounds.volume() > MAX_VOLUME)
            throw new IllegalArgumentException("Building volume exceeds " + MAX_VOLUME + " half-block cells");
        return new Grid(words[1], bounds);
    }

    private static void requireId(String value) {
        if (!ID.matcher(value).matches())
            throw new IllegalArgumentException("Use an ID of 1–48 letters, digits, underscores or hyphens, starting with a letter or digit");
    }

    private static int dimension(String value) {
        int number = integer(value);
        if (number < 1 || number > MAX_DIMENSION)
            throw new IllegalArgumentException("Dimensions must be between 1 and " + MAX_DIMENSION);
        return number;
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Expected an integer, got " + value, e); }
    }

    private static void requireLength(String[] words, int length, String usage) {
        if (words.length != length) throw new IllegalArgumentException("Expected '" + usage + "'");
    }

    private static Bounds bounds(String[] words, int i, Bounds outer) {
        var shape = new Bounds(integer(words[i]), integer(words[i+1]), integer(words[i+2]),
                integer(words[i+3]), integer(words[i+4]), integer(words[i+5]));
        if (!outer.contains(shape))
            throw new IllegalArgumentException("Bounds must be nonempty, half-open, and within their enclosing bounds");
        return shape;
    }

    private static int material(String value) {
        String token = value.toUpperCase(Locale.ROOT);
        Integer material = MATERIALS.get(token);
        if (material != null) return material << 24;
        if (token.startsWith("LED:") && RGB.matcher(token.substring(4)).matches())
            return Blocks.LED << 24 | Integer.parseInt(token.substring(4), 16);
        throw new IllegalArgumentException("Unknown material " + value + " (LED colors use LED:rrggbb)");
    }

    private static Axis axis(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "x" -> Axis.X;
            case "y" -> Axis.Y;
            case "z" -> Axis.Z;
            default -> throw new IllegalArgumentException("Cylinder axis must be x, y, or z");
        };
    }

    private enum Axis { X, Y, Z }

    private record Bounds(int x0, int y0, int z0, int x1, int y1, int z1) {
        boolean contains(Bounds inner) {
            return x0 <= inner.x0 && inner.x0 < inner.x1 && inner.x1 <= x1
                    && y0 <= inner.y0 && inner.y0 < inner.y1 && inner.y1 <= y1
                    && z0 <= inner.z0 && inner.z0 < inner.z1 && inner.z1 <= z1;
        }

        int volume() {
            return (x1-x0)*(y1-y0)*(z1-z0);
        }
    }

    private static final class Grid {
        private final String name;
        private final Bounds bounds;
        private final int[] cells;

        Grid(String name, Bounds bounds) {
            this.name = name;
            this.bounds = bounds;
            cells = new int[bounds.volume()];
        }

        Bounds bounds() { return bounds; }

        private int index(int x, int y, int z) {
            return (y*bounds.z1+z)*bounds.x1+x;
        }

        void paint(Bounds b, int material, Axis axis) {
            int sx = b.x1-b.x0, sy = b.y1-b.y0, sz = b.z1-b.z0;
            for (int y=b.y0; y<b.y1; y++) for (int z=b.z0; z<b.z1; z++) for (int x=b.x0; x<b.x1; x++) {
                int dx = 2*(x-b.x0)+1-sx, dy = 2*(y-b.y0)+1-sy, dz = 2*(z-b.z0)+1-sz;
                if (axis == null || switch (axis) {
                    case X -> inEllipse(dy, dz, sy, sz);
                    case Y -> inEllipse(dx, dz, sx, sz);
                    case Z -> inEllipse(dx, dy, sx, sy);
                }) cells[index(x, y, z)] = material;
            }
        }

        private static boolean inEllipse(int a, int b, int lengthA, int lengthB) {
            // Measure cell centers and use exact integer math for deterministic boundary decisions.
            long aa = (long) lengthA*lengthA, bb = (long) lengthB*lengthB;
            return a*a*bb + b*b*aa <= aa*bb;
        }

        private int get(int x, int y, int z) {
            return x < bounds.x1 && y < bounds.y1 && z < bounds.z1 ? cells[index(x, y, z)] : 0;
        }

        Building build() {
            List<Protocol.Edit> edits = new ArrayList<>();
            int occupied = 0;
            int[] block = new int[8];
            for (int y=0; y<bounds.y1; y+=2) for (int z=0; z<bounds.z1; z+=2) for (int x=0; x<bounds.x1; x+=2) {
                boolean uniform = true;
                for (int i=0; i<8; i++) {
                    int value = get(x+(i&1), y+(i>>2), z+((i>>1)&1));
                    block[i] = value;
                    if (value != 0) occupied++;
                    if (value != block[0]) uniform = false;
                }
                if (uniform && block[0] != 0) {
                    emit(edits, x/2, y/2, z/2, block[0], 0, 0, 0, 0);
                } else if (!uniform) {
                    for (int i=0; i<8; i++) if (block[i] != 0)
                        emit(edits, x/2, y/2, z/2, block[i], 1, i&1, i>>2, (i>>1)&1);
                }
            }
            if (occupied == 0) throw new IllegalArgumentException("Recipe leaves an empty building");
            return new Building(name, bounds.x1, bounds.y1, bounds.z1, edits, occupied);
        }

        private static void emit(List<Protocol.Edit> edits, int x, int y, int z,
                                 int packed, int depth, int ix, int iy, int iz) {
            if (edits.size() >= MAX_EDITS)
                throw new IllegalArgumentException("Building exceeds " + MAX_EDITS + " placement edits");
            int material = packed >>> 24;
            edits.add(new Protocol.Edit(x, y, z, Blocks.piece(material, depth),
                    depth, ix, iy, iz, packed & 0xffffff));
        }
    }
}
