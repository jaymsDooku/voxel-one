import dev.jayms.*;
import dev.jayms.building.*;
import dev.jayms.net.city.GameConfig;
import dev.jayms.net.city.StressGrid;
import dev.jayms.player.Player;
import dev.jayms.window.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWKeyCallback;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Native building inspection with representative views and an engine recording. */
public final class BuildingPreviewWindowsPlaytest implements Main.FrameObserver {
    private final Path output;
    private World world;
    private Player player;
    private GLFWKeyCallback keys;
    private long window, since, previous;
    private int stage;
    private boolean cedar, westminster, grand;
    private boolean clockLighting;
    private boolean facadeLighting;
    private boolean roofLighting;
    private PreviewFlickerPlaytest flicker;
    private DistantGridMotion distantGrid;
    private int clockFrames;
    private Vector3f overviewStart;

    private BuildingPreviewWindowsPlaytest(Path output) { this.output=output; }

    public static void main(String[] args) throws Exception {
        Path output=Files.createDirectories(Path.of(args[1]).toAbsolutePath());
        System.setProperty("user.home",Files.createDirectory(output.resolve("profile")).toString());
        var game=new Main();
        var building=BuildingCompiler.compile(BuildingApi.readRecipe(Path.of(args[0])));
        field("buildingPreview").set(game,building);
        field("offlineSave").set(game,output.resolve("unused.dat"));
        field("gameConfig").set(game,new GameConfig(false,false,1200,16));
        field("renderOnlyReplay").setBoolean(game,true);
        var probe=new BuildingPreviewWindowsPlaytest(output);
        probe.cedar=building.name().equals("cedar-court-entry");
        probe.grand=building.name().equals("westminster-grand");
        probe.clockLighting=probe.grand && args.length==3 && args[2].equals("clock-lighting");
        probe.facadeLighting=probe.grand && args.length==3 && args[2].equals("facade-lighting");
        probe.roofLighting=probe.grand && args.length==3 && args[2].equals("roof-lighting");
        if(probe.grand && args.length==3 && args[2].equals("flicker")) probe.flicker=new PreviewFlickerPlaytest(output);
        if(probe.grand && args.length==3 && args[2].equals("distant-grid")) probe.distantGrid=new DistantGridMotion();
        probe.westminster=probe.grand || building.name().equals("westminster");
        game.run(probe);
        if (probe.stage!=(probe.flicker!=null ? 4 : probe.westminster ? 3 : 2) || Files.exists(output.resolve("unused.dat")))
            throw new AssertionError("Preview did not finish or wrote a world save");
        if (probe.facadeLighting) checkTowerLighting(output);
        if (probe.roofLighting) checkRoofLighting(output);
        Files.writeString(output.resolve("results.txt"),
                probe.roofLighting ? "Captured the sunlit central spire, slate dormers and stone facade at rest and in motion.\n" :
                probe.distantGrid!=null ? "Captured the Palace grid at rest and over a cross-tile flight.\n" :
                probe.flicker!=null ? "Captured settled Palace, flight, and clock detail under fixed daylight.\n" :
                probe.clockLighting ? "Captured two Elizabeth Tower corners and approach under fixed daylight.\n" :
                probe.facadeLighting ? "PASS: Both tower shafts received exterior daylight. Captured the Thames view and both towers.\n" :
                probe.grand ? "PASS: Captured the opening Palace overview, river roof and Westminster Hall lanterns in the native client. The plot stayed unsaved.\n" : probe.westminster
                        ? "PASS: Windows client captured the Thames elevation, Elizabeth Tower detail and an aerial view of the courts and hall. The plot stayed unsaved.\n"
                        : "PASS: Windows client rendered the compiled building, walked through its entrance, and left the inspection plot unsaved.\n");
    }

    @Override public void started(Main game) throws Exception {
        world=(World)field("world").get(game);
        window=((Window)field("window").get(game)).getHandle();
        if (roofLighting) glfwSetWindowSize(window,1280,720);
        else if (facadeLighting || flicker!=null || distantGrid!=null) glfwSetWindowSize(window,800,480);
        else glfwSetWindowSize(window,westminster ? 800 : 960,westminster ? 450 : 600);
        keys=glfwSetKeyCallback(window,null);
        glfwSetKeyCallback(window,keys);
        var cursor = glfwSetCursorPosCallback(window,null);
        if (cursor != null) cursor.free();
        if (roofLighting) placeFlying(game,99,75,-8,57,-5);
        else if (clockLighting) placeFlying(game,49,77,-3,127,0);
        else if (grand) {
            player=(Player)field("player").get(game);
            overviewStart=new Vector3f(player.position());
        }
        else if (westminster) placeFlying(game,71,54,-47,90,-2);
        else if (cedar) place(game,5,33.04f,-8,90,9);
        else place(game,13,33.04f,-25,90,15);
        since=previous=System.nanoTime();
    }

    @Override public void beforeFrame(Main game) throws Exception {
        long now=System.nanoTime();
        float dt=Math.min(.04f,(now-previous)/1_000_000_000f);
        previous=now;
        if (roofLighting) {
            if (stage==1) {
                float t=Math.min(1,(clockFrames+1)/72f);
                placeFlying(game,99+7*t,75,-8+6*t,57+2*t,-5);
            }
            return;
        }
        if (distantGrid!=null) {
            if(stage==2 && clockFrames<96) {
                float t=(clockFrames+1)/96f;
                placeFlying(game,-180-84*t,87,-146+128*t,90+12*t,-18);
            }
            return;
        }
        if (flicker!=null) {
            if (stage==3) {
                placeFlying(game,49,77,-3,127+(clockFrames+1)*PreviewFlickerPlaytest.PAN_DEGREES,0);
            } else if (stage==1) {
                float t=Math.min(1,clockFrames/64f);
                placeFlying(game,overviewStart.x+(49-overviewStart.x)*t,
                        overviewStart.y+(77-overviewStart.y)*t,overviewStart.z+(-3-overviewStart.z)*t,
                        90+37*t,-10+10*t);
            }
            return;
        }
        if (clockLighting) {
            if (stage==1) {
                float t=Math.min(1,(now-since)/1_800_000_000f);
                placeFlying(game,49-9*t,77,-3+12*t,127,0);
            }
        } else if (facadeLighting && stage==1) {
            float t=Math.min(1,(now-since)/2_400_000_000f);
            placeFlying(game,overviewStart.x+(49-overviewStart.x)*t,
                    overviewStart.y+(77-overviewStart.y)*t,overviewStart.z+(-3-overviewStart.z)*t,
                    90+37*t,-10+10*t);
        } else if (westminster && stage==1) {
            float t=Math.min(1,(now-since)/(grand ? 2_400_000_000f : 3_000_000_000f));
            if (grand) placeFlying(game,overviewStart.x+(110-overviewStart.x)*t,
                    overviewStart.y+(89-overviewStart.y)*t,overviewStart.z+(23-overviewStart.z)*t,
                    90-48*t,-10-16*t);
            else placeFlying(game,71-61*t,54+3*t,-47+37*t,90,-2+11*t);
        }
        if (!westminster && stage==1 && player.position().z<(cedar ? 4.5f : 8))
            player.step(world,dt,1,0,false,true);
    }

    @Override public void afterFrame(Main game) throws Exception {
        long elapsed=System.nanoTime()-since;
        if (roofLighting) {
            clockFrames++;
            if (stage==0 && clockFrames==144) {
                capture(game,"roof-still.png");
                pressRecord();
            } else if (stage==1 && clockFrames==72) {
                capture(game,"roof-moving.png");
            } else if (stage==2 && clockFrames==48) {
                capture(game,"roof-settled.png");
                pressRecord();
                glfwSetWindowShouldClose(window,true);
            } else return;
            stage++;clockFrames=0;
            return;
        }
        if (distantGrid!=null) {
            clockFrames++;
            if((stage==1 && clockFrames>=209) || (stage==2 && clockFrames<=96)) {
                Camera camera=(Camera)field("camera").get(game);
                Matrix4f vp=new Matrix4f((Matrix4f)field("projection").get(game)).mul(camera.createViewMatrix());
                boolean still=stage==1;
                int sample=still?clockFrames-208:clockFrames;
                distantGrid.sample(vp,camera.position(),world.terrain().stressGrid().grade()+1,sample==1);
                if(sample==(still?32:96)) distantGrid.write(output,still?"stationary":"crossing");
            }
            if(stage==0 && clockFrames==240) {
                capture(game,"palace-grid.png");
                placeFlying(game,-180,87,-146,90,-18);
            } else if(stage==1 && clockFrames==240) {
                measureGrid(game,"stationary");
                pressRecord();
            } else if(stage==2 && clockFrames==144) {
                measureGrid(game,"after-flight");
                pressRecord();
                glfwSetWindowShouldClose(window,true);
            } else {
                if(stage==2 && clockFrames==64) measureGrid(game,"in-flight");
                return;
            }
            stage++;clockFrames=0;
            return;
        }
        if (flicker!=null) {
            clockFrames++;
            if (stage==1) {
                if (clockFrames>=65) {stage++;clockFrames=0;}
                return;
            }
            int warmup=stage==0?240:stage==2?96:0;
            if (clockFrames<=warmup) return;
            int sample=clockFrames-warmup;
            String view=stage==0?"overview":stage==2?"clock":"pan";
            if (sample==1) {
                if (field("framebufferWidth").getInt(game)!=800 || field("framebufferHeight").getInt(game)!=480)
                    throw new AssertionError("Flicker measurements require the 800 x 480 framebuffer");
                capture(game,view+".png");
                if(stage==0) pressRecord();
            }
            flicker.sample(view,sample);
            if(sample==PreviewFlickerPlaytest.SAMPLES) {
                if(stage==3) {pressRecord();glfwSetWindowShouldClose(window,true);}
                stage++;clockFrames=0;
            }
            return;
        }
        if (clockLighting) {
            clockFrames++;
            if (stage==0 && clockFrames==120) capture(game,"clock-warm.png");
            if (stage==0 && elapsed>6_000_000_000L && clockFrames>=240) {
                capture(game,"clock-corner.png");
                pressRecord();
            } else if (stage==1 && elapsed>2_800_000_000L) {
                capture(game,"clock-close.png");
                placeFlying(game,-17,77,-3,51,0);
            } else if (stage==2 && elapsed>3_000_000_000L) {
                capture(game,"clock-opposite.png");
                pressRecord();
                glfwSetWindowShouldClose(window,true);
            } else return;
            stage++;
            since=System.nanoTime();
            return;
        }
        if (westminster) {
            inspectWestminster(game,elapsed);
            return;
        }
        if (stage==0 && elapsed>2_400_000_000L) {
            capture(game,cedar ? "cedar-exterior.png" : "breakwater-exterior.png");
            if (cedar) place(game,5,33.04f,-5,90,-6);
            else place(game,17,33.04f,-8,90,-6);
            pressRecord();
            since=previous=System.nanoTime();
            stage=1;
        } else if (stage==1) {
            if (elapsed>16_000_000_000L) throw new AssertionError("Could not walk through entrance at "+player.position());
            if (player.position().z>=(cedar ? 4.5f : 8) && elapsed>2_000_000_000L) {
                if (player.collides(world)) throw new AssertionError("Lobby is obstructed");
                capture(game,cedar ? "cedar-interior.png" : "breakwater-lobby.png");
                pressRecord();
                stage=2;
                glfwSetWindowShouldClose(window,true);
            }
        }
    }

    private void inspectWestminster(Main game,long elapsed) throws Exception {
        if (stage==0 && elapsed>2_500_000_000L && (!facadeLighting || ++clockFrames>=240)) {
            capture(game,"westminster-thames.png");
            pressRecord();
            stage=1;
            since=System.nanoTime();
        } else if (stage==1 && elapsed>(grand ? 3_000_000_000L : 4_000_000_000L)) {
            capture(game,facadeLighting ? "elizabeth-tower.png" : grand ? "westminster-river-roof.png" : "westminster-clock.png");
            if (facadeLighting) placeFlying(game,244,91,49,54,-5);
            else if (grand) placeFlying(game,12,84,104,38,-30);
            else placeFlying(game,78,118,90,-90,-48);
            stage=2;
            since=System.nanoTime();
        } else if (stage==2 && elapsed>(grand ? 1_600_000_000L : 3_500_000_000L)) {
            capture(game,facadeLighting ? "victoria-tower.png" : grand ? "westminster-hall-roof.png" : "westminster-courts.png");
            pressRecord();
            stage=3;
            glfwSetWindowShouldClose(window,true);
        }
    }

    private static void checkRoofLighting(Path output) throws Exception {
        BufferedImage image=ImageIO.read(output.resolve("roof-still.png").toFile());
        if (image.getWidth()!=1280 || image.getHeight()!=720)
            throw new AssertionError("Roof lighting measurements require a 1280 x 720 framebuffer");
        // Measure alternating light/dark columns inside the flat faces, away from trim.
        double spire=horizontalRipple(image,608,261,30,40);
        double dormer=horizontalRipple(image,950,427,17,21);
        // Both patches are slate. The dormer shades the first one.
        int shadow=pixelMedianLuma(image,827,466,853,481),sun=pixelMedianLuma(image,1003,458,1023,468);
        String report=String.format(Locale.ROOT,
                "Horizontal luma ripple (0-255 scale): spire %.2f, dormer %.2f%nMedian slate luma: shaded %d, sunlit %d%n",
                spire,dormer,shadow,sun);
        Files.writeString(output.resolve("roof-lighting.txt"),report);
        if (spire>2.5 || dormer>5 || sun-shadow<40) throw new AssertionError(report);
    }

    private static double horizontalRipple(BufferedImage image,int left,int top,int width,int height) {
        long sum=0;
        for (int y=top;y<top+height;y++) for (int x=left;x<left+width;x++)
            sum+=Math.abs(2*luma(image.getRGB(x,y))-luma(image.getRGB(x-1,y))-luma(image.getRGB(x+1,y)));
        return sum/(double)(width*height);
    }

    private static int luma(int rgb) {
        return (int)Math.round(.2126*((rgb>>16)&255)+.7152*((rgb>>8)&255)+.0722*(rgb&255));
    }

    private void placeFlying(Main game,float x,float y,float z,float yaw,float pitch) throws Exception {
        place(game,x,y,z,yaw,pitch);
        player.toggleFlight();
    }

    private void place(Main game,float x,float y,float z,float yaw,float pitch) throws Exception {
        player=new Player(new Vector3f(x,y,z),yaw,pitch,(Camera)field("camera").get(game));
        field("player").set(game,player);
    }

    private void pressRecord() { keys.invoke(window,GLFW_KEY_F10,0,GLFW_PRESS,0); }

    private void measureGrid(Main game,String label) throws Exception {
        if(field("framebufferWidth").getInt(game)!=800 || field("framebufferHeight").getInt(game)!=480)
            throw new AssertionError("Grid measurements require the 800 x 480 framebuffer");
        String file=label+".png";
        capture(game,file);
        var image=ImageIO.read(output.resolve(file).toFile());
        Camera camera=(Camera)field("camera").get(game);
        var vp=new Matrix4f((Matrix4f)field("projection").get(game)).mul(camera.createViewMatrix());
        float surface=world.terrain().stressGrid().grade()+1;
        // Compare the visible material at road centers with the grass in the
        // neighboring plot. Sample mid-plot Z to stay clear of intersections.
        int[] hits=new int[3],samples=new int[3];
        int firstRow=(int)Math.floor((camera.position().z-StressGrid.MIN_Z)/StressGrid.STRIDE);
        for(int iz=Math.max(0,firstRow);iz<StressGrid.SIDE;iz++) {
            float z=StressGrid.MIN_Z+iz*StressGrid.STRIDE+StressGrid.ROAD+StressGrid.PLOT*.5f;
            float distance=z-camera.position().z;
            if(distance>620) break;
            if(distance<90) continue;
            int band=distance<180?0:distance<340?1:2;
            int firstColumn=(int)Math.floor((camera.position().x-distance-StressGrid.MIN_X)/StressGrid.STRIDE);
            for(int ix=Math.max(0,firstColumn);ix<StressGrid.SIDE;ix++) {
                float road=StressGrid.MIN_X+ix*StressGrid.STRIDE+StressGrid.ROAD*.5f;
                if(road> -24) break; // Stay outside the authored Palace plot.
                if(Math.abs(road-camera.position().x)>distance) continue;
                int street=greenExcess(image,vp,road,surface,z);
                int lawn=greenExcess(image,vp,road+StressGrid.STRIDE*.5f,surface,z);
                if(street==Integer.MIN_VALUE || lawn==Integer.MIN_VALUE) continue;
                samples[band]++;
                if(lawn-street>8) hits[band]++;
            }
        }
        for(int band=0;band<3;band++) {
            String line=String.format(Locale.ROOT,"%s %s: %d/%d road and grass pairs aligned%n",
                    label,new String[]{"90-180","180-340","340-620"}[band],hits[band],samples[band]);
            Files.writeString(output.resolve("grid-alignment.txt"),line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            // At this resolution the first two bands have resolvable street
            // widths. The last band includes legitimately filtered subpixel lines.
            if(band<2 && (samples[band]<20 || hits[band]<.85*samples[band])) throw new AssertionError(line);
        }
    }

    private static int greenExcess(BufferedImage image,Matrix4f vp,float x,float y,float z) {
        var ndc=vp.transformProject(new Vector3f(x,y,z));
        int px=(int)((.5f+.5f*ndc.x)*image.getWidth()),py=(int)((.5f-.5f*ndc.y)*image.getHeight());
        if(ndc.z<=0||ndc.z>=1||px<8||py<108||px>=image.getWidth()-8||py>=image.getHeight()-116
                || Math.abs(px-image.getWidth()/2)<10 && Math.abs(py-image.getHeight()/2)<10) return Integer.MIN_VALUE;
        int rgb=image.getRGB(px,py);
        return ((rgb>>8)&255)-((rgb>>16)&255);
    }

    private static void checkTowerLighting(Path output) throws Exception {
        var image=ImageIO.read(output.resolve("westminster-thames.png").toFile());
        // Coordinates in the fixed 800 x 480 opening view. Each pair samples
        // the same sandstone on the shaft and an adjacent projecting detail.
        int victoria=medianLuma(image,185,202,206,208), buttress=medianLuma(image,220,199,223,208);
        int elizabeth=medianLuma(image,648,275,673,282), band=medianLuma(image,648,271,673,273);
        var close=ImageIO.read(output.resolve("elizabeth-tower.png").toFile());
        int shaded=medianLuma(close,331,320,356,341), sunlit=medianLuma(close,398,323,434,341);
        String report="Median displayed luminance (0-255)\n"
                +"Victoria shaft / buttress: "+victoria+" / "+buttress+"\n"
                +"Elizabeth shaft / band: "+elizabeth+" / "+band+"\n"
                +"Elizabeth close-up, shaded / sunlit shaft: "+shaded+" / "+sunlit+"\n";
        Files.writeString(output.resolve("tower-lighting.txt"), report);
        // The open-sky control on this view measured 144/144 and 143/159.
        // The close-up control measured 92/161. The shaded face looks toward another wing.
        if (buttress<110 || band<110 || victoria<.70*buttress || elizabeth<.70*band
                || shaded<.4*sunlit)
            throw new AssertionError(report);
    }

    private static int medianLuma(BufferedImage image,int left,int top,int right,int bottom) {
        int width=image.getWidth(),height=image.getHeight();
        if (width*480!=height*800) throw new AssertionError("Unexpected opening view aspect ratio: "+width+"x"+height);
        return pixelMedianLuma(image,left*width/800,top*height/480,right*width/800,bottom*height/480);
    }

    private static int pixelMedianLuma(BufferedImage image,int left,int top,int right,int bottom) {
        int[] histogram=new int[256];
        int count=0;
        for (int y=top;y<bottom;y++) {
            for (int x=left;x<right;x++) {
                histogram[luma(image.getRGB(x,y))]++;
                count++;
            }
        }
        int cumulative=0;
        for (int value=0;value<histogram.length;value++) {
            cumulative+=histogram[value];
            if (cumulative>count/2) return value;
        }
        throw new AssertionError("Empty tower sample");
    }

    private void capture(Main game,String file) throws Exception {
        int w=field("framebufferWidth").getInt(game), h=field("framebufferHeight").getInt(game);
        var pixels=BufferUtils.createByteBuffer(w*h*4);
        glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int i=(x+y*w)*4;
            image.setRGB(x,h-y-1,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
        }
        ImageIO.write(image,"png",output.resolve(file).toFile());
    }

    private static Field field(String name) throws Exception {
        var field=Main.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

/** Reproject the grid plane to compare the same piece of ground on successive frames. */
final class DistantGridMotion {
    private static final int X=485,Y=293,W=260,H=33; // lower-left coordinates, 800 x 480
    private final ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);
    private final Matrix4f previousVP=new Matrix4f(),inverse=new Matrix4f();
    private final Vector3f point=new Vector3f();
    private int[] previous=new int[W*H],current=new int[W*H];
    private long count,sum,large;
    private double peak;

    void sample(Matrix4f vp,Vector3f eye,float grade,boolean first) {
        glReadPixels(X,Y,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        for(int i=0;i<W*H;i++) {
            int k=i*4;
            current[i]=(54*(pixels.get(k)&255)+183*(pixels.get(k+1)&255)+19*(pixels.get(k+2)&255)+128)>>8;
        }
        inverse.set(vp).invert();
        long frameSum=0,frameCount=0;
        if(first){count=0;sum=0;large=0;peak=0;}
        else for(int y=0;y<H;y++) for(int x=0;x<W;x++) {
            point.set(2*(X+x+.5f)/800-1,2*(Y+y+.5f)/480-1,0);
            inverse.transformProject(point).sub(eye);
            if(point.y>=0) continue;
            point.mul((grade-eye.y)/point.y).add(eye);
            previousVP.transformProject(point);
            double sx=(point.x+1)*400-X-.5,sy=(point.y+1)*240-Y-.5;
            if(sx<0 || sy<0 || sx>=W-1 || sy>=H-1) continue;
            int ix=(int)sx,iy=(int)sy;
            double fx=sx-ix,fy=sy-iy;
            double old=(1-fy)*((1-fx)*previous[iy*W+ix]+fx*previous[iy*W+ix+1])
                    +fy*((1-fx)*previous[(iy+1)*W+ix]+fx*previous[(iy+1)*W+ix+1]);
            int diff=(int)Math.round(Math.abs(current[y*W+x]-old));
            frameSum+=diff;frameCount++;if(diff>=12)large++;
        }
        previousVP.set(vp);
        int[] swap=previous;previous=current;current=swap;
        if(frameCount>0){sum+=frameSum;count+=frameCount;peak=Math.max(peak,(double)frameSum/frameCount);}
    }

    void write(Path output,String label) throws Exception {
        if(count==0) throw new AssertionError("Missing projected ground samples");
        String line=String.format(Locale.ROOT,"%s distant ground: mean luma change %.3f, worst frame mean %.3f, change >=12 %.3f%% (%d samples)%n",
                label,(double)sum/count,peak,100.*large/count,count);
        Files.writeString(output.resolve("grid-motion.txt"),line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        if(large>.015*count) throw new AssertionError("Distant grid shimmer: "+line);
    }
}

/** Adjacent-frame changes on opaque building details in the fixed daylight flight route. */
final class PreviewFlickerPlaytest {
    static final int SAMPLES=32;
    static final float PAN_DEGREES=.10f;
    private static final double FOCAL_LENGTH=240/Math.tan(Math.toRadians(35));
    private static final double PAN_COS=Math.cos(Math.toRadians(PAN_DEGREES));
    private static final double PAN_SIN=Math.sin(Math.toRadians(PAN_DEGREES));
    private final Path report;
    private static final int MAX_REGION_PIXELS=177*157;
    private final ByteBuffer pixels=BufferUtils.createByteBuffer(MAX_REGION_PIXELS*4);
    private int[] last=new int[MAX_REGION_PIXELS],current=new int[MAX_REGION_PIXELS];
    private long absolute,total,changed;
    private double worstFrame;

    PreviewFlickerPlaytest(Path output) {
        report=output.resolve("flicker.txt");
    }

    void sample(String view,int frame) throws Exception {
        boolean overview=view.equals("overview");
        // The two rectangles cover the river facade and clock tower respectively.
        // Keep sky, grass and the updating HUD outside the sample.
        int left=overview?205:330,top=overview?285:176;
        int width=overview?385:177,height=overview?47:157;
        glReadPixels(left,480-top-height,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        long frameAbsolute=0,frameCount=0;
        if(frame==1){absolute=0;total=0;changed=0;worstFrame=0;}
        for(int i=0;i<width*height;i++){
            int b=i*4;
            int value=(54*(pixels.get(b)&255)+183*(pixels.get(b+1)&255)+19*(pixels.get(b+2)&255)+128)>>8;
            current[i]=value;
        }
        boolean pan=view.equals("pan");
        for(int y=1;frame>1 && y<height-2;y++)for(int x=1;x<width-3;x++){
            // Reproject a pure camera rotation to the preceding frame. The
            // camera's position, field of view and pitch are held fixed.
            double prior=pan?previousPanSample(left,top,width,height,x,y):last[y*width+x];
            if(left+x>=393 && left+x<=407 && top+height-1-y>=232 && top+height-1-y<=248) continue;
            int delta=(int)Math.round(Math.abs(current[y*width+x]-prior));
            frameAbsolute+=delta;
            if(delta>=8) changed++;
            frameCount++;
        }
        int[] swap=last;last=current;current=swap;
        if(frame>1){
            total+=frameCount;
            absolute+=frameAbsolute;
            worstFrame=Math.max(worstFrame,frameAbsolute/(double)frameCount);
        }
        if(frame==SAMPLES){
            double mean=absolute/(double)total,changedPercent=100.*changed/total;
            String line=String.format(Locale.ROOT,"%s: mean_luma_delta=%.3f worst_frame_mean=%.3f changed_by_8_pct=%.3f pairs=%d%n",
                    view,mean,worstFrame,changedPercent,SAMPLES-1);
            Files.writeString(report,line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            System.out.print(line);
            // Permit isolated lighting updates while catching persistent shimmer.
            if(mean>4 || changedPercent>12) throw new AssertionError("Persistent flicker: "+line);
        }
    }

    private double previousPanSample(int left,int top,int width,int height,int x,int y){
        double nx=(left+x+.5-400)/FOCAL_LENGTH;
        double denom=PAN_COS-nx*PAN_SIN;
        double oldX=400+FOCAL_LENGTH*(nx*PAN_COS+PAN_SIN)/denom-.5-left;
        double bottom=480-top-height;
        double oldY=240+(bottom+y+.5-240)/denom-.5-bottom;
        int ix=(int)Math.floor(oldX),iy=(int)Math.floor(oldY);
        double fx=oldX-ix,fy=oldY-iy;
        return (1-fy)*((1-fx)*last[iy*width+ix]+fx*last[iy*width+ix+1])
                +fy*((1-fx)*last[(iy+1)*width+ix]+fx*last[(iy+1)*width+ix+1]);
    }
}
