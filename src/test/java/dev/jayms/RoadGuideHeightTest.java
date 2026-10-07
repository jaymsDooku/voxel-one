package dev.jayms;

import dev.jayms.net.city.GameConfig;
import dev.jayms.net.city.CityFrame;
import dev.jayms.ui.CityTools;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoadGuideHeightTest {
    @Test void pickingFollowsSurfaceAndInvalidatesAfterEdits() {
        var tools = new CityTools(); tools.tool = 4;
        var city = new CityFrame(GameConfig.cityGame(), 0, List.of(), List.of(), List.of(), List.of(), List.of());
        var projection = new Matrix4f().ortho(-100,100,-100,100,.1f,500);
        var view = new Matrix4f().lookAt(100,160,100,0,32,0,0,1,0);
        Object world = new Object();
        for(int level : new int[]{-32,32,60,95}) {
            tools.surface(world, level, (x,z) -> level);
            var p = new Matrix4f(projection).mul(view).transform(new Vector4f(10,level+1.04f,10,1));
            float x=(p.x/p.w*.5f+.5f)*1280, y=(.5f-p.y/p.w*.5f)*1000;
            assertTrue(y>=130 && y<=800);
            var picked=tools.cursorPoint(x,y,1280,1000,projection,view,city);
            assertNotNull(picked);
            assertEquals(10,picked.x(),.5f); assertEquals(10,picked.z(),.5f);
        }
    }
}
