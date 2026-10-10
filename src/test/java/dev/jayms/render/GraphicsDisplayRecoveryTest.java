package dev.jayms.render;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class GraphicsDisplayRecoveryTest {
 @TempDir Path dir;
 @Test void unconfirmedDisplayRecoversWindowedWithoutTouchingSavedProfile()throws Exception {
  Path file=dir.resolve("graphics.properties");var p=GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.DISPLAY_MODE,"BORDERLESS");p.save(file);
  Files.writeString(dir.resolve("graphics.properties.display-pending"),"graphics-display-pending-v2\n");var loaded=GraphicsController.load(file);assertEquals("WINDOWED",loaded.profile().get(GraphicsProfile.Key.DISPLAY_MODE));assertFalse(loaded.warning().isEmpty());assertEquals(p,GraphicsProfile.load(file).profile());
 }
}
