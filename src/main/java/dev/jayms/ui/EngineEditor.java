package dev.jayms.ui;

import dev.jayms.net.city.CityFrame;
import static org.lwjgl.glfw.GLFW.*;

/** Engine workspace. Existing games keep their own simulation and editing tools. */
public final class EngineEditor {
    public boolean open;
    public int selected;
    public enum Command { NONE, PLAY, MODEL }
    private static final String[] NODES = {"World", "Roads", "Buildings", "Citizens"};

    public Command key(int key) {
        if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_ENTER) return Command.PLAY;
        if (key == GLFW_KEY_M) return Command.MODEL;
        if (key == GLFW_KEY_DOWN) selected = (selected + 1) % NODES.length;
        if (key == GLFW_KEY_UP) selected = (selected + NODES.length - 1) % NODES.length;
        return Command.NONE;
    }
    public Command click(float x, float y, int width) {
        if (y >= 58 && y < 94) {
            if (x >= 20 && x < 190) return Command.PLAY;
            if (x >= 200 && x < 390) return Command.MODEL;
        }
        if (x >= 20 && x < width * .28f && y >= 220 && y < 348)
            selected = (int)(y - 220) / 32;
        return Command.NONE;
    }
    public void render(Overlay o, int w, int h, CityFrame city, String shortcut, boolean offline) {
        o.rectangle(0, 0, w, h, .025f, .035f, .055f, .96f);
        o.text("VOXEL ONE / GAME ENGINE EDITOR", 20, 20, 1.6f);
        button(o, "Play game [Enter]", 20, 58, 170);
        button(o, "Voxel assets [M]", 200, 58, 190);
        o.text(shortcut + ": return to workspace", 410, 70, 1);
        float split = w * .28f;
        o.rectangle(16, 114, split - 24, h - 150, .055f, .075f, .105f, 1);
        o.text("PROJECTS", 28, 130, 1.2f);
        String project = city.config().city() ? "Voxel One City Builder" : "Voxel One Sandbox";
        o.text(project, 28, 163, 1);
        o.text("SCENE / Up and Down to select", 28, 196, .85f);
        for (int i = 0; i < NODES.length; i++) {
            if (i == selected) o.rectangle(24, 220 + i * 32, split - 40, 30, .12f, .28f, .38f, 1);
            o.text(NODES[i], 32, 230 + i * 32, 1);
        }
        float left = split + 16;
        o.text("INSPECTOR / " + NODES[selected], left, 130, 1.4f);
        o.text("Game: " + project, left, 172, 1.2f);
        o.text("Runtime: " + (offline ? "Local / simulation paused in workspace" : "Multiplayer / server stays live"), left, 204, 1);
        String value = switch (selected) {
            case 1 -> "Road segments: " + city.roads().size();
            case 2 -> "Buildings: " + city.buildings().size();
            case 3 -> "Citizens in scene: " + city.citizens().size();
            default -> "World seed and saves are managed by the game";
        };
        o.text(value, left, 246, 1.1f);
        o.text("Scene data is read from the current game.", left, 282, 1);
        o.text("WORKFLOW", left, 340, 1.2f);
        o.text("1. Inspect the game scene here.", left, 376, 1);
        o.text("2. Open Voxel assets to sculpt and create model items.", left, 408, 1);
        o.text("3. Play to place assets and build the city.", left, 440, 1);
        o.text("Game saves and controls: Escape while playing.", left, 480, 1);
        o.text("First engine project / more games can add their own tools", 20, h - 24, .9f);
    }
    private void button(Overlay o, String label, float x, float y, float width) {
        o.rectangle(x, y, width, 36, .10f, .30f, .39f, 1);
        o.text(label, x + 10, y + 12, 1);
    }
}
