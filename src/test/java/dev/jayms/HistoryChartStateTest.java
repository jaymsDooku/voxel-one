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
    @Test void lastDayOverviewShowsActualChangeAfterTwoCompleteDays() {
        var h=new CityHistory();
        var metric=new CityHistory.Metric("population.total","Residents","people","Population",CityHistory.Kind.STOCK,"Test city","Measured residents",0);
        for(int t=0;t<=864;t++) {
            h.observe(t,clock,List.of(metric),Map.of(metric.id(),t<288?10.0:t<576?20.0:30.0));
            if(t==576||t==864) {
                var ui=new MetricTrends();ui.open=true;ui.update(frame(t),h,null);
                ui.click(50,190,900); // Actual Last day range control.
                ui.open=false;ui.update(frame(t),h,null);
                assertEquals("1d completed mean +10.00 people",ui.overviewComparison(0));
            }
        }
        var shortHistory=history(288);var ui=new MetricTrends();ui.open=true;ui.update(frame(288),shortHistory,null);
        ui.click(50,190,900);ui.open=false;ui.update(frame(288),shortHistory,null);
        assertEquals("1d comparison: insufficient coverage",ui.overviewComparison(0));
        ui.update(frame(300),shortHistory,null);
        assertEquals("1d comparison: insufficient coverage",ui.overviewComparison(0));
        var missing=new CityHistory();
        for(int t=0;t<=576;t++)if(t<101||t>104)missing.observe(t,clock,List.of(metric),Map.of(metric.id(),t<288?10.0:20.0));
        var gapUi=new MetricTrends();gapUi.open=true;gapUi.update(frame(576),missing,null);
        gapUi.click(50,190,900);gapUi.open=false;gapUi.update(frame(576),missing,null);
        assertEquals("1d comparison: insufficient coverage",gapUi.overviewComparison(0));
    }

    void custom(MetricTrends ui,String range) {
        ui.key(GLFW_KEY_C,GLFW_PRESS);for(int i=0;i<64;i++)ui.key(GLFW_KEY_BACKSPACE,GLFW_PRESS);
        range.chars().forEach(ui::character);ui.key(GLFW_KEY_ENTER,GLFW_PRESS);
    }
    @Test void overviewFixedAndCustomWindowsNeverFollowLatest() {
        var h=new CityHistory();var metric=new CityHistory.Metric("population.total","Residents","people","Population",CityHistory.Kind.STOCK,"Test city","Measured residents",0);
        for(int t=0;t<=1152;t++)h.observe(t,clock,List.of(metric),Map.of(metric.id(),t<288?10.0:t<576?20.0:t<864?40.0:t<1152?80.0:160.0));
        var panned=new MetricTrends();panned.open=true;panned.update(frame(1152),h,null);
        panned.click(50,190,900);panned.update(frame(1152),h,null);panned.key(GLFW_KEY_PAGE_UP,GLFW_PRESS);
        panned.open=false;panned.update(frame(1152),h,null);
        assertEquals(720,panned.overviewRangeStart());assertEquals(1008,panned.overviewRangeEnd());
        assertEquals("Fixed 720-1008s completed mean +30.00 people",panned.overviewComparison(0));
        var ui=new MetricTrends();ui.open=true;ui.update(frame(1152),h,null);
        ui.click(50,190,900);ui.update(frame(1152),h,null);custom(ui,"288,576");
        ui.open=false;ui.update(frame(1152),h,null);
        assertEquals("Fixed 288-576s completed mean +10.00 people",ui.overviewComparison(0));
        assertEquals(288,ui.overviewRangeStart());assertEquals(576,ui.overviewRangeEnd());
        for(int t=1153;t<=1440;t++)h.observe(t,clock,List.of(metric),Map.of(metric.id(),160.0));
        ui.update(frame(1440),h,null);
        assertFalse(ui.live);assertEquals(288,ui.rangeStart());assertEquals(576,ui.rangeEnd());
        assertEquals(288,ui.overviewRangeStart());assertEquals(576,ui.overviewRangeEnd());
        assertEquals("Fixed 288-576s completed mean +10.00 people",ui.overviewComparison(0));
        custom(ui,"288,432");ui.update(frame(1440),h,null);
        assertEquals("Fixed 288-432s completed mean +10.00 people",ui.overviewComparison(0));
        custom(ui,"288,432.5");ui.update(frame(1440),h,null);
        assertEquals("Fixed 288-432.5s comparison: insufficient coverage",ui.overviewComparison(0));
        custom(ui,"2000,2288");ui.update(frame(1440),h,null);
        assertEquals("Fixed 2000-2288s comparison: insufficient coverage",ui.overviewComparison(0));
        assertEquals(2000,ui.overviewRangeStart());assertEquals(2288,ui.overviewRangeEnd());
        ui.key(GLFW_KEY_HOME,GLFW_PRESS);ui.update(frame(1440),h,null);
        assertTrue(ui.live);assertEquals(1440,ui.overviewRangeEnd());
        assertEquals("1d completed mean +80.00 people",ui.overviewComparison(0));
        ui.update(frame(1728),h,null); // Live frame ahead of this card's bounded cache refresh.
        assertEquals(1728,ui.overviewRangeEnd());
        assertEquals("1d comparison: updating",ui.overviewComparison(0));
        var bounded=new MetricTrends();bounded.open=true;bounded.update(frame(1440),h,null);
        for(int i=0;i<100;i++){bounded.key(GLFW_KEY_MINUS,GLFW_PRESS);bounded.key(GLFW_KEY_PAGE_DOWN,GLFW_PRESS);}
        assertTrue(Double.isFinite(bounded.rangeStart())&&bounded.rangeStart()>=0);
        assertTrue(Double.isFinite(bounded.rangeEnd())&&bounded.rangeEnd()<=1e12);
        assertDoesNotThrow(()->bounded.update(frame(1440),h,null));
    }

}
