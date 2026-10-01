package dev.jayms.net;

import java.io.*;

/** Protocol 4 shares immutable microvoxel models over authenticated TLS. */
public final class Protocol {
    public static final int MAGIC = 0x564F5831, VERSION = 4, PORT = 25565;
    public static final int MOVE = 1, BLOCK = 2, LEAVE = 3, READY = 4, JOIN = 5, EDIT_RESULT = 6;
    public static final int INVENTORY = 7, DROP = 8, SWAP = 9, RESPAWN = 10;
    public static final int MODEL_CREATE = 11, MODEL_DEFINE = 12, MODEL_RESULT = 13;
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
            boolean flying) {
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
                    in.readBoolean());
        }

        public boolean valid() {
            return Float.isFinite(x)
                    && Float.isFinite(y)
                    && Float.isFinite(z)
                    && Float.isFinite(yaw)
                    && Float.isFinite(pitch)
                    && Float.isFinite(walkPhase)
                    && Float.isFinite(walkAmount)
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

    public record Edit(int x, int y, int z, int type) {
        public void write(DataOutputStream out) throws IOException {
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.writeInt(type);
        }

        public static Edit read(DataInputStream in) throws IOException {
            return new Edit(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }

        public boolean valid() {
            return Math.abs((long) x) <= Terrain.LIMIT
                    && Math.abs((long) z) <= Terrain.LIMIT
                    && y >= Terrain.MIN_Y
                    && y <= Terrain.MAX_Y
                    && Blocks.valid(type);
        }

        public String key() {
            return x + "," + y + "," + z;
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
