import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;

import java.nio.file.*;

/** Decode and inspect a recording independently of the engine's capture loop. */
public final class RecordingProbe {
    public static void main(String[] args) throws Exception {
        try (var channel = NIOUtils.readableChannel(Path.of(args[0]).toFile())) {
            var grab = FrameGrab.createFrameGrab(channel);
            var meta = grab.getVideoTrack().getMeta();
            int count = 0;
            while (grab.getNativeFrame() != null) count++;
            if (count != meta.getTotalFrames() || count < 2 || meta.getTotalDuration() <= 0)
                throw new AssertionError("Incomplete video");
            var size = meta.getVideoCodecMeta().getSize();
            System.out.printf(
                    "{\"codec\":\"%s\",\"width\":%d,\"height\":%d,\"durationSeconds\":%.3f,\"decodedFrames\":%d,\"status\":\"passed\"}%n",
                    meta.getCodec(),
                    size.getWidth(),
                    size.getHeight(),
                    meta.getTotalDuration(),
                    count);
        }
    }
}
