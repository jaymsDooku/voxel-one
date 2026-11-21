package dev.jayms.combat.arena;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.net.Blocks;

/** Shared layout and collision for practice servers and clients. */
public enum PracticeTerrain implements Arena {
    COURTYARD("Basalt Courtyard", BasaltCourtyard.MAX_X, BasaltCourtyard.MAX_Z, BasaltCourtyard.CEILING_Y),
    BATTLEFIELD("Basalt Battlefield", BasaltBattlefield.HALF_WIDTH, BasaltBattlefield.HALF_LENGTH, BasaltBattlefield.CEILING_Y);

    private final String title;
    private final int halfWidth, halfLength, ceiling;

    PracticeTerrain(String title, int halfWidth, int halfLength, int ceiling) {
        this.title = title;
        this.halfWidth = halfWidth;
        this.halfLength = halfLength;
        this.ceiling = ceiling;
    }

    public String title() { return title; }

    public BasaltCourtyard.Spawn spawn(int side, int member) {
        return this == BATTLEFIELD ? BasaltBattlefield.spawn(side, member) : BasaltCourtyard.teamSpawn(side, member);
    }

    public BasaltCourtyard.Spawn spectator() {
        return this == BATTLEFIELD ? BasaltBattlefield.spectator() : BasaltCourtyard.spectator();
    }

    public int block(int x, int y, int z) {
        return this == BATTLEFIELD ? BasaltBattlefield.block(x, y, z) : BasaltCourtyard.block(x, y, z);
    }

    @Override public boolean contains(Vec3 point) {
        return point.y() < ceiling && containsThrowable(point);
    }

    @Override public boolean containsThrowable(Vec3 point) {
        return withinFootprint(point.x(), point.y(), point.z());
    }

    @Override public boolean solid(int x, int y, int z) {
        return y >= ceiling || !withinFootprint(x, y, z) || block(x, y, z) != Blocks.AIR;
    }

    @Override public boolean throwableSolid(int x, int y, int z) {
        return withinFootprint(x, y, z) && block(x, y, z) != Blocks.AIR;
    }

    private boolean withinFootprint(double x, double y, double z) {
        return x >= -halfWidth && x < halfWidth && z >= -halfLength && z < halfLength && y >= BasaltCourtyard.FLOOR_Y;
    }
}
