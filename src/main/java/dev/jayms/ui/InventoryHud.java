package dev.jayms.ui;

import dev.jayms.net.*;
import dev.jayms.net.model.*;

import java.util.function.BiConsumer;

public final class InventoryHud {
    public boolean open;
    public int selected;
    private boolean crafting;
    private int firstRecipe, selectedRecipe;
    private int source = -1;
    private static final int ICON_SIZE = 16;
    private final java.util.Map<ModelLibrary.Entry, int[]> icons = new java.util.HashMap<>();

    private int[] icon(ModelLibrary.Entry entry) {
        return icons.computeIfAbsent(
                entry,
                e -> {
                    int[] result = new int[ICON_SIZE * ICON_SIZE];
                    int n = e.definition().voxels().size();
                    for (int y = 0; y < ICON_SIZE; y++)
                        for (int x = 0; x < ICON_SIZE; x++) {
                            var hit =
                                    e.definition()
                                            .voxels()
                                            .raycast(
                                                    (x + .5f) / ICON_SIZE,
                                                    (ICON_SIZE - 1 - y + .5f) / ICON_SIZE,
                                                    2,
                                                    0,
                                                    0,
                                                    -1,
                                                    0,
                                                    3);
                            if (hit != null) {
                                float z = 2 - hit.distance() - .00001f;
                                result[x + y * ICON_SIZE] =
                                        e.definition()
                                                .voxels()
                                                .get(
                                                        (int) ((x + .5f) / ICON_SIZE * n),
                                                        (int)
                                                                ((ICON_SIZE - 1 - y + .5f)
                                                                        / ICON_SIZE
                                                                        * n),
                                                        Math.max(
                                                                0, Math.min(n - 1, (int) (z * n))));
                            }
                        }
                    return result;
                });
    }

    public void toggle() {
        open = !open;
        source = -1;
        crafting = false;
    }

    public void close() {
        open = false;
        source = -1;
    }

    public void scroll(double amount) {
        if (open && crafting) {
            firstRecipe =
                    Math.max(
                            0,
                            Math.min(
                                    Crafting.recipes().size() - 6,
                                    firstRecipe - (int) Math.signum(amount) * 3));
            return;
        }
        selected = Math.floorMod(selected - (int) Math.signum(amount), 9);
    }

    private void slot(
            Overlay ui,
            Inventory inv,
            int i,
            float x,
            float y,
            float size,
            boolean active,
            ModelLibrary models) {
        ui.rectangle(
                x,
                y,
                size - 3,
                size - 3,
                active ? .12f : .035f,
                active ? .43f : .085f,
                active ? .53f : .13f,
                .96f);
        if (active) {
            ui.rectangle(x, y, size - 3, 2, .3f, .95f, 1, 1);
            ui.rectangle(x, y + size - 5, size - 3, 2, .3f, .95f, 1, 1);
        }
        int type = inv.type(i);
        if (type != 0) {
            var model = models.get(type);
            if (model != null) {
                int[] pixels = icon(model);
                for (int iy = 0; iy < ICON_SIZE; iy++)
                    for (int ix = 0; ix < ICON_SIZE; ix++) {
                        int c = pixels[ix + iy * ICON_SIZE];
                        if (c != 0)
                            ui.rectangle(
                                    x + ix * 2 + 8,
                                    y + iy * 2 + 7,
                                    2,
                                    2,
                                    ((c >>> 16) & 255) / 255f,
                                    ((c >>> 8) & 255) / 255f,
                                    (c & 255) / 255f,
                                    1);
                    }
            } else {
                float[] c = Blocks.color(type);
                float iconSide = Math.max(7, 25f / (1 << Blocks.depth(type)));
                float ix = x + 11 + (25 - iconSide) / 2, iy = y + 8 + (25 - iconSide) / 2;
                ui.rectangle(
                        ix + 2,
                        iy + 3,
                        iconSide,
                        iconSide,
                        c[0] * .65f,
                        c[1] * .65f,
                        c[2] * .65f,
                        1);
                ui.rectangle(ix, iy, iconSide, iconSide, c[0], c[1], c[2], 1);
                ui.rectangle(
                        ix,
                        iy,
                        iconSide,
                        Math.min(5, iconSide / 3),
                        Math.min(1, c[0] * 1.2f),
                        Math.min(1, c[1] * 1.2f),
                        Math.min(1, c[2] * 1.2f),
                        1);
            }
            ui.text("" + inv.count(i), x + size - 23, y + size - 19, 1.4f);
        }
    }

    public void render(
            Overlay ui,
            Inventory inv,
            int hp,
            int w,
            int h,
            Controls controls,
            ModelLibrary models,
            float mx,
            float my) {
        float size = 56, left = w / 2f - size * 9 / 2, top = h - 72;
        ui.rectangle(left - 7, top - 36, size * 9 + 11, 30, .02f, .05f, .09f, .85f);
        ui.text("HEALTH", left + 4, top - 28, 1.5f);
        for (int i = 0; i < 20; i++)
            ui.rectangle(
                    left + 90 + i * 17,
                    top - 26,
                    14,
                    13,
                    i < hp ? .9f : .12f,
                    i < hp ? .23f : .16f,
                    i < hp ? .3f : .20f,
                    1);
        ui.text(hp + "/20", left + 437, top - 28, 1.4f);
        for (int i = 0; i < 9; i++) {
            slot(ui, inv, i, left + i * size, top, size, i == selected, models);
            ui.text(
                    Controls.keyName(
                            controls.code(
                                    Controls.Action.values()[
                                            Controls.Action.SLOT_1.ordinal() + i])),
                    left + i * size + 4,
                    top + 3,
                    1.1f);
        }
        ui.text(
                models.name(inv.type(selected))
                        + " | "
                        + Controls.keyName(controls.code(Controls.Action.INVENTORY))
                        + ": inventory",
                left,
                top - 56,
                1.5f);
        if (!open) return;
        float x = w / 2f - 268, y = h / 2f - 175;
        ui.rectangle(0, 0, w, h, .01f, .025f, .045f, .65f);
        ui.rectangle(x - 18, y - 65, 540, 362, .025f, .06f, .11f, .98f);
        ui.rectangle(x - 18, y - 65, 540, 3, .15f, .85f, 1, 1);
        ui.text(
                crafting
                        ? "CRAFTING / " + Crafting.recipes().size() + " RECIPES"
                        : "INVENTORY / 36 SLOTS",
                x,
                y - 43,
                2.1f);
        ui.rectangle(x + 385, y - 49, 115, 28, .07f, .24f, .32f, 1);
        ui.text(crafting ? "Inventory" : "Recipes", x + 394, y - 41, 1.5f);
        if (crafting) {
            renderCrafting(ui, inv, x, y);
            return;
        }
        ui.text("Click a stack, then a destination to move or merge it.", x, y - 15, 1.4f);
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 9; col++) {
                int index = row == 3 ? col : 9 + row * 9 + col;
                slot(
                        ui,
                        inv,
                        index,
                        x + col * 56,
                        y + row * 60 + (row == 3 ? 10 : 0),
                        56,
                        index == source,
                        models);
            }
        ui.text("Bottom row is your hotbar. Escape closes inventory.", x, y + 265, 1.4f);
        if (source >= 0) ui.text("Selected: " + models.name(inv.type(source)), x, y + 285, 1.3f);
        String name = hoveredName(inv, models, mx, my, w, h);
        if (!name.isEmpty()) {
            float width = ui.textWidth(name, 1.5f) + 18, height = 29;
            float tx = Math.max(0, Math.min(w - width, mx + 14));
            float ty = my + 16 + height > h ? Math.max(0, my - height - 8) : my + 16;
            ui.rectangle(tx, ty, width, height, .025f, .045f, .085f, .98f);
            ui.rectangle(tx, ty, width, 2, .25f, .8f, 1, 1);
            ui.text(name, tx + 9, ty + 8, 1.5f);
        }
    }

    public void click(
            float mx,
            float my,
            int w,
            int h,
            Inventory inventory,
            BiConsumer<Integer, Integer> swap) {
        click(mx, my, w, h, inventory, swap, id -> {});
    }

    public void click(
            float mx,
            float my,
            int w,
            int h,
            Inventory inventory,
            BiConsumer<Integer, Integer> swap,
            java.util.function.IntConsumer craft) {
        float x = w / 2f - 268, y = h / 2f - 175;
        if (mx >= x + 385 && mx < x + 500 && my >= y - 49 && my < y - 21) {
            crafting = !crafting;
            source = -1;
            return;
        }
        if (crafting) {
            if (mx >= x && mx < x + 504 && my >= y + 15 && my < y + 195) {
                selectedRecipe =
                        Math.min(
                                Crafting.recipes().size() - 1,
                                firstRecipe + (int) ((my - y - 15) / 30));
                return;
            }
            if (mx >= x + 370 && mx < x + 504 && my >= y + 260 && my < y + 292)
                craft.accept(selectedRecipe);
            return;
        }
        int index = slotAt(mx, my, w, h);
        if (index < 0) {
            source = -1;
        } else if (source < 0) {
            if (inventory.count(index) > 0) source = index;
        } else {
            if (source != index) swap.accept(source, index);
            source = -1;
        }
    }

    private void renderCrafting(Overlay ui, Inventory inventory, float x, float y) {
        ui.text("Scroll to browse. Select a recipe, then Craft.", x, y - 15, 1.4f);
        for (int row = 0; row < 6; row++) {
            var recipe = Crafting.recipe(firstRecipe + row);
            if (recipe == null) break;
            boolean active = recipe.id() == selectedRecipe, available = recipe.available(inventory);
            ui.rectangle(
                    x,
                    y + 15 + row * 30,
                    504,
                    27,
                    active ? .1f : .03f,
                    active ? .27f : .10f,
                    active ? .34f : .16f,
                    1);
            ui.text(
                    recipe.name() + " -> " + recipe.count() + " " + Blocks.name(recipe.output()),
                    x + 7,
                    y + 23 + row * 30,
                    1.15f,
                    available ? .75f : .5f,
                    available ? 1 : .55f,
                    available ? .84f : .62f,
                    1);
        }
        var selected = Crafting.recipe(selectedRecipe);
        ui.text(
                (firstRecipe + 1)
                        + "-"
                        + Math.min(firstRecipe + 6, Crafting.recipes().size())
                        + " / "
                        + Crafting.recipes().size()
                        + " recipes",
                x,
                y + 200,
                1.2f);
        ui.text("Requires:", x, y + 225, 1.3f);
        String inputs = selected.ingredients(inventory);
        if (inputs.length() > 62) {
            int at = inputs.lastIndexOf(" | ", 62);
            if (at < 0) at = 62;
            ui.text(inputs.substring(0, at), x, y + 242, 1.15f);
            ui.text(inputs.substring(at).stripLeading(), x, y + 257, 1.15f);
        } else ui.text(inputs, x, y + 242, 1.15f);
        boolean available = selected.available(inventory);
        ui.rectangle(x + 370, y + 260, 134, 32, .08f, available ? .42f : .16f, .28f, 1);
        ui.text("Craft", x + 414, y + 270, 1.6f);
        ui.text("Overflow drops on the ground.", x, y + 281, 1.2f);
    }

    public String hoveredName(
            Inventory inventory, ModelLibrary models, float mx, float my, int w, int h) {
        if (!open || crafting) return "";
        int index = slotAt(mx, my, w, h);
        return index < 0 || inventory.count(index) == 0 ? "" : models.name(inventory.type(index));
    }

    public static int slotAt(float mx, float my, int w, int h) {
        float x = w / 2f - 268, y = h / 2f - 175;
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 9; col++) {
                float sx = x + col * 56, sy = y + row * 60 + (row == 3 ? 10 : 0);
                if (mx >= sx && mx < sx + 53 && my >= sy && my < sy + 53)
                    return row == 3 ? col : 9 + row * 9 + col;
            }
        return -1;
    }
}
