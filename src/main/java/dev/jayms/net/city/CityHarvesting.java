package dev.jayms.net.city;

import dev.jayms.net.*;

import java.util.*;
import java.util.function.BiPredicate;

/** Natural deposits are checked against the live world and removed before crediting their owner. */
public final class CityHarvesting {
    private record Node(int x, int y, int z) {}

    private final CitySimulation.Ground ground;
    private final Terrain terrain;
    private final BiPredicate<Integer, Integer> protectedLand;
    private final Map<Integer, Deposit> deposits = new HashMap<>();

    private final class Deposit {
        final int material;
        int radius = 32, index;
        final ArrayDeque<Node> pending = new ArrayDeque<>();

        Deposit(int material) {
            this.material = material;
        }

        Node next() {
            // Bound search work per tick; continue through the region instead of declaring
            // the first small batch to be the entire natural deposit.
            for (int searched = 0;
                    pending.isEmpty() && radius <= 512 && searched < 4096;
                    searched++) {
                int side = index / (radius * 2), offset = index % (radius * 2);
                int x =
                        8
                                + (side == 0
                                        ? -radius + offset
                                        : side == 1
                                                ? radius
                                                : side == 2 ? radius - offset : -radius);
                int z =
                        24
                                + (side == 0
                                        ? -radius
                                        : side == 1
                                                ? -radius + offset
                                                : side == 2 ? radius : radius - offset);
                index++;
                if (index >= radius * 8) {
                    index = 0;
                    radius++;
                }
                if (protectedLand.test(x, z)) continue;
                var column = terrain.column(x, z);
                if (material == Blocks.WOOD && column.biome() != Terrain.Biome.FOREST
                        || material == Blocks.SAND && column.biome() != Terrain.Biome.DESERT
                        || material == Blocks.GRASS
                                && column.biome() != Terrain.Biome.FOREST
                                && column.biome() != Terrain.Biome.PLAINS) continue;
                int lo =
                        material == Blocks.WOOD
                                ? column.height() + 1
                                : material == Blocks.STONE
                                        ? column.height() - 16
                                        : column.height() - (material == Blocks.SAND ? 2 : 0);
                int hi =
                        material == Blocks.WOOD
                                ? column.height() + 8
                                : material == Blocks.STONE ? column.height() - 5 : column.height();
                for (int y = Math.max(Terrain.MIN_Y + 3, lo); y <= Math.min(Terrain.MAX_Y, hi); y++)
                    if (terrain.block(x, y, z) == material) pending.add(new Node(x, y, z));
            }
            return pending.pollFirst();
        }
    }

    private Node field;

    public CityHarvesting(
            CitySimulation.Ground ground,
            Terrain terrain,
            BiPredicate<Integer, Integer> protectedLand) {
        this.ground = ground;
        this.terrain = terrain;
        this.protectedLand = protectedLand;
    }

    private Deposit deposits(int material) {
        return deposits.computeIfAbsent(material, Deposit::new);
    }

    public boolean harvest(int material) {
        var nodes = deposits(material);
        for (int tries = 0; tries < 64; tries++) {
            var n = nodes.next();
            if (n == null) return false;
            if (protectedLand.test(n.x, n.z)
                    || ground.type(n.x, n.y, n.z) != material
                    || ground.occupied(n.x, n.y, n.z, 1, 1)) continue;
            ground.apply(List.of(new Protocol.Edit(n.x, n.y, n.z, Blocks.AIR)));
            return true;
        }
        return false;
    }

    /** Crops regrow through paid game time, on an actual fertile grass field, not in a mine. */
    public boolean fertileField() {
        if (field != null
                && !protectedLand.test(field.x, field.z)
                && ground.type(field.x, field.y, field.z) == Blocks.GRASS) return true;
        var nodes = deposits(Blocks.GRASS);
        for (int tries = 0; tries < 64; tries++) {
            var n = nodes.next();
            if (n == null) return false;
            if (!protectedLand.test(n.x, n.z) && ground.type(n.x, n.y, n.z) == Blocks.GRASS) {
                field = n;
                return true;
            }
        }
        return false;
    }

    public void work(CityEconomy economy, CityEconomy.Company firm, double hours) {
        var stocks = economy.resources;
        int output = CityMaterials.output(firm.kind);
        var old = stocks.production(firm.id);
        double progress = old.progress() + hours * (firm.kind == CityMaterials.FARM ? 16 : 256);
        int cycles = Math.min(512, (int) progress);
        progress -= (int) progress;
        long harvested = 0, processed = 0;
        String status = cycles == 0 ? old.status() : "Working";
        for (int i = 0; i < cycles; i++) {
            if (stocks.available(0, firm.id, output)
                    >= CityMaterials.capacity(firm.kind) * CityMaterials.UNIT) {
                status = "Storage full";
                break;
            }
            int raw =
                    firm.kind == CityMaterials.LOGGING
                            ? Blocks.WOOD
                            : firm.kind == CityMaterials.GLASSWORKS
                                    ? Blocks.SAND
                                    : firm.kind == CityEconomy.MINE ? Blocks.STONE : 0;
            if (raw != 0 && stocks.available(0, firm.id, raw) < 32 * CityMaterials.UNIT) {
                if (!harvest(raw)) {
                    status =
                            deposits(raw).radius > 512
                                    ? "Natural deposit exhausted"
                                    : "Searching for natural deposit";
                    break;
                }
                stocks.add(0, firm.id, raw, CityMaterials.UNIT);
                harvested++;
                if (raw == output) {
                    processed++;
                    continue;
                }
            }
            if (firm.kind == CityMaterials.FARM) {
                if (!fertileField()) {
                    status = "No fertile field";
                    break;
                }
                stocks.add(0, firm.id, CityMaterials.FOOD, CityMaterials.UNIT);
                harvested++;
                processed++;
                continue;
            }
            if (firm.kind == CityMaterials.MASONRY)
                economy.purchase(firm.id, Blocks.STONE, 4 * CityMaterials.UNIT);
            if (firm.kind == CityMaterials.LIGHTING) {
                economy.purchase(firm.id, Blocks.STONE, CityMaterials.UNIT);
                economy.purchase(firm.id, Blocks.GLASS, CityMaterials.UNIT);
            }
            if (firm.kind == CityEconomy.MINE) {
                if (!harvest(Blocks.STONE)) {
                    status =
                            deposits(Blocks.STONE).radius > 512
                                    ? "Natural deposit exhausted"
                                    : "Searching for natural deposit";
                    break;
                }
                stocks.add(0, firm.id, Blocks.STONE, CityMaterials.UNIT);
                harvested++;
                processed++;
                continue;
            }
            // Keep logs for exposed beams and sand for trade; refinement consumes the surplus.
            if (raw != 0 && stocks.available(0, firm.id, raw) <= 16 * CityMaterials.UNIT) continue;
            int result = stocks.craft(firm.id, output);
            if (result == 0) {
                status = "Waiting for input materials";
                continue;
            }
            processed += result;
        }
        stocks.production(firm.id, progress, harvested, processed, status);
        economy.businesses.produced(economy.account(firm.id), (int) processed);
    }
}
