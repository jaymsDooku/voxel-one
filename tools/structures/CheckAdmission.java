import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Decode-only prerequisite gate. Never creates a world or qualifies performance. */
public final class CheckAdmission {
    public static void main(String[] args) {
        int status;
        try {
            if (args.length != 2 || !args[1].matches("[0-9a-f]{64}")) {
                inputError("Expected package path and trusted SHA-256");
                status = 1;
            } else status = inspect(Path.of(args[0]), args[1]);
        } catch (Exception failure) {
            // File-system errors may contain private paths. Never print exception messages/stacks.
            inputError("Cannot inspect input");
            status = 1;
        }
        System.exit(status);
    }

    private static int inspect(Path path, String expected) throws Exception {
        if (!Files.isRegularFile(path)) {
            inputError("Input is not a regular file");
            return 1;
        }
        long size = Files.size(path);
        if (size > StructurePackage.COMPRESSED) {
            System.out.println("{\"status\":\"rejected\",\"admitted\":false,\"identity_verified\":false,"
                    + "\"reason\":\"compressed_limit\",\"qualification\":\"NOT QUALIFIED\"}");
            return 2;
        }
        // Hash and decode the same bounded byte snapshot, even if the path later changes.
        byte[] bytes = new byte[(int) size];
        try (var in = Files.newInputStream(path)) {
            if (in.readNBytes(bytes, 0, bytes.length) != bytes.length || in.read() != -1) {
                inputError("Input changed while reading");
                return 1;
            }
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!expected.equals(actual)) {
            inputError("Package identity mismatch");
            return 1;
        }
        String identity = "\"identity_verified\":true,\"package_sha256\":\"" + actual
                + "\",\"compressed_bytes\":" + bytes.length;
        try {
            var asset = StructurePackage.decode(bytes);
            System.out.println("{\"status\":\"admitted\",\"admitted\":true," + identity
                    + ",\"cells\":" + asset.cells.size() + ",\"models\":" + asset.models.size()
                    + ",\"geometry_leaves\":" + asset.leafCount + ",\"instances\":" + asset.instanceCount
                    + ",\"qualification\":\"NOT QUALIFIED\"}");
            return 0;
        } catch (IOException rejected) {
            // A cap may reject before checksum/trailing-data validation: this does not certify
            // that rejected input is well formed. Report only the first decoder rejection.
            String reason = switch (String.valueOf(rejected.getMessage())) {
                case "Experimental dependency surface cap" -> "dependency_surface_cap";
                case "Experimental geometry cap" -> "geometry_cap";
                case "Experimental instance cap" -> "instance_cap";
                case "Experimental instance surface cap" -> "instance_surface_cap";
                default -> "decoder_rejected";
            };
            System.out.println("{\"status\":\"rejected\",\"admitted\":false," + identity
                    + ",\"reason\":\"" + reason + "\",\"qualification\":\"NOT QUALIFIED\"}");
            return 2;
        }
    }

    private static void inputError(String reason) {
        System.out.println("{\"status\":\"input_error\",\"admitted\":false,\"identity_verified\":false,"
                + "\"reason\":\"" + reason + "\",\"qualification\":\"NOT QUALIFIED\"}");
    }
}
