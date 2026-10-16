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
    static Rectangle contentBounds() {
        Container content=frame.getContentPane();
        return new Rectangle(content.getLocationOnScreen(),content.getSize());
    }
    static void capture(Path file) throws Exception {
        Thread.sleep(400);
        Rectangle bounds=frame.getBounds();
        Rectangle desktop=new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        if(!desktop.contains(bounds)) throw new AssertionError("Requested UI extent "+bounds.width+"x"+bounds.height+" exceeds runner desktop "+desktop.width+"x"+desktop.height);
        ImageIO.write(new Robot().createScreenCapture(contentBounds()),"png",file.toFile());
    }
    public static void main(String[] args) throws Exception {
        try { run(args); }
        catch(Throwable error) {
            Throwable cause=error;
            while(cause.getCause()!=null)cause=cause.getCause();
            String message=String.valueOf(cause.getMessage()).replaceAll("[\\r\\n]"," ");
            System.out.println("HUB_CHECK_FAILURE: "+cause.getClass().getSimpleName()+": "+message.substring(0,Math.min(200,message.length())));
            System.exit(1);
        }
    }
    static void run(String[] args) throws Exception {
        Path out=Path.of(args[0]), profile=Path.of(args[1]);
        Files.createDirectories(out);Files.createDirectories(profile);
        if(args.length>2 && args[2].equals("process")) {
            var parent=ProcessHandle.of(Long.parseLong(args[3])).orElseThrow();
            if(!parent.isAlive())throw new AssertionError("Native executable exited");
            String expected=args[4];
            if(!expected.equals("-")) {
                boolean found=false;
                for(var child:parent.descendants().toList()) {
                    if(java.util.Arrays.asList(child.info().arguments().orElse(new String[0])).contains(expected)) {found=true;break;}
                    // Windows ProcessHandle may omit arguments. Query only our own child,
                    // return a Boolean, and never print its command line.
                    if(System.getProperty("os.name").startsWith("Windows")) {
                        Path powershell=Path.of(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe");
                        String code="$p=Get-CimInstance Win32_Process -Filter 'ProcessId = "+child.pid()+"'; if($p -and $p.CommandLine.Contains('"+expected.replace("'","''")+"')) {'MATCH'}";
                        Process query=new ProcessBuilder(powershell.toString(),"-NoProfile","-NonInteractive","-Command",code).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                        if(!query.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){query.destroyForcibly();throw new AssertionError("Own child process query timed out");}
                        if(new String(query.getInputStream().readAllBytes()).trim().equals("MATCH")){found=true;break;}
                    }
                }
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
            Dimension desktop=Toolkit.getDefaultToolkit().getScreenSize();
            int width=Math.min(1280,desktop.width),height=Math.min(800,desktop.height);
            Rectangle ownContent=new Rectangle((desktop.width-width)/2+40,(desktop.height-height)/2+40,width-80,height-80);
            ImageIO.write(robot.createScreenCapture(ownContent),"png",out.resolve("native-executable.png").toFile());
            return;
        }
        System.setProperty("user.home",profile.toAbsolutePath().toString());
        Files.writeString(profile.resolve("hub.properties"),"updates=false\n");
        try {
            SwingUtilities.invokeAndWait(()->{try {frame=new HubFrame(profile);frame.setVisible(true);}catch(Exception e){throw new RuntimeException(e);}});
            if(args.length>2 && args[2].equals("initial")) {
                Thread.sleep(1500);
                SwingUtilities.invokeAndWait(frame::validate);
                new Robot().waitForIdle();
                capture(out.resolve("initial-window.png"));
                System.out.println("Playtest: initial hub window fits desktop at "+frame.getWidth()+"x"+frame.getHeight());
                return;
            }
            capture(out.resolve("initial-fit.png"));
            Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            StringBuilder unavailable=new StringBuilder();
            java.util.List<int[]> sizes=new java.util.ArrayList<>();
            sizes.add(new int[]{frame.getWidth(),frame.getHeight()});
            for(int[] requested:new int[][]{{900,600},{1280,800}}) {
                if(requested[0]<=usable.width && requested[1]<=usable.height)sizes.add(requested);
                else unavailable.append(requested[0]+"x"+requested[1]+" unavailable on "+usable.width+"x"+usable.height+" desktop; qualification pending\n");
            }
            Files.writeString(out.resolve("unavailable-extents.txt"),unavailable.toString());
            for(int[] size:sizes) {
                SwingUtilities.invokeAndWait(()->{frame.setSize(size[0],size[1]);frame.setLocationRelativeTo(null);});
                for(String page:new String[]{"Home","Games","Engine & Tools","Downloads","Settings / Help"}) {
                    SwingUtilities.invokeAndWait(()->frame.showPage(page));
                    capture(out.resolve(page.replaceAll("[^a-zA-Z]","-")+"-"+size[0]+".png"));
                }
            }
            SwingUtilities.invokeAndWait(()->{frame.setSize(Math.min(900,usable.width),Math.min(600,usable.height));frame.setLocationRelativeTo(null);frame.showPage("Games");});
            boolean[] cards={false,false,false};
            SwingUtilities.invokeAndWait(()->{for(Component c:HubPlaytest.descendants(frame))if(c instanceof JLabel l){
                cards[0]|=l.getText().equals("Voxel City One");cards[1]|=l.getText().contains("Sandbox");cards[2]|=l.getText().equals("Space Invaders");
            }});
            for(boolean present:cards)if(!present)throw new AssertionError("Bundled game card absent");
            SwingUtilities.invokeAndWait(()->frame.detail(HubCatalogEntry()));
            boolean[] webAction={false};
            SwingUtilities.invokeAndWait(()->{for(Component c:HubPlaytest.descendants(frame))if(c instanceof JButton b && b.getText().equals("Open in browser"))webAction[0]=b.isEnabled();});
            if(!webAction[0])throw new AssertionError("Published web action unavailable");
            capture(out.resolve("space-invaders-detail.png"));
            SwingUtilities.invokeAndWait(()->frame.showPage("Games"));
            Robot robot=new Robot();robot.keyPress(KeyEvent.VK_ALT);robot.keyPress(KeyEvent.VK_4);robot.keyRelease(KeyEvent.VK_4);robot.keyRelease(KeyEvent.VK_ALT);
            capture(out.resolve("keyboard-downloads.png"));
            // A short real desktop UI clip; capture timestamps preserve actual time.
            try(VideoRecording movie=new VideoRecording(out,900,600)) {
                for(int i=0;i<20;i++) {
                    if(i==10)SwingUtilities.invokeAndWait(()->frame.showPage("Home"));
                    BufferedImage captured=robot.createScreenCapture(contentBounds());
                    BufferedImage img=new BufferedImage(900,600,BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics=img.createGraphics();graphics.drawImage(captured,0,0,null);graphics.dispose();
                    byte[] rgb=new byte[900*600*3];
                    for(int y=0;y<600;y++)for(int x=0;x<900;x++){int v=img.getRGB(x,599-y),p=(y*900+x)*3;rgb[p]=(byte)(v>>16);rgb[p+1]=(byte)(v>>8);rgb[p+2]=(byte)v;}
                    movie.offer(rgb,i*150L);Thread.sleep(150);
                }
            }
            try(var files=Files.list(out)){for(Path p:files.filter(p->p.toString().endsWith(".mp4")).toList())if(Files.size(p)>6*1024*1024)throw new AssertionError("Video exceeds6MB");}
            System.out.println("Playtest: bundled-runtime UI/card/size/keyboard/capture checks passed");
        } finally {if(frame!=null)SwingUtilities.invokeAndWait(()->frame.dispose());}
    }
    static dev.jayms.launcher.HubCatalog.Entry HubCatalogEntry() {
        try {return dev.jayms.launcher.HubCatalog.bundled().entries().stream().filter(e->e.id().equals("space-invaders")).findFirst().orElseThrow();}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
