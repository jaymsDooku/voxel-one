package dev.jayms.launcher;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HubShellActivationTest {
    @TempDir Path temp;
    byte[] shell(String name, boolean ready, boolean fail) throws Exception {
        Path dir=Files.createDirectory(temp.resolve("compile-"+name));
        Path source=dir.resolve("Launcher.java");
        Files.writeString(source,"package dev.jayms.launcher; public class Launcher { public static void main(String[] a) throws Exception {"
                +"java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty(\"user.home\"),\"observed-shell\"),\""+name+"\");"
                +(ready?"java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty(\"voxel.hub.activation\")),\"ready\",java.nio.file.StandardOpenOption.CREATE_NEW);":"")
                +(fail?"System.exit(7);":"Thread.sleep(60000);")+"} }");
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,OutputStream.nullOutputStream(),OutputStream.nullOutputStream(),"--release","17","-d",dir.toString(),source.toString()));
        var bytes=new ByteArrayOutputStream();var manifest=new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Main-Class","dev.jayms.launcher.Launcher");
        try(var jar=new JarOutputStream(bytes,manifest)){jar.putNextEntry(new JarEntry("dev/jayms/launcher/Launcher.class"));jar.write(Files.readAllBytes(dir.resolve("dev/jayms/launcher/Launcher.class")));jar.closeEntry();}
        return bytes.toByteArray();
    }
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for(int i=0;i<320;i++){if(condition.getAsBoolean())return;Thread.sleep(100);}fail("Activation did not reach expected state");
    }
    Process bootstrap(Path profile, URI manifest) throws Exception {
        var pb=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",Platform.current()==Platform.WINDOWS?"java.exe":"java").toString(),
                "-Duser.home="+profile,"-Djava.io.tmpdir="+temp,"-Dvoxel.hub.preview=true","-Dvoxel.hub.shellManifest="+manifest,
                "-cp",Path.of("target/classes").toAbsolutePath().toString(),"dev.jayms.launcher.Bootstrap");
        pb.environment().put("LOCALAPPDATA",profile.resolve("local").toString());
        return pb.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    static void stop(Process p) throws Exception {
        if(p==null)return;p.descendants().forEach(h->{h.destroy();if(h.isAlive())h.destroyForcibly();});p.destroy();if(!p.waitFor(5,TimeUnit.SECONDS))p.destroyForcibly();
    }
    final class Fixture implements AutoCloseable {
        final HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        final AtomicReference<byte[]> blob=new AtomicReference<>();final AtomicInteger downloads=new AtomicInteger();
        final URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/manifest");
        Fixture() throws Exception {
            server.createContext("/manifest",e->{try{
                byte[] bytes=blob.get();String key=Platform.current().id();byte[] data=("format=1\nrevision="+"a".repeat(40)+"\n"+key+".url="+uri.resolve("/blob")+"\n"+key+".size="+bytes.length+"\n"+key+".sha256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+"\n").getBytes();
                e.sendResponseHeaders(200,data.length);try(var out=e.getResponseBody()){out.write(data);}
            }catch(Exception x){throw new RuntimeException(x);}});
            server.createContext("/blob",e->{downloads.incrementAndGet();byte[] bytes=blob.get();e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();
        }
        public void close(){server.stop(0);}
    }
    @Test void executableFailureAndUnacknowledgedShellRollBackToDownloadedWorkingShell() throws Exception {
        var platform=Platform.current();Path profile=Files.createDirectory(temp.resolve("profile"));
        Path base=platform==Platform.WINDOWS?profile.resolve("local/VoxelOne"):profile.resolve(".voxel-one/client");Files.createDirectories(base);
        Files.writeString(base.resolve("hub.properties"),"updates=false\n");Path marker=profile.resolve("observed-shell");Process process=null;
        try(var f=new Fixture()) {
            var updater=new Updater(base.resolve("hub-shell"),f.uri,true);
            f.blob.set(shell("good-a",true,false));var a=updater.update(platform,s->{});
            try {
                process=bootstrap(profile,f.uri);await(()->updater.acknowledged(platform)!=null);assertEquals(a.client(),updater.acknowledged(platform).result().client());stop(process);process=null;
                f.blob.set(shell("bad-b",true,true));var b=updater.update(platform,s->{});assertNotEquals(a.client(),b.client());Files.delete(marker);
                process=bootstrap(profile,f.uri);await(()->{try{return Files.readString(marker).equals("good-a")&&updater.installed(platform).client().equals(a.client());}catch(Exception e){return false;}});
                assertTrue(process.isAlive());assertEquals(a.client(),updater.acknowledged(platform).result().client());assertTrue(Files.isRegularFile(a.client()));
                int downloads=f.downloads.get();assertEquals(a.client(),updater.update(platform,s->{}).client());assertEquals(downloads,f.downloads.get());stop(process);process=null;
                f.blob.set(shell("good-c",true,false));var c=updater.update(platform,s->{});process=bootstrap(profile,f.uri);
                await(()->updater.acknowledged(platform).result().client().equals(c.client()));stop(process);process=null;
                f.blob.set(shell("hang-d",false,false));updater.update(platform,s->{});Files.delete(marker);process=bootstrap(profile,f.uri);
                await(()->{try{return Files.readString(marker).equals("good-c")&&updater.installed(platform).client().equals(c.client());}catch(Exception e){return false;}});
                assertTrue(process.isAlive());assertEquals(c.client(),updater.acknowledged(platform).result().client());
            } finally {stop(process);}
        }
    }
    @Test void rollbackCannotOverwriteNewerStagingAndAcknowledgementUsesCandidateSnapshot() throws Exception {
        var platform=Platform.current();
        try(var f=new Fixture()) {
            var updater=new Updater(temp.resolve("cache"),f.uri,true);
            f.blob.set(shell("first",true,false));updater.update(platform,s->{});var first=updater.candidate(platform);updater.acknowledge(first);
            f.blob.set(shell("failed",false,true));updater.update(platform,s->{});var failed=updater.candidate(platform);
            f.blob.set(shell("newer",true,false));var newer=updater.update(platform,s->{});
            updater.rejectAndRollback(failed,platform);assertEquals(newer.client(),updater.installed(platform).client());
            updater.acknowledge(first);assertEquals(first.result().client(),updater.acknowledged(platform).result().client());
            assertTrue(Files.isRegularFile(first.result().client()));assertTrue(Files.isRegularFile(failed.result().client()));
        }
    }
}
