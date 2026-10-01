package dev.jayms;

import static org.junit.jupiter.api.Assertions.*;

import dev.jayms.net.*;
import dev.jayms.net.model.*;
import dev.jayms.ui.InventoryHud;

import org.junit.jupiter.api.Test;

class InventoryHudTest {
    @Test
    void hoveredNamesFollowBuiltInAndCustomItemsWhenMoved() throws Exception {
        var hud = new InventoryHud();
        var inv = new Inventory();
        var models = new ModelLibrary();
        var pot = ModelGenerators.flowerPot();
        var custom =
                models.register(new ModelDefinition("Jayms Flower Pot", pot.voxels()), "jayms");
        inv.add(Blocks.STONE, 3);
        inv.add(custom.id(), 1);
        assertEquals("", hud.hoveredName(inv, models, 392, 395, 1280, 720));
        hud.open = true;
        assertEquals("Stone", hud.hoveredName(inv, models, 392, 395, 1280, 720));
        assertEquals("Jayms Flower Pot", hud.hoveredName(inv, models, 448, 395, 1280, 720));
        hud.click(448, 395, 1280, 720, inv, inv::swap);
        hud.click(840, 325, 1280, 720, inv, inv::swap);
        assertEquals("", hud.hoveredName(inv, models, 448, 395, 1280, 720));
        assertEquals("Jayms Flower Pot", hud.hoveredName(inv, models, 840, 325, 1280, 720));
        assertEquals("", hud.hoveredName(inv, models, 425, 395, 1280, 720));
        assertEquals("", hud.hoveredName(inv, models, 10, 10, 1280, 720));
    }

    @Test
    void slotCoordinatesMatchAllThirtySixSlotsAndResizedWindows() {
        for (int w : new int[] {640, 1280, 2560})
            for (int h : new int[] {480, 720, 1440})
                for (int row = 0; row < 4; row++)
                    for (int col = 0; col < 9; col++) {
                        float x = w / 2f - 268 + col * 56;
                        float y = h / 2f - 175 + row * 60 + (row == 3 ? 10 : 0);
                        assertEquals(
                                row == 3 ? col : 9 + row * 9 + col,
                                InventoryHud.slotAt(x + 20, y + 20, w, h));
                        assertEquals(-1, InventoryHud.slotAt(x + 53, y + 20, w, h));
                    }
    }
}
