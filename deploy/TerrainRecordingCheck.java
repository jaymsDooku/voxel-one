import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;

import java.nio.file.*;

public class TerrainRecordingCheck {
    static long checksum(Picture p) {
        long h = 0;
        for (byte[] plane : p.getData())
            if (plane != null) for (int i = 0; i < plane.length; i += 97) h = h * 31 + plane[i];
        return h;
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        try (var channel = NIOUtils.readableChannel(path.toFile())) {
            var grab = FrameGrab.createFrameGrab(channel);
            var meta = grab.getVideoTrack().getMeta();
            var first = grab.getNativeFrame();
            long start = checksum(first);
            grab.seekToSecondPrecise(meta.getTotalDuration() - .25);
            var last = grab.getNativeFrame();
            if (meta.getTotalFrames() < 10
                    || meta.getTotalDuration() < 3
                    || last == null
                    || start == checksum(last))
                throw new AssertionError("Camera recording invalid or unchanged");
            String result =
                    String.format(
                            java.util.Locale.ROOT,
                            "{\"codec\":\"%s\",\"frames\":%d,\"durationSeconds\":%.3f,\"width\":%d,\"height\":%d,\"bytes\":%d,\"seekable\":true,\"cameraFramesDiffer\":true}\n",
                            meta.getCodec(),
                            meta.getTotalFrames(),
                            meta.getTotalDuration(),
                            first.getCroppedWidth(),
                            first.getCroppedHeight(),
                            Files.size(path));
            Files.writeString(Path.of(args[1]), result);
            System.out.print(result);
        }
    }
}
