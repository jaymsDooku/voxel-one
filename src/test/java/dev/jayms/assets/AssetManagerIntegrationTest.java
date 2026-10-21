package dev.jayms.assets;

import dev.jayms.GameIdentity;
import dev.jayms.net.*;
import dev.jayms.net.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AssetManagerIntegrationTest {
    @TempDir Path profile;
    @Test void httpAuthenticationSearchUploadAndEngineImport() throws Exception {
        try(var server=new AssetManagerServer(profile)) {
            var client=HttpClient.newHttpClient();
            String base=server.origin(), token=server.browserURI().getFragment();
            assertEquals(200,client.send(HttpRequest.newBuilder(URI.create(base+"/")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401,client.send(HttpRequest.newBuilder(URI.create(base+"/api/catalog")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            var catalog=client.send(HttpRequest.newBuilder(URI.create(base+"/api/catalog?q=FLOWER")).header("Authorization","Bearer "+token).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,catalog.statusCode()); assertTrue(catalog.body().contains("Flower pot"));
            var blocked=client.send(HttpRequest.newBuilder(URI.create(base+"/api/import?id=flower-pot&game=sandbox")).header("Authorization","Bearer "+token).header("Origin","https://example.invalid").POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(403,blocked.statusCode());
            try(var socket=new java.net.Socket("127.0.0.1",URI.create(base).getPort())) {
                socket.setSoTimeout(3000);
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: example.invalid\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String response=new String(socket.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 403"));
            }
            var download=client.send(HttpRequest.newBuilder(URI.create(base+"/api/download?id=flower-pot")).header("Authorization","Bearer "+token).build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(ModelGenerators.flowerPot().fingerprint(),AssetCatalog.parse(download.body()).fingerprint());
            var malformed=client.send(HttpRequest.newBuilder(URI.create(base+"/api/upload?filename=test.vxm&author=Test&description=Test")).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1,2,3})).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(400,malformed.statusCode()); assertTrue(malformed.body().contains("Invalid or truncated"));
            var imported=client.send(HttpRequest.newBuilder(URI.create(base+"/api/import?id=flower-pot&game=sandbox")).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,imported.statusCode());
            Path save=profile.resolve("offline-world.dat");
            var local=new LocalGame(save,123);
            assertTrue(AssetIntake.apply(profile,GameIdentity.SANDBOX,save,local).contains("1 asset(s)"));
            assertEquals(Blocks.FLOWER_POT,local.inventory.type(0)); assertEquals(1,local.inventory.count(0));
            var reloaded=new LocalGame(save,999);
            assertEquals("",AssetIntake.apply(profile,GameIdentity.SANDBOX,save,reloaded));
            assertEquals(1,reloaded.inventory.count(0)); assertEquals(123,reloaded.seed);
            var city=new LocalGame(profile.resolve("offline-city.dat"),123);
            assertEquals("",AssetIntake.apply(profile,GameIdentity.CITY,profile.resolve("offline-city.dat"),city));
            assertEquals(0,city.inventory.count(0));
        }
    }
    @Test void fullInventoryRetainsPendingImportForRetry() throws Exception {
        var catalog=new AssetCatalog(profile);catalog.addToGame("flower-pot","sandbox",profile);
        Path save=profile.resolve("offline-world.dat"); var local=new LocalGame(save,1);
        local.inventory.add(1,Inventory.SIZE*64);
        assertTrue(AssetIntake.apply(profile,GameIdentity.SANDBOX,save,local).contains("Inventory full"));
        assertFalse(Files.exists(save.resolveSibling("offline-world.dat.assets")));
        local.inventory.swap(0,35);
        for(int i=0;i<64;i++) local.inventory.take(0,1);
        assertTrue(AssetIntake.apply(profile,GameIdentity.SANDBOX,save,local).contains("1 asset(s)"));
        assertEquals(Blocks.FLOWER_POT,local.inventory.type(0));
    }
    @Test void browserWorkflow() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("asset.browser"));
        Path fixture=profile.resolve("upload.vxm");
        try(var out=new DataOutputStream(Files.newOutputStream(fixture))) {
            new ModelDefinition("Balcony flower",ModelGenerators.flowerPot().voxels()).write(out);
        }
        try(var server=new AssetManagerServer(profile)) {
            var process=new ProcessBuilder("node","deploy/test_asset_manager.cjs",fixture.toString()).inheritIO().redirectInput(ProcessBuilder.Redirect.PIPE).start();
            try(var input=process.getOutputStream()) { input.write((server.browserURI().toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            assertTrue(process.waitFor(60,TimeUnit.SECONDS)); assertEquals(0,process.exitValue());
            var local=new LocalGame(profile.resolve("offline-world.dat"),42);
            assertTrue(AssetIntake.apply(profile,GameIdentity.SANDBOX,profile.resolve("offline-world.dat"),local).contains("2 asset(s)"));
            assertTrue(local.models.entries().stream().anyMatch(e->e.definition().name().equals("Balcony flower")));
            assertTrue(local.inventory.type(0)>=9);
            assertTrue(local.models.entries().stream().anyMatch(e->e.definition().name().equals("Hudson Crown Tower")));
            Path citySave=profile.resolve("offline-city.dat");var city=new LocalGame(citySave,42);
            assertTrue(AssetIntake.apply(profile,GameIdentity.CITY,citySave,city).contains("1 asset(s)"));
            assertEquals(Blocks.FLOWER_POT,city.inventory.type(0));
            assertFalse(city.models.entries().stream().anyMatch(e->e.definition().name().equals("Hudson Crown Tower")));
        }
    }
}
