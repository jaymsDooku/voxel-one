package dev.jayms;

import dev.jayms.launcher.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HubShellTest {
    @TempDir Path temp;
    byte[] shell(String version, String mainClass) throws Exception {
        var bytes=new ByteArrayOutputStream();var manifest=new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        manifest.getMainAttributes().putValue("Main-Class",mainClass);
        try(var jar=new JarOutputStream(bytes,manifest)){jar.putNextEntry(new JarEntry(mainClass.replace('.','/')+".class"));jar.write(version.getBytes());jar.closeEntry();}
        return bytes.toByteArray();
    }
    @Test void stagesTwoImmutableShellsRejectsWrongEntrypointAndRetainsPrevious() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var bytes=new AtomicReference<>(shell("one","dev.jayms.launcher.Launcher"));var revision=new AtomicReference<>("a".repeat(40));
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        server.createContext("/shell",e->{byte[] data=bytes.get();e.sendResponseHeaders(200,data.length);try(var out=e.getResponseBody()){out.write(data);}});
        server.createContext("/metadata",e->{try{
            byte[] data=("format=1\nrevision="+revision.get()+"\nwindows.url="+base+"/shell\nwindows.size="+bytes.get().length+"\nwindows.sha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.get()))+"\n").getBytes();
            e.sendResponseHeaders(200,data.length);try(var out=e.getResponseBody()){out.write(data);}
        }catch(Exception x){throw new RuntimeException(x);}});server.start();
        try {
            var updater=new Updater(temp,URI.create(base+"/metadata"),true);
            var first=updater.update(Platform.WINDOWS,s->{});
            try(var active=java.nio.channels.FileChannel.open(first.client(),StandardOpenOption.READ)) {
                bytes.set(shell("two","dev.jayms.launcher.Launcher"));revision.set("a".repeat(40));
                var second=updater.update(Platform.WINDOWS,s->{});assertNotEquals(first.client(),second.client());assertTrue(Files.isRegularFile(first.client()));
                assertEquals(first.revision(), second.revision());
                bytes.set(shell("invalid","dev.jayms.Main"));revision.set("c".repeat(40));
                assertEquals(second.client(),updater.update(Platform.WINDOWS,s->{}).client());
                assertFalse(Files.exists(temp.resolve("versions").resolve("c".repeat(40)).resolve("windows.jar")));
            }
        } finally {server.stop(0);}
        // Linux host checks an open prior file. This does not simulate Windows locking semantics.
    }
}
