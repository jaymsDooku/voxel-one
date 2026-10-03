package dev.jayms;

import static org.lwjgl.opengl.GL33.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.render.*;
import dev.jayms.ui.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Real renderer and menu, synthetic flat site, no accounts or private runtime data. */
public final class SpecialBuildingsSmoke {
    static BufferedImage capture(Path path) throws Exception {
        var pixels = BufferUtils.createByteBuffer(1280 * 720 * 4);
        glFinish();
        glReadPixels(0, 0, 1280, 720, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        var image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 720; y++) for (int x = 0; x < 1280; x++) {
            int i = ((719-y)*1280+x)*4;
            image.setRGB(x,y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
        }
        ImageIO.write(image,"png",path.toFile());
        return image;
    }
    public static void main(String[] args) throws Exception {
        GL.createCapabilities();
        Path output = Path.of(args[0]); Files.createDirectories(output);
        var terrain = new Terrain(Terrain.DEFAULT_SEED);
        int grade = terrain.column(8,24).height();
        var config = GameConfig.cityGame();
        var roads = new ArrayList<CityFrame.Road>();
        for (int x=48;x<64;x++) roads.add(new CityFrame.Road(x,50,grade));
        var blocks = new HashMap<List<Integer>,Integer>();
        try (var world = new World(); var overlay = new Overlay();
                var shader = new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");
                var pipeline = new RenderPipeline(); var models = new VoxelModelRenderer(world.models())) {
            int cy = Math.floorDiv(grade,16);
            for (int layer=cy;layer<=Math.floorDiv(grade+7,16);layer++) {
                var chunk=new Chunk();
                if (layer==cy) for(int x=0;x<16;x++) for(int z=0;z<16;z++) chunk.setBlock(x,Math.floorMod(grade,16),z,Blocks.DIRT);
                world.addChunk(new ChunkPos(3,layer,3),chunk);
            }
            var ground = new CitySimulation.Ground() {
                public int type(int x,int y,int z) { return blocks.getOrDefault(List.of(x,y,z),y<=grade?Blocks.DIRT:0); }
                public boolean occupied(int x,int y,int z,int w,int d) { return false; }
                public void apply(List<Protocol.Edit> edits) { for(var edit:edits) { blocks.put(List.of(edit.x(),edit.y(),edit.z()),edit.type()); world.apply(edit); } }
            };
            var city = new CitySimulation(config,ground,terrain,new CityFrame(config,0,roads,List.of(),List.of(),List.of(),List.of()));
            var eye = new Vector3f(80,grade+35,85);
            var projection = new Matrix4f().ortho(-24,12,-10.125f,10.125f,.1f,300);
            var view = new Matrix4f().lookAt(eye,new Vector3f(53,grade+3,54),new Vector3f(0,1,0));
            var tools = new CityTools(); tools.tool=6; tools.specialKind=0; tools.specialLevel=3;
            BufferedImage before=null,after=null;
            for(int stage=0;stage<2;stage++) {
                for(var chunk:world.getLoadedChunks().values()) chunk.checkMesh();
                pipeline.time(config,0);
                pipeline.update(world,53,54);
                pipeline.renderShadows(world,models,eye);
                pipeline.begin(1280,720,projection,view,eye,false,shader);
                shader.setMatrix4("uProjection",projection); shader.setMatrix4("uView",view);
                shader.setInt("uVertexColor",1); shader.setInt("uInstanced",0);
                for(var entry:world.getLoadedChunks().entrySet()) {
                    var pos=entry.getKey();
                    shader.setMatrix4("uModel",new Matrix4f().translation(pos.chunkX()*16,pos.chunkY()*16,pos.chunkZ()*16));
                    if(entry.getValue().getMesh()!=null) entry.getValue().getMesh().render();
                }
                pipeline.finish();
                overlay.begin(1280,720);
                tools.render(overlay,1280,720,projection,view,city.frame(),true);
                overlay.end();
                if(stage==0) before=capture(output.resolve("special-buildings-menu.png"));
                else after=capture(output.resolve("special-buildings-placement.png"));
                if(glGetError()!=GL_NO_ERROR) throw new AssertionError("OpenGL error");
                if(stage==0) {
                    var p=new Matrix4f(projection).mul(view).transform(new Vector4f(50,grade+1.03f,52,1));
                    float px=(p.x/p.w*.5f+.5f)*1280, py=(.5f-p.y/p.w*.5f)*720;
                    tools.click(px,py,1280,720,projection,view,city.frame(),command -> tools.message=city.command(command,1,null));
                    if(city.frame().buildings().size()!=1 || !tools.message.equals("Permitted City hall")) throw new AssertionError("Menu click placement failed: "+tools.message);
                }
            }
            int changed=0;
            for(int y=180;y<500;y++) for(int x=460;x<1260;x++) if(before.getRGB(x,y)!=after.getRGB(x,y)) changed++;
            if(changed<1000) throw new AssertionError("Placed geometry not visible: "+changed);
            Files.writeString(output.resolve("special-buildings-rendering.json"),"{\"result\":\"passed\",\"fixture\":\"Synthetic flat road-accessible site; actual CityTools click, authoritative CitySimulation and engine voxel renderer; static captures, not live gameplay\",\"platform\":\"Linux Mesa surfaceless EGL\",\"changedWorldPixels\":"+changed+",\"captures\":[\"special-buildings-menu.png\",\"special-buildings-placement.png\"]}\n");
        }
    }
}
