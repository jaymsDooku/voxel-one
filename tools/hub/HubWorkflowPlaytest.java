import dev.jayms.launcher.*;
import java.awt.*;
import java.nio.file.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import com.sun.net.httpserver.HttpServer;

public class HubWorkflowPlaytest {
    static HubFrame frame;
    static Path evidence;
    static String gameWindow;
    static void capture(String name) throws Exception { Thread.sleep(500); ImageIO.write(new Robot().createScreenCapture(frame.getBounds()), "png", evidence.resolve(name+".png").toFile()); }
    static void click(String text) throws Exception { SwingUtilities.invokeAndWait(() -> {
        for (Component c : HubPlaytest.descendants(frame)) if (c instanceof AbstractButton b && b.getText().equals(text)) { b.doClick(); return; }
        throw new AssertionError("Button missing: " + text);
    }); }
    static boolean textPresent(String text) throws Exception { AtomicBoolean found = new AtomicBoolean(); SwingUtilities.invokeAndWait(() -> {
        for (Component c : HubPlaytest.descendants(frame)) if (c instanceof JLabel l && l.getText() != null && l.getText().contains(text) || c instanceof JTextArea a && a.getText().contains(text)) found.set(true);
    }); return found.get(); }
    static void focusGame() throws Exception {
        boolean focused = false;
        for (int attempt=0;attempt<150 && !focused;attempt++) {
        for (ProcessHandle child : ProcessHandle.current().descendants().toList()) {
            Process search = new ProcessBuilder("xdotool", "search", "--all", "--onlyvisible", "--pid", Long.toString(child.pid()), "--name", "^Voxel One( .*)?$").start();
            String windows = new String(search.getInputStream().readAllBytes()).trim(); search.waitFor();
            if (!windows.isEmpty()) {
                String id = windows.split("\s+")[0];
                Process title=new ProcessBuilder("xdotool","getwindowname",id).start();
                String name=new String(title.getInputStream().readAllBytes());title.waitFor();
                if(name.contains("Voxel One Hub"))throw new AssertionError("Native capture selected hub window");
                new ProcessBuilder("xdotool", "windowraise", id, "windowfocus", id).start().waitFor(); gameWindow = id; focused = true;
            }
        }
        if (!focused) Thread.sleep(400);
        }
        if (!focused) throw new AssertionError("No visible native game window found");
        new ProcessBuilder("xdotool", "windowsize", gameWindow, "900", "600").start().waitFor();
        Thread.sleep(2000);
    }
    static void recordGame(Path profile, String name) throws Exception {
        if (Boolean.getBoolean("hub.playtest.skipRecording")) { Thread.sleep(3000); return; }
        Path recordings = profile.resolve(".voxel-one/recordings");
        Set<Path> previous = new HashSet<>();
        if (Files.isDirectory(recordings)) try(var files=Files.list(recordings)) { files.forEach(previous::add); }
        // Send one start edge. Repeated toggles queue together on a slow render loop
        // and can stop the recorder before its first frame.
        new ProcessBuilder("xdotool", "windowraise", gameWindow, "windowfocus", gameWindow).start().waitFor();
        new ProcessBuilder("xdotool", "keydown", "--window", gameWindow, "F10").start().waitFor();
        Thread.sleep(1500);
        new ProcessBuilder("xdotool", "keyup", "--window", gameWindow, "F10").start().waitFor();
        Path partial = null, movie = null;
        for(int attempt=0;attempt<180 && partial==null;attempt++) {
            if(Files.isDirectory(recordings)) try(var files=Files.list(recordings)) {
                partial=files.filter(p->!previous.contains(p)&&p.toString().endsWith(".partial")).findFirst().orElse(null);
            }
            if(partial==null)Thread.sleep(500);
        }
        if(partial==null)throw new AssertionError("F10 start was not acknowledged with a recording file");
        // Header alone is not a rendered frame. Wait for encoded media before stop.
        for(int attempt=0;attempt<180 && Files.size(partial)<=64;attempt++)Thread.sleep(500);
        if(Files.size(partial)<=64)throw new AssertionError("Recorder started but no rendered frames were encoded");
        Thread.sleep(3500);
        new ProcessBuilder("xdotool", "windowraise", gameWindow, "windowfocus", gameWindow).start().waitFor();
        new ProcessBuilder("xdotool", "keydown", "--window", gameWindow, "F10").start().waitFor();
        Thread.sleep(1500);
        new ProcessBuilder("xdotool", "keyup", "--window", gameWindow, "F10").start().waitFor();
        for(int attempt=0;attempt<180 && movie==null;attempt++) {
            if(Files.isDirectory(recordings)) try(var files=Files.list(recordings)) { movie=files.filter(p->!previous.contains(p)&&p.toString().endsWith(".mp4")).findFirst().orElse(null); }
            if(movie==null)Thread.sleep(500);
        }
        if(movie==null)throw new AssertionError("Native F10 produced no movie");
        if(Files.size(movie)>6*1024*1024)throw new AssertionError("Movie exceeds artifact limit");
        Files.copy(movie,evidence.resolve(name+".mp4"),StandardCopyOption.REPLACE_EXISTING);
    }
    public static void main(String[] args) throws Exception {
        Path profile=Path.of(args[1]).resolve("run-" + System.nanoTime()); Files.createDirectories(profile); evidence=Path.of(args[0]);
        System.setProperty("user.home", profile.toAbsolutePath().toString());
        Files.createDirectories(profile.resolve(".voxel-one"));
        dev.jayms.render.GraphicsProfile.preset(dev.jayms.render.GraphicsProfile.Preset.LOW).save(profile.resolve(".voxel-one/graphics.properties"));
        byte[] client=Files.readAllBytes(Path.of(System.getProperty("hub.playtest.clientJar", "target/voxel-one-1.0-SNAPSHOT-client.jar")));
        AtomicInteger downloads=new AtomicInteger(); AtomicBoolean corrupt=new AtomicBoolean(false), slow=new AtomicBoolean(false);
        AtomicReference<String> revision=new AtomicReference<>("a".repeat(40));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/manifest", e -> {
            String metadata="format=1\nrevision="+revision.get()+"\n"+Platform.current().id()+".url="+base+"/client\n"+Platform.current().id()+".size="+client.length+"\n"+Platform.current().id()+".sha256=";
            try { metadata+=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(client))+"\n"; } catch(Exception x) { throw new RuntimeException(x); }
            byte[] bytes=metadata.getBytes(); e.sendResponseHeaders(200,bytes.length); try(var out=e.getResponseBody()){out.write(bytes);}
        });
        server.createContext("/client",e->{ downloads.incrementAndGet(); e.sendResponseHeaders(200,client.length); try(var out=e.getResponseBody()) {
            byte[] bytes=client.clone(); if(corrupt.get())bytes[bytes.length-1]^=1;
            for(int pos=0;pos<bytes.length;pos+=65536) { out.write(bytes,pos,Math.min(65536,bytes.length-pos)); if(slow.get())try{Thread.sleep(20);}catch(InterruptedException x){Thread.currentThread().interrupt();} }
        } catch(java.io.IOException ignored){} }); server.start();
        Properties prefs=new Properties(); prefs.setProperty("updates","true"); try(var out=Files.newOutputStream(profile.resolve("hub.properties"))){prefs.store(out,"Synthetic");}
        try {
            SwingUtilities.invokeAndWait(()->{try{frame=new HubFrame(profile,URI.create(base+"/manifest"));frame.setSize(900,600);frame.setVisible(true);}catch(Exception x){throw new RuntimeException(x);}});
            SwingUtilities.invokeAndWait(()->frame.showPage("Downloads")); click("Retry / Repair client");
            for(int i=0;i<100&&!textPresent("Client ready.");i++)Thread.sleep(200);
            if(!textPresent("Client ready."))throw new AssertionError("Install did not finish"); capture("hub-install-complete");
            String fixtureCatalog;
            try (var in = HubCatalog.class.getResourceAsStream("catalog.properties")) {
                fixtureCatalog = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1)
                        .replace(HubCatalog.bundled().entries().get(0).source(), "a".repeat(40));
            }
            HubCatalog catalog=HubCatalog.read(new java.io.ByteArrayInputStream(fixtureCatalog.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
            SwingUtilities.invokeAndWait(()->{
                frame.detail(catalog.entries().get(0));
                for(Component c:HubPlaytest.descendants(frame)) if(c instanceof JTextField f) {
                    if("Port".equals(f.getAccessibleContext().getAccessibleName())) f.setText("not-a-port");
                    if("Server".equals(f.getAccessibleContext().getAccessibleName())) f.setText("bad address");
                }
            }); click("Play / Install");
            for(int i=0;i<100&&!textPresent("Game started.");i++)Thread.sleep(200);
            if(!textPresent("Game started."))throw new AssertionError("City did not launch"); Thread.sleep(10000);
            if (ProcessHandle.current().descendants().noneMatch(ProcessHandle::isAlive)) throw new AssertionError("Game exited before gameplay capture");
            focusGame();
            recordGame(profile, "hub-native-city");
            ProcessHandle.current().descendants().forEach(ProcessHandle::destroy); Thread.sleep(1500);
            revision.set("b".repeat(40)); // Global latest is not in this experience's tested source set.
            var checkClient = HubFrame.class.getDeclaredMethod("checkClient"); checkClient.setAccessible(true);
            SwingUtilities.invokeAndWait(()->{try{checkClient.invoke(frame);}catch(Exception x){throw new RuntimeException(x);}});
            boolean refreshNeeded = false;
            for(int i=0;i<60;i++) {
                Thread.sleep(100); SwingUtilities.invokeAndWait(()->frame.detail(catalog.entries().get(1)));
                if(textPresent("catalog refresh needed")){refreshNeeded=true;break;}
            }
            if(!refreshNeeded)throw new AssertionError("Incompatible latest was incorrectly offered as an update");
            capture("hub-incompatible-latest-status"); click("Play / Install");
            for(int i=0;i<100&&!textPresent("Game started.");i++)Thread.sleep(200);
            if(!textPresent("Game started."))throw new AssertionError("Sandbox did not launch"); Thread.sleep(10000);
            if (ProcessHandle.current().descendants().noneMatch(ProcessHandle::isAlive)) throw new AssertionError("Game exited before gameplay capture");
            focusGame();
            recordGame(profile, "hub-native-sandbox");
            ProcessHandle.current().descendants().forEach(ProcessHandle::destroy); Thread.sleep(1000);
            SwingUtilities.invokeAndWait(()->frame.detail(catalog.entries().get(3))); click("Play / Install");
            for(int i=0;i<100&&!textPresent("Game started.");i++)Thread.sleep(200);
            if(!textPresent("Game started."))throw new AssertionError("Engine workspace did not launch");
            focusGame(); recordGame(profile, "hub-native-editor");
            ProcessHandle.current().descendants().forEach(ProcessHandle::destroy); Thread.sleep(1000);
            if(downloads.get()!=1)throw new AssertionError("Shared client downloaded more than once");
            capture("hub-compatible-cache-fallback");
            if (!textPresent("compatible installed version")) throw new AssertionError("Incompatible latest did not explain compatible fallback");
            HubCatalog incompatible = HubCatalog.read(new java.io.ByteArrayInputStream(fixtureCatalog.replace("a".repeat(40), "c".repeat(40)).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
            SwingUtilities.invokeAndWait(()->frame.showPage("Settings / Help"));
            click("Check library at startup and client before Play");
            SwingUtilities.invokeAndWait(()->frame.detail(incompatible.entries().get(0))); click("Play / Install");
            for(int i=0;i<100&&!textPresent("No incompatible client was launched");i++)Thread.sleep(100);
            if(!textPresent("No incompatible client was launched"))throw new AssertionError("Incompatible cached client was not refused");
            if(ProcessHandle.current().descendants().anyMatch(ProcessHandle::isAlive))throw new AssertionError("Incompatible cache spawned a game");
            capture("hub-incompatible-cache-refused");
            SwingUtilities.invokeAndWait(()->frame.showPage("Settings / Help")); click("Check library at startup and client before Play");
            revision.set("b".repeat(40));corrupt.set(true);
            SwingUtilities.invokeAndWait(()->frame.showPage("Downloads"));click("Retry / Repair client");
            for(int i=0;i<100&&!textPresent("Client ready.");i++)Thread.sleep(200);
            if(!textPresent("Update unavailable"))throw new AssertionError("Corrupt download did not preserve verified client");capture("hub-corrupt-download-fallback");
            corrupt.set(false);slow.set(true);click("Retry / Repair client");Thread.sleep(600);click("Cancel");Thread.sleep(1500);capture("hub-download-cancelled");
            if(new Updater(profile,URI.create(base+"/manifest")).installed(Platform.current())==null)throw new AssertionError("Cancel lost installation");
            server.stop(0);click("Retry / Repair client");Thread.sleep(1800);capture("hub-offline-native-cache");
            System.out.println("Playtest: real hub install, City offline launch, Sandbox and editor offline launch, invalid offline server/port ignored, one shared download, incompatible latest fallback, incompatible cached launch refused, corrupt update fallback, cancel and service-offline verified cache exercised.");
        } finally { server.stop(0);ProcessHandle.current().descendants().forEach(ProcessHandle::destroy);if(frame!=null)SwingUtilities.invokeAndWait(()->frame.dispose()); }
    }
}
