package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import dev.jayms.*;
import dev.jayms.net.model.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Layer painting and orbit preview edit the same octree used by world models. */
public final class ModelEditor implements AutoCloseable {
    private static final int[] PALETTE = {
        ModelGenerators.CLAY,
        ModelGenerators.RIM,
        ModelGenerators.SOIL,
        ModelGenerators.STEM,
        ModelGenerators.LEAF,
        ModelGenerators.PETAL,
        ModelGenerators.CENTER,
        0xffe5edf6,
        0xff425178,
        0xff302b39
    };
    private ModelDefinition definition = ModelGenerators.flowerPot();
    private final Deque<ModelDefinition> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Mesh mesh;
    private boolean dirty = true, nameFocus, selectName, painting;
    private String nameDraft = "";
    private int axis = 2, layer = 16, color = 0, dragButton = -1, lastCell = -1;
    private float yaw = .75f, pitch = .4f, zoom = 1, lastX, lastY;
    public boolean open;
    public String message =
            "Paint with left click; erase with right click. Create item to place your model.";
    private final Path draft = Controls.directory().resolve("models/draft.vxm");

    private record Layout(
            float left, float top, float size, float previewWidth, float previewHeight) {}

    private Layout layout(int w, int h) {
        float size = Math.min(384, Math.min(w * .43f, h - 320));
        return new Layout(w - size - 24, 170, size, w - size - 72, h - 170);
    }

    public ModelDefinition snapshot() {
        return new ModelDefinition(
                nameFocus ? nameDraft : definition.name(), definition.voxels().copy());
    }

    public void load(ModelDefinition model) {
        history();
        definition = new ModelDefinition(model.name(), model.voxels().copy());
        layer = Math.min(layer, model.voxels().size() - 1);
        dirty = true;
        nameFocus = false;
        message = "Loaded " + model.name() + ". Editing a copy.";
    }

    private void history() {
        undo.addLast(new ModelDefinition(definition.name(), definition.voxels().copy()));
        if (undo.size() > 64) undo.removeFirst();
        redo.clear();
    }

    private void undo(boolean forward) {
        var from = forward ? redo : undo;
        var to = forward ? undo : redo;
        if (from.isEmpty()) return;
        to.addLast(snapshot());
        definition = from.removeLast();
        layer = Math.min(layer, definition.voxels().size() - 1);
        dirty = true;
    }

    private void finishName() {
        if (nameFocus) {
            try {
                definition = new ModelDefinition(nameDraft, definition.voxels());
            } catch (IllegalArgumentException e) {
                message = e.getMessage();
            }
            nameFocus = false;
        }
    }

    public void closeEditor() {
        finishName();
        open = false;
        dragButton = -1;
    }

    public void key(int key, int action, int mods) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        if (key == GLFW_KEY_ESCAPE) {
            if (nameFocus) nameFocus = false;
            else closeEditor();
            return;
        }
        if (nameFocus) {
            if (key == GLFW_KEY_ENTER) finishName();
            else if ((mods & GLFW_MOD_CONTROL) != 0 && key == GLFW_KEY_A) selectName = true;
            else if (key == GLFW_KEY_BACKSPACE) {
                if (selectName) {
                    nameDraft = "";
                    selectName = false;
                } else if (!nameDraft.isEmpty())
                    nameDraft = nameDraft.substring(0, nameDraft.length() - 1);
            }
            return;
        }
        if ((mods & GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW_KEY_Z) undo(false);
            else if (key == GLFW_KEY_Y) undo(true);
            return;
        }
        if (key == GLFW_KEY_X) axis = 0;
        else if (key == GLFW_KEY_Y) axis = 1;
        else if (key == GLFW_KEY_Z) axis = 2;
        else if (key == GLFW_KEY_UP) layer = Math.min(definition.voxels().size() - 1, layer + 1);
        else if (key == GLFW_KEY_DOWN) layer = Math.max(0, layer - 1);
    }

    public void character(int codepoint) {
        if (!nameFocus || codepoint > 127) return;
        char c = (char) codepoint;
        if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-') {
            if (selectName) {
                nameDraft = "";
                selectName = false;
            }
            if (nameDraft.length() < 32) nameDraft += c;
        }
    }

    public void scroll(float x, float y, double amount, int w, int h) {
        Layout l = layout(w, h);
        if (x >= l.left) {
            layer =
                    Math.max(
                            0,
                            Math.min(
                                    definition.voxels().size() - 1,
                                    layer + (int) Math.signum(amount)));
        } else
            zoom = Math.max(.5f, Math.min(4, zoom * (float) Math.pow(1.12, Math.signum(amount))));
    }

    public void release() {
        dragButton = -1;
        lastCell = -1;
    }

    public void click(
            int button, float x, float y, int w, int h, Consumer<ModelDefinition> publish) {
        Layout l = layout(w, h);
        lastX = x;
        lastY = y;
        lastCell = -1;
        if (button != GLFW_MOUSE_BUTTON_LEFT && button != GLFW_MOUSE_BUTTON_RIGHT) return;
        if (y >= 40 && y < 70 && button == 0) {
            finishName();
            if (x >= 20 && x < 170) load(ModelGenerators.flowerPot());
            else if (x >= 180 && x < 250) {
                history();
                definition = new ModelDefinition("My model", new SparseVoxelOctree(32));
                dirty = true;
            } else if (x >= 260 && x < 330) undo(false);
            else if (x >= 340 && x < 410) undo(true);
            else if (x >= 420 && x < 500) saveDraft();
            else if (x >= 510 && x < 590) loadDraft();
            return;
        }
        if (button == 0 && x >= l.left && x < l.left + l.size && y >= 101 && y < 129) {
            nameFocus = true;
            nameDraft = definition.name();
            selectName = true;
            return;
        }
        finishName();
        if (button == 0 && y >= 137 && y < 162 && x >= l.left && x < l.left + 120) {
            axis = Math.min(2, (int) ((x - l.left) / 40));
            return;
        }
        if (button == 0
                && y >= 137
                && y < 162
                && x >= l.left + l.size - 54
                && x < l.left + l.size) {
            layer =
                    Math.max(
                            0,
                            Math.min(
                                    definition.voxels().size() - 1,
                                    layer + (x < l.left + l.size - 27 ? -1 : 1)));
            return;
        }
        if (button == 0
                && y >= l.top + l.size + 9
                && y < l.top + l.size + 36
                && x >= l.left
                && x < l.left + l.size) {
            color = Math.min(PALETTE.length - 1, (int) ((x - l.left) * PALETTE.length / l.size));
            return;
        }
        if (button == 0
                && y >= l.top + l.size + 46
                && y < l.top + l.size + 82
                && x >= l.left
                && x < l.left + l.size) {
            try {
                publish.accept(snapshot());
            } catch (IllegalArgumentException e) {
                message = e.getMessage();
            }
            return;
        }
        if (x >= l.left && x < l.left + l.size && y >= l.top && y < l.top + l.size) {
            history();
            dragButton = button;
            painting = true;
            paint(x, y, l);
            return;
        }
        if (x >= 20 && x < 20 + l.previewWidth && y >= 100 && y < 100 + l.previewHeight) {
            dragButton = button;
            painting = false;
        }
    }

    public void drag(float x, float y, int w, int h) {
        Layout l = layout(w, h);
        if (dragButton < 0) return;
        if (painting && x >= l.left && x < l.left + l.size && y >= l.top && y < l.top + l.size)
            paint(x, y, l);
        else if (!painting && x < l.left - 16) {
            yaw += (x - lastX) * .01f;
            pitch = Math.max(-1.2f, Math.min(1.2f, pitch + (y - lastY) * .01f));
        }
        lastX = x;
        lastY = y;
    }

    private void paint(float mx, float my, Layout l) {
        int n = definition.voxels().size(),
                a = Math.min(n - 1, (int) ((mx - l.left) * n / l.size)),
                b = n - 1 - Math.min(n - 1, (int) ((my - l.top) * n / l.size));
        int cell = a + b * n;
        if (cell == lastCell) return;
        lastCell = cell;
        int[] xyz = new int[3];
        xyz[axis] = layer;
        xyz[(axis + 1) % 3] = a;
        xyz[(axis + 2) % 3] = b;
        definition
                .voxels()
                .set(
                        xyz[0],
                        xyz[1],
                        xyz[2],
                        dragButton == GLFW_MOUSE_BUTTON_RIGHT ? 0 : PALETTE[color]);
        dirty = true;
    }

    private void saveDraft() {
        try {
            Files.createDirectories(draft.getParent());
            try (var out = new DataOutputStream(Files.newOutputStream(draft))) {
                snapshot().write(out);
            }
            message = "Saved draft: " + draft;
        } catch (IOException | IllegalArgumentException e) {
            message = "Save failed: " + e.getMessage();
        }
    }

    private void loadDraft() {
        try (var in = new DataInputStream(Files.newInputStream(draft))) {
            load(ModelDefinition.read(in));
        } catch (IOException e) {
            message = "Load failed: " + e.getMessage();
        }
    }

    public void renderPreview(ShaderProgram shader, int w, int h) {
        Layout l = layout(w, h);
        if (dirty) {
            try {
                Mesh next = VoxelModelRenderer.mesh(definition);
                if (mesh != null) mesh.close();
                mesh = next;
            } catch (IOException e) {
                message = e.getMessage();
            }
            dirty = false;
        }
        if (mesh == null) return;
        glViewport(
                20, h - 100 - (int) l.previewHeight, (int) l.previewWidth, (int) l.previewHeight);
        shader.bind();
        shader.setInt("uInstanced", 0);
        shader.setInt("uVertexColor", 1);
        shader.setVector3("uLightDirection", -.4f, -1, -.3f);
        float span = .8f / zoom, aspect = l.previewWidth / l.previewHeight;
        shader.setMatrix4(
                "uProjection",
                new Matrix4f().ortho(-span * aspect, span * aspect, -span, span, .1f, 10));
        Vector3f eye =
                new Vector3f(
                        (float) (Math.cos(yaw) * Math.cos(pitch)) * 3,
                        (float) Math.sin(pitch) * 3,
                        (float) (Math.sin(yaw) * Math.cos(pitch)) * 3);
        shader.setMatrix4(
                "uView", new Matrix4f().lookAt(eye, new Vector3f(), new Vector3f(0, 1, 0)));
        shader.setMatrix4("uModel", new Matrix4f().translation(-.5f, -.5f, -.5f));
        mesh.render();
        glViewport(0, 0, w, h);
    }

    public void render(Overlay ui, int w, int h) {
        Layout l = layout(w, h);
        ui.text("VOXEL MODEL EDITOR", 20, 13, 2.5f);
        String[] buttons = {"Flower pot", "Clear", "Undo", "Redo", "Save draft", "Load draft"};
        float[] bx = {20, 180, 260, 340, 420, 510}, bw = {150, 70, 70, 70, 80, 80};
        for (int i = 0; i < buttons.length; i++) {
            ui.rectangle(bx[i], 40, bw[i], 30, .07f, .21f, .28f, 1);
            ui.text(buttons[i], bx[i] + 8, 50, 1.2f);
        }
        ui.text("Orbit: drag preview | Wheel: zoom", 20, 79, 1.35f);
        ui.text("MODEL NAME", l.left, 82, 1.4f);
        ui.rectangle(l.left, 101, l.size, 28, nameFocus ? .1f : .03f, .17f, .22f, 1);
        ui.text(
                (nameFocus ? nameDraft : definition.name()) + (nameFocus ? "_" : ""),
                l.left + 7,
                110,
                1.3f);
        for (int i = 0; i < 3; i++) {
            ui.rectangle(
                    l.left + i * 40,
                    137,
                    35,
                    25,
                    i == axis ? .15f : .05f,
                    i == axis ? .5f : .15f,
                    .35f,
                    1);
            ui.text("" + (char) ('X' + i), l.left + i * 40 + 11, 144, 1.5f);
        }
        ui.text(
                "Layer " + (layer + 1) + "/" + definition.voxels().size(),
                l.left + 124,
                144,
                1.15f);
        ui.rectangle(l.left + l.size - 54, 137, 24, 25, .07f, .23f, .3f, 1);
        ui.rectangle(l.left + l.size - 27, 137, 24, 25, .07f, .23f, .3f, 1);
        ui.text("-", l.left + l.size - 47, 144, 1.5f);
        ui.text("+", l.left + l.size - 20, 144, 1.5f);
        int n = definition.voxels().size();
        float cell = l.size / n;
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++) {
                int[] xyz = new int[3];
                xyz[axis] = layer;
                xyz[(axis + 1) % 3] = a;
                xyz[(axis + 2) % 3] = n - 1 - b;
                int c = definition.voxels().get(xyz[0], xyz[1], xyz[2]);
                float r = c == 0 ? .045f : ((c >>> 16) & 255) / 255f,
                        g = c == 0 ? .085f : ((c >>> 8) & 255) / 255f,
                        blue = c == 0 ? .12f : (c & 255) / 255f;
                ui.rectangle(
                        l.left + a * cell, l.top + b * cell, cell - 1, cell - 1, r, g, blue, 1);
            }
        for (int i = 0; i < PALETTE.length; i++) {
            int c = PALETTE[i];
            float width = l.size / PALETTE.length;
            ui.rectangle(
                    l.left + i * width,
                    l.top + l.size + 9,
                    width - 3,
                    25,
                    ((c >>> 16) & 255) / 255f,
                    ((c >>> 8) & 255) / 255f,
                    (c & 255) / 255f,
                    1);
            if (i == color)
                ui.rectangle(l.left + i * width, l.top + l.size + 34, width - 3, 2, 1, 1, 1, 1);
        }
        ui.rectangle(l.left, l.top + l.size + 46, l.size, 36, .06f, .4f, .31f, 1);
        ui.text("CREATE ITEM", l.left + 14, l.top + l.size + 57, 1.8f);
        ui.text(
                n
                        + "^3 grid | "
                        + definition.voxels().occupied()
                        + " voxels | "
                        + definition.voxels().nodes()
                        + " octree nodes",
                20,
                h - 76,
                1.3f);
        ui.text(
                "X/Y/Z: axis | Up/Down: layer | Ctrl+Z/Y: undo/redo | Esc: close",
                20,
                h - 58,
                1.25f);
        int columns = Math.max(30, (w - 40) / 8);
        String text = message;
        for (int line = 0; line < 2 && !text.isEmpty(); line++) {
            int end = Math.min(columns, text.length());
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > 0) end = space;
            }
            ui.text(text.substring(0, end), 20, h - 39 + line * 15, 1.2f, .6f, .95f, .8f, 1);
            text = text.substring(end).stripLeading();
        }
    }

    @Override
    public void close() {
        if (mesh != null) mesh.close();
    }
}
