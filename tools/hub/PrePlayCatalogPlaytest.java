package dev.jayms.launcher;

import com.sun.net.httpserver.HttpServer;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual Swing Play actions with a synthetic catalog service and browser dispatcher. */
public final class PrePlayCatalogPlaytest {
    static HubFrame frame;
    static Path output;
    static List<Component> children(Container root) {
        var all = new ArrayList<Component>();
        for (var c : root.getComponents()) { all.add(c); if (c instanceof Container nested) all.addAll(children(nested)); }
        return all;
    }
    static void click(String label) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var c : children(frame)) if (c instanceof AbstractButton b && b.getText().equals(label)) { b.doClick(); return; }
            throw new AssertionError("Missing action: " + label);
        });
    }
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i=0;i<150;i++) { if (condition.getAsBoolean()) return; Thread.sleep(100); }
        throw new AssertionError("Expected action did not complete");
    }
    static boolean text(String wanted) {
        var found = new AtomicBoolean();
        try { SwingUtilities.invokeAndWait(() -> {
            for (var c : children(frame)) if (c instanceof JLabel l && l.getText()!=null && l.getText().contains(wanted)
                    || c instanceof JTextArea a && a.getText().contains(wanted)) found.set(true);
        }); } catch(Exception e) { throw new RuntimeException(e); }
        return found.get();
    }
    static void capture(String name) throws Exception {
        Thread.sleep(250);
        ImageIO.write(new Robot().createScreenCapture(frame.getBounds()), "png", output.resolve(name+".png").toFile());
    }
    public static void main(String[] args) throws Exception {
        Path profile = Path.of(args[0]).toAbsolutePath(); output=Path.of(args[1]);
        Files.createDirectories(profile); Files.createDirectories(output);
        System.setProperty("user.home", profile.toString());
        String template;
        try(var in=HubCatalog.class.getResourceAsStream("catalog.properties")) { template=new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1); }
        String nativeSource=HubCatalog.bundled().entries().get(0).source();
        String oldWeb="https://voxel-one.jamesleaver1.chatgpt.site/games/space-invaders/";
        String old=template.replace(nativeSource,"a".repeat(40)).replace("version=bundled-6","version=fixture-a");
        String promoted=old.replace("a".repeat(40),"b".repeat(40)).replace("version=fixture-a","version=fixture-b")
                .replace("playtest-565","playtest-566").replace(oldWeb,oldWeb+"?build=promoted-b");
        Files.writeString(profile.resolve("catalog.properties"),old);
        Files.writeString(profile.resolve("hub.properties"),"updates=false\n");
        byte[] client=Files.readAllBytes(Path.of("target/voxel-one-1.0-SNAPSHOT-client.jar"));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(client));
        var body=new AtomicReference<>(promoted); var catalogRequests=new AtomicInteger();var downloads=new AtomicInteger();
        var opened=new CopyOnWriteArrayList<URI>();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/catalog",e->{
            catalogRequests.incrementAndGet();entered.countDown();
            try { release.await(10,TimeUnit.SECONDS); byte[] bytes=body.get().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);} }
            catch(InterruptedException x){Thread.currentThread().interrupt();} finally { e.close(); }
        });
        server.createContext("/manifest",e->{String key=Platform.current().id();byte[] bytes=("format=1\nrevision="+"b".repeat(40)+"\n"+key+".url="+base+"/client\n"+key+".size="+client.length+"\n"+key+".sha256="+hash+"\n").getBytes();e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});
        server.createContext("/client",e->{downloads.incrementAndGet();e.sendResponseHeaders(200,client.length);try(var out=e.getResponseBody()){out.write(client);}});
        server.start();
        var oldCatalog=HubCatalog.read(new ByteArrayInputStream(old.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
        try {
            SwingUtilities.invokeAndWait(()->{try{
                frame=new HubFrame(profile,URI.create(base+"/manifest"),new CatalogStore(profile,URI.create(base+"/catalog")),opened::add);
                frame.setSize(900,600);frame.setVisible(true);frame.showPage("Settings / Help");
            }catch(Exception e){throw new RuntimeException(e);}});
            click("Check library at startup and client before Play");
            SwingUtilities.invokeAndWait(()->frame.detail(oldCatalog.entries().get(0)));
            click("Play / Install");
            if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("Play did not refresh catalog");
            // This EDT call must finish while the network handler remains blocked.
            click("Play / Install");
            if(catalogRequests.get()!=1)throw new AssertionError("Duplicate Play refresh");
            release.countDown();await(()->text("Game started."));
            var installed=new Updater(profile,URI.create(base+"/manifest")).installed(Platform.current());
            if(installed==null||!installed.revision().equals("b".repeat(40)))throw new AssertionError("Stale native source launched");
            if(downloads.get()!=1)throw new AssertionError("Unexpected native transfers");
            capture("native-promoted-before-play");
            ProcessHandle.current().descendants().forEach(ProcessHandle::destroy);
            SwingUtilities.invokeAndWait(()->frame.detail(oldCatalog.entries().get(2)));
            click("Open in browser");await(()->opened.size()==1);
            if(!opened.get(0).toString().endsWith("?build=promoted-b"))throw new AssertionError("Stale browser URL opened");
            capture("web-promoted-before-play");
            body.set("malformed catalog");
            click("Open in browser");await(()->opened.size()==2);
            if(!opened.get(1).equals(opened.get(0)))throw new AssertionError("Malformed catalog lost verified entry");
            body.set("format=1\nchannel=playtest\nversion=removed\nentries=\n");
            click("Open in browser");await(()->text("no longer available"));
            if(opened.size()!=2)throw new AssertionError("Removed game opened stale URL");
            capture("removed-experience-refused");
            // Updates off must not contact the catalog, even from an old detail button.
            SwingUtilities.invokeAndWait(()->frame.showPage("Settings / Help"));
            click("Check library at startup and client before Play");int before=catalogRequests.get();
            SwingUtilities.invokeAndWait(()->frame.detail(oldCatalog.entries().get(2)));
            click("Open in browser");await(()->text("no longer available"));
            if(catalogRequests.get()!=before||opened.size()!=2)throw new AssertionError("Updates-off or removal bypassed");
            System.out.println("Playtest: open Hub refreshed native A to B and stale web URL to B before Play; EDT responsive during blocked fetch; duplicate Play suppressed; malformed catalog retained verified B; removed entry refused; updates-off made no catalog request; one native download.");
        } finally { release.countDown();server.stop(0);ProcessHandle.current().descendants().forEach(ProcessHandle::destroy);if(frame!=null)SwingUtilities.invokeAndWait(frame::dispose); }
    }
}
