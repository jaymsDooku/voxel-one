package dev.jayms.assets;

import dev.jayms.net.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class AssetCatalogTest {
    @TempDir Path profile;
    private byte[] packet(String name) throws IOException {
        var original = ModelGenerators.flowerPot();
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { new ModelDefinition(name, original.voxels()).write(out); }
        return bytes.toByteArray();
    }
    @Test void uploadSearchPersistenceAndSelectedGameStaging() throws Exception {
        var catalog = new AssetCatalog(profile);
        int seeds = catalog.search("").size();
        assertEquals(1, catalog.search("FLOWER").size());
        var asset = catalog.upload("custom.vxm", "Blue balcony decoration", "test", packet("Balcony pot"));
        assertEquals(asset.id(), catalog.search("balcony").get(0).id());
        assertEquals(asset.id(), new AssetCatalog(profile).search("balcony").get(0).id());
        assertEquals(asset.id(), catalog.upload("again.vxm", "same geometry", "test", packet("Balcony pot")).id());
        assertEquals(seeds + 1, catalog.search("").size());
        assertTrue(catalog.addToGame(asset.id(), "sandbox", profile).contains("Voxel Sandbox"));
        Path staged = profile.resolve("asset-imports/sandbox/"+asset.id()+".vxm");
        assertEquals(asset.model().fingerprint(), AssetCatalog.parse(Files.readAllBytes(staged)).fingerprint());
        assertFalse(Files.exists(profile.resolve("asset-imports/city")));
        assertThrows(IOException.class, () -> catalog.addToGame(asset.id(), "../city", profile));
    }
    @Test void skyscraperSeedUsesSupportedEngineFormatAndScale() throws Exception {
        var catalog = new AssetCatalog(profile);
        assertEquals(2, catalog.search("").size());
        var asset = catalog.get("hudson-crown");
        assertTrue(asset.seed()); assertEquals("Hudson Crown Tower", asset.model().name());
        assertEquals(32, asset.model().voxels().size()); assertEquals(7246, asset.model().voxels().occupied());
        var geometry = Files.readString(Path.of("assets/generated/vam-skyscraper-8c68a1cd/r1/model.voxel.json"));
        var expected = new SparseVoxelOctree(32);
        int[] palette = {0xffb8ad95,0xffd5cbb5,0xff8f8776,0xff242e35,0xff46515a,0xffe2d8c1};
        var boxes = java.util.regex.Pattern.compile("\\[(-?\\d+(?:,-?\\d+){6})\\]").matcher(geometry);
        int count=0;
        while(boxes.find()) {
            int[] b=java.util.Arrays.stream(boxes.group(1).split(",")).mapToInt(Integer::parseInt).toArray();
            expected.fill(b[0],b[1],b[2],b[3],b[4],b[5],b[6]<0?0:palette[b[6]]);count++;
        }
        assertTrue(count>20);
        for(int x=0;x<32;x++)for(int y=0;y<32;y++)for(int z=0;z<32;z++)
            assertEquals(expected.get(x,y,z),asset.model().voxels().get(x,y,z),"Native packet matches specialist geometry");
        assertEquals(asset.id(), catalog.search("skyscraper").get(0).id());
        assertEquals(asset.id(), catalog.search("CROWN").get(0).id());
        var mesh = ModelMesher.mesh(asset.model().voxels());
        assertTrue(mesh.quads() > 0 && mesh.quads() <= ModelMesher.MAX_QUADS);
        for (int i=0;i<mesh.vertices().length;i+=9) for(int axis=0;axis<3;axis++)
            assertTrue(mesh.vertices()[i+axis]>=0 && mesh.vertices()[i+axis]<=1, "One-block engine model bounds");
    }
    @Test void invalidUploadsDoNotEnterCatalog() throws Exception {
        var catalog = new AssetCatalog(profile);
        int seeds = catalog.search("").size();
        byte[] valid = packet("Upload pot");
        assertThrows(IOException.class, () -> catalog.upload("bad.txt", "desc", "test", valid));
        assertThrows(IOException.class, () -> catalog.upload("bad.vxm", " ", "test", valid));
        assertThrows(IOException.class, () -> catalog.upload("bad.vxm", "desc", "", valid));
        assertThrows(IOException.class, () -> catalog.upload("bad.vxm", "desc", "test", new byte[]{1,2,3}));
        assertThrows(IOException.class, () -> catalog.upload("bad.vxm", "desc", "test", java.util.Arrays.copyOf(valid, valid.length+1)));
        assertThrows(IOException.class, () -> AssetCatalog.parse(new byte[200005]));
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { new ModelDefinition("Empty", new SparseVoxelOctree(8)).write(out); }
        assertThrows(IOException.class, () -> catalog.upload("empty.vxm", "desc", "test", bytes.toByteArray()));
        assertEquals(seeds, catalog.search("").size());
        assertEquals(ModelGenerators.flowerPot().fingerprint(), catalog.get("flower-pot").model().fingerprint());
    }
}
