import dev.jayms.launcher.*;
import dev.jayms.recording.VideoRecording;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Runs production hub UI with the packaged runtime. No accounts or network secrets. */
public final class PackageUiProbe {
    static HubFrame frame;
    static void capture(Path file) throws Exception {
        Thread.sleep(400);
        Rectangle bounds=frame.getBounds();
        Rectangle desktop=new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        if(!desktop.contains(bounds)) throw new AssertionError("Requested UI extent exceeds runner desktop");
        ImageIO.write(new Robot().createScreenCapture(bounds),"png",file.toFile());
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]), profile=Path.of(args[1]);
        Files.createDirectories(out);Files.createDirectories(profile);
        if(args.length>2 && args[2].equals("process")) {
            var parent=ProcessHandle.of(Long.parseLong(args[3])).orElseThrow();
            if(!parent.isAlive())throw new AssertionError("Native executable exited");
            String expected=args[4];
            if(!expected.equals("-")) {
                boolean found=parent.descendants().anyMatch(p->java.util.Arrays.asList(p.info().arguments().orElse(new String[0])).contains(expected));
                if(!found)throw new AssertionError("Expected verified shell JVM not running");
            }
            return;
        }
        if(args.length>2 && args[2].equals("stop")) {
            ProcessHandle.of(Long.parseLong(args[3])).ifPresent(p->{p.descendants().forEach(ProcessHandle::destroy);p.destroy();});return;
        }
        if(GraphicsEnvironment.isHeadless())throw new AssertionError("Interactive desktop unavailable");
        if(args.length>2 && args[2].equals("screen")) {
            // Only use this on the ephemeral/synthetic desktop owned by this test.
            Robot robot=new Robot();robot.keyPress(KeyEvent.VK_ALT);robot.keyPress(KeyEvent.VK_2);
            robot.keyRelease(KeyEvent.VK_2);robot.keyRelease(KeyEvent.VK_ALT);Thread.sleep(700);
            ImageIO.write(robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),"png",out.resolve("native-executable.png").toFile());
            return;
        }
        System.setProperty("user.home",profile.toAbsolutePath().toString());
        Files.writeString(profile.resolve("hub.properties"),"updates=false\n");
        try {
            SwingUtilities.invokeAndWait(()->{try {frame=new HubFrame(profile);frame.setVisible(true);}catch(Exception e){throw new RuntimeException(e);}});
            for(int[] size:new int[][]{{900,600},{1280,800}}) {
                SwingUtilities.invokeAndWait(()->frame.setSize(size[0],size[1]));
                for(String page:new String[]{"Home","Games","Engine & Tools","Downloads","Settings / Help"}) {
                    SwingUtilities.invokeAndWait(()->frame.showPage(page));
                    capture(out.resolve(page.replaceAll("[^a-zA-Z]","-")+"-"+size[0]+".png"));
                }
            }
            SwingUtilities.invokeAndWait(()->{frame.setSize(900,600);frame.showPage("Games");});
            boolean[] cards={false,false,false};
            SwingUtilities.invokeAndWait(()->{for(Component c:HubPlaytest.descendants(frame))if(c instanceof JLabel l){
                cards[0]|=l.getText().equals("Voxel City One");cards[1]|=l.getText().contains("Sandbox");cards[2]|=l.getText().equals("Space Invaders");
            }});
            for(boolean present:cards)if(!present)throw new AssertionError("Bundled game card absent");
            Robot robot=new Robot();robot.keyPress(KeyEvent.VK_ALT);robot.keyPress(KeyEvent.VK_4);robot.keyRelease(KeyEvent.VK_4);robot.keyRelease(KeyEvent.VK_ALT);
            capture(out.resolve("keyboard-downloads.png"));
            // A short real desktop UI clip; capture timestamps preserve actual time.
            try(VideoRecording movie=new VideoRecording(out,900,600)) {
                for(int i=0;i<20;i++) {
                    if(i==10)SwingUtilities.invokeAndWait(()->frame.showPage("Home"));
                    BufferedImage img=robot.createScreenCapture(frame.getBounds());byte[] rgb=new byte[900*600*3];
                    for(int y=0;y<600;y++)for(int x=0;x<900;x++){int v=img.getRGB(x,599-y),p=(y*900+x)*3;rgb[p]=(byte)(v>>16);rgb[p+1]=(byte)(v>>8);rgb[p+2]=(byte)v;}
                    movie.offer(rgb,i*150L);Thread.sleep(150);
                }
            }
            try(var files=Files.list(out)){for(Path p:files.filter(p->p.toString().endsWith(".mp4")).toList())if(Files.size(p)>6*1024*1024)throw new AssertionError("Video exceeds6MB");}
            System.out.println("Playtest: bundled-runtime UI/card/size/keyboard/capture checks passed");
        } finally {if(frame!=null)SwingUtilities.invokeAndWait(()->frame.dispose());}
    }
}
