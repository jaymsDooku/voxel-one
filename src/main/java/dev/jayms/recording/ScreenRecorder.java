package dev.jayms.recording;

import static org.lwjgl.opengl.GL33.*;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/** Game framebuffer capture. All GL operations stay on the engine's render thread. */
public final class ScreenRecorder implements AutoCloseable {
    private final Path directory;
    private VideoRecording recording;
    private ByteBuffer pixels;
    private int framebuffer, texture, width, height;
    private long started, nextCapture, messageUntil;
    private boolean active;
    private String message = "";

    public ScreenRecorder(Path directory) {
        this.directory = directory;
    }

    public boolean active(){return active;}

    public void toggle(int sourceWidth, int sourceHeight) {
        if (active) {
            active = false;
            recording.stop(elapsedMillis());
            message = "Saving recording...";
            return;
        }
        if (recording != null && !recording.done()) return;
        if (sourceWidth < 2 || sourceHeight < 2) return;
        releaseCapture();
        recording = null;
        try {
            double scale = Math.min(1, Math.min(960.0 / sourceWidth, 540.0 / sourceHeight));
            width = Math.max(2, (int) (sourceWidth * scale) & ~1);
            height = Math.max(2, (int) (sourceHeight * scale) & ~1);
            allocateCapture();
            recording = new VideoRecording(directory, width, height);
            started = System.nanoTime();
            nextCapture = started;
            active = true;
            message = "";
        } catch (Exception e) {
            releaseCapture();
            message = "Cannot start recording. Check your recordings folder.";
            messageUntil = System.nanoTime() + 10_000_000_000L;
        }
    }

    private void allocateCapture() {
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
                oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGB8,
                    width,
                    height,
                    0,
                    GL_RGB,
                    GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(
                    GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Recording framebuffer unavailable");
            pixels = MemoryUtil.memAlloc(width * height * 3);
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
            glBindTexture(GL_TEXTURE_2D, oldTexture);
        }
    }

    /** Call after rendering world + HUD, before swapping the window's buffers. */
    public void capture(int sourceWidth, int sourceHeight) {
        if (!active) return;
        if (recording.done()) {
            active = false;
            return;
        }
        long now = System.nanoTime();
        if (now < nextCapture || sourceWidth < 2 || sourceHeight < 2) return;
        nextCapture = now + 1_000_000_000L / 15;
        int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
                draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int pack = glGetInteger(GL_PACK_ALIGNMENT);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        float[] clear = new float[4];
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        try {
            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            double scale = Math.min((double) width / sourceWidth, (double) height / sourceHeight);
            int w = Math.max(1, (int) (sourceWidth * scale)),
                    h = Math.max(1, (int) (sourceHeight * scale));
            int x = (width - w) / 2, y = (height - h) / 2;
            glBlitFramebuffer(
                    0,
                    0,
                    sourceWidth,
                    sourceHeight,
                    x,
                    y,
                    x + w,
                    y + h,
                    GL_COLOR_BUFFER_BIT,
                    GL_LINEAR);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            pixels.clear();
            glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixels);
            byte[] data = new byte[pixels.capacity()];
            pixels.get(data);
            recording.offer(data, Math.max(0, (now - started) / 1_000_000));
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glPixelStorei(GL_PACK_ALIGNMENT, pack);
            glClearColor(clear[0], clear[1], clear[2], clear[3]);
            if (scissor) glEnable(GL_SCISSOR_TEST);
        }
    }

    public String status(String shortcut) {
        if (recording != null && recording.done()) {
            message =
                    recording.error() != null
                            ? recording.error()
                            : recording.encodedFrames() == 0
                                    ? "Recording cancelled: no frames"
                                    : "Recording saved in .voxel-one/recordings";
            recording = null;
            releaseCapture();
            messageUntil = System.nanoTime() + 10_000_000_000L;
        }
        if (active) {
            long seconds = elapsedMillis() / 1000;
            return String.format("REC %02d:%02d | %s STOP", seconds / 60, seconds % 60, shortcut);
        }
        if (recording != null) return "Saving recording...";
        return System.nanoTime() < messageUntil ? message : "";
    }

    private long elapsedMillis() {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private void releaseCapture() {
        if (pixels != null) {
            MemoryUtil.memFree(pixels);
            pixels = null;
        }
        if (framebuffer != 0) {
            glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (texture != 0) {
            glDeleteTextures(texture);
            texture = 0;
        }
    }

    @Override
    public void close() throws InterruptedException {
        if (recording != null) {
            recording.stop(elapsedMillis());
            recording.await();
        }
        releaseCapture();
    }
}
