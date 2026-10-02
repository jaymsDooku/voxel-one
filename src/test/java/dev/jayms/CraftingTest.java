package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.ui.InventoryHud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class CraftingTest {
    @TempDir Path temp;

    @Test
    void splittingAndJoiningPreserveMaterialVolumeAcrossEverySubdivision() {
        assertEquals(95, Crafting.recipes().size());
        for (int material : new int[] {Blocks.STONE, Blocks.WOOD, Blocks.BRICKS}) {
            Inventory inventory = new Inventory();
            inventory.add(material, 1);
            for (int depth = 1; depth <= 4; depth++) {
                int child = Blocks.piece(material, depth);
                var recipe =
                        Crafting.recipes().stream()
                                .filter(r -> r.output() == child && r.count() == 8)
                                .findFirst()
                                .orElseThrow();
                var result = Crafting.prepare(inventory, recipe.id());
                assertTrue(result.accepted());
                inventory = result.inventory();
                long volume = 0;
                for (int slot = 0; slot < 36; slot++)
                    volume +=
                            (long) inventory.count(slot)
                                    * (4096 >> (3 * Blocks.depth(inventory.type(slot))));
                assertEquals(4096, volume);
            }
            for (int depth = 4; depth >= 1; depth--) {
                int parent = Blocks.piece(material, depth - 1),
                        child = Blocks.piece(material, depth);
                var recipe =
                        Crafting.recipes().stream()
                                .filter(r -> r.output() == parent && r.inputs().containsKey(child))
                                .findFirst()
                                .orElseThrow();
                var result = Crafting.prepare(inventory, recipe.id());
                assertTrue(result.accepted());
                inventory = result.inventory();
            }
            assertEquals(1, inventory.countType(material));
        }
    }

    @Test
    void missingInputsAndUnknownRecipesNeverConsumeInventory() {
        Inventory inventory = new Inventory();
        inventory.add(Blocks.SAND, 3);
        inventory.add(Blocks.DIRT, 1);
        int pot =
                Crafting.recipes().stream()
                        .filter(r -> r.output() == Blocks.FLOWER_POT)
                        .findFirst()
                        .orElseThrow()
                        .id();
        assertFalse(Crafting.prepare(inventory, pot).accepted());
        assertEquals(3, inventory.countType(Blocks.SAND));
        assertFalse(inventory.consume(Map.of(Blocks.SAND, 3, Blocks.LEAVES, 1)));
        assertEquals(3, inventory.countType(Blocks.SAND));
        assertFalse(Crafting.prepare(inventory, 999).accepted());
        inventory.add(Blocks.LEAVES, 1);
        var result = Crafting.prepare(inventory, pot);
        assertTrue(result.accepted());
        assertEquals(1, result.inventory().countType(Blocks.FLOWER_POT));
        assertEquals(0, result.inventory().countType(Blocks.SAND));
        assertEquals(3, inventory.countType(Blocks.SAND));
    }

    @Test
    void overflowBecomesGroundItemsAndFractionalInventorySurvivesSaveReload() throws Exception {
        var game = new LocalGame(temp.resolve("offline.dat"), 42);
        game.inventory.add(Blocks.STONE, 64);
        game.inventory.add(Blocks.DIRT, 35 * 64);
        int half = Blocks.piece(Blocks.STONE, 1);
        int recipe =
                Crafting.recipes().stream()
                        .filter(r -> r.output() == half && r.count() == 8)
                        .findFirst()
                        .orElseThrow()
                        .id();
        String message = game.craft(recipe, new Protocol.Pose(0, 8, 40, 24, 0, 0));
        assertTrue(message.contains("dropped"));
        assertEquals(63, game.inventory.countType(Blocks.STONE));
        assertEquals(8, game.drops.values().stream().mapToInt(ItemDrop::count).sum());
        game.edits.put("10,70,24/1/0/0/0", new Protocol.Edit(10, 70, 24, half, 1, 0, 0, 0));
        game.save();
        var restored = new LocalGame(temp.resolve("offline.dat"), 100);
        assertEquals(42, restored.seed);
        assertEquals(63, restored.inventory.countType(Blocks.STONE));
        assertEquals(half, restored.edits.values().iterator().next().type());
        assertEquals(8, restored.drops.values().iterator().next().count());
    }

    @Test
    void brokenFractionalPiecesDropAtTheirCenterAndCanBeCollected() throws Exception {
        var game = new LocalGame(temp.resolve("pieces.dat"), 42);
        int piece = Blocks.piece(Blocks.STONE, 4);
        var edit = new Protocol.Edit(8, 30, 21, 0, 4, 15, 8, 15);
        assertTrue(game.edit(edit, piece, 0));
        var drop = game.drops.values().iterator().next();
        assertEquals(edit.minX() + edit.size() / 2, drop.x());
        assertEquals(edit.minZ() + edit.size() / 2, drop.z());
        assertEquals(piece, drop.type());
        game.tick(
                new Protocol.Pose(0, drop.x(), drop.y() - .7f, drop.z(), 0, 0),
                true,
                .016f,
                e -> Blocks.STONE);
        assertTrue(game.drops.isEmpty());
        assertEquals(1, game.inventory.countType(piece));
    }

    @Test
    void recipeTabSubmitsTheSelectedRecipeAndHidesInventoryHover() {
        var hud = new InventoryHud();
        hud.open = true;
        var inventory = new Inventory();
        inventory.add(Blocks.GRASS, 1);
        List<Integer> requests = new ArrayList<>();
        hud.click(800, 145, 1280, 720, inventory, inventory::swap, requests::add);
        hud.click(450, 230, 1280, 720, inventory, inventory::swap, requests::add);
        hud.click(800, 460, 1280, 720, inventory, inventory::swap, requests::add);
        assertEquals(List.of(1), requests);
        assertEquals(
                "",
                hud.hoveredName(
                        inventory, new dev.jayms.net.model.ModelLibrary(), 392, 395, 1280, 720));
    }

    @Test
    void legacyOfflineSavesUpgradeWithoutLosingBlocksOrInventory() throws Exception {
        Path save = temp.resolve("legacy.dat");
        Inventory inventory = new Inventory();
        inventory.add(Blocks.WOOD, 5);
        try (var out = new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(save))) {
            out.writeInt(4);
            out.writeLong(42);
            new dev.jayms.net.model.ModelLibrary().write(out);
            inventory.write(out);
            out.writeByte(17);
            out.writeInt(1);
            out.writeInt(-1);
            out.writeInt(70);
            out.writeInt(-1);
            out.writeInt(Blocks.STONE);
            out.writeInt(0);
        }
        var game = new LocalGame(save, 100);
        assertEquals(5, game.inventory.countType(Blocks.WOOD));
        assertEquals(Blocks.STONE, game.edits.get("-1,70,-1").type());
        game.save();
        var reloaded = new LocalGame(save, 100);
        assertEquals(17, reloaded.health);
        assertEquals(42, reloaded.seed);
        assertEquals(game.edits, reloaded.edits);
    }
}
