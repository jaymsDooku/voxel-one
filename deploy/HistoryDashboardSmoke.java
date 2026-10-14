import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Running native dashboard probe. Only an isolated synthetic profile is used. */
public final class HistoryDashboardSmoke extends CitySavesSmoke {
    String companyPrefix;
    UUID savedIdentity;
    long firstChartFrames,firstChartNanos;
    float playerX,playerZ;
    List<CityFrame.Road> initialRoads;
    MayorDashboard mayor()throws Exception{return (MayorDashboard)get(game,"mayorDashboard");}
    @Override String id()throws Exception{return Long.toUnsignedString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(((dev.jayms.window.Window)get(game,"window")).getHandle()));}
    int width()throws Exception{return ((dev.jayms.window.Window)get(game,"window")).getSize()[0];}
    void at(int x,int y)throws Exception {x("mousemove","--window",id(),""+x,""+y);x("click","1");}
    void range(int index)throws Exception{at((int)(24+(index+.5)*(width()-48)/8),190);}
    public void started(Main value)throws Exception {
        game=value;starts++;x("windowfocus",id());
        var s=local().city;s.collectHistory();
        if(starts>1) {
            require(savedIdentity.equals(s.history.identity()),"Actual application reload retained city/history identity");
            key("F10");due=System.nanoTime()+1_000_000_000L;return;
        }
        int initialResidents=s.frame().citizens().size();
        long began=System.nanoTime();
        for(int i=0;i<1800;i++) {
            if(i%300==0||i==1740)s.command(new CityCommand(CityCommand.SETTLE_DISTRICT,1000,List.of()),1,null);
            s.advance(1);
        }
        require(s.frame().elapsed()>=1800,"30 actual simulation days advanced with dashboard closed");
        Files.writeString(root.resolve("simulation-summary.txt"),String.format(Locale.ROOT,"30 simulated days; initial_local_residents=%d; seven district settlements of 1,000 residents. Native application owns simulation and history. Elapsed_ms=%.3f%n",initialResidents,(System.nanoTime()-began)/1e6));
        due=System.nanoTime()+1_000_000_000L;
    }
    public void beforeFrame(Main value)throws Exception {
        try {runStep(value);}catch(Throwable failure){Files.writeString(root.resolve("assertion-failure.txt"),failure.getClass().getSimpleName()+": "+failure.getMessage()+"\n");throw failure;}
    }
    private void runStep(Main value)throws Exception {
        if(System.nanoTime()<due)return;due=System.nanoTime()+700_000_000L;
        Files.writeString(root.resolve("stage.txt"),"stage="+step+"\n");
        switch(step++) {
            case 0 -> {if(menu().open)key("Escape");key("F9 F10");due=System.nanoTime()+6_000_000_000L;}
            case 1 -> {require(mayor().open,"Mayor opened by F9");var position=((dev.jayms.player.Player)get(game,"player")).position();playerX=position.x;playerZ=position.z;initialRoads=local().city.frame().roads();capture="history-overview.png";at(150,170);}
            case 2 -> {require(mayor().trends.open,"Overview card opened chart");range(0);}
            case 3 -> {require(mayor().trends.rangeEnd()-mayor().trends.rangeStart()<=60.001,"Last day range applied");at(50,104);due=System.nanoTime()+6_000_000_000L;}
            case 4 -> {require(mayor().trends.overviewComparison(0).startsWith("1d completed mean"),"Last day Overview has both completed periods: "+mayor().trends.overviewComparison(0));capture="history-overview-last-day.png";at(150,170);}
            case 5 -> {require(mayor().trends.open,"Last day card reopened full chart");range(2);}
            case 6 -> {require(mayor().trends.rangeEnd()-mayor().trends.rangeStart()>1700,"30-day range uses simulation time");at(100,150);}
            case 7 -> type("employment");
            case 8 -> at(100,257);
            case 9 -> {require(mayor().trends.selected().contains("population.employment"),"Search selected all-resident employment");at(100,150);}
            case 10 -> type("employment");
            case 11 -> at(100,286);
            case 12 -> {require(mayor().trends.selected().size()==2,"Two compatible employment series compared");x("mousemove","--window",id(),"650","360");}
            case 13 -> {capture="history-comparison-tooltip.png";key("Right");}
            case 14 -> {key("Page_Up");}
            case 15 -> {var position=((dev.jayms.player.Player)get(game,"player")).position();require(position.x==playerX&&position.z==playerZ,"Chart keys did not move the player");require(initialRoads.equals(local().city.frame().roads()),"Chart input did not place roads");require(!mayor().trends.live,"Pan leaves fixed range");capture="history-fixed-range.png";key("Page_Down Page_Down Page_Down Page_Down Page_Down Page_Down");}
            case 16 -> {capture="history-no-data.png";key("Home");}
            case 17 -> {require(mayor().trends.live,"Home restores Live");savedIdentity=local().city.history.identity();firstChartFrames=mayor().chartFrames;firstChartNanos=mayor().chartRenderNanos;key("Escape F1");}
            case 18 -> {require(menu().open,"Controls opened for actual reload");click(470,32);}
            case 19 -> {require(menu().saves.open,"City saves menu opened");click(100,372);}
            case 20 -> {require(Files.exists(CitySaves.sidecar(active(),".city.history")),"Save current wrote history sidecar");capture="history-save-reload-menu.png";click(80,140);click(420,372);}
            case 21 -> {require(starts==2,"Application loaded current city through its save menu");key("F9");}
            case 22 -> {require(mayor().open,"Mayor reopened after reload");at((int)(24+4.5*(width()-48)/6),104);}
            case 23 -> {require(mayor().tab==4,"Business snapshot preserved");at(100,480);}
            case 24 -> {require(mayor().businesses.selected!=0,"Workplace detail selected");at(width()-195,61);}
            case 25 -> {require(mayor().trends.selected().stream().anyMatch(id->id.startsWith("company.")),"Workplace detail drilled into stable company revenue");companyPrefix=mayor().trends.selected().iterator().next().replace(".revenue","");key("D");}
            case 26 -> {require(mayor().trends.resolution()==2,"Daily resolution applied after workplace drill-down");capture="history-company-daily-revenue.png";at(24+(width()-48)/5+20,150);}
            case 27 -> type(companyPrefix+".costs");
            case 28 -> at(100,257);
            case 29 -> {require(mayor().trends.selected().size()==2,"Company revenue/cost comparison");at(24+(width()-48)/5+20,150);}
            case 30 -> type(companyPrefix+".profit");
            case 31 -> at(100,257);
            case 32 -> {require(mayor().trends.selected().size()==3,"Company revenue/cost/profit comparison");capture="history-company-comparison.png";key("F10");at(width()-195,61);}
            case 33 -> capture="history-reloaded-snapshot.png";
            case 34 -> at(width()-195,61);
            case 35 -> capture="history-small-900x600.png";
            case 36 -> set(game,"dashboardScale",1.25);
            case 37 -> at(125,188);
            case 38 -> type("residents");
            case 39 -> at(125,321);
            case 40 -> {require(mayor().trends.selected().contains("population.total"),"125% pointer/search selection uses logical coordinates");capture="history-scaled-window.png";key("Escape F6");}
            case 41 -> {require(!mayor().open,"Escape returns to world");key("F6");capture="history-return-world.png";}
            case 42 -> {
                long frames=firstChartFrames+mayor().chartFrames,nanos=firstChartNanos+mayor().chartRenderNanos;
                Files.writeString(evidence.resolve("history-chart-cost.txt"),String.format(Locale.ROOT,"Native overlay chart render CPU: frames=%d mean_ms=%.3f. Excludes Overlay.end GPU upload/draw, world render and F10 encode. JVM heap used including the running synthetic city and renderer: bytes=%d. Tested comparisons: two employment series with hourly summaries and three company flows with daily summaries.\n",frames,nanos/1e6/Math.max(1,frames),java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()));
                Files.writeString(evidence.resolve("history-native-playtest.txt"),"Playtest: PASS. Linux X11, software OpenGL, isolated synthetic profile. Actual Main simulation advanced 30 days with dashboard closed and seven settlements. F9 opened mayor, Overview card opened chart, Last day selected, Overview showed a completed-day comparison with both periods retained, mouse selected 30 days, searchable selector chose all-resident employment and a compatible cohort series. Mouse hovered, Right inspected a sample, Page Up fixed/panned, Page Down showed future no-data, Home resumed Live. Player X/Z and road list stayed unchanged. Real Controls > City saves > Save current > Load current reloaded the application in the same window and retained history UUID. F10 recorded both sides of reload. Business snapshot/workplace detail drilled into stable company revenue; D selected daily summaries; search added that company's costs and profit. Snapshot, 900x600 and 1100x700 at 125% UI scale tested; scaled pointer/search selected Residents. Escape returned to world and F6 toggled twice. Two-client reconnect/identical-range checks are separate integration tests. No physical HiDPI hardware claim.\n");
                glfwSetWindowShouldClose(((dev.jayms.window.Window)get(game,"window")).getHandle(),true);
            }
            default -> throw new AssertionError("Harness did not stop");
        }
    }
    @Override public void afterFrame(Main value)throws Exception {
        String pending=capture;super.afterFrame(value);
        long handle=((dev.jayms.window.Window)get(game,"window")).getHandle();
        if("history-reloaded-snapshot.png".equals(pending))glfwSetWindowSize(handle,900,600);
        if("history-small-900x600.png".equals(pending))glfwSetWindowSize(handle,1100,700);
    }
    public static void main(String[] args)throws Exception {
        var test=new HistoryDashboardSmoke();test.root=Path.of(args[0]).toAbsolutePath();test.evidence=Path.of(args[1]);Files.createDirectories(test.root);Files.createDirectories(test.evidence);
        var game=new Main();set(game,"offlineSave",test.root.resolve("offline-city.dat"));set(game,"gameConfig",new GameConfig(true,true,60,0));
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);game.run(test);test.require(test.step==43,"All native history steps executed");System.out.println("PASS: native history dashboard workflow");
    }
}
