package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;

import java.io.IOException;

public final class ControlsMenu {
    private final Controls controls;
    private int selected;

    private int offset() {
        return Math.max(0, Math.min(selected - 6, Controls.Action.values().length - 13));
    }

    public void scroll(double direction) {
        if (rendering != null && rendering.open) { rendering.scroll(direction); return; }
        if (saves != null && saves.open) { saves.scroll(direction); return; }
        selected =
                Math.max(
                        0,
                        Math.min(
                                Controls.Action.values().length - 1,
                                selected - (int) Math.signum(direction)));
    }

    private boolean editing;
    private String message = "Click a control or use Up/Down and Enter to change it.";
    public boolean open;
    public SavesMenu saves;
    public RenderingMenu rendering;

    public ControlsMenu(Controls controls) {
        this.controls = controls;
    }

    public boolean editing() {
        return editing;
    }

    public void toggle() {
        open = !open;
        editing = false;
    }

    public boolean key(int key, int action) {
        if (!open) return false;
        if (rendering != null && rendering.open) { rendering.key(key, action); return true; }
        if (saves != null && saves.open) { saves.key(key, action); return true; }
        if (action != GLFW_PRESS) return true;
        if (key == GLFW_KEY_ESCAPE) {
            if (editing) {
                editing = false;
                message = "Binding cancelled.";
            } else open = false;
            return true;
        }
        if (editing) {
            assign(key);
            return true;
        }
        if (key == GLFW_KEY_R && rendering != null) { rendering.show(); return true; }
        if (key == GLFW_KEY_UP)
            selected = Math.floorMod(selected - 1, Controls.Action.values().length);
        else if (key == GLFW_KEY_DOWN) selected = (selected + 1) % Controls.Action.values().length;
        else if (key == GLFW_KEY_ENTER) edit();
        return true;
    }

    private void edit() {
        editing = true;
        message =
                "Press a key or mouse button for "
                        + Controls.Action.values()[selected].label
                        + ". Esc cancels.";
    }

    private void assign(int code) {
        try {
            controls.bind(Controls.Action.values()[selected], code);
            editing = false;
            message = "Saved. Existing bindings swap when you choose an occupied key.";
        } catch (IOException e) {
            message = e.getMessage();
        }
    }

    public void click(int button, float x, float y, int width, int height, Runnable quit) {
        if (!open) return;
        if (rendering != null && rendering.open) { rendering.click(button,x,y,width,height); return; }
        if (saves != null && saves.open) { saves.click(button, x, y, width, height); return; }
        if (editing) {
            assign(-button - 1);
            return;
        }
        if (button != GLFW_MOUSE_BUTTON_LEFT) return;
        float left = width / 2f - 300, top = height / 2f - 290;
        if (x < left || x > left + 600) return;
        if (saves != null && y >= top + 18 && y < top + 50 && x >= left + 400) { saves.show(); return; }
        if (rendering != null && y >= top + 18 && y < top + 50 && x >= left + 215 && x < left + 395) { rendering.show(); return; }
        int row = (int) ((y - top - 80) / 29);
        if (y >= top + 80 && row >= 0 && row < 13) {
            selected = row + offset();
            edit();
            return;
        }
        if (y >= top + 472 && y < top + 501) {
            controls.sensitivity =
                    Math.max(
                            .02f,
                            Math.min(.5f, controls.sensitivity + (x < left + 300 ? -.02f : .02f)));
            try {
                controls.save();
                message = "Mouse sensitivity saved.";
            } catch (IOException e) {
                message = e.getMessage();
            }
            return;
        }
        if (y >= top + 530 && y <= top + 566) {
            if (x < left + 200) open = false;
            else if (x < left + 400) {
                controls.reset();
                try {
                    controls.save();
                    message = "Defaults restored.";
                } catch (IOException e) {
                    message = e.getMessage();
                }
            } else quit.run();
        }
    }

    public void render(Overlay ui, int width, int height) {
        if (!open) return;
        if (rendering != null && rendering.open) { rendering.render(ui,width,height); return; }
        if (saves != null && saves.open) { saves.render(ui, width, height); return; }
        float left = width / 2f - 300, top = height / 2f - 290;
        ui.rectangle(0, 0, width, height, .01f, .02f, .06f, .7f);
        ui.rectangle(left, top, 600, 580, .025f, .06f, .11f, .98f);
        ui.rectangle(left, top, 600, 3, .1f, .85f, 1, 1);
        if (saves != null) {
            ui.rectangle(left + 400, top + 18, 180, 32, .06f, .27f, .35f, 1);
            ui.text("City saves", left + 418, top + 27, 1.8f);
        }
        ui.rectangle(left+215,top+18,180,32,.06f,.27f,.35f,1);
        ui.text("Rendering (R)",left+225,top+27,1.6f);
        ui.text("CONTROLS", left + 24, top + 22, 3);
        ui.text("Escape resumes | Scroll or arrows for more controls", left + 24, top + 55, 1.6f);
        for (int i = offset(); i < Math.min(offset() + 13, Controls.Action.values().length); i++) {
            Controls.Action a = Controls.Action.values()[i];
            float row = top + 80 + (i - offset()) * 29;
            if (i == selected) ui.rectangle(left + 14, row, 572, 27, .07f, .27f, .36f, .9f);
            ui.text(a.label, left + 24, row + 6, 1.7f);
            ui.text(
                    editing && i == selected
                            ? "Press input..."
                            : Controls.keyName(controls.code(a)),
                    left + 360,
                    row + 6,
                    1.7f);
        }
        ui.text(
                String.format(
                        java.util.Locale.ROOT,
                        "Sensitivity: %.2f      [-] Left click     [+] Right side",
                        controls.sensitivity),
                left + 24,
                top + 478,
                1.5f);
        ui.text(message, left + 24, top + 509, 1.2f);
        ui.rectangle(left + 14, top + 530, 182, 36, .06f, .27f, .35f, 1);
        ui.rectangle(left + 208, top + 530, 184, 36, .08f, .18f, .25f, 1);
        ui.rectangle(left + 404, top + 530, 182, 36, .25f, .08f, .1f, 1);
        ui.text("Resume", left + 65, top + 541, 1.8f);
        ui.text("Reset defaults", left + 231, top + 541, 1.8f);
        ui.text("Quit", left + 470, top + 541, 1.8f);
    }
}
