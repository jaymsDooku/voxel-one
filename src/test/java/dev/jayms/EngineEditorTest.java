package dev.jayms;

import dev.jayms.ui.EngineEditor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class EngineEditorTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @Test void newWorkspaceBindingPreservesLegacyCustomF11() throws Exception {
        var file = temp.resolve("legacy.properties");
        var controls = new dev.jayms.ui.Controls(file);
        controls.bind(dev.jayms.ui.Controls.Action.FORWARD, GLFW_KEY_F11);
        controls.save();
        var properties = new java.util.Properties();
        try (var in = java.nio.file.Files.newInputStream(file)) { properties.load(in); }
        properties.remove("ENGINE_EDITOR");
        try (var out = java.nio.file.Files.newOutputStream(file)) { properties.store(out, "Legacy synthetic controls"); }
        var loaded = new dev.jayms.ui.Controls(file);
        assertEquals(GLFW_KEY_F11, loaded.code(dev.jayms.ui.Controls.Action.FORWARD));
        assertNotEquals(GLFW_KEY_F11, loaded.code(dev.jayms.ui.Controls.Action.ENGINE_EDITOR));
        assertEquals(dev.jayms.ui.Controls.Action.LIGHT_COLOR.defaultCode,
                loaded.code(dev.jayms.ui.Controls.Action.LIGHT_COLOR));
    }

    @Test void navigationAndActionsStayInsideWorkspace() {
        var e = new EngineEditor();
        assertEquals(EngineEditor.Command.NONE, e.key(GLFW_KEY_W));
        e.key(GLFW_KEY_UP); assertEquals(3, e.selected);
        e.key(GLFW_KEY_DOWN); assertEquals(0, e.selected);
        assertEquals(EngineEditor.Command.PLAY, e.key(GLFW_KEY_ENTER));
        assertEquals(EngineEditor.Command.PLAY, e.key(GLFW_KEY_ESCAPE));
        assertEquals(EngineEditor.Command.MODEL, e.key(GLFW_KEY_M));
    }
    @Test void mouseActionsHaveBoundedHitTargets() {
        var e = new EngineEditor();
        assertEquals(EngineEditor.Command.NONE, e.click(10,75,1280));
        assertEquals(EngineEditor.Command.PLAY, e.click(100,75,1280));
        assertEquals(EngineEditor.Command.MODEL, e.click(250,75,1280));
        assertEquals(EngineEditor.Command.NONE, e.click(250,100,1280));
        e.click(32,347,1280);assertEquals(3,e.selected);
        e.click(32,348,1280);assertEquals(3,e.selected);
    }
}
