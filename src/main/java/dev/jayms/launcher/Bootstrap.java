package dev.jayms.launcher;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Stable package entry point. New shells are staged for the next hub start. */
public final class Bootstrap {
    public static final URI MANIFEST = URI.create("https://github.com/jaymsDooku/voxel-one/releases/latest/download/shell.properties");
    /** Scoped loopback fixture for package validation; never accepts remote overrides. */
    private static URI manifest() {
        String fixture=System.getProperty("voxel.hub.shellManifest");
        if(fixture==null)return MANIFEST;
        URI uri=URI.create(fixture);
        if(!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort()<1 || uri.getPort()>65535 || uri.getUserInfo()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("Shell validation fixture must use loopback HTTP");
        return uri;
    }
    public static void main(String[] args) {
        Path cache = Launcher.cache().resolve("hub-shell");
        Updater updater = new Updater(cache, manifest(), true);
        Updater.Result shell = updater.installed(Platform.current());
        boolean started = false;
        Process activeShell = null;
        if (shell != null) {
            try {
                List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin",
                        Platform.current() == Platform.WINDOWS ? "javaw.exe" : "java").toString(),
                        "-Dvoxel.hub.preview=" + Boolean.getBoolean("voxel.hub.preview"), "-Duser.home=" + System.getProperty("user.home"), "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-jar", shell.client().toString()));
                command.addAll(Arrays.asList(args));
                Process process = new ProcessBuilder(command).inheritIO().start();
                started = !(process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS) && process.exitValue() != 0);
                if(started)activeShell=process;
            } catch (Exception e) { if (e instanceof InterruptedException) Thread.currentThread().interrupt(); }
        }
        if (!started) Launcher.main(args);
        // Immediate bundled/cached UI. Do not replace the current process or a locked JAR.
        Properties preferences=new Properties();
        try {Path file=Launcher.cache().resolve("hub.properties");if(Files.isRegularFile(file)&&Files.size(file)<=4096)
            try(var in=Files.newInputStream(file)){preferences.load(in);}}
        catch(IOException ignored) { }
        if(Boolean.parseBoolean(preferences.getProperty("updates","true"))) {
          Thread stage = new Thread(() -> {
            try { updater.update(Platform.current(), text -> {}); } catch (IOException ignored) { }
          }, "hub-shell-stage");
          stage.setDaemon(!started); stage.start();
        }
        if(activeShell!=null)try {activeShell.waitFor();}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private Bootstrap() {}
}
