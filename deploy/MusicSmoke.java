import dev.jayms.music.*;
import dev.jayms.ui.LoginDialog;
import dev.jayms.GameIdentity;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native input against production UI/audio/render loop, using synthetic local signals only. */
public final class MusicSmoke {
    static Path out,fixture;static volatile MusicVisualizer active;static volatile Throwable failure;
    static final ConcurrentLinkedQueue<FutureTask<Void>> commands=new ConcurrentLinkedQueue<>();
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void render(Runnable action)throws Exception{var task=new FutureTask<Void>(action,null);commands.add(task);task.get(20,TimeUnit.SECONDS);}
    static String x(String...args)throws Exception {var cmd=new ArrayList<String>(List.of("xdotool"));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();String result=new String(p.getInputStream().readAllBytes()).trim();require(p.waitFor()==0,"X11 input "+args[0]);return result;}
    static void key(String key)throws Exception {String w=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(active.window));x("windowfocus",w);x("key","--clearmodifiers","--window",w,key);Thread.sleep(200);}
    static void shot(String name)throws Exception {render(()->{try{var a=active;ByteBuffer pixels=org.lwjgl.system.MemoryUtil.memAlloc(a.width*a.height*3);try{glPixelStorei(GL_PACK_ALIGNMENT,1);glReadPixels(0,0,a.width,a.height,GL_RGB,GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(a.width,a.height,BufferedImage.TYPE_INT_RGB);int colored=0;for(int y=0;y<a.height;y++)for(int xx=0;xx<a.width;xx++){int p=(y*a.width+xx)*3;int rgb=(pixels.get(p)&255)<<16|(pixels.get(p+1)&255)<<8|(pixels.get(p+2)&255);image.setRGB(xx,a.height-1-y,rgb);if(y>120&&y<a.height-120&&((rgb&255)>70||(rgb>>8&255)>70||(rgb>>16&255)>70))colored++;}require(colored>500,"Scene visible");require(glGetError()==GL_NO_ERROR,"GL error");ImageIO.write(image,"png",out.resolve(name).toFile());}finally{org.lwjgl.system.MemoryUtil.memFree(pixels);}}catch(Exception e){throw new RuntimeException(e);}});}
    static void load(Path file)throws Exception {render(()->active.requestLoad(file));Thread.sleep(350);for(int i=0;i<200;i++){boolean[] done={false};render(()->done[0]=!active.loading());if(done[0])return;Thread.sleep(25);}throw new AssertionError("Load timed out");}
    static void fixture()throws Exception {
        int rate=44100;byte[] pcm=new byte[rate*18*4];
        for(int i=0;i<rate*18;i++){double t=i/(double)rate;double hz=t<4?100:t<8?1000:t<12?6000:100;double amp=t>=15?0:t>=12?(t%0.5<.14?.7:0):.45;short v=(short)(Math.sin(2*Math.PI*hz*t)*amp*32767);short r=(short)-v;pcm[4*i]=(byte)v;pcm[4*i+1]=(byte)(v>>8);pcm[4*i+2]=(byte)r;pcm[4*i+3]=(byte)(r>>8);}
        try(var in=new AudioInputStream(new java.io.ByteArrayInputStream(pcm),new AudioFormat(rate,16,2,true,false),rate*18)){AudioSystem.write(in,AudioFileFormat.Type.WAVE,fixture.toFile());}
    }
    static Path singleChannel(String name,boolean left)throws Exception {
        int rate=44100;byte[] pcm=new byte[rate*3*4];
        for(int i=0;i<rate*3;i++){short v=(short)(.45*32767*Math.sin(2*Math.PI*100*i/rate));int at=i*4+(left?0:2);pcm[at]=(byte)v;pcm[at+1]=(byte)(v>>8);}
        Path file=out.resolve(name);try(var in=new AudioInputStream(new java.io.ByteArrayInputStream(pcm),new AudioFormat(rate,16,2,true,false),rate*3)){AudioSystem.write(in,AudioFileFormat.Type.WAVE,file.toFile());}return file;
    }
    static void chooseMusic()throws Exception {
        CompletableFuture<Void> clicked=new CompletableFuture<>();
        SwingUtilities.invokeLater(()->{try{for(java.awt.Window window:java.awt.Window.getWindows())if(window.isShowing()&&window instanceof JFrame f&&f.getTitle().equals("Voxel One - Play")){pick(f);clicked.complete(null);return;}clicked.completeExceptionally(new AssertionError("Application selection missing"));}catch(Throwable t){clicked.completeExceptionally(t);}});clicked.get(10,TimeUnit.SECONDS);
    }
    static void pick(java.awt.Container root){
        for(java.awt.Component c:root.getComponents()){if(c instanceof JComboBox box&&box.getItemCount()>0&&box.getItemAt(0) instanceof GameIdentity)box.setSelectedItem(GameIdentity.MUSIC);if(c instanceof java.awt.Container group)pick(group);}
        for(java.awt.Component c:root.getComponents())if(c instanceof JButton b&&b.getText().equals("Open visualizer"))b.doClick();
    }
    public static void main(String[]args)throws Exception {
        out=Path.of(args[0]);Files.createDirectories(out);Thread.setDefaultUncaughtExceptionHandler((thread,error)->{try{Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();Files.writeString(out.resolve("failure.txt"),cause.getClass().getSimpleName()+": "+cause.getMessage());}catch(Exception ignored){}System.exit(1);});fixture=out.resolve("controlled-antiphase.wav");fixture();
        // Exercise real catalog selection before both launches, without reading a saved profile.
        Thread input=new Thread(()->{try{
            Thread.sleep(700);chooseMusic();while(active==null)Thread.sleep(25);
            load(out.resolve("missing.wav"));render(()->require(active.status.startsWith("Cannot play"),"Recoverable missing-file message"));
            Path invalid=out.resolve("invalid.wav");Files.writeString(invalid,"invalid audio");load(invalid);render(()->require(active.status.startsWith("Cannot play"),"Recoverable unsupported file"));shot("music-error.png");
            // Open the production file picker via real key, approve a synthetic file in its Swing UI.
            key("o");Thread.sleep(400);SwingUtilities.invokeAndWait(()->{for(java.awt.Window w:java.awt.Window.getWindows())if(w.isShowing())approve(w);});Thread.sleep(900);
            render(()->require(active.track.playing(),"File picker load starts audio"));
            key("F10");render(()->require(active.recording(),"F10 starts engine recorder"));
            Thread.sleep(850);render(()->require(active.bands.bass()>.4&&active.bands.mid()<.02&&active.bands.treble()<.02,"100 Hz expands bass only"));shot("music-bass.png");shot("music-antiphase.png");
            key("space");double[] position={0};render(()->position[0]=active.track.position());Thread.sleep(1900);render(()->{require(!active.track.playing(),"Pause stops audio");require(Math.abs(position[0]-active.track.position())<.025,"Paused clock stable");require(active.bands.bass()<.01,"Pause settles bass");});shot("music-paused.png");
            key("space");Thread.sleep(500);render(()->require(active.bands.bass()>.4,"Resume restores response"));
            key("Right");render(()->require(active.memories.size()<=1,"Seek clears old effects"));Thread.sleep(500);
            // Set an exact position on render thread to avoid timing-dependent section boundaries.
            render(()->active.seek(5));Thread.sleep(700);double[] rotation={0};render(()->{require(active.bands.mid()>.4&&active.bands.bass()<.02,"1000 Hz mid response");rotation[0]=active.rotation;});Thread.sleep(400);render(()->require(active.rotation>rotation[0]+.1,"Mids rotate layers"));shot("music-mid.png");
            key("p");key("minus");key("bracketright");render(()->require(active.palette==1&&active.sensitivity==.75&&active.motion==1.25,"Palette sensitivity and motion keys"));
            render(()->active.seek(9));Thread.sleep(700);render(()->require(active.bands.treble()>.3&&active.bands.mid()<.02,"6000 Hz illuminates edges"));shot("music-treble.png");
            key("F11");render(()->require(glfwGetWindowMonitor(active.window)!=0,"Fullscreen monitor attached"));key("F11");render(()->require(glfwGetWindowMonitor(active.window)==0,"Windowed restored"));
            render(()->active.seek(12));Thread.sleep(2200);render(()->require(active.memories.size()>=2&&active.memories.size()<=12,"Onsets leave bounded memory"));shot("music-beats.png");
            key("F10");render(()->require(!active.recording(),"F10 stops recorder"));
            String clickWindow=Long.toString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(active.window));
            x("mousemove","--window",clickWindow,Integer.toString((int)(24+(active.width-48)/7.0*3.5)),"60");x("click","1");Thread.sleep(250);render(()->require(active.palette==2,"Visible palette button matches click region"));shot("music-controls.png");
            render(()->{active.seek(5);active.motion=0;});Thread.sleep(500);render(()->rotation[0]=active.rotation);Thread.sleep(500);render(()->require(active.rotation==rotation[0],"Zero motion stops mid rotation"));
            render(()->{active.seek(1);active.sensitivity=.25;active.motion=1;});Thread.sleep(500);double[] quiet={0};render(()->quiet[0]=active.bands.bass());render(()->active.sensitivity=2);Thread.sleep(500);render(()->require(active.bands.bass()>quiet[0]*2,"Sensitivity visibly raises response"));
            render(()->active.seek(15));Thread.sleep(2600);render(()->{require(active.bands.bass()==0&&active.bands.mid()==0&&active.bands.treble()==0,"Silence stable");require(active.memories.isEmpty(),"Silence effects expire");});shot("music-silence.png");
            Thread.sleep(900);render(()->{require(active.track.ended()&&!active.track.playing(),"Completion settles and stops");});
            render(()->active.seek(6));render(()->require(Math.abs(active.track.position()-6)<.1&&!active.track.playing(),"Seeking after completion preserves selected paused position"));
            key("space");Thread.sleep(400);render(()->require(active.track.playing()&&active.track.position()>6&&active.track.position()<7,"Resume at completion seek position"));
            key("r");Thread.sleep(350);render(()->require(active.track.playing()&&active.track.position()<2,"Restart returns to beginning"));
            render(()->active.seek(5));Thread.sleep(500);render(()->require(active.bands.mid()>.4,"Mid passage before replacing track"));
            load(fixture);Thread.sleep(400);render(()->{require(active.track.playing()&&active.track.position()<1.5,"Track replacement starts at new clock");require(active.bands.mid()<.02&&active.memories.size()<=1,"Track replacement clears old analysis and beat history");});
            for(boolean left:new boolean[]{true,false}){
                Path channel=singleChannel(left?"left-only.wav":"right-only.wav",left);
                render(()->active.sensitivity=1);load(channel);Thread.sleep(450);
                render(()->{require(active.track.playing()&&active.bands.bass()>.4,"Either single channel drives native bass response");require(active.bands.mid()<.02&&active.bands.treble()<.02,"Single-channel band isolation");});
            }
            key("Escape");
        }catch(Throwable t){failure=t;if(active!=null)glfwSetWindowShouldClose(active.window,true);}},"native-music-input");input.start();
        LoginDialog.open("localhost",25565,null);require(LoginDialog.musicSelected,"Catalog registers Music Visualizer");
        var app=new MusicVisualizer();final long[] first={0},startFrames={0};final double[] fps={0};
        app.run(a->{active=a;FutureTask<Void> task;while((task=commands.poll())!=null)task.run();if(first[0]==0){first[0]=System.nanoTime();startFrames[0]=a.frames;}if(a.frames%60==0)fps[0]=(a.frames-startFrames[0])*1e9/(System.nanoTime()-first[0]);});input.join();
        if(failure!=null)throw new AssertionError("Native workflow failed",failure);
        require(app.track.data()==null&&app.window==0,"Exit releases audio and window");active=null;
        Thread again=new Thread(()->{try{Thread.sleep(500);chooseMusic();while(active==null)Thread.sleep(20);load(fixture);render(()->require(active.track.playing(),"Relaunch audio recreated"));shot("music-relaunch.png");key("o");Thread.sleep(400);render(()->glfwSetWindowShouldClose(active.window,true));}catch(Throwable t){failure=t;if(active!=null)glfwSetWindowShouldClose(active.window,true);}});again.start();
        LoginDialog.open("localhost",25565,null);require(LoginDialog.musicSelected,"Return to catalog");new MusicVisualizer().run(a->{active=a;FutureTask<Void> task;while((task=commands.poll())!=null)task.run();});again.join();if(failure!=null)throw new AssertionError("Relaunch failed",failure);
        SwingUtilities.invokeAndWait(()->{for(java.awt.Window w:java.awt.Window.getWindows())if(w.isShowing())require(!hasChooser(w),"Exit closes a pending file picker");});
        Files.writeString(out.resolve("music-playtest.json"),"{\n\"Playtest\":\"Production catalog and MusicVisualizer; Linux X11 inherited role display; isolated synthetic profile; OpenAL Soft null output (physical speakers not tested)\",\n\"platform\":\""+System.getProperty("os.name")+" "+System.getProperty("os.arch")+"; "+app.renderer+"\",\n\"resolution\":\"1100x720; F11 tested separately\",\n\"settings\":\"80 ring voxels plus center and planar mirrored copy; floor grid; up to 12 traces, each 40 pulse voxels and 4 memory voxels; sensitivity 1 then 0.75, motion 1 then 1.25; palette 0 then 1; vsync requested; F10 active during contrasting passages\",\n\"observedAverageFps\":"+fps[0]+",\n\"checks\":[\"selection launch exit return relaunch\",\"missing and unsupported audio recover\",\"antiphase stereo WAV plays using native clock and drives all bands/onsets\",\"left-only and right-only stereo WAVs drive isolated bass\",\"100 Hz bass, 1000 Hz rotation, 6000 Hz illumination\",\"pause stable clock and decay, resume\",\"seek clears effects\",\"onset memory bounded and expires; silence no jitter\",\"completion seek, resume and restart\",\"track replacement clears clock, analysis and effects\",\"zero motion halts rotation and native sensitivity gain changes response\",\"palette sensitivity motion fullscreen and F10\",\"exit frees audio and window\"],\n\"observed\":\"All assertions passed\"\n}\n");
        System.out.println("PASS: music native workflow; average FPS "+fps[0]);System.exit(0);
    }
    static boolean hasChooser(java.awt.Container c){for(java.awt.Component child:c.getComponents()){if(child instanceof JFileChooser)return true;if(child instanceof java.awt.Container group&&hasChooser(group))return true;}return false;}
    static void approve(java.awt.Container c){for(java.awt.Component child:c.getComponents()){if(child instanceof JFileChooser picker){picker.setSelectedFile(fixture.toFile());picker.approveSelection();return;}if(child instanceof java.awt.Container group)approve(group);}}
}
