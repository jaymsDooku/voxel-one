import dev.jayms.music.MusicTrack;
import java.nio.file.Path;

/** Confirms SPI discovery using only the shipped shaded client JAR. */
public class MusicMp3DecodeSmoke {
    public static void main(String[] args) throws Exception {
        for (String name : new String[]{"cbr-stereo.mp3", "vbr-mono.mp3"}) {
            var data = MusicTrack.decode(Path.of("src/test/resources/music", name));
            if (data.rate() != 44100 || Math.abs(data.duration() - 6) > .15)
                throw new AssertionError("Wrong PCM for " + name);
            System.out.printf(java.util.Locale.ROOT, "PASS: shaded JAR %s -> %d Hz, %.3f s PCM%n", name, data.rate(), data.duration());
        }
    }
}
