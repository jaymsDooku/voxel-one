package dev.jayms.net;

import java.io.*;

/** Versioned, fixed-size TCP messages. No Java object deserialization. */
public final class Protocol {
    public static final int MAGIC = 0x564F5831, VERSION = 1, PORT = 25565;
    public static final int MOVE = 1, BLOCK = 2, LEAVE = 3, READY = 4;
    public record Pose(int id, float x, float y, float z, float yaw, float pitch) {
        public void write(DataOutputStream out) throws IOException {
            out.writeInt(id); out.writeFloat(x); out.writeFloat(y); out.writeFloat(z); out.writeFloat(yaw); out.writeFloat(pitch);
        }
        public static Pose read(DataInputStream in) throws IOException {
            return new Pose(in.readInt(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        }
        public boolean valid() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Float.isFinite(yaw) && Float.isFinite(pitch)
                && x >= -63 && x <= 79 && z >= -63 && z <= 79 && y >= -31 && y <= 46 && Math.abs(pitch) <= 89;
        }
    }
    public record Edit(int x, int y, int z, int type) {
        public void write(DataOutputStream out) throws IOException { out.writeInt(x); out.writeInt(y); out.writeInt(z); out.writeInt(type); }
        public static Edit read(DataInputStream in) throws IOException { return new Edit(in.readInt(), in.readInt(), in.readInt(), in.readInt()); }
        public boolean valid() { return x >= -64 && x < 80 && z >= -64 && z < 80 && y >= -32 && y < 48 && type >= 0 && type <= 3; }
    }
    public static int terrain(int x, int y, int z) {
        int height = (int)Math.floor(8 + Math.sin(x * .08) * 6 + Math.cos(z * .06) * 4);
        return y > height ? 0 : y == height ? 1 : y >= height - 2 ? 2 : 3;
    }
    public static float spawnY() { return (float)Math.floor(8 + Math.sin(8 * .08) * 6 + Math.cos(24 * .06) * 4) + 1.01f; }
    private Protocol() {}
}
