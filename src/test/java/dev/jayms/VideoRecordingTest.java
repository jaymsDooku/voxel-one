package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.recording.VideoRecording;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class VideoRecordingTest {
    @TempDir Path directory;

    private long fileCount() throws Exception {
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }

    private byte[] frame() {
        byte[] pixels = new byte[96 * 64 * 3];
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 96; x++) pixels[(y * 96 + x) * 3 + (y < 32 ? 2 : 0)] = (byte) 255;
        return pixels;
    }

    @Test
    void mp4DecodesWithUprightColoursAndRealCaptureTiming() throws Exception {
        var recording = new VideoRecording(directory, 96, 64);
        assertTrue(recording.offer(frame(), 10));
        assertTrue(recording.offer(frame(), 1500));
        assertTrue(recording.offer(frame(), 2000));
        recording.stop(3000);
        recording.await();
        assertNull(recording.error());
        assertTrue(recording.done());
        assertEquals(3, recording.encodedFrames());
        assertTrue(Files.isRegularFile(recording.path()));
        assertEquals(1, fileCount());
        try (var channel = NIOUtils.readableChannel(recording.path().toFile())) {
            var grab = FrameGrab.createFrameGrab(channel);
            var meta = grab.getVideoTrack().getMeta();
            assertEquals(Codec.H264, meta.getCodec());
            assertEquals(3, meta.getTotalFrames());
            assertEquals(3, meta.getTotalDuration(), .002);
            var decoded = grab.getNativeFrame();
            Picture rgb = Picture.create(decoded.getWidth(), decoded.getHeight(), ColorSpace.RGB);
            ColorUtil.getTransform(decoded.getColor(), ColorSpace.RGB).transform(decoded, rgb);
            byte[] data = rgb.getPlaneData(0);
            assertTrue(data[(8 * 96 + 8) * 3] + 128 > 200, "Top must be red");
            assertTrue(data[(55 * 96 + 8) * 3 + 2] + 128 > 200, "Bottom must be blue");
            grab.seekToSecondPrecise(2.5);
            assertNotNull(grab.getNativeFrame(), "Recording must be seekable");
        }
    }

    @Test
    void emptyRecordingLeavesNoPartialFile() throws Exception {
        var recording = new VideoRecording(directory, 96, 64);
        recording.stop(500);
        recording.await();
        assertNull(recording.error());
        assertEquals(0, fileCount());
    }

    @Test
    void stoppingRejectsAdditionalFramesAndCloseFinalizes() throws Exception {
        var recording = new VideoRecording(directory, 96, 64);
        assertTrue(recording.offer(frame(), 0));
        recording.stop(1000);
        assertFalse(recording.offer(frame(), 1001));
        recording.close();
        assertTrue(Files.exists(recording.path()));
    }

    @Test
    void boundedQueueDropsFramesWithoutShorteningPlayback() throws Exception {
        var recording = new VideoRecording(directory, 96, 64);
        byte[] pixels = frame();
        for (int i = 0; i < 1000; i++) recording.offer(pixels, i);
        recording.stop(1000);
        recording.await();
        assertTrue(recording.droppedFrames() > 0);
        assertNull(recording.error());
        try (var channel = NIOUtils.readableChannel(recording.path().toFile())) {
            assertEquals(
                    1,
                    FrameGrab.createFrameGrab(channel).getVideoTrack().getMeta().getTotalDuration(),
                    .002);
        }
    }

    @Test
    void invalidFramesAndOutOfOrderTimesAreRejected() throws Exception {
        var recording = new VideoRecording(directory, 96, 64);
        assertThrows(IllegalArgumentException.class, () -> recording.offer(new byte[3], 0));
        assertTrue(recording.offer(frame(), 10));
        assertFalse(recording.offer(frame(), 10));
        assertFalse(recording.offer(frame(), 5));
        recording.close();
        assertNull(recording.error());
    }

    @Test
    void invalidDimensionsAreRejectedBeforeCreatingFiles() {
        assertThrows(IllegalArgumentException.class, () -> new VideoRecording(directory, 97, 64));
        assertThrows(IllegalArgumentException.class, () -> new VideoRecording(directory, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new VideoRecording(directory, 1922, 64));
    }

    @Test
    void unavailableFolderReportsIoError() throws Exception {
        Path file = directory.resolve("not-a-folder");
        Files.writeString(file, "occupied");
        assertThrows(java.io.IOException.class, () -> new VideoRecording(file, 96, 64));
    }
}
