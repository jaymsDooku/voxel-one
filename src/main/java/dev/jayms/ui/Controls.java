package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Controls {
    public enum Action {
        FORWARD("Walk forward", GLFW_KEY_W),
        BACKWARD("Walk backward", GLFW_KEY_S),
        LEFT("Strafe left", GLFW_KEY_A),
        RIGHT("Strafe right", GLFW_KEY_D),
        JUMP("Jump / fly up", GLFW_KEY_SPACE),
        DESCEND("Fly down", GLFW_KEY_LEFT_CONTROL),
        SPRINT("Sprint", GLFW_KEY_LEFT_SHIFT),
        FLY("Toggle flight", GLFW_KEY_F),
        VIEW("Cycle camera", GLFW_KEY_F5),
        CURSOR("Release mouse", GLFW_KEY_TAB),
        BREAK("Break block", -1),
        PLACE("Place block", -2),
        MENU("Controls menu", GLFW_KEY_F1),
        INVENTORY("Inventory", GLFW_KEY_E),
        SLOT_1("Hotbar slot 1", GLFW_KEY_1),
        SLOT_2("Hotbar slot 2", GLFW_KEY_2),
        SLOT_3("Hotbar slot 3", GLFW_KEY_3),
        SLOT_4("Hotbar slot 4", GLFW_KEY_4),
        SLOT_5("Hotbar slot 5", GLFW_KEY_5),
        SLOT_6("Hotbar slot 6", GLFW_KEY_6),
        SLOT_7("Hotbar slot 7", GLFW_KEY_7),
        SLOT_8("Hotbar slot 8", GLFW_KEY_8),
        SLOT_9("Hotbar slot 9", GLFW_KEY_9),
        ISOMETRIC("Isometric sky view", GLFW_KEY_F6),
        ZOOM_IN("Sky view: zoom in", GLFW_KEY_EQUAL),
        ZOOM_OUT("Sky view: zoom out", GLFW_KEY_MINUS),
        FIT_VIEW("Sky view: fit world", GLFW_KEY_HOME),
        MODEL_EDITOR("Voxel model editor", GLFW_KEY_F7),
        LIGHT_COLOR("LED light colour", GLFW_KEY_F8),
        DISMOUNT("Dismount horse", GLFW_KEY_H),
        MAYOR_DASHBOARD("Mayor dashboard", GLFW_KEY_F9),
        RECORD("Start / stop recording", GLFW_KEY_F10),
        ROTATE_LEFT("Sky view: rotate left", GLFW_KEY_LEFT),
        ROTATE_RIGHT("Sky view: rotate right", GLFW_KEY_RIGHT),
        JEEP("Enter / exit vehicle (Shift: body)", GLFW_KEY_J);
        public final String label;
        public final int defaultCode;

        Action(String label, int code) {
            this.label = label;
            defaultCode = code;
        }
    }

    private final EnumMap<Action, Integer> bindings = new EnumMap<>(Action.class);
    private final Path file;
    public float sensitivity = .1f;

    public Controls(Path file) throws IOException {
        this.file = file;
        reset();
        if (Files.exists(file)) {
            Properties p = new Properties();
            try (var in = Files.newInputStream(file)) {
                p.load(in);
            }
            EnumMap<Action, Integer> loaded = new EnumMap<>(Action.class);
            Set<Integer> used = new HashSet<>();
            boolean validFile = true;
            for (Action a : Action.values()) {
                try {
                    int preferred = a.defaultCode;
                    if (!p.containsKey(a.name()) && used.contains(preferred)) {
                        preferred = 0;
                        for (int candidate = GLFW_KEY_F1; candidate <= GLFW_KEY_F25; candidate++)
                            if (!used.contains(candidate)) {
                                preferred = candidate;
                                break;
                            }
                        if (preferred == 0)
                            for (int candidate = GLFW_KEY_SPACE;
                                    candidate <= GLFW_KEY_LAST;
                                    candidate++)
                                if (valid(candidate) && !used.contains(candidate)) {
                                    preferred = candidate;
                                    break;
                                }
                    }
                    int code =
                            Integer.parseInt(p.getProperty(a.name(), Integer.toString(preferred)));
                    if (!valid(code) || !used.add(code)) validFile = false;
                    loaded.put(a, code);
                } catch (NumberFormatException ignored) {
                    validFile = false;
                }
            }
            if (validFile) bindings.putAll(loaded);
            try {
                sensitivity =
                        Math.max(
                                .02f,
                                Math.min(
                                        .5f, Float.parseFloat(p.getProperty("sensitivity", ".1"))));
                if (!Float.isFinite(sensitivity)) sensitivity = .1f;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static Path directory() {
        return Path.of(System.getProperty("user.home"), ".voxel-one");
    }

    public void reset() {
        for (Action a : Action.values()) bindings.put(a, a.defaultCode);
        sensitivity = .1f;
    }

    private static boolean valid(int code) {
        return code >= -8
                && code != 0
                && code != GLFW_KEY_ESCAPE
                && (code < 0 || code >= GLFW_KEY_SPACE && code <= GLFW_KEY_LAST);
    }

    public int code(Action action) {
        return bindings.get(action);
    }

    public boolean matches(Action action, int code) {
        return bindings.get(action) == code;
    }

    /**
     * Swap conflicting actions so every action remains available. Escape always closes the menu.
     */
    public void bind(Action action, int code) throws IOException {
        if (!valid(code))
            throw new IOException("Choose a keyboard key or mouse button. Escape is reserved.");
        int old = bindings.get(action);
        for (Action a : Action.values())
            if (a != action && bindings.get(a) == code) bindings.put(a, old);
        bindings.put(action, code);
        save();
    }

    public boolean down(long window, Action action) {
        int code = code(action);
        return code < 0
                ? glfwGetMouseButton(window, -code - 1) == GLFW_PRESS
                : glfwGetKey(window, code) == GLFW_PRESS;
    }

    public void save() throws IOException {
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Properties p = new Properties();
        bindings.forEach((a, code) -> p.setProperty(a.name(), code.toString()));
        p.setProperty("sensitivity", Float.toString(sensitivity));
        Path temp = Files.createTempFile(absolute.getParent(), "controls-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) {
                p.store(out, "Voxel One controls");
            }
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static String keyName(int code) {
        if (code < 0)
            return switch (code) {
                case -1 -> "Mouse left";
                case -2 -> "Mouse right";
                case -3 -> "Mouse middle";
                default -> "Mouse " + -code;
            };
        return switch (code) {
            case GLFW_KEY_HOME -> "Home";
            case GLFW_KEY_SPACE -> "Space";
            case GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
            case GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
            case GLFW_KEY_TAB -> "Tab";
            case GLFW_KEY_ENTER -> "Enter";
            case GLFW_KEY_UP -> "Up";
            case GLFW_KEY_DOWN -> "Down";
            case GLFW_KEY_LEFT -> "Left";
            case GLFW_KEY_RIGHT -> "Right";
            default -> {
                if (code >= GLFW_KEY_F1 && code <= GLFW_KEY_F25)
                    yield "F" + (code - GLFW_KEY_F1 + 1);
                String name = glfwGetKeyName(code, 0);
                yield name == null ? "Key " + code : name.toUpperCase(Locale.ROOT);
            }
        };
    }
}
