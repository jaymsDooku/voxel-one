package dev.jayms.recording;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.*;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.*;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.ColorUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Bounded, asynchronous H.264/MP4 writer. Capture timestamps preserve real playback speed. */
public final class VideoRecording implements AutoCloseable {
    private record Frame(byte[] pixels, long millis) {}

    private final ArrayBlockingQueue<Frame> queue = new ArrayBlockingQueue<>(3);
    private final int width, height;
    private final Path temporary, destination;
    private final SeekableByteChannel channel;
    private final Thread worker;
    private volatile boolean stopping, done;
    private volatile long stopMillis, encoded, dropped;
    private volatile String error;
    private long lastSubmitted = -1;

    public VideoRecording(Path directory, int width, int height) throws IOException {
        if (width < 2
                || height < 2
                || width > 1920
                || height > 1080
                || (width & 1) != 0
                || (height & 1) != 0)
            throw new IllegalArgumentException(
                    "Video dimensions must be even and at most 1920 x 1080");
        this.width = width;
        this.height = height;
        Files.createDirectories(directory);
        String stamp =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        temporary = Files.createTempFile(directory, "Voxel-One_" + stamp + "_", ".partial");
        destination =
                temporary.resolveSibling(
                        temporary.getFileName().toString().replace(".partial", ".mp4"));
        try {
            channel = NIOUtils.writableChannel(temporary.toFile());
        } catch (IOException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        worker = new Thread(this::encode, "voxel-video-encoder");
        worker.setDaemon(true);
        worker.start();
    }

    /** Transfers ownership of a tightly packed RGB framebuffer (bottom row first). Never waits. */
    public synchronized boolean offer(byte[] pixels, long millis) {
        if (pixels.length != width * height * 3 || millis < 0)
            throw new IllegalArgumentException("Invalid captured frame");
        if (stopping || done || millis <= lastSubmitted) return false;
        lastSubmitted = millis;
        if (!queue.offer(new Frame(pixels, millis))) {
            dropped++;
            return false;
        }
        return true;
    }

    public synchronized void stop(long millis) {
        if (!stopping) {
            stopMillis = Math.max(millis, lastSubmitted + 1);
            stopping = true;
        }
    }

    private void encode() {
        try {
            MP4Muxer muxer = MP4Muxer.createMP4MuxerToChannel(channel);
            var track =
                    muxer.addVideoTrack(
                            Codec.H264,
                            VideoCodecMeta.createSimpleVideoCodecMeta(
                                    new Size(width, height), ColorSpace.YUV420J));
            H264Encoder encoder = H264Encoder.createH264Encoder();
            encoder.setKeyInterval(30);
            Picture rgb = Picture.create(width, height, ColorSpace.RGB);
            Picture yuv = Picture.create(width, height, ColorSpace.YUV420J);
            var transform = ColorUtil.getTransform(ColorSpace.RGB, ColorSpace.YUV420J);
            ByteBuffer buffer = ByteBuffer.allocate(encoder.estimateBufferSize(yuv));
            Packet previous = null;
            long previousTime = 0;
            while (!stopping || !queue.isEmpty()) {
                Frame frame = queue.poll(100, TimeUnit.MILLISECONDS);
                if (frame == null) continue;
                long time = previous == null ? 0 : Math.max(previousTime + 1, frame.millis);
                if (previous != null) {
                    previous.setDuration(time - previousTime);
                    track.addFrame(previous);
                }
                byte[] data = rgb.getPlaneData(0);
                int row = width * 3;
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < row; x++)
                        data[y * row + x] =
                                (byte) ((frame.pixels[(height - y - 1) * row + x] & 255) - 128);
                transform.transform(rgb, yuv);
                buffer.clear();
                var result = encoder.encodeFrame(yuv, buffer);
                ByteBuffer copy = ByteBuffer.allocate(result.getData().remaining());
                copy.put(result.getData()).flip();
                previous =
                        Packet.createPacket(
                                copy,
                                time,
                                1000,
                                1,
                                encoded++,
                                result.isKeyFrame() ? Packet.FrameType.KEY : Packet.FrameType.INTER,
                                null);
                previousTime = time;
            }
            if (previous != null) {
                previous.setDuration(Math.max(1, stopMillis - previousTime));
                track.addFrame(previous);
                muxer.finish();
                channel.close();
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, destination);
                }
            } else {
                channel.close();
                Files.deleteIfExists(temporary);
            }
        } catch (Exception e) {
            error = "Recording could not be saved. Check free disk space and folder permissions.";
            NIOUtils.closeQuietly(channel);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        } finally {
            queue.clear();
            done = true;
        }
    }

    public boolean done() {
        return done;
    }

    public String error() {
        return error;
    }

    public long encodedFrames() {
        return encoded;
    }

    public long droppedFrames() {
        return dropped;
    }

    public Path path() {
        return destination;
    }

    public void await() throws InterruptedException {
        worker.join();
    }

    @Override
    public void close() throws InterruptedException {
        stop(Math.max(1, lastSubmitted + 67));
        await();
    }
}
