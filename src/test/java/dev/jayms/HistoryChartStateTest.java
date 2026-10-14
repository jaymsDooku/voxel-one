package dev.jayms;

import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class HistoryChartStateTest {
    GameConfig clock=new GameConfig(true,true,288,0);
    CityFrame frame(double at){return new CityFrame(clock,at,List.of(),List.of(),List.of(),List.of(),List.of());}
    CityHistory history(double at) {
        var h=new CityHistory();var metric=new CityHistory.Metric("population.total","Residents","people","Population",CityHistory.Kind.STOCK,"Test city","Measured residents",0);
        for(int i=0;i<=at;i++)h.observe(i,clock,List.of(metric),Map.of("population.total",10.0));return h;
    }
    @Test void fixedRangeLiveResetCustomAndWorldSwitch() {
        var ui=new MetricTrends();ui.open=true;var h=history(60);ui.update(frame(30),h,null);ui.update(frame(30),h,null);
        assertNotNull(ui.reply);assertEquals(30,ui.rangeEnd());
        ui.key(GLFW_KEY_PAGE_UP,GLFW_PRESS);ui.update(frame(60),h,null);assertFalse(ui.live);assertEquals(30,ui.rangeEnd());
        ui.key(GLFW_KEY_HOME,GLFW_PRESS);ui.update(frame(60),h,null);assertTrue(ui.live);assertEquals(60,ui.rangeEnd());
        ui.selectDomain(0);ui.select("population.total");ui.key(GLFW_KEY_D,GLFW_PRESS);ui.update(frame(60),h,null);assertEquals(2,ui.resolution());
        ui.key(GLFW_KEY_C,GLFW_PRESS);for(int i=0;i<64;i++)ui.key(GLFW_KEY_BACKSPACE,GLFW_PRESS);"10,20".chars().forEach(ui::character);ui.key(GLFW_KEY_ENTER,GLFW_PRESS);ui.update(frame(60),h,null);
        assertEquals(10,ui.rangeStart());assertEquals(20,ui.rangeEnd());assertFalse(ui.live);
        var other=history(5);ui.update(frame(5),other,null);ui.update(frame(5),other,null);assertEquals(other.identity(),ui.reply.city());assertNotEquals(h.identity(),ui.reply.city());
        ui.key(GLFW_KEY_C,GLFW_PRESS);for(int i=0;i<64;i++)ui.key(GLFW_KEY_BACKSPACE,GLFW_PRESS);"0,1e30".chars().forEach(ui::character);ui.key(GLFW_KEY_ENTER,GLFW_PRESS);
        assertDoesNotThrow(()->ui.update(frame(5),other,null));assertTrue(ui.error.contains("Enter start,end"));
    }
}
