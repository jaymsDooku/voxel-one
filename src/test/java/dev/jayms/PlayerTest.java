package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.player.Player;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PlayerTest {
    private World floor() {
        World w = new World();
        Chunk c = new Chunk();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) c.setBlock(x, 0, z, 3);
        w.addChunk(new ChunkPos(0, 0, 0), c);
        return w;
    }

    @Test
    void gravityLandingAndJump() {
        World w = floor();
        Player p = new Player(new Vector3f(8, 5, 8), 0, 0, new Camera());
        for (int i = 0; i < 120; i++) p.step(w, 1f / 60, 0, 0, false, false);
        assertEquals(1, p.position().y, .002);
        assertTrue(p.grounded());
        p.step(w, 1f / 60, 0, 0, true, false);
        assertTrue(p.position().y > 1.1);
        assertFalse(p.grounded());
        int takeoffs = 0;
        for (int i = 0; i < 120; i++) {
            boolean landed = p.grounded();
            p.step(w, 1f / 60, 0, 0, true, false);
            if (landed && !p.grounded()) takeoffs++;
        }
        assertTrue(takeoffs >= 2, "Holding jump should launch again after each landing");
        for (int i = 0; i < 120; i++) p.step(w, 1f / 60, 0, 0, false, false);
        assertEquals(1, p.position().y, .002);
        assertTrue(p.grounded());
    }

    @Test
    void wallsStopMovementAndDiagonalSpeedIsNormalized() {
        World w = floor();
        for (int y = 1; y < 4; y++) w.setBlock(9, y, 8, 3);
        Player p = new Player(new Vector3f(8, 1.001f, 8.5f), 0, 0, new Camera());
        for (int i = 0; i < 60; i++) p.step(w, 1f / 60, 1, 0, false, false);
        assertEquals(8.7, p.position().x, .002);
        Player diagonal = new Player(new Vector3f(3, 1.001f, 3), 0, 0, new Camera());
        diagonal.step(w, .1f, 1, 1, false, false);
        Vector3f displacement = diagonal.position().sub(3, diagonal.position().y, 3);
        assertTrue(displacement.length() > .2 && displacement.length() < .5);
    }

    @Test
    void pendingSolidPlacementStopsMovementAndLateBlocksRecover() {
        World w = floor();
        Player p = new Player(new Vector3f(8.5f, 1.001f, 8.5f), 0, 0, new Camera());
        w.setBlock(9, 1, 8, 3); // Optimistic placement reserves collision before the server reply.
        for (int i = 0; i < 60; i++) p.step(w, 1f / 60, 1, 0, false, false);
        assertFalse(p.collides(w));
        assertTrue(p.position().x < 8.701f);
        w.setBlock(8, 1, 8, 3); // A remote edit arrives overlapping the client's newer position.
        p.resolvePenetration(w);
        assertFalse(p.collides(w));
        Vector3f recovered = p.position();
        for (int i = 0; i < 60; i++) p.step(w, 1f / 60, 0, recovered.z < 8 ? -1 : 1, false, false);
        assertTrue(
                p.position().distance(recovered) > 1,
                "Recovered " + recovered + " then " + p.position());
    }

    @Test
    void flightAscendsHoversDescendsAndReturnsToGravity() {
        World w = floor();
        Player p = new Player(new Vector3f(8, 3, 8), 0, 0, new Camera());
        p.toggleFlight();
        for (int i = 0; i < 30; i++) p.step(w, 1f / 60, 0, 0, true, false, false);
        assertTrue(p.position().y > 6);
        float height = p.position().y;
        for (int i = 0; i < 120; i++) p.step(w, 1f / 60, 0, 0, false, false, false);
        assertTrue(p.position().y >= height);
        float hovering = p.position().y;
        for (int i = 0; i < 30; i++) p.step(w, 1f / 60, 0, 0, false, false, false);
        assertEquals(hovering, p.position().y, .001);
        for (int i = 0; i < 30; i++) p.step(w, 1f / 60, 0, 0, false, false, true);
        assertTrue(p.position().y < hovering - 3);
        p.toggleFlight();
        for (int i = 0; i < 180; i++) p.step(w, 1f / 60, 0, 0, false, false);
        assertEquals(1, p.position().y, .002);
        assertTrue(p.grounded());
    }

    @Test
    void accelerationIsSmoothAndAnimationStopsAtRest() {
        World w = floor();
        Player p = new Player(new Vector3f(3, 1.001f, 8), 0, 0, new Camera());
        p.step(w, 1f / 60, 1, 0, false, false);
        float first = p.position().x - 3;
        p.step(w, 1f / 60, 1, 0, false, false);
        float second = p.position().x - 3 - first;
        assertTrue(second > first);
        for (int i = 0; i < 30; i++) p.step(w, 1f / 60, 1, 0, false, false);
        assertTrue(p.walkAmount() > .8);
        assertTrue(p.walkPhase() > 0);
        for (int i = 0; i < 120; i++) p.step(w, 1f / 60, 0, 0, false, false);
        assertTrue(p.walkAmount() < .001);
    }

    @Test
    void negativeChunkCoordinatesAndWorldEdge() {
        World w = floor();
        Player p = new Player(new Vector3f(.4f, 1.001f, 8), 180, 0, new Camera());
        for (int i = 0; i < 60; i++) p.step(w, 1f / 60, 1, 0, false, false);
        assertTrue(p.position().x >= .299f);
        Chunk c = new Chunk();
        w.addChunk(new ChunkPos(-1, 0, -1), c);
        w.setBlock(-1, 1, -1, 3);
        assertEquals(3, w.getBlock(-1, 1, -1));
    }
}
