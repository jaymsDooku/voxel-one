package dev.jayms;

import dev.jayms.ui.CityActionBar;
import dev.jayms.ui.CityTools;
import dev.jayms.net.city.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CityActionBarTest {
    @Test void dockTargetsStayCenteredAndReachableAcrossViewportSizes() {
        for (int width : new int[]{320, 640, 1280, 2560}) {
            float cell = CityActionBar.cell(width), left = CityActionBar.left(width);
            assertTrue(cell * 9 <= 396);
            assertEquals(width / 2f, left + cell * 4.5f);
            for (int i=0;i<9;i++) {
                assertEquals(i, CityActionBar.hit(left+(i+.5f)*cell, 682, width,720));
                assertEquals(-1, CityActionBar.hit(left+(i+1)*cell-1,682,width,720));
            }
            assertEquals(-1,CityActionBar.hit(left-1,682,width,720));
            assertEquals(-1,CityActionBar.hit(left+9*cell,682,width,720));
            assertEquals(-1,CityActionBar.hit(width/2f,704,width,720));
        }
    }
    @Test void dockGapCannotSubmitWorldPlacement() {
        var tools = new CityTools(); tools.tool=7;
        var frame = CityFrame.empty(GameConfig.cityGame());
        tools.click(CityActionBar.left(1280)+43,682,1280,720,
                new Matrix4f(),new Matrix4f(),frame,c -> fail("Dock gap submitted a world command"));
        assertEquals(7,tools.tool);
    }
}
