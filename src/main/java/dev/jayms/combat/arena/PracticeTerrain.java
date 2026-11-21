package dev.jayms.combat.arena;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.net.Blocks;

/** Shared layout and collision for practice servers and clients. */
public enum PracticeTerrain implements Arena {
    COURTYARD("Basalt Courtyard", BasaltCourtyard.MAX_X, BasaltCourtyard.MAX_Z,
            BasaltCourtyard.FLOOR_Y, BasaltCourtyard.CEILING_Y),
    BATTLEFIELD("Basalt Battlefield", BasaltBattlefield.HALF_WIDTH, BasaltBattlefield.HALF_LENGTH,
            BasaltBattlefield.FLOOR_Y, BasaltBattlefield.CEILING_Y),
    VAULT_BATTLEFIELD("Vault Battlefield", VaultBattlefield.HALF_WIDTH, VaultBattlefield.HALF_LENGTH,
            VaultBattlefield.TRENCH_Y, VaultBattlefield.CEILING_Y);

    private final String title;
    private final int halfWidth, halfLength, floor, ceiling;

    PracticeTerrain(String title, int halfWidth, int halfLength, int floor, int ceiling) {
        this.title = title;
        this.halfWidth = halfWidth;
        this.halfLength = halfLength;
        this.floor = floor;
        this.ceiling = ceiling;
    }

    public String title() { return title; }

    public BasaltCourtyard.Spawn spawn(int side, int member) {
        return switch (this) {
            case COURTYARD -> BasaltCourtyard.teamSpawn(side, member);
            case BATTLEFIELD -> BasaltBattlefield.spawn(side, member);
            case VAULT_BATTLEFIELD -> VaultBattlefield.spawn(side, member);
        };
    }

    public BasaltCourtyard.Spawn spectator() {
        return switch (this) {
            case COURTYARD -> BasaltCourtyard.spectator();
            case BATTLEFIELD -> BasaltBattlefield.spectator();
            case VAULT_BATTLEFIELD -> VaultBattlefield.spectator();
        };
    }

    public int block(int x, int y, int z) {
        return switch (this) {
            case COURTYARD -> BasaltCourtyard.block(x, y, z);
            case BATTLEFIELD -> BasaltBattlefield.block(x, y, z);
            case VAULT_BATTLEFIELD -> VaultBattlefield.block(x, y, z);
        };
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
        return x >= -halfWidth && x < halfWidth && z >= -halfLength && z < halfLength && y >= floor;
    }
}
