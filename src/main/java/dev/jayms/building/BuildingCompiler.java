package dev.jayms.building;

import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;
import dev.jayms.net.WorldVoxels;
import dev.jayms.net.model.SparseVoxelOctree;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Turns bounded, ordered building recipes into the engine's canonical placement edits. */
public final class BuildingCompiler {
    public static final int MAX_SOURCE_CHARS = 262_144;
    public static final int MAX_LINE_CHARS = 512;
    public static final int MAX_DIMENSION = 128;
    public static final int MAX_VOLUME = 1_500_000;
    public static final int MAX_OPERATIONS = 8_000;
    public static final int MAX_WORK = 8_000_000;
    public static final int MAX_EDITS = 160_000;
    public static final int FINE_UNITS_PER_BLOCK = 16;
    public static final int MAX_FINE_DIMENSION = MAX_DIMENSION * 8;
    public static final int MAX_OCTREE_WORK = 2_000_000;

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

    /** Dimensions and occupied-cell count use the recipe grid. Edits are local to the building. */
    public record Building(String name, int width, int height, int depth,
                           List<Protocol.Edit> edits, int occupied, int unitsPerBlock) {
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
                        if (grid.scale == 8) work += paint.volume();
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
        boolean fine = words.length == 6 && words[5].equals("grid16");
        if (!fine && words.length != 5)
            throw new IllegalArgumentException("Expected 'building ID WIDTH HEIGHT DEPTH [grid16]'");
        requireId(words[1]);
        int max = fine ? MAX_FINE_DIMENSION : MAX_DIMENSION;
        int w = dimension(words[2],max), h = dimension(words[3],max), d = dimension(words[4],max);
        var bounds = new Bounds(0, 0, 0, w, h, d);
        if (!fine && bounds.volume() > MAX_VOLUME)
            throw new IllegalArgumentException("Building volume exceeds " + MAX_VOLUME + " half-block cells");
        return new Grid(words[1],bounds,fine ? 1 : 8);
    }

    private static void requireId(String value) {
        if (!ID.matcher(value).matches())
            throw new IllegalArgumentException("Use an ID of 1–48 letters, digits, underscores or hyphens, starting with a letter or digit");
    }

    private static int dimension(String value, int max) {
        int number = integer(value);
        if (number < 1 || number > max)
            throw new IllegalArgumentException("Dimensions must be between 1 and " + max);
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

    /** Forest of engine octrees. A tile covers sixteen game blocks on each axis. */
    private static final class Grid {
        private static final int TILE = 256, TILES_PER_AXIS = MAX_FINE_DIMENSION / TILE;
        private final String name;
        private final Bounds bounds;
        private final int scale;
        private final SparseVoxelOctree[] tiles = new SparseVoxelOctree[TILES_PER_AXIS*TILES_PER_AXIS*TILES_PER_AXIS];
        private long fineWork;

        Grid(String name, Bounds bounds, int scale) {
            this.name = name;
            this.bounds = bounds;
            this.scale = scale;
        }

        Bounds bounds() { return bounds; }

        void paint(Bounds b, int packed, Axis axis) {
            int material = packed >>> 24;
            int color = material == Blocks.LED ? 0xfe000000 | (packed & 0xffffff) : WorldVoxels.encode(material);
            if (axis == null) {
                fill(b,color);
                return;
            }
            // An ellipse is symmetric. Binary search for the first occupied center on
            // each row, then fill the entire span directly into the sparse trees.
            int a = axis == Axis.X ? b.y1-b.y0 : b.x1-b.x0;
            int length = axis == Axis.Z ? b.y1-b.y0 : b.z1-b.z0;
            for (int row=0; row<a; row++) {
                int lo=0, hi=(length+1)/2;
                while (lo<hi) {
                    int mid=(lo+hi)/2;
                    if (inEllipse(2*row+1-a,2*mid+1-length,a,length)) hi=mid;
                    else lo=mid+1;
                }
                if (lo>=length-lo || !inEllipse(2*row+1-a,2*lo+1-length,a,length)) continue;
                Bounds span = switch (axis) {
                    case X -> new Bounds(b.x0,b.y0+row,b.z0+lo,b.x1,b.y0+row+1,b.z1-lo);
                    case Y -> new Bounds(b.x0+row,b.y0,b.z0+lo,b.x0+row+1,b.y1,b.z1-lo);
                    case Z -> new Bounds(b.x0+row,b.y0+lo,b.z0,b.x0+row+1,b.y1-lo,b.z1);
                };
                fill(span,color);
            }
        }

        private static boolean inEllipse(int a, int b, int lengthA, int lengthB) {
            long aa=(long)lengthA*lengthA, bb=(long)lengthB*lengthB;
            return a*a*bb + b*b*aa <= aa*bb;
        }

        private void fill(Bounds b, int color) {
            int x0=b.x0*scale, y0=b.y0*scale, z0=b.z0*scale;
            int x1=b.x1*scale, y1=b.y1*scale, z1=b.z1*scale;
            if (scale==1) {
                // Large aligned volumes collapse cheaply. A thin, unaligned sheet can
                // create many leaves, so charge at the smallest required dyadic size.
                int side=Math.min(16,Integer.lowestOneBit(x0|y0|z0|x1|y1|z1));
                fineWork+=(long)((x1-x0)/side)*((y1-y0)/side)*((z1-z0)/side);
                if (fineWork>MAX_OCTREE_WORK) throw new IllegalArgumentException("Recipe exceeds the octree fill-work budget");
            }
            for (int ty=y0/TILE; ty<=(y1-1)/TILE; ty++)
                for (int tz=z0/TILE; tz<=(z1-1)/TILE; tz++)
                    for (int tx=x0/TILE; tx<=(x1-1)/TILE; tx++) {
                        int i=index(tx,ty,tz);
                        SparseVoxelOctree tree=tiles[i];
                        if (tree==null) {
                            if (color==0) continue;
                            tree=tiles[i]=new SparseVoxelOctree(TILE);
                        }
                        int bx=tx*TILE, by=ty*TILE, bz=tz*TILE;
                        tree.fill(Math.max(0,x0-bx),Math.max(0,y0-by),Math.max(0,z0-bz),
                                Math.min(TILE,x1-bx),Math.min(TILE,y1-by),Math.min(TILE,z1-bz),color);
                    }
        }

        private static int index(int x, int y, int z) {
            return (y*TILES_PER_AXIS+z)*TILES_PER_AXIS+x;
        }

        Building build() {
            List<Protocol.Edit> edits=new ArrayList<>();
            int occupied=0;
            for (int y=0;y<bounds.y1*scale;y+=16) for (int z=0;z<bounds.z1*scale;z+=16) for (int x=0;x<bounds.x1*scale;x+=16) {
                var tree=tiles[index(x/TILE,y/TILE,z/TILE)];
                if (tree!=null && !tree.isEmpty()) occupied+=emit(edits,tree,x,y,z,16,0);
            }
            if (occupied==0) throw new IllegalArgumentException("Recipe leaves an empty building");
            return new Building(name,bounds.x1,bounds.y1,bounds.z1,edits,occupied,16/scale);
        }

        private int emit(List<Protocol.Edit> edits,SparseVoxelOctree tree,int x,int y,int z,int side,int depth) {
            int value=tree.uniform(x%TILE,y%TILE,z%TILE,side);
            if (value==0) return 0;
            if (value==-1) {
                int half=side/2, total=0;
                for (int i=0;i<8;i++)
                    total+=emit(edits,tree,x+(i&1)*half,y+(i>>2)*half,z+((i>>1)&1)*half,half,depth+1);
                return total;
            }
            if (edits.size()>=MAX_EDITS) throw new IllegalArgumentException("Building exceeds " + MAX_EDITS + " placement edits");
            int material=WorldVoxels.decode(value), units=side/scale;
            edits.add(new Protocol.Edit(x/16,y/16,z/16,Blocks.piece(material,depth),depth,
                    x%16/side,y%16/side,z%16/side,material==Blocks.LED ? WorldVoxels.lightColor(value) : 0));
            return units*units*units;
        }
    }
}
