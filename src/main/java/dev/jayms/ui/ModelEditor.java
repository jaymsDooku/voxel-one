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

/** Direct 3D sculpting and optional layer painting share the world-model octree. */
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
    private Mesh mesh, floorMesh, cursorMesh;
    private ModelSculptor.Tool tool = ModelSculptor.Tool.ADD;
    private int brush = 1, paintColor = PALETTE[0];
    private boolean sculpting, colorFocus;
    private String colorDraft = "";
    private float pointerX, pointerY;
    private boolean dirty = true, nameFocus, selectName, painting;
    private String nameDraft = "";
    private int axis = 2, layer = 16, color = 0, dragButton = -1, lastCell = -1;
    private float yaw = .75f, pitch = .4f, zoom = 1, lastX, lastY;
    public boolean open;
    public String message =
            "Build directly in 3D with Add, Paint, Erase or Pick. Right drag orbits; Create item"
                    + " publishes your model.";
    private final Path draft = Controls.directory().resolve("models/draft.vxm");

    private record Layout(
            float left, float top, float size, float previewWidth, float previewHeight) {}

    private Layout layout(int w, int h) {
        float size = Math.min(384, Math.min(w * .43f, h - 320));
        return new Layout(w - size - 24, 170, size, w - size - 72, h - 240);
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
        if (colorFocus) {
            try {
                if (colorDraft.length() != 6)
                    throw new IllegalArgumentException("Enter six hexadecimal digits");
                paintColor = 0xff000000 | Integer.parseInt(colorDraft, 16);
                color = -1;
            } catch (IllegalArgumentException e) {
                message = "Colour: enter six hex digits, for example FF8844.";
            }
            colorFocus = false;
        }
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
            if (colorFocus) colorFocus = false;
            else if (nameFocus) nameFocus = false;
            else closeEditor();
            return;
        }
        if (colorFocus) {
            if (key == GLFW_KEY_ENTER) finishName();
            else if (key == GLFW_KEY_BACKSPACE && !colorDraft.isEmpty())
                colorDraft = colorDraft.substring(0, colorDraft.length() - 1);
            else if ((mods & GLFW_MOD_CONTROL) != 0 && key == GLFW_KEY_A) colorDraft = "";
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
        if (key >= GLFW_KEY_1 && key <= GLFW_KEY_4)
            tool = ModelSculptor.Tool.values()[key - GLFW_KEY_1];
        else if (key == GLFW_KEY_X) axis = 0;
        else if (key == GLFW_KEY_Y) axis = 1;
        else if (key == GLFW_KEY_Z) axis = 2;
        else if (key == GLFW_KEY_UP) layer = Math.min(definition.voxels().size() - 1, layer + 1);
        else if (key == GLFW_KEY_DOWN) layer = Math.max(0, layer - 1);
    }

    public void character(int codepoint) {
        if (colorFocus) {
            char c = (char) codepoint;
            if (colorDraft.length() < 6 && Character.digit(c, 16) >= 0)
                colorDraft += Character.toUpperCase(c);
            return;
        }
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
        sculpting = false;
    }

    public void click(
            int button, float x, float y, int w, int h, Consumer<ModelDefinition> publish) {
        Layout l = layout(w, h);
        lastX = pointerX = x;
        lastY = pointerY = y;
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
        if (button == 0 && x >= 20 && x < 284 && y >= 101 && y < 129) {
            tool = ModelSculptor.Tool.values()[Math.min(3, (int) ((x - 20) / 66))];
            return;
        }
        if (button == 0 && x >= 62 && x < 174 && y >= 136 && y < 160) {
            brush = 1 << Math.min(3, (int) ((x - 62) / 28));
            return;
        }
        if (button == 0 && x >= 190 && x < 282 && y >= 136 && y < 160) {
            colorFocus = true;
            colorDraft = String.format("%06X", paintColor & 0xffffff);
            return;
        }
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
            paintColor = PALETTE[color];
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
        if (x >= 20 && x < 20 + l.previewWidth && y >= 170 && y < 170 + l.previewHeight) {
            dragButton = button;
            painting = false;
            sculpting = button == GLFW_MOUSE_BUTTON_LEFT;
            if (sculpting) {
                history();
                sculpt(x, y, l);
            }
        }
    }

    public void drag(float x, float y, int w, int h) {
        Layout l = layout(w, h);
        pointerX = x;
        pointerY = y;
        if (dragButton < 0) return;
        if (painting && x >= l.left && x < l.left + l.size && y >= l.top && y < l.top + l.size)
            paint(x, y, l);
        else if (sculpting
                && x >= 20
                && x < 20 + l.previewWidth
                && y >= 170
                && y < 170 + l.previewHeight) sculpt(x, y, l);
        else if (!painting && !sculpting && x < l.left - 16) {
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
                        dragButton == GLFW_MOUSE_BUTTON_RIGHT ? 0 : paintColor);
        dirty = true;
    }

    private Matrix4f previewProjection(Layout l) {
        float span = .8f / zoom, aspect = l.previewWidth / l.previewHeight;
        return new Matrix4f().ortho(-span * aspect, span * aspect, -span, span, .1f, 10);
    }

    private Matrix4f previewView() {
        Vector3f eye =
                new Vector3f(
                        (float) (Math.cos(yaw) * Math.cos(pitch)) * 3,
                        (float) Math.sin(pitch) * 3,
                        (float) (Math.sin(yaw) * Math.cos(pitch)) * 3);
        return new Matrix4f().lookAt(eye, new Vector3f(), new Vector3f(0, 1, 0));
    }

    private ModelSculptor.Target target(float x, float y, Layout l) {
        if (x < 20 || x >= 20 + l.previewWidth || y < 170 || y >= 170 + l.previewHeight)
            return null;
        Matrix4f inverse =
                previewProjection(l).mul(previewView()).translate(-.5f, -.5f, -.5f).invert();
        float nx = (x - 20) / l.previewWidth * 2 - 1, ny = 1 - (y - 170) / l.previewHeight * 2;
        Vector3f near = inverse.transformProject(new Vector3f(nx, ny, -1));
        Vector3f far = inverse.transformProject(new Vector3f(nx, ny, 1));
        return ModelSculptor.target(definition.voxels(), near, far.sub(near), tool, brush);
    }

    private void sculpt(float x, float y, Layout l) {
        var target = target(x, y, l);
        if (target == null) return;
        int id = target.x() + target.y() * 32 + target.z() * 1024;
        if (id == lastCell) return;
        lastCell = id;
        if (tool == ModelSculptor.Tool.PICK) {
            paintColor = target.color();
            color = -1;
            return;
        }
        if (ModelSculptor.apply(definition.voxels(), target, tool, paintColor)) dirty = true;
    }

    private void initHelpers() {
        if (floorMesh != null) return;
        float[] vertices = new float[32 * 32 * 36];
        int[] indices = new int[32 * 32 * 6];
        for (int x = 0; x < 32; x++)
            for (int z = 0; z < 32; z++) {
                int face = x * 32 + z;
                float[] corners = Face.TOP.vertices();
                for (int i = 0; i < 4; i++) {
                    int at = face * 36 + i * 9;
                    vertices[at] = (x + corners[i * 3]) / 32f - .5f;
                    vertices[at + 1] = -.502f;
                    vertices[at + 2] = (z + corners[i * 3 + 2]) / 32f - .5f;
                    vertices[at + 4] = 1;
                    float shade = (x + z) % 2 == 0 ? .12f : .17f;
                    vertices[at + 6] = shade;
                    vertices[at + 7] = shade + .05f;
                    vertices[at + 8] = shade + .08f;
                }
                int[] order = {0, 1, 2, 2, 3, 0};
                for (int i = 0; i < 6; i++) indices[face * 6 + i] = face * 4 + order[i];
            }
        floorMesh = new Mesh(new MeshData(vertices, indices));
        Chunk cube = new Chunk();
        cube.setBlock(0, 0, 0, 3);
        cursorMesh = new Mesh(MeshDataGenerator.generate(cube));
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
        initHelpers();
        glViewport(
                20, h - 170 - (int) l.previewHeight, (int) l.previewWidth, (int) l.previewHeight);
        shader.bind();
        shader.setInt("uInstanced", 0);
        shader.setInt("uDistantTerrain", 0);
        shader.setInt("uFog", 0);
        shader.setInt("uLightingEnabled", 0);
        shader.setInt("uVertexColor", 1);
        shader.setVector3("uLightDirection", -.4f, -1, -.3f);
        shader.setMatrix4("uProjection", previewProjection(l));
        shader.setMatrix4("uView", previewView());
        shader.setMatrix4("uModel", new Matrix4f());
        floorMesh.render();
        shader.setMatrix4("uModel", new Matrix4f().translation(-.5f, -.5f, -.5f));
        if (mesh != null) mesh.render();
        var target = target(pointerX, pointerY, l);
        if (target != null) {
            float n = definition.voxels().size();
            shader.setInt("uVertexColor", 0);
            shader.setVector3("uColor", .3f, 1, .9f);
            shader.setMatrix4(
                    "uModel",
                    new Matrix4f()
                            .translation(
                                    target.x() / n - .5f,
                                    target.y() / n - .5f,
                                    target.z() / n - .5f)
                            .translate(-.001f, -.001f, -.001f)
                            .scale(target.side() / n + .002f));
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            cursorMesh.render();
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }
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
        ui.text("Left: selected tool | Right drag: orbit | Wheel: zoom", 20, 79, 1.25f);
        for (int i = 0; i < 4; i++) {
            ui.rectangle(
                    20 + i * 66,
                    101,
                    62,
                    28,
                    tool.ordinal() == i ? .12f : .04f,
                    tool.ordinal() == i ? .4f : .17f,
                    .28f,
                    1);
            ui.text(ModelSculptor.Tool.values()[i].name(), 27 + i * 66, 110, 1.3f);
        }
        ui.text("Size", 20, 142, 1.2f);
        for (int i = 0; i < 4; i++) {
            ui.rectangle(62 + i * 28, 136, 25, 24, .06f, brush == (1 << i) ? .42f : .15f, .28f, 1);
            ui.text("" + (1 << i), 70 + i * 28, 143, 1.3f);
        }
        ui.rectangle(190, 136, 92, 24, .06f, .2f, .28f, 1);
        ui.text(
                "#" + (colorFocus ? colorDraft : String.format("%06X", paintColor & 0xffffff)),
                195,
                143,
                1.2f);
        float[] selectedColor = {
            (paintColor >>> 16 & 255) / 255f,
            (paintColor >>> 8 & 255) / 255f,
            (paintColor & 255) / 255f
        };
        ui.rectangle(294, 136, 24, 24, selectedColor[0], selectedColor[1], selectedColor[2], 1);
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
        if (floorMesh != null) floorMesh.close();
        if (cursorMesh != null) cursorMesh.close();
    }
}
