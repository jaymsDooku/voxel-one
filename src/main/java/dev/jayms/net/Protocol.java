package dev.jayms.net;

import java.io.*;

/** Protocol 25 preserves road ownership and command IDs, and appends railway state. */
public final class Protocol {
    public static final int CITY_STATE = 16, CITY_COMMAND = 17, CITY_WORLD = 18, CITY_RESULT = 19;
    public static final int MAGIC = 0x564F5831, VERSION = 26, PORT = 25565;
    public static final int CITY_BASE_VERSION = 14, SPECIAL_BUILDINGS_VERSION = 15;
    public static final int MOVE = 1, BLOCK = 2, LEAVE = 3, READY = 4, JOIN = 5, EDIT_RESULT = 6;
    public static final int INVENTORY = 7, DROP = 8, SWAP = 9, RESPAWN = 10;
    public static final int MODEL_CREATE = 11, MODEL_DEFINE = 12, MODEL_RESULT = 13;
    public static final int CRAFT = 14, CRAFT_RESULT = 15;
    public static final int LOGIN = 1, REGISTER = 2;

    public record Pose(
            int id,
            float x,
            float y,
            float z,
            float yaw,
            float pitch,
            float walkPhase,
            float walkAmount,
            boolean flying,
            float swingProgress,
            int heldItem,
            boolean placingSwing,
            int heldColor) {
        public Pose(
                int id,
                float x,
                float y,
                float z,
                float yaw,
                float pitch,
                float walkPhase,
                float walkAmount,
                boolean flying,
                float swingProgress,
                int heldItem,
                boolean placingSwing) {
            this(
                    id,
                    x,
                    y,
                    z,
                    yaw,
                    pitch,
                    walkPhase,
                    walkAmount,
                    flying,
                    swingProgress,
                    heldItem,
                    placingSwing,
                    0xffffff);
        }

        public Pose(
                int id,
                float x,
                float y,
                float z,
                float yaw,
                float pitch,
                float walkPhase,
                float walkAmount,
                boolean flying,
                float swingProgress,
                int heldItem) {
            this(
                    id,
                    x,
                    y,
                    z,
                    yaw,
                    pitch,
                    walkPhase,
                    walkAmount,
                    flying,
                    swingProgress,
                    heldItem,
                    false);
        }

        public Pose(
                int id,
                float x,
                float y,
                float z,
                float yaw,
                float pitch,
                float walkPhase,
                float walkAmount,
                boolean flying) {
            this(id, x, y, z, yaw, pitch, walkPhase, walkAmount, flying, 1, 0);
        }

        public Pose(int id, float x, float y, float z, float yaw, float pitch) {
            this(id, x, y, z, yaw, pitch, 0, 0, false);
        }

        public void write(DataOutputStream out) throws IOException {
            out.writeInt(id);
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(z);
            out.writeFloat(yaw);
            out.writeFloat(pitch);
            out.writeFloat(walkPhase);
            out.writeFloat(walkAmount);
            out.writeBoolean(flying);
            out.writeFloat(swingProgress);
            out.writeByte(heldItem);
            out.writeBoolean(placingSwing);
            out.writeInt(heldColor);
        }

        public static Pose read(DataInputStream in) throws IOException {
            return new Pose(
                    in.readInt(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat(),
                    in.readBoolean(),
                    in.readFloat(),
                    in.readUnsignedByte(),
                    in.readBoolean(),
                    in.readInt());
        }

        public boolean valid() {
            return heldColor >= 0
                    && heldColor <= 0xffffff
                    && Float.isFinite(x)
                    && Float.isFinite(y)
                    && Float.isFinite(z)
                    && Float.isFinite(yaw)
                    && Float.isFinite(pitch)
                    && Float.isFinite(walkPhase)
                    && Float.isFinite(walkAmount)
                    && Float.isFinite(swingProgress)
                    && swingProgress >= 0
                    && swingProgress <= 1
                    && Blocks.valid(heldItem)
                    && walkAmount >= 0
                    && walkAmount <= 1
                    && x >= -Terrain.LIMIT
                    && x <= Terrain.LIMIT
                    && z >= -Terrain.LIMIT
                    && z <= Terrain.LIMIT
                    && y >= Terrain.MIN_Y
                    && y <= Terrain.MAX_Y + 32
                    && Math.abs(pitch) <= 89;
        }
    }

    public record Edit(
            int x, int y, int z, int type, int depth, int ix, int iy, int iz, int color) {
        public Edit(int x, int y, int z, int type, int depth, int ix, int iy, int iz) {
            this(
                    x,
                    y,
                    z,
                    type,
                    depth,
                    ix,
                    iy,
                    iz,
                    Blocks.material(type) == Blocks.LED ? 0xffffff : 0);
        }

        public Edit withColor(int rgb) {
            return new Edit(x, y, z, type, depth, ix, iy, iz, rgb);
        }

        public Edit(int x, int y, int z, int type) {
            this(x, y, z, type, 0, 0, 0, 0);
        }

        public float size() {
            return 1f / (1 << depth);
        }

        public float minX() {
            return x + ix * size();
        }

        public float minY() {
            return y + iy * size();
        }

        public float minZ() {
            return z + iz * size();
        }

        public Edit withType(int value) {
            return new Edit(
                    x,
                    y,
                    z,
                    value,
                    depth,
                    ix,
                    iy,
                    iz,
                    Blocks.material(value) == Blocks.LED
                            ? (Blocks.material(type) == Blocks.LED ? color : 0xffffff)
                            : 0);
        }

        public static Edit at(double x, double y, double z, int type, int depth) {
            int bx = (int) Math.floor(x),
                    by = (int) Math.floor(y),
                    bz = (int) Math.floor(z),
                    n = 1 << depth;
            return new Edit(
                    bx,
                    by,
                    bz,
                    type,
                    depth,
                    (int) Math.floor((x - bx) * n),
                    (int) Math.floor((y - by) * n),
                    (int) Math.floor((z - bz) * n));
        }

        public void write(DataOutputStream out) throws IOException {
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.writeInt(type);
            out.writeByte(depth);
            out.writeByte(ix);
            out.writeByte(iy);
            out.writeByte(iz);
            out.writeInt(color);
        }

        public static Edit readV7(DataInputStream in) throws IOException {
            return new Edit(
                    in.readInt(),
                    in.readInt(),
                    in.readInt(),
                    in.readInt(),
                    in.readUnsignedByte(),
                    in.readUnsignedByte(),
                    in.readUnsignedByte(),
                    in.readUnsignedByte());
        }

        public static Edit read(DataInputStream in) throws IOException {
            var edit = readV7(in);
            return edit.withColor(in.readInt());
        }

        public static Edit readLegacy(DataInputStream in) throws IOException {
            return new Edit(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }

        public boolean valid() {
            return color >= 0
                    && color <= 0xffffff
                    && (Blocks.material(type) == Blocks.LED || color == 0)
                    && depth >= 0
                    && depth <= 4
                    && ix >= 0
                    && iy >= 0
                    && iz >= 0
                    && ix < (1 << depth)
                    && iy < (1 << depth)
                    && iz < (1 << depth)
                    && (type == 0 || Blocks.depth(type) == depth)
                    && Math.abs((long) x) <= Terrain.LIMIT
                    && Math.abs((long) z) <= Terrain.LIMIT
                    && y >= Terrain.MIN_Y
                    && y <= Terrain.MAX_Y
                    && Blocks.valid(type);
        }

        public String key() {
            return cellKey() + (depth == 0 ? "" : "/" + depth + "/" + ix + "/" + iy + "/" + iz);
        }

        public String cellKey() {
            return x + "," + y + "," + z;
        }
    }

    public record CellState(int x, int y, int z, dev.jayms.net.model.SparseVoxelOctree tree) {
        public void write(DataOutputStream out) throws IOException {
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            tree.write(out);
        }

        public static CellState read(DataInputStream in) throws IOException {
            int x = in.readInt(), y = in.readInt(), z = in.readInt();
            var tree = dev.jayms.net.model.SparseVoxelOctree.read(in);
            if (tree.size() != 16) throw new IOException("Invalid world cell");
            for (var leaf : tree.leaves()) {
                int material = WorldVoxels.decode(leaf.color());
                if (!Blocks.valid(material)
                        || Blocks.isPiece(material)
                        || material == 0
                        || Blocks.isModel(material) && leaf.side() != 16)
                    throw new IOException("Invalid world material");
            }
            if (!new Edit(x, y, z, 0).valid()) throw new IOException("Invalid world cell position");
            return new CellState(x, y, z, tree.freeze());
        }

        public java.util.List<Edit> edits() {
            var out = new java.util.ArrayList<Edit>();
            out.add(new Edit(x, y, z, 0));
            for (var leaf : tree.leaves()) {
                int depth =
                        Integer.numberOfTrailingZeros(16)
                                - Integer.numberOfTrailingZeros(leaf.side());
                out.add(
                        new Edit(
                                x,
                                y,
                                z,
                                Blocks.piece(WorldVoxels.decode(leaf.color()), depth),
                                depth,
                                leaf.x() / leaf.side(),
                                leaf.y() / leaf.side(),
                                leaf.z() / leaf.side(),
                                WorldVoxels.decode(leaf.color()) == Blocks.LED
                                        ? WorldVoxels.lightColor(leaf.color())
                                        : 0));
            }
            return out;
        }
    }

    public record BlockRequest(int requestId, Pose pose, Edit edit, int slot) {
        public BlockRequest(int requestId, Pose pose, Edit edit) {
            this(requestId, pose, edit, 0);
        }

        public void write(DataOutputStream out) throws IOException {
            out.writeInt(requestId);
            pose.write(out);
            edit.write(out);
            out.writeByte(slot);
        }

        public static BlockRequest read(DataInputStream in) throws IOException {
            return new BlockRequest(
                    in.readInt(), Pose.read(in), Edit.read(in), in.readUnsignedByte());
        }
    }

    public static int terrain(int x, int y, int z) {
        return DEFAULT_TERRAIN.block(x, y, z);
    }

    private static final Terrain DEFAULT_TERRAIN = new Terrain(Terrain.DEFAULT_SEED);

    public static float spawnY() {
        return DEFAULT_TERRAIN.column(8, 24).height() + 1.01f;
    }

    public static String readText(DataInputStream in, int maximum) throws IOException {
        int length = in.readUnsignedShort();
        if (length > maximum * 4) throw new IOException("Text too long");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        byte[] framed = new byte[length + 2];
        framed[0] = (byte) (length >>> 8);
        framed[1] = (byte) length;
        System.arraycopy(bytes, 0, framed, 2, length);
        String text = new DataInputStream(new ByteArrayInputStream(framed)).readUTF();
        if (text.length() > maximum) throw new IOException("Text too long");
        return text;
    }

    private Protocol() {}
}
