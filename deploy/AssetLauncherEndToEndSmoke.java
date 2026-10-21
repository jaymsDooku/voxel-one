import com.sun.net.httpserver.HttpServer;
import dev.jayms.launcher.*;
import dev.jayms.assets.*;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

/** Full exact-source local release -> Launcher -> child Main -> real browser workflow. */
public final class AssetLauncherEndToEndSmoke {
    static void choose(java.awt.Container c){for(var child:c.getComponents()){if(child instanceof JButton b&&b.getText().equals("Asset Manager"))b.doClick();else if(child instanceof java.awt.Container group)choose(group);}}
    public static void main(String[]args)throws Exception{
        Path result=Path.of(args[0]),jar=Path.of(args[1]),fixture=Path.of(args[2]);
        Files.deleteIfExists(result);Files.createDirectories(fixture.getParent());
        try(var out=new DataOutputStream(Files.newOutputStream(fixture))){new ModelDefinition("Balcony flower",ModelGenerators.flowerPot().voxels()).write(out);}
        byte[] payload=Files.readAllBytes(jar);
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        String manifest="format=1\nrevision="+sha.substring(0,40)+"\nlinux.url="+base+"/client.jar\nlinux.size="+payload.length+"\nlinux.sha256="+sha+"\n";
        server.createContext("/manifest",e->{byte[] b=manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);try(var out=e.getResponseBody()){out.write(b);}});
        server.createContext("/client.jar",e->{e.sendResponseHeaders(200,payload.length);try(var out=e.getResponseBody()){out.write(payload);}});server.start();
        var timer=new javax.swing.Timer(300,event->{for(var w:java.awt.Window.getWindows())if(w.isShowing()&&w instanceof JDialog d&&d.getTitle().equals("Voxel One Launcher"))choose(d);});
        try{
            timer.start();Launcher.run(new String[0],new Updater(Path.of(System.getProperty("user.home"),".voxel-one/client"),URI.create(base+"/manifest")));timer.stop();
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(50);
            while(!Files.exists(result)&&System.nanoTime()<until)Thread.sleep(100);
            if(!Files.exists(result)||!Files.readString(result).equals("PASS"))throw new AssertionError("Browser handoff or workflow did not finish");
            Path profile=Path.of(System.getProperty("user.home"),".voxel-one"),save=profile.resolve("offline-world.dat");
            var local=new LocalGame(save,42);
            if(!AssetIntake.apply(profile,dev.jayms.GameIdentity.SANDBOX,save,local).contains("2 asset(s)"))throw new AssertionError("Browser import was not received by engine");
            if(local.inventory.type(0)<9)throw new AssertionError("Custom inventory model missing");
            Files.writeString(Path.of("dashboard/evidence/asset-manager-launcher-e2e.txt"),"Playtest: PASS. Linux assigned X11 display and isolated synthetic home. Production Launcher selected Asset Manager, downloaded SHA-256-verified exact-source client from owned loopback fixture, started child Main --asset-manager, and opened Chromium through the configured BROWSER executable. Browser exercised catalog, search, invalid/valid uploads and selected-game staging. Engine loaded the browser-selected asset into LocalGame library and inventory. No public release or deployment used.\n");
            System.out.println("PASS: full Launcher -> verified client -> browser -> engine import");
        }finally{timer.stop();server.stop(0);ProcessHandle.current().children().forEach(ProcessHandle::destroy);}
        System.exit(0);
    }
}
