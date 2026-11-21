import dev.jayms.net.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

/** Checks fixture dependencies against the actual, unchanged model codec and mesher. */
class CheckFixtureModels {
    public static void main(String[] args) throws Exception {
        long bytes = 0, quads = 0;
        int maximumBytes = 0, maximumQuads = 0;
        int count = Integer.parseInt(args[1]);
        try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))))) {
            if (!new String(in.readNBytes(8), java.nio.charset.StandardCharsets.US_ASCII).equals("VXBF0001"))
                throw new IOException("Wrong fixture magic");
            int header = in.readInt();
            if (header < 1 || header > 4096) throw new IOException("Header limit");
            in.skipNBytes(header);
            for (int id = 0; id < count; id++) {
                if (in.readUnsignedShort() != id) throw new IOException("Dependency order");
                int size = in.readUnsignedByte(), length = in.readUnsignedByte();
                String name = new String(in.readNBytes(length), java.nio.charset.StandardCharsets.US_ASCII);
                var tree = new SparseVoxelOctree(size);
                for (int v = 0; v < size * size * size; v++)
                    tree.set(v % size, (v / size) % size, v / (size * size), in.readInt());
                var definition = new ModelDefinition(name, tree.freeze());
                var storage = new ByteArrayOutputStream();
                definition.write(new DataOutputStream(storage));
                var restored = ModelDefinition.read(new DataInputStream(new ByteArrayInputStream(storage.toByteArray())));
                if (!definition.fingerprint().equals(restored.fingerprint())) throw new IOException("Model roundtrip");
                int faces = ModelMesher.mesh(restored.voxels()).quads();
                if (storage.size() > ModelDefinition.MAX_BYTES + 4 || faces > ModelMesher.MAX_QUADS)
                    throw new IOException("Existing model limit");
                bytes += storage.size(); quads += faces;
                maximumBytes = Math.max(maximumBytes, storage.size());
                maximumQuads = Math.max(maximumQuads, faces);
            }
        }
        System.out.printf("{\"models\":%d,\"production_codec_bytes\":%d,\"production_mesh_quads\":%d,\"largest_model_bytes\":%d,\"largest_model_quads\":%d}%n",
                count, bytes, quads, maximumBytes, maximumQuads);
    }
}
