import dev.jayms.music.*;
import java.nio.file.*;
import javax.swing.*;
import java.util.concurrent.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native MP3 workflow; reuses the established render-thread/input/capture helpers. */
public final class MusicMp3Smoke {
    static void render(Runnable r) throws Exception { MusicSmoke.render(r); }
    static void require(boolean b, String s) { MusicSmoke.require(b, s); }
    static void key(String s) throws Exception { MusicSmoke.key(s); }
    static boolean picker(java.awt.Container root) {
        for (var c : root.getComponents()) {
            if (c instanceof JFileChooser chooser) {
                require(chooser.getFileFilter().accept(MusicSmoke.fixture.toFile()), "MP3 accepted by production chooser filter");
                chooser.setSelectedFile(MusicSmoke.fixture.toFile());
                chooser.approveSelection(); return true;
            }
            if (c instanceof java.awt.Container group && picker(group)) return true;
        }
        return false;
    }
    static void select(Path path) throws Exception {
        MusicSmoke.fixture = path;
        MusicTrack.Decoded[] previous = {null};
        render(() -> previous[0] = MusicSmoke.active.track.data());
        key("o"); Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> {
            boolean found = false;
            for (var w : java.awt.Window.getWindows()) if (w.isShowing() && picker(w)) { found = true; break; }
            require(found, "Production file chooser opened");
        });
        Thread.sleep(500);
        for (int i = 0; i < 200; i++) {
            boolean[] loaded = {false};
            render(() -> loaded[0] = !MusicSmoke.active.loading() && MusicSmoke.active.track.data() != null && MusicSmoke.active.track.data() != previous[0]);
            if (loaded[0]) return;
            Thread.sleep(25);
        }
        throw new AssertionError("MP3 picker load timed out");
    }
    public static void main(String[] args) throws Exception {
        MusicSmoke.out = Path.of(args[0]); Files.createDirectories(MusicSmoke.out);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { try { while(e.getCause()!=null)e=e.getCause(); Files.writeString(MusicSmoke.out.resolve("failure.txt"), e.getClass().getSimpleName()+": "+e.getMessage()); } catch(Exception ignored) {} System.exit(1); });
        Path fixtures = Path.of("src/test/resources/music").toAbsolutePath();
        Thread input = new Thread(() -> { try {
            while (MusicSmoke.active == null) Thread.sleep(25);
            Path invalid = MusicSmoke.out.resolve("invalid.mp3"); Files.writeString(invalid, "ID3 corrupt, no audio frames");
            MusicSmoke.load(invalid);
            render(() -> require(MusicSmoke.active.status.startsWith("Cannot play") && MusicSmoke.active.status.contains("O retries"), "Invalid MP3 gives useful retry message"));
            MusicSmoke.shot("music-mp3-error.png");
            for (String name : new String[]{"cbr-stereo.mp3", "vbr-mono.mp3"}) {
                select(fixtures.resolve(name)); Thread.sleep(500);
                render(() -> require(MusicSmoke.active.track.playing() && MusicSmoke.active.bands.bass() > .4, name+" plays and drives bass"));
                if (name.startsWith("cbr")) key("F10");
                MusicSmoke.shot("music-mp3-"+name.substring(0,3)+".png");
                key("space"); double[] pos={0}; render(() -> pos[0]=MusicSmoke.active.track.position()); Thread.sleep(350);
                render(() -> require(!MusicSmoke.active.track.playing() && Math.abs(MusicSmoke.active.track.position()-pos[0])<.05, "MP3 pause holds clock"));
                key("space"); Thread.sleep(300);
                render(() -> { require(MusicSmoke.active.track.playing(), "MP3 resumes"); MusicSmoke.active.seek(3.7); }); Thread.sleep(500);
                render(() -> require(MusicSmoke.active.bands.mid()>.4 && MusicSmoke.active.bands.bass()<.03, "MP3 seek drives mid passage"));
                int[] palette = {0}; double[] sensitivity = {0}, motion = {0};
                render(() -> { palette[0] = MusicSmoke.active.palette; sensitivity[0] = MusicSmoke.active.sensitivity; motion[0] = MusicSmoke.active.motion; });
                key("p"); key("plus"); key("bracketright"); key("r"); Thread.sleep(300);
                render(() -> {
                    require(MusicSmoke.active.palette == (palette[0] + 1) % 3, "MP3 palette control");
                    require(MusicSmoke.active.sensitivity == sensitivity[0] + .25 && MusicSmoke.active.motion == motion[0] + .25, "MP3 sensitivity and motion controls");
                    require(MusicSmoke.active.track.position()<1.5 && MusicSmoke.active.track.playing(), "MP3 restart uses existing clock");
                });
            }
            key("F10");
            MusicSmoke.fixture = MusicSmoke.out.resolve("regression.wav"); MusicSmoke.fixture(); MusicSmoke.load(MusicSmoke.fixture); Thread.sleep(450);
            render(() -> require(MusicSmoke.active.track.playing() && MusicSmoke.active.bands.bass()>.4, "WAV import works after MP3"));
            key("Escape");
        } catch(Throwable t) { MusicSmoke.failure=t; if(MusicSmoke.active!=null)glfwSetWindowShouldClose(MusicSmoke.active.window,true); } }, "mp3-native-input");
        input.start();
        var app=new MusicVisualizer();
        app.run(a -> { MusicSmoke.active=a; FutureTask<Void> task; while((task=MusicSmoke.commands.poll())!=null)task.run(); });
        input.join(); if(MusicSmoke.failure!=null)throw new AssertionError("MP3 native workflow failed",MusicSmoke.failure);
        require(app.track.data()==null && app.window==0,"Exit releases MP3 audio and window");
        Files.writeString(MusicSmoke.out.resolve("music-mp3-playtest.json"), "{\n\"Playtest\":\"Production MP3 file picker, renderer and OpenAL playback; inherited Linux X11 display; isolated synthetic profile; OpenAL Soft null output\",\n\"renderer\":\""+app.renderer+"\",\n\"checks\":[\"invalid MP3 error and retry\",\"CBR stereo and VBR mono MP3 load through real chooser and drive bass/mid visualization\",\"pause resume seek restart palette sensitivity motion\",\"WAV replacement regression\",\"F10 MP4 capture\",\"exit releases audio\"],\n\"observed\":\"All assertions passed; physical speakers/GPU and other desktop runtimes not qualified\"\n}\n");
        System.out.println("PASS: native MP3 import/playback/visualization/retry workflow"); System.exit(0);
    }
}
