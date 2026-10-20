package dev.jayms.launcher;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Stable package entry point. New shells are staged for the next hub start. */
public final class Bootstrap {
    public static final URI MANIFEST = URI.create("https://raw.githubusercontent.com/jaymsDooku/voxel-one/experience-catalog/shell.properties");
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
        // One startup uses one preference snapshot. A slow child must not pick
        // up another launcher's later settings change and race its staging.
        Properties preferences=new Properties();
        try {Path file=Launcher.cache().resolve("hub.properties");if(Files.isRegularFile(file)&&Files.size(file)<=4096)
            try(var in=Files.newInputStream(file)){preferences.load(in);}}
        catch(IOException ignored) { }
        boolean updatesAtStartup=Boolean.parseBoolean(preferences.getProperty("updates","true"));
        Updater updater = new Updater(cache, manifest(), true);
        var candidate = updater.candidate(Platform.current());
        boolean started = false;
        boolean stageAllowed = true;
        Process activeShell = null;
        if (candidate != null) {
            try {
                if (!updater.rejected(candidate, Platform.current())) activeShell = start(candidate, cache, args);
                if (activeShell != null) {
                    started = true;
                    // A busy/unwritable pointer does not make a ready shell a bad executable.
                    // Keep its UI, but stage nothing until acknowledgement can be persisted.
                    try { updater.acknowledge(candidate); } catch (IOException unavailable) { stageAllowed = false; }
                } else updater.rejectAndRollback(candidate, Platform.current());
            } catch (Exception failure) {
                stop(activeShell); activeShell = null;
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                try { updater.rejectAndRollback(candidate, Platform.current()); } catch (IOException ignored) { }
            }
        }
        if (!started && !Thread.currentThread().isInterrupted()) {
            var working = updater.acknowledged(Platform.current());
            if (working != null) try {
                activeShell = start(working, cache, args);
                started = activeShell != null;
            } catch (Exception failure) {
                stop(activeShell); activeShell = null;
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
        if (!started) Launcher.main(args);
        // Immediate bundled/cached UI. Do not replace the current process or a locked JAR.
        if(stageAllowed && updatesAtStartup) {
          Thread stage = new Thread(() -> {
            try { updater.update(Platform.current(), text -> {}); } catch (IOException ignored) { }
          }, "hub-shell-stage");
          stage.setDaemon(!started); stage.start();
        }
        if(activeShell!=null)try {activeShell.waitFor();}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    /** Only a visible Hub (or initialized legacy updater) acknowledges startup. */
    static void acknowledgeStartup() throws IOException {
        String value = System.getProperty("voxel.hub.activation");
        if (value == null) return;
        Path ready = Path.of(value).toAbsolutePath().normalize();
        Path cache = Launcher.cache().resolve("hub-shell").toAbsolutePath().normalize();
        if (!ready.startsWith(cache) || !ready.getFileName().toString().equals("ready")
                || !ready.getParent().getFileName().toString().startsWith("activation-"))
            throw new IOException("Invalid shell activation path");
        Files.writeString(ready, "ready", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    private static Process start(Updater.ShellActivation shell, Path cache, String[] args) throws Exception {
        Files.createDirectories(cache);
        Path directory = Files.createTempDirectory(cache, "activation-");
        Path ready = directory.resolve("ready").toAbsolutePath();
        Process process = null;
        boolean accepted = false;
        try {
            List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin",
                    Platform.current() == Platform.WINDOWS ? "javaw.exe" : "java").toString(),
                    "-Dvoxel.hub.preview=" + Boolean.getBoolean("voxel.hub.preview"),
                    "-Duser.home=" + System.getProperty("user.home"), "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
                    "-Dvoxel.hub.activation=" + ready, "-jar", shell.result().client().toString()));
            command.addAll(Arrays.asList(args));
            process = new ProcessBuilder(command).inheritIO().start();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
            // A process that survives 1.5 seconds has not necessarily initialized its shell.
            process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
            while (System.nanoTime() < deadline) {
                if ((!process.isAlive() && process.exitValue() != 0)) return null;
                if (Files.isRegularFile(ready) && Files.size(ready) == 5 && Files.readString(ready).equals("ready")) {
                    accepted = true; return process;
                }
                if (!process.isAlive()) return null;
                Thread.sleep(50);
            }
            return null;
        } finally {
            if (!accepted) stop(process);
            // Cleanup failure must not orphan an accepted shell or mask interruption.
            try { Files.deleteIfExists(ready); Files.deleteIfExists(directory); } catch (IOException ignored) { }
        }
    }
    private static void stop(Process process) {
        if (process == null) return;
        process.descendants().forEach(child -> { child.destroy(); if (child.isAlive()) child.destroyForcibly(); });
        process.destroy(); if (process.isAlive()) process.destroyForcibly();
    }
    private Bootstrap() {}
}
