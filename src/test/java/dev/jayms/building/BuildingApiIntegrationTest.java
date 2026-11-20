package dev.jayms.building;

import dev.jayms.assets.AssetManagerServer;
import dev.jayms.net.mobile.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BuildingApiIntegrationTest {
    @Test void monumentWallToolsAreDiscoverableAndCompileOverHttp(@TempDir Path directory) throws Exception {
        String source="""
                building portico 4800 64 16 grid16 monument
                part wing 4096 0 0 4800 64 16
                masonry STONE x 32 16 2 0 4096 0 8 4800 64 16
                row 4 32 0 0
                box SAND 4096 0 0 4104 64 8
                endrow
                end
                """;
        try (var server=new AssetManagerServer(directory)) {
            var client=HttpClient.newHttpClient();
            var schema=send(client,authorized(server,SCHEMA).build());
            assertTrue(schema.body().contains("\"monument\""));
            assertTrue(schema.body().contains("masonry MATERIAL"));
            var response=post(client,server,source.getBytes(StandardCharsets.UTF_8));
            assertEquals(200,response.statusCode());
            var expected=new ByteArrayOutputStream();
            BuildingApi.writeJson(BuildingCompiler.compile(source),expected);
            assertEquals(expected.toString(StandardCharsets.UTF_8),response.body());
        }
    }
    private static final String SCHEMA = "/api/buildings/schema";
    private static final String COMPILE = "/api/buildings/compile";
    private static final String RECIPE = """
            building lamp 4 4 4
            part facade 0 0 0 4 4 4
            box STONE 0 0 0 2 2 2
            box LED:11aaee 2 0 0 3 1 1
            end
            """;

    @Test void authenticatedHttpAndCliProduceIdenticalEdits(@TempDir Path directory) throws Exception {
        Path recipe = Files.writeString(directory.resolve("lamp.vbuild"),RECIPE);
        var cli = new ByteArrayOutputStream();
        BuildingApi.run(new String[]{recipe.toString()},cli);
        try (var server = new AssetManagerServer(directory)) {
            var client = HttpClient.newHttpClient();
            assertEquals(401,send(client,request(server,SCHEMA).build()).statusCode());
            assertEquals(401,send(client,request(server,COMPILE).POST(HttpRequest.BodyPublishers.ofString(RECIPE)).build()).statusCode());
            var schema = send(client,authorized(server,SCHEMA).build());
            assertEquals(200,schema.statusCode());
            assertEquals(BuildingApi.MAX_RECIPE_BYTES,Json.integer(Json.object(schema.body()),"maxRecipeBytes"));
            var blocked = authorized(server,COMPILE).header("Origin","https://example.invalid")
                    .POST(HttpRequest.BodyPublishers.ofString(RECIPE)).build();
            assertEquals(403,send(client,blocked).statusCode());
            var response = send(client,authorized(server,COMPILE).header("Origin",server.origin())
                    .header("Content-Type","text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(RECIPE)).build());
            assertEquals(200,response.statusCode());
            assertEquals(BuildingApi.JSON_TYPE,response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(cli.toString(StandardCharsets.UTF_8),response.body());
            var result = Json.object(response.body());
            assertEquals("lamp",Json.text(result,"name"));
            assertEquals(9,Json.integer(result,"occupied"));
            assertEquals(2,Json.integer(result,"editCount"));
            assertTrue(response.body().contains(Integer.toString(0x11aaee)));
        }
    }

    @Test void fineRecipesReturnTheSameVersionedMetadataOverHttpAndCli(@TempDir Path directory) throws Exception {
        String source = RECIPE.replace("building lamp 4 4 4", "building lamp 4 4 4 grid16");
        Path file = Files.writeString(directory.resolve("fine.vbuild"),source);
        var cli = new ByteArrayOutputStream();
        BuildingApi.run(new String[]{file.toString()},cli);
        try (var server = new AssetManagerServer(directory)) {
            var response = post(HttpClient.newHttpClient(),server,source.getBytes(StandardCharsets.UTF_8));
            assertEquals(200,response.statusCode());
            assertEquals(cli.toString(StandardCharsets.UTF_8),response.body());
            var result = Json.object(response.body());
            assertEquals(2,Json.integer(result,"schemaVersion"));
            assertEquals(16,Json.integer(result,"unitsPerBlock"));
            assertEquals(9,Json.integer(result,"occupied"));
            assertTrue(response.body().contains("[0, 0, 0, 1, 1]"));
        }
    }

    @Test void rejectsMalformedAndOversizedRecipesBeforeEmittingEdits(@TempDir Path directory) throws Exception {
        try (var server = new AssetManagerServer(directory)) {
            var client = HttpClient.newHttpClient();
            var wrongMethod = send(client,authorized(server,COMPILE).build());
            assertEquals(405,wrongMethod.statusCode());
            assertEquals("POST",wrongMethod.headers().firstValue("Allow").orElseThrow());
            var wrongType = authorized(server,COMPILE).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(RECIPE)).build();
            assertEquals(415,send(client,wrongType).statusCode());
            var invalid = RECIPE.replace("3 1 1","5 1 1");
            var outOfBounds = post(client,server,invalid.getBytes(StandardCharsets.UTF_8));
            assertEquals(400,outOfBounds.statusCode());
            assertTrue(Json.text(Json.object(outOfBounds.body()),"error").toLowerCase(java.util.Locale.ROOT).contains("line 4"));
            var malformed = post(client,server,new byte[]{(byte)0xc3,0x20});
            assertEquals(400,malformed.statusCode());
            assertTrue(malformed.body().contains("UTF-8"));
            byte[] tooLarge = new byte[BuildingApi.MAX_RECIPE_BYTES+1];
            assertEquals(413,post(client,server,tooLarge).statusCode());
            Path file = Files.write(directory.resolve("large.vbuild"),tooLarge);
            assertThrows(BuildingApi.RecipeTooLarge.class,() -> BuildingApi.readRecipe(file));
        }
    }

    private static HttpRequest.Builder request(AssetManagerServer server,String path) {
        return HttpRequest.newBuilder(URI.create(server.origin()+path)).timeout(Duration.ofSeconds(8));
    }

    private static HttpRequest.Builder authorized(AssetManagerServer server,String path) {
        return request(server,path).header("Authorization","Bearer "+server.browserURI().getFragment());
    }

    private static HttpResponse<String> post(HttpClient client,AssetManagerServer server,byte[] body) throws Exception {
        return send(client,authorized(server,COMPILE).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
    }

    private static HttpResponse<String> send(HttpClient client,HttpRequest request) throws IOException,InterruptedException {
        return client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
