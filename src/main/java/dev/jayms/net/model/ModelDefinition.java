package dev.jayms.net.model;

import dev.jayms.net.Protocol;

import java.io.*;
import java.security.*;
import java.util.HexFormat;

public record ModelDefinition(String name, SparseVoxelOctree voxels) {
    public static final int MAX_BYTES = 200000;

    public ModelDefinition {
        if (name != null) name = name.trim();
        if (voxels == null) throw new IllegalArgumentException("Missing voxel data");
        if (name == null || !name.matches("[A-Za-z0-9 _-]{1,32}"))
            throw new IllegalArgumentException(
                    "Name must contain 1-32 letters, numbers, spaces, underscores or dashes");
    }

    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] bytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF(name);
            voxels.write(out);
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Model is too complex to share");
        return bytes.toByteArray();
    }

    public void write(DataOutputStream out) throws IOException {
        byte[] bytes = bytes();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static ModelDefinition read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > MAX_BYTES) throw new IOException("Invalid model packet size");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        try (var data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String name = Protocol.readText(data, 32);
            var voxels = SparseVoxelOctree.read(data);
            if (data.available() != 0) throw new IOException("Trailing model data");
            try {
                return new ModelDefinition(name, voxels);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }
}
