import dev.jayms.launcher.HubFrame;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Provisional software-session measurements, never physical performance qualification. */
public final class HubPerformanceProbe {
    public static void main(String[] args) throws Exception {
        Path root=Path.of("").toAbsolutePath(), profile=Path.of(args[0]).toAbsolutePath().normalize();
        Path output=Path.of(args[1]).toAbsolutePath().normalize();
        if(!profile.startsWith(root)||!output.startsWith(root))throw new IllegalArgumentException("Use worktree paths");
        Files.createDirectories(profile);Files.createDirectories(output);
        Files.writeString(profile.resolve("hub.properties"),"updates=false\n",StandardOpenOption.CREATE_NEW);
        System.setProperty("user.home",profile.toString());
        HubFrame[] frame={null};long start=System.nanoTime();
        try {
            SwingUtilities.invokeAndWait(()->{try {
                frame[0]=new HubFrame(profile);frame[0].setSize(900,600);
                frame[0].setLocationRelativeTo(null);frame[0].setVisible(true);
            } catch(Exception error){throw new RuntimeException(error);}});
            Robot robot=new Robot();robot.waitForIdle();Toolkit.getDefaultToolkit().sync();
            long firstUiIdleMillis=(System.nanoTime()-start)/1_000_000;
            long processFirstUiIdleMillis=ManagementFactory.getRuntimeMXBean().getUptime();
            String[] pages={"Home","Games","Engine & Tools","Downloads","Settings / Help"};
            long[] navigation=new long[30], nativeQueueDrain=new long[30];
            for(int i=0;i<navigation.length;i++) {
                String page=pages[i%pages.length];long before=System.nanoTime();
                SwingUtilities.invokeAndWait(()->{frame[0].showPage(page);frame[0].validate();});
                navigation[i]=(System.nanoTime()-before)/1_000_000;
                long paintStart=System.nanoTime();
                robot.waitForIdle();
                nativeQueueDrain[i]=(System.nanoTime()-paintStart)/1_000_000;
            }
            Arrays.sort(navigation);Arrays.sort(nativeQueueDrain);
            var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long p95=navigation[(int)Math.ceil(navigation.length*.95)-1];
            boolean passed=processFirstUiIdleMillis<=10000 && heap.getUsed()<=256L*1024*1024 && p95<=1000;
            String report="{\n  \"scope\":\"software-session regression only; no physical performance qualification\","
                    +"\n  \"javaVisibleProcessors\":"+Runtime.getRuntime().availableProcessors()+","
                    +"\n  \"jvmToVisibleUiIdleMillis\":"+processFirstUiIdleMillis+","
                    +"\n  \"frameConstructionAndUiIdleMillis\":"+firstUiIdleMillis+","
                    +"\n  \"heapUsedBytes\":"+heap.getUsed()+",\n  \"heapCommittedBytes\":"+heap.getCommitted()+","
                    +"\n  \"memoryScope\":\"Java heap after navigation; not RSS, native memory or peak memory\","
                    +"\n  \"navigationSamples\":30,\n  \"navigationMedianMillis\":"+((navigation[14]+navigation[15])/2)+","
                    +"\n  \"navigationP95Millis\":"+p95+",\n  \"navigationMaxMillis\":"+navigation[29]+","
                    +"\n  \"navigationScope\":\"EDT page rebuild and layout round trip; native queue drain is separate\","
                    +"\n  \"nativeQueueDrainP95Millis\":"+nativeQueueDrain[28]+","
                    +"\n  \"nativeQueueDrainMaxMillis\":"+nativeQueueDrain[29]+","
                    +"\n  \"budget\":{\"firstUiIdleMillis\":10000,\"heapUsedBytes\":268435456,\"navigationP95Millis\":1000},"
                    +"\n  \"provisionalBudgetPassed\":"+passed+",\n  \"physicalQualificationPassed\":false\n}\n";
            Files.writeString(output.resolve("performance.json"),report);
            SwingUtilities.invokeAndWait(()->frame[0].showPage("Games"));robot.waitForIdle();
            if(!new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()).contains(frame[0].getBounds()))
                throw new AssertionError("Performance capture exceeds desktop; full layout qualification pending");
            ImageIO.write(robot.createScreenCapture(frame[0].getBounds()),"png",output.resolve("performance-library.png").toFile());
            if(!passed)throw new AssertionError("Provisional software-session performance budget exceeded");
            System.out.println("Playtest: Hub startup, Java heap and 30 real page navigations measured; provisional software-session budgets passed. Physical performance remains pending.");
        } finally {SwingUtilities.invokeAndWait(()->{if(frame[0]!=null)frame[0].dispose();});}
    }
}
