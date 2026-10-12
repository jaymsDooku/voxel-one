package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SandboxIdentityTest {
    @TempDir Path directory;
    static final CitySimulation.Ground GROUND = new CitySimulation.Ground() {
        public int type(int x, int y, int z) { return y <= 0 ? Blocks.STONE : 0; }
        public void apply(java.util.List<Protocol.Edit> batch) {}
        public boolean occupied(int x,int y,int z,int width,int depth) { return false; }
    };
    @Test void identityDefaultsAndCustomPorts() {
        assertEquals("Voxel Sandbox", GameIdentity.parse("sandbox").title);
        assertEquals("Voxel City One", GameIdentity.parse("city").title);
        assertEquals(25565, GameIdentity.SANDBOX.launchPort(null));
        assertEquals(25566, GameIdentity.CITY.launchPort(null));
        assertEquals(25565, GameIdentity.CITY.launchPort(25565));
        assertEquals(23456, GameIdentity.SANDBOX.launchPort(23456));
        assertEquals("25566", GameIdentity.CITY.selectedPort(GameIdentity.SANDBOX,"25565",false));
        assertEquals("25565", GameIdentity.CITY.selectedPort(GameIdentity.SANDBOX,"25565",true));
        assertEquals("23456", GameIdentity.CITY.selectedPort(GameIdentity.SANDBOX,"23456",false));
        assertThrows(IllegalArgumentException.class, () -> GameIdentity.parse("other"));
        assertThrows(IllegalArgumentException.class, () -> GameIdentity.CITY.launchPort(0));
        assertThrows(IllegalArgumentException.class, () -> GameIdentity.SANDBOX.launchPort(65536));
    }
    @Test void updaterForwardsStableSandboxArgumentsOnEveryDesktop() {
        var args = java.util.List.of("--game","sandbox","--offline","--world","my world.dat");
        for (var platform : dev.jayms.launcher.Platform.values()) {
            var command = dev.jayms.launcher.Launcher.command(Path.of("client.jar"),platform,args);
            assertEquals(args,command.subList(command.size()-args.size(),command.size()));
            assertFalse(command.contains("--server"));
        }
    }
    @Test void explicitWorldIsNeverRemapped() {
        var sandbox = directory.resolve("offline-world.dat");
        var city = directory.resolve("offline-city.dat");
        assertEquals(sandbox, GameIdentity.SANDBOX.savePath(directory,null));
        assertEquals(city, GameIdentity.CITY.savePath(directory,null));
        assertEquals(sandbox, GameIdentity.CITY.savePath(directory,sandbox));
        assertEquals(city, GameIdentity.SANDBOX.savePath(directory,city));
    }
    @Test void legacySandboxAndCityPersistIndependentlyAndRejectCrossGame() throws Exception {
        var sandboxPath = GameIdentity.SANDBOX.savePath(directory,null);
        var sandbox = new LocalGame(sandboxPath, 42);
        sandbox.inventory.add(Blocks.STONE,7);
        sandbox.save(); // Original save format without a sidecar.
        LocalGame.validateGame(sandboxPath,GameConfig.sandbox());
        var resumed = new LocalGame(sandboxPath,99);
        assertEquals(42,resumed.seed);
        assertEquals(7,resumed.inventory.countType(Blocks.STONE));
        resumed.startGame(GameConfig.sandbox(),GROUND);
        assertFalse(resumed.city.frame().config().city());
        assertTrue(resumed.city.frame().citizens().isEmpty());
        assertTrue(resumed.city.frame().buildings().isEmpty());
        resumed.save();
        byte[] original = Files.readAllBytes(sandboxPath);
        var cityPath = GameIdentity.CITY.savePath(directory,null);
        var city = new LocalGame(cityPath,91);
        city.startGame(GameConfig.cityGame(),GROUND);
        assertTrue(city.city.frame().config().city());
        assertFalse(city.city.frame().citizens().isEmpty());
        city.save();
        byte[] cityOriginal = Files.readAllBytes(cityPath);
        assertThrows(java.io.IOException.class, () -> LocalGame.validateGame(cityPath,GameConfig.sandbox()));
        assertThrows(java.io.IOException.class, () -> LocalGame.validateGame(sandboxPath,GameConfig.cityGame()));
        assertArrayEquals(original,Files.readAllBytes(sandboxPath));
        assertArrayEquals(cityOriginal,Files.readAllBytes(cityPath));
        assertEquals(7,new LocalGame(sandboxPath,99).inventory.countType(Blocks.STONE));
        assertEquals(0,new LocalGame(cityPath,99).inventory.countType(Blocks.STONE));
    }
    @Test void invalidArgumentsFailBeforeOpeningGame() {
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"--game","other"}));
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"--game","sandbox","--port","0"}));
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"--unknown"}));
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"--game"}));
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"--world",""}));
        assertThrows(IllegalArgumentException.class, () -> MultiplayerServer.main(new String[]{"--game","city","--port","0"}));
        assertThrows(IllegalArgumentException.class, () -> MultiplayerServer.main(new String[]{"--game","sandbox","--port","65536"}));
    }
}
