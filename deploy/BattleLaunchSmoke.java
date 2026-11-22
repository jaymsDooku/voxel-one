import dev.jayms.Main;
import dev.jayms.GameIdentity;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowEvent;
import java.util.*;
import javax.swing.*;

/** Exercise the actual Main CLI and catalog launch routes with an isolated home. */
public final class BattleLaunchSmoke {
    static volatile Throwable failure;
    static <T> List<T> components(Container root, Class<T> type) {
        var result = new ArrayList<T>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container child) result.addAll(components(child, type));
        }
        return result;
    }
    static void x(String... args) throws Exception {
        var cmd = new ArrayList<String>(); cmd.add("xdotool"); cmd.addAll(List.of(args));
        var process = new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        String error = new String(process.getErrorStream().readAllBytes());
        if (process.waitFor() != 0)
            throw new AssertionError("Native launch input failed: " + args[0] + ": " + error.substring(0, Math.min(240, error.length())));
    }
    public static void main(String[] args) throws Exception {
        boolean catalog = args[0].equals("catalog");
        var driver = new Thread(() -> {
            try {
                if (catalog) {
                    boolean[] selected = {false};
                    for (int i = 0; i < 300 && !selected[0]; i++) {
                        SwingUtilities.invokeAndWait(() -> {
                            for (var frame : java.awt.Frame.getFrames()) if (frame.isVisible()) {
                                for (var combo : components(frame, JComboBox.class)) if (combo.getItemCount() > 0 && combo.getItemAt(0) instanceof GameIdentity) {
                                    combo.setSelectedItem(GameIdentity.BATTLE);
                                    var buttons = components(frame, JButton.class);
                                    for (var button : buttons) if (button.getText().equals("Sign in") && button.isEnabled()) throw new AssertionError("Offline catalog must disable sign in");
                                    var open = buttons.stream().filter(b -> b.getText().equals("Open arena")).findFirst().orElseThrow();
                                    open.doClick(); selected[0] = true;
                                }
                            }
                        });
                        if (!selected[0]) Thread.sleep(20);
                    }
                    if (!selected[0]) throw new AssertionError("Battle catalog entry not found");
                }
                String id = "";
                for (int i = 0; i < 300 && id.isEmpty(); i++) {
                    var p = new ProcessBuilder("xdotool", "search", "--onlyvisible", "--name", "^Voxel Battle Simulator$")
                            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    id = new String(p.getInputStream().readAllBytes()).trim(); p.waitFor(); if (id.isEmpty()) Thread.sleep(30);
                }
                if (id.isEmpty()) throw new AssertionError("Main did not launch arena");
                id = id.lines().findFirst().orElseThrow();
                System.out.println("Launch stage: native arena found");
                Thread.sleep(600); x("windowfocus", id); Thread.sleep(300);
                x("key", "--window", id, "r"); Thread.sleep(250);
                // A full key command can send key-up after Escape has destroyed the window.
                x("keydown", "--window", id, "Escape"); Thread.sleep(80); x("keyup", "Escape");
                System.out.println("Launch stage: R and Escape sent");
                if (catalog) {
                    for (int i = 0; i < 100; i++) {
                        SwingUtilities.invokeAndWait(() -> { for (var frame : java.awt.Frame.getFrames()) if (frame.isVisible()) frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); });
                        Thread.sleep(30);
                    }
                }
            } catch (Throwable e) {
                failure = e;
                System.err.println("Launch assertion: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                System.exit(2);
            }
        }, "battle-launch-input");
        driver.setDaemon(true); driver.start();
        Main.main(catalog ? new String[0] : new String[]{"--game", "battle", "--offline"});
        System.out.println("Launch stage: Main returned");
        driver.join();
        if (failure != null) throw new AssertionError("Main launch workflow failed", failure);
        System.out.println("PASS: Main " + args[0] + " battle launch, R reset and Escape");
    }
}
