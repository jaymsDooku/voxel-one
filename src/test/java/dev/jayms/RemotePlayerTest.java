package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;

import org.junit.jupiter.api.Test;

class RemotePlayerTest {
    @Test
    void interpolatesMovementAndYawAcrossWrap() {
        var remote = new RemotePlayer("jayms");
        remote.accept(new Protocol.Pose(1, 0, 1, 0, 179, 0, 1, .5f, false), 0);
        remote.accept(new Protocol.Pose(1, 10, 1, 0, -179, 0, 2, 1, false), 100000000L);
        var p = remote.sample(150000000L);
        assertEquals(5, p.x(), .001);
        assertEquals(180, p.yaw(), .001);
        assertEquals(1.5, p.walkPhase(), .001);
        assertEquals(.75, p.walkAmount(), .001);
        assertEquals(10, remote.sample(1000000000L).x(), .001);
    }
}
