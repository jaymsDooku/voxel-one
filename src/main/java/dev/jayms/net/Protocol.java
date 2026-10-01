package dev.jayms.net;

import java.io.*;

/** Protocol 2 requires authenticated TLS before world state is sent. */
public final class Protocol {
    public static final int MAGIC = 0x564F5831, VERSION = 2, PORT = 25565;
    public static final int MOVE = 1, BLOCK = 2, LEAVE = 3, READY = 4, JOIN = 5, EDIT_RESULT = 6;
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
                    && x >= -63.7f
                    && x <= 79.701f
                    && z >= -63.7f
                    && z <= 79.701f
                    && y >= -32
                    && y <= 46.201f
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
            return x >= -64 && x < 80 && z >= -64 && z < 80 && y >= -32 && y < 48 && type >= 0
                    && type <= 3;
        }

        public String key() {
            return x + "," + y + "," + z;
        }
    }

    public record BlockRequest(int requestId, Pose pose, Edit edit) {
        public void write(DataOutputStream out) throws IOException {
            out.writeInt(requestId);
            pose.write(out);
            edit.write(out);
        }

        public static BlockRequest read(DataInputStream in) throws IOException {
            return new BlockRequest(in.readInt(), Pose.read(in), Edit.read(in));
        }
    }

    public static int terrain(int x, int y, int z) {
        int height = (int) Math.floor(8 + Math.sin(x * .08) * 6 + Math.cos(z * .06) * 4);
        return y > height ? 0 : y == height ? 1 : y >= height - 2 ? 2 : 3;
    }

    public static float spawnY() {
        return (float) Math.floor(8 + Math.sin(8 * .08) * 6 + Math.cos(24 * .06) * 4) + 1.01f;
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
