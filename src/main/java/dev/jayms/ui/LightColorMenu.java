package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Device-local placement colour; the chosen RGB value is stored on each world light. */
public final class LightColorMenu {
    public boolean open;
    private int color = 0xff8844;
    private String draft = "FF8844", message = "Choose any six-digit RGB colour, then Apply.";
    private final Path file;

    public LightColorMenu(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file))
            try (var in = Files.newInputStream(file)) {
                var p = new Properties();
                p.load(in);
                try {
                    int rgb = Integer.parseInt(p.getProperty("rgb", "FF8844"), 16);
                    if (rgb >= 0 && rgb <= 0xffffff) color = rgb;
                } catch (NumberFormatException ignored) {
                }
            }
        draft = String.format("%06X", color);
    }

    public int color() {
        return color;
    }

    public void show() {
        draft = String.format("%06X", color);
        open = true;
    }

    public void character(int value) {
        if (draft.length() < 6 && Character.digit((char) value, 16) >= 0)
            draft += Character.toUpperCase((char) value);
    }

    public void key(int key, int action, int mods) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        if (key == GLFW_KEY_ESCAPE) open = false;
        else if (key == GLFW_KEY_ENTER) apply();
        else if (key == GLFW_KEY_BACKSPACE && !draft.isEmpty())
            draft = draft.substring(0, draft.length() - 1);
        else if (key == GLFW_KEY_A && (mods & GLFW_MOD_CONTROL) != 0) draft = "";
    }

    private void apply() {
        if (!draft.matches("[0-9A-Fa-f]{6}")) {
            message = "Enter exactly six hexadecimal digits.";
            return;
        }
        int next = Integer.parseInt(draft, 16);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            var p = new Properties();
            p.setProperty("rgb", draft);
            try (var out = Files.newOutputStream(file)) {
                p.store(out, "Voxel One LED placement colour");
            }
            color = next;
            open = false;
        } catch (IOException e) {
            message = "Could not save the light colour.";
        }
    }

    public void click(float mx, float my, int w, int h) {
        float x = w / 2f - 240, y = h / 2f - 170;
        if (mx >= x + 310 && mx < x + 450 && my >= y + 280 && my < y + 324) {
            apply();
            return;
        }
        if (mx >= x + 20 && mx < x + 150 && my >= y + 280 && my < y + 324) {
            open = false;
            return;
        }
        int[] presets = {
            0xff8844, 0xff3b59, 0x38a5ff, 0x70ff9c, 0xffe5af, 0xe264ff, 0xffffff, 0x000000
        };
        if (my >= y + 218 && my < y + 252 && mx >= x + 20 && mx < x + 452) {
            int at = Math.min(7, (int) ((mx - x - 20) / 54));
            draft = String.format("%06X", presets[at]);
            return;
        }
        for (int c = 0; c < 3; c++)
            if (mx >= x + 85 && mx <= x + 445 && my >= y + 105 + c * 32 && my < y + 129 + c * 32) {
                int rgb = draft.matches("[0-9A-Fa-f]{6}") ? Integer.parseInt(draft, 16) : color;
                int shift = 16 - c * 8,
                        value = Math.max(0, Math.min(255, Math.round((mx - x - 85) / 360 * 255)));
                draft = String.format("%06X", (rgb & ~(255 << shift)) | (value << shift));
            }
    }

    public void render(Overlay ui, int w, int h) {
        if (!open) return;
        float x = w / 2f - 240, y = h / 2f - 170;
        ui.rectangle(0, 0, w, h, .01f, .02f, .04f, .76f);
        ui.rectangle(x, y, 480, 340, .025f, .06f, .10f, 1);
        ui.text("LED LIGHT COLOUR", x + 20, y + 20, 2.3f);
        ui.text("RGB hex: #" + draft + "_", x + 20, y + 61, 2);
        int rgb = draft.matches("[0-9A-Fa-f]{6}") ? Integer.parseInt(draft, 16) : color;
        ui.rectangle(
                x + 355,
                y + 54,
                90,
                30,
                (rgb >>> 16 & 255) / 255f,
                (rgb >>> 8 & 255) / 255f,
                (rgb & 255) / 255f,
                1);
        for (int c = 0; c < 3; c++) {
            int value = rgb >>> (16 - c * 8) & 255;
            ui.text(new String[] {"Red", "Green", "Blue"}[c], x + 20, y + 111 + c * 32, 1.5f);
            ui.rectangle(x + 85, y + 105 + c * 32, 360, 24, .06f, .14f, .19f, 1);
            ui.rectangle(
                    x + 85,
                    y + 105 + c * 32,
                    360 * value / 255f,
                    24,
                    c == 0 ? .85f : .12f,
                    c == 1 ? .85f : .12f,
                    c == 2 ? .85f : .12f,
                    1);
            ui.text("" + value, x + 95, y + 111 + c * 32, 1.5f);
        }
        ui.text("Ctrl+A clears hex | Enter applies | Esc cancels", x + 20, y + 195, 1.15f);
        int[] presets = {
            0xff8844, 0xff3b59, 0x38a5ff, 0x70ff9c, 0xffe5af, 0xe264ff, 0xffffff, 0x000000
        };
        for (int i = 0; i < presets.length; i++) {
            int p = presets[i];
            ui.rectangle(
                    x + 20 + i * 54,
                    y + 218,
                    46,
                    34,
                    (p >>> 16 & 255) / 255f,
                    (p >>> 8 & 255) / 255f,
                    (p & 255) / 255f,
                    1);
        }
        ui.text(message, x + 20, y + 263, 1.15f);
        ui.rectangle(x + 20, y + 280, 130, 44, .07f, .18f, .25f, 1);
        ui.text("Cancel", x + 52, y + 294, 1.5f);
        ui.rectangle(x + 310, y + 280, 140, 44, .07f, .40f, .30f, 1);
        ui.text("Apply", x + 354, y + 294, 1.5f);
    }
}
