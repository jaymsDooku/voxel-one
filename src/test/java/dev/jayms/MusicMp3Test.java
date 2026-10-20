package dev.jayms;

import dev.jayms.music.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicMp3Test {
    @Test void cbrStereoAndVbrMonoDecodeThroughJavaSound() throws Exception {
        for (String name : new String[]{"cbr-stereo.mp3", "vbr-mono.mp3"}) {
            Path file = Path.of("src/test/resources/music", name);
            try (var stream = AudioSystem.getAudioInputStream(file.toFile())) {
                assertEquals(name.startsWith("cbr") ? 2 : 1, stream.getFormat().getChannels());
            }
            var data = MusicTrack.decode(file);
            assertEquals(44100, data.rate());
            assertEquals(6, data.duration(), .15, "Encoder delay/padding may remain");
            var analysis = new MusicAnalysis();
            MusicAnalysis.Bands bands = null;
            for (int i = 1; i <= 100; i++) bands = analysis.sample(data.stereo(), data.rate(), i / 60.0, 1 / 60.0, true, 1);
            assertTrue(bands.bass() > .4 && bands.mid() < .03, name + " bass passage: " + bands);
            analysis.reset();
            for (int i = 1; i <= 100; i++) bands = analysis.sample(data.stereo(), data.rate(), 3.5 + i / 60.0, 1 / 60.0, true, 1);
            assertTrue(bands.mid() > .4 && bands.bass() < .03, name + " mid passage: " + bands);
            if (name.startsWith("vbr")) for (int i = 0; i < data.stereo().length; i += 2)
                assertEquals(data.stereo()[i], data.stereo()[i + 1], "Mono duplicates to stereo");
        }
    }

    @Test void invalidMp3AndLimitsAllowAnotherDecode(@TempDir Path dir) throws Exception {
        Path invalid = dir.resolve("invalid.mp3");
        Files.writeString(invalid, "ID3 corrupt MP3, no audio frames");
        assertThrows(Exception.class, () -> MusicTrack.decode(invalid));
        Files.write(invalid, new byte[0]);
        assertThrows(Exception.class, () -> MusicTrack.decode(invalid));
        Path large = dir.resolve("large.mp3");
        try (var file = new RandomAccessFile(large.toFile(), "rw")) { file.setLength(MusicTrack.MAX_PCM_BYTES + 1L); }
        assertThrows(IOException.class, () -> MusicTrack.decode(large));
        assertTrue(MusicTrack.decode(Path.of("src/test/resources/music/cbr-stereo.mp3")).duration() > 5);
    }

    @Test void compressedInputCannotBypassDecodedLimit(@TempDir Path dir) throws Exception {
        byte[] clip = Files.readAllBytes(Path.of("src/test/resources/music/cbr-stereo.mp3"));
        Path longTrack = dir.resolve("long.mp3");
        try (var out = Files.newOutputStream(longTrack)) { for (int i = 0; i < 34; i++) out.write(clip); }
        assertTrue(Files.size(longTrack) < MusicTrack.MAX_PCM_BYTES);
        var error = assertThrows(IOException.class, () -> MusicTrack.decode(longTrack));
        assertTrue(error.getMessage().contains("decoded limit"));
    }

    @Test void existingPcmContainersStillDecode(@TempDir Path dir) throws Exception {
        for (var type : new AudioFileFormat.Type[]{AudioFileFormat.Type.WAVE, AudioFileFormat.Type.AIFF, AudioFileFormat.Type.AU}) {
            byte[] pcm = new byte[44100 * 2];
            for (int i = 0; i < 44100; i++) { short v = (short)(12000 * Math.sin(2 * Math.PI * 100 * i / 44100)); pcm[i * 2] = (byte)v; pcm[i * 2 + 1] = (byte)(v >> 8); }
            Path file = dir.resolve("tone." + type.getExtension());
            try (var stream = new AudioInputStream(new ByteArrayInputStream(pcm), new AudioFormat(44100, 16, 1, true, false), 44100)) {
                assertTrue(AudioSystem.write(stream, type, file.toFile()) > 0);
            }
            var data = MusicTrack.decode(file);
            assertEquals(1, data.duration(), .0001);
            assertTrue(java.util.stream.IntStream.range(0, data.stereo().length).anyMatch(i -> data.stereo()[i] != 0));
        }
    }
}
