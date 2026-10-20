package dev.jayms.music;

import java.io.IOException;
import java.nio.file.*;
import javax.sound.sampled.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SEC_OFFSET;
import static org.lwjgl.openal.ALC10.*;

/** Bounded local PCM decode and an OpenAL playback clock. Native calls stay on render thread. */
public final class MusicTrack implements AutoCloseable {
    public record Decoded(short[] stereo, float[] mono, int rate) {
        public double duration() { return mono.length / (double) rate; }
    }
    public static final int MAX_PCM_BYTES = 32 * 1024 * 1024;
    private long device, context;
    private int buffer, source;
    private Decoded decoded;
    private boolean ended;
    private double queuedPosition = -1;
    public static Decoded decode(Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_PCM_BYTES)
            throw new IOException("Choose a WAV, AIFF or AU file below 32 MB.");
        try (var original = AudioSystem.getAudioInputStream(path.toFile())) {
            var f = original.getFormat();
            if (f.getChannels() < 1 || f.getChannels() > 2 || f.getSampleRate() < 8000 || f.getSampleRate() > 96000)
                throw new IOException("Use mono or stereo audio at 8-96 kHz.");
            var pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                    f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
            try (var input = AudioSystem.getAudioInputStream(pcm, original)) {
                byte[] bytes = input.readNBytes(MAX_PCM_BYTES + 4);
                if (bytes.length > MAX_PCM_BYTES || bytes.length < pcm.getFrameSize())
                    throw new IOException("Audio is empty or exceeds the 32 MB decoded limit.");
                int frames = bytes.length / pcm.getFrameSize();
                short[] stereo = new short[frames * 2]; float[] mono = new float[frames];
                for (int i = 0; i < frames; i++) {
                    int p = i * pcm.getFrameSize();
                    short left = (short)((bytes[p] & 255) | bytes[p+1] << 8);
                    short right = f.getChannels() == 1 ? left : (short)((bytes[p+2] & 255) | bytes[p+3] << 8);
                    stereo[i*2] = left; stereo[i*2+1] = right;
                    mono[i] = (left + right) / 65536f;
                }
                return new Decoded(stereo, mono, Math.round(pcm.getSampleRate()));
            }
        }
    }
    public void load(Decoded data) {
        close();
        try {
            device = alcOpenDevice((java.nio.ByteBuffer)null);
            if (device == 0) throw new IllegalStateException("Audio output unavailable. Connect an output and load again.");
            context = alcCreateContext(device, (java.nio.IntBuffer)null);
            if (context == 0 || !alcMakeContextCurrent(context)) throw new IllegalStateException("Cannot open audio output. Load again.");
            AL.createCapabilities(ALC.createCapabilities(device));
            buffer = alGenBuffers(); source = alGenSources();
            var pcm = BufferUtils.createShortBuffer(data.stereo.length); pcm.put(data.stereo).flip();
            alBufferData(buffer, AL_FORMAT_STEREO16, pcm, data.rate);
            alSourcei(source, AL_BUFFER, buffer);
            alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
            if (alGetError() != AL_NO_ERROR) throw new IllegalStateException("Cannot buffer this audio. Choose another file.");
            decoded = data; ended = false; alSourcePlay(source);
        } catch (RuntimeException | LinkageError e) { close(); throw e; }
    }
    public Decoded data() { return decoded; }
    public boolean playing() { return source != 0 && alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING; }
    public boolean ended() { position(); return ended; }
    public double position() {
        if (source == 0) return 0;
        if (queuedPosition >= 0) return queuedPosition;
        if (alGetSourcei(source, AL_SOURCE_STATE) == AL_STOPPED) ended = true;
        return ended ? decoded.duration() : alGetSourcef(source, AL_SEC_OFFSET);
    }
    public void toggle() {
        if (source == 0) return;
        if (playing()) alSourcePause(source);
        else { if (ended) seek(0); alSourcePlay(source); queuedPosition = -1; }
    }
    public void seek(double seconds) {
        if (source == 0) return;
        boolean resume = playing();
        alSourceRewind(source);
        queuedPosition = Math.max(0, Math.min(decoded.duration() - 1.0 / decoded.rate, seconds));
        alSourcef(source, AL_SEC_OFFSET, (float)queuedPosition);
        ended = false;
        if (resume) { alSourcePlay(source); queuedPosition = -1; }
    }
    @Override public void close() {
        if (source != 0) { alSourceStop(source); alDeleteSources(source); source = 0; }
        if (buffer != 0) { alDeleteBuffers(buffer); buffer = 0; }
        if (context != 0) { alcMakeContextCurrent(0); alcDestroyContext(context); context = 0; AL.setCurrentThread(null); }
        if (device != 0) { alcCloseDevice(device); device = 0; }
        decoded = null; ended = false; queuedPosition = -1;
    }
}
