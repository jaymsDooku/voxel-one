package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeClient;
import dev.jayms.combat.practice.PracticeMatch.Kit;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.View;
import dev.jayms.combat.practice.PracticeServer;
import dev.jayms.net.SecureTransport;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.ui.Controls;
import dev.jayms.window.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.glfw.GLFW.*;

/** Exercises the desktop input callbacks against an actual local match server. */
public final class PracticeInventoryNativePlaytest implements PracticeGame.FrameObserver {
    private final PracticeClient player, opponent;
    private final Controls controls;
    private final Path output;
    private long start;
    private int step;

    private PracticeInventoryNativePlaytest(PracticeClient player, PracticeClient opponent, Path output)
            throws Exception {
        this.player = player;
        this.opponent = opponent;
        this.output = output;
        controls = new Controls(Controls.directory().resolve("controls.properties"));
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        var tls = SecureTransport.server(Files.createTempDirectory(output.getParent(), "inventory-test-tls-"));
        try (var server = new PracticeServer("127.0.0.1", 0, tls.context(), Kit.NO_DEBUFF);
             var player = new PracticeClient("127.0.0.1", server.port(), tls.fingerprint(), "Aster");
             var opponent = new PracticeClient("127.0.0.1", server.port(), tls.fingerprint(), "Rowan")) {
            var test = new PracticeInventoryNativePlaytest(player, opponent, output);
            new PracticeGame(player, Kit.NO_DEBUFF, false, output).run(test);
            if (test.step != 9) throw new IllegalStateException("Inventory playtest was interrupted at step " + test.step);
        }
    }

    @Override public Intent input(Intent controlsIntent, View view) {
        boolean use = step == 1 && view.phase() == Phase.FIGHT && item(view, 5) == Item.HEALING;
        return new Intent(controlsIntent.sequence(), -90, use ? -80 : 0, 0, 0, false,
                false, false, use, 5);
    }

    @Override public void frame(Window window, ScreenRecorder recorder, View view, int width, int height)
            throws Exception {
        if (start == 0) {
            start = System.nanoTime();
            glfwHideWindow(window.getHandle());
        }
        if (System.nanoTime() - start > TimeUnit.SECONDS.toNanos(20))
            throw new IllegalStateException("Inventory playtest timed out at step " + step + " in " + view.phase());
        long handle = window.getHandle();
        switch (step) {
            case 0 -> {
                if (view.fighters().size() == 2) {
                    player.ready();
                    opponent.ready();
                    step++;
                }
            }
            case 1 -> {
                if (view.phase() == Phase.FIGHT && item(view, 5) == Item.EMPTY) {
                    pressKey(handle, controls.code(Controls.Action.INVENTORY));
                    step++;
                }
            }
            case 2 -> { hover(window, width, height, 21); step++; }
            case 3 -> { shiftClick(handle); step++; }
            case 4 -> {
                if (item(view, 5) == Item.HEALING && item(view, 21) == Item.EMPTY) {
                    hover(window, width, height, 20);
                    step++;
                }
            }
            case 5 -> { pressBinding(handle, controls.code(Controls.Action.SLOT_5)); step++; }
            case 6 -> {
                if (item(view, 4) == Item.HEALING && item(view, 20) == Item.FIRE_RESISTANCE) {
                    hover(window, width, height, 4);
                    step++;
                }
            }
            case 7 -> { shiftClick(handle); step++; }
            case 8 -> {
                if (item(view, 4) == Item.EMPTY && item(view, 21) == Item.HEALING) {
                    PracticeNativePlaytest.screenshot(output.resolve("inventory-shortcuts.png"), width, height);
                    Files.writeString(output.resolve("inventory-playtest.txt"),
                            "Native Windows inventory controls in Basalt Courtyard.\n"
                            + "Shift-click refilled an emptied hotbar slot from reserve.\n"
                            + "Hovered reserve swapped into slot 5 using "
                            + Controls.keyName(controls.code(Controls.Action.SLOT_5)) + ".\n"
                            + "Shift-click moved that potion back to an empty reserve slot.\n");
                    step++;
                    glfwSetWindowShouldClose(handle, true);
                }
            }
            default -> { }
        }
    }

    private static Item item(View view, int slot) {
        return view.inventory().get(slot).item();
    }

    private static void hover(Window window, int width, int height, int slot) {
        int[] size = window.getSize();
        float x = (width - 540) / 2f + slot % 9 * 60 + 30;
        float y = (height - 184) / 2f + slot / 9 * 46 + 23;
        long handle = window.getHandle();
        var callback = glfwSetCursorPosCallback(handle, null);
        glfwSetCursorPosCallback(handle, callback);
        callback.invoke(handle, x * size[0] / width, y * size[1] / height);
    }

    private static void pressKey(long window, int key) {
        var callback = glfwSetKeyCallback(window, null);
        glfwSetKeyCallback(window, callback);
        callback.invoke(window, key, 0, GLFW_PRESS, 0);
    }

    private static void pressBinding(long window, int binding) {
        if (binding < 0) pressMouse(window, -binding - 1, 0);
        else pressKey(window, binding);
    }

    private static void shiftClick(long window) {
        pressMouse(window, GLFW_MOUSE_BUTTON_LEFT, GLFW_MOD_SHIFT);
    }

    private static void pressMouse(long window, int button, int mods) {
        var callback = glfwSetMouseButtonCallback(window, null);
        glfwSetMouseButtonCallback(window, callback);
        callback.invoke(window, button, GLFW_PRESS, mods);
    }
}
