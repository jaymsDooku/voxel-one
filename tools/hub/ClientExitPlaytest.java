package dev.jayms.launcher;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Actual Play workflow against verified, synthetic delayed-exit client processes. */
public final class ClientExitPlaytest {
    static Path profile;
    static HubFrame frame;
    static String status() throws Exception {
        var result = new AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            try { var f=HubFrame.class.getDeclaredField("status");f.setAccessible(true);result.set(((JLabel)f.get(frame)).getText()); }
            catch(Exception e){throw new RuntimeException(e);}
        });
        return result.get();
    }
    static void awaitStatus(String value) throws Exception {
        for(int i=0;i<150;i++){if(status().contains(value))return;Thread.sleep(100);}
        throw new AssertionError("Missing status: "+value+"; observed: "+status());
    }
    static void play(long delay,int code) throws Exception {
        Files.writeString(profile.resolve("exit-control.txt"),delay+"\n"+code+"\n");
        var entry=HubCatalog.bundled().entries().get(0);
        SwingUtilities.invokeAndWait(() -> frame.detail(entry));
        PrePlayCatalogPlaytest.click("Play / Install");
        awaitStatus("Client running.");
    }
    public static void main(String[] args) throws Exception {
        profile=Path.of(args[0]).toAbsolutePath();Files.createDirectories(profile);
        Path output=Path.of(args[1]);Files.createDirectories(output);
        System.setProperty("user.home",profile.toString());
        Files.writeString(profile.resolve("hub.properties"),"updates=false\n");
        Path save=profile.resolve("synthetic-save.txt");Files.writeString(save,"keep-save\n");
        byte[] client=Files.readAllBytes(Path.of(args[2]));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(client));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/manifest",e->{byte[] bytes=("format=1\nrevision="+HubCatalog.bundled().entries().get(0).source()+"\n"+Platform.current().id()+".url="+base+"/client\n"+Platform.current().id()+".size="+client.length+"\n"+Platform.current().id()+".sha256="+hash+"\n").getBytes();e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});
        server.createContext("/client",e->{e.sendResponseHeaders(200,client.length);try(var out=e.getResponseBody()){out.write(client);}});
        server.start();
        try {
            SwingUtilities.invokeAndWait(()->{try{frame=new HubFrame(profile,URI.create(base+"/manifest"));frame.setSize(900,600);frame.setVisible(true);}catch(Exception e){throw new RuntimeException(e);}});
            PrePlayCatalogPlaytest.frame=frame;PrePlayCatalogPlaytest.output=output;
            play(3000,7);awaitStatus("Client stopped (exit 7)");
            var updater=new Updater(profile,URI.create(base+"/manifest"));
            var verified=updater.installed(Platform.current());
            if(verified==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(verified.client()))).equals(hash))throw new AssertionError("Verified client lost");
            PrePlayCatalogPlaytest.capture("delayed-client-failure");
            play(3000,0);awaitStatus("Client closed.");
            play(8000,7);play(3000,0);awaitStatus("Client closed.");
            Thread.sleep(4500);
            if(!status().equals("Client closed."))throw new AssertionError("Older client overwrote new status");
            if(updater.installed(Platform.current())==null)throw new AssertionError("Installation lost after exits");
            Files.writeString(profile.resolve("exit-control.txt"),"8000\n0\n");
            var entry=HubCatalog.bundled().entries().get(0);
            SwingUtilities.invokeAndWait(()->frame.detail(entry));
            PrePlayCatalogPlaytest.click("Play / Install");
            java.util.List<ProcessHandle> children=java.util.List.of();
            for(int i=0;i<50 && children.isEmpty();i++){children=ProcessHandle.current().descendants().toList();Thread.sleep(20);}
            if(children.isEmpty())throw new AssertionError("Cancellation fixture did not start");
            PrePlayCatalogPlaytest.click("Cancel");awaitStatus("Cancelled");
            Thread.sleep(1000);
            if(children.stream().anyMatch(ProcessHandle::isAlive))throw new AssertionError("Cancelled launch left running client");
            if(!Files.readString(save).equals("keep-save\n"))throw new AssertionError("Save changed");
            System.out.println("Playtest: actual Play; delayed exit 7 reported; verified client retained; clean exit 0 reported; older exit cannot overwrite latest launch; startup cancellation stops child; synthetic save preserved. Synthetic process fixture, not gameplay qualification.");
        } finally {server.stop(0);ProcessHandle.current().descendants().forEach(ProcessHandle::destroy);if(frame!=null)SwingUtilities.invokeAndWait(frame::dispose);}
    }
}
