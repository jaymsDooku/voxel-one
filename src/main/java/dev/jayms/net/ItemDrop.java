package dev.jayms.net;

import java.io.*;

public record ItemDrop(int id, int type, int count, float x, float y, float z) {
    public void write(DataOutputStream out) throws IOException {
        out.writeInt(id);
        out.writeByte(type);
        out.writeByte(count);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
    }

    public static ItemDrop read(DataInputStream in) throws IOException {
        ItemDrop d =
                new ItemDrop(
                        in.readInt(),
                        in.readUnsignedByte(),
                        in.readUnsignedByte(),
                        in.readFloat(),
                        in.readFloat(),
                        in.readFloat());
        if (d.id <= 0
                || d.type <= 0
                || !Blocks.valid(d.type)
                || d.count > 64
                || !Float.isFinite(d.x)
                || !Float.isFinite(d.y)
                || !Float.isFinite(d.z)) throw new IOException("Invalid item drop");
        return d;
    }
}
