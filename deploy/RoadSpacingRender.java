package dev.jayms;

import static org.lwjgl.opengl.GL33.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.player.*;
import dev.jayms.render.*;
import dev.jayms.ui.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import java.util.*;

/** Supplemental production-renderer evidence. Does not replace live GLFW playtesting. */
public final class RoadSpacingRender {
    public static void main(String[] args) throws Exception {
        GL.createCapabilities();
        Path out=Path.of(args[0]);Files.createDirectories(out);
        RoadSpacing.configure("--pedestrian-spacing","1.0");
        RoadSpacing.configure("--mounted-spacing","1.4");
        var terrain=new Terrain(Terrain.DEFAULT_SEED);
        int grade=Math.max(-26,Math.min(88,terrain.column(8,24).height()));
        var blocks=new HashMap<List<Integer>,Integer>();
        try(var world=new World();var overlay=new Overlay();
            var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");
            var pipeline=new RenderPipeline();var models=new VoxelModelRenderer(world.models());
            var people=new PlayerModel();var horses=new HorseModel()) {
            for(int cx=-1;cx<3;cx++)for(int cz=0;cz<3;cz++)for(int cy=Math.floorDiv(grade,16);cy<=Math.floorDiv(grade+10,16);cy++) {
                var chunk=new Chunk();
                if(cy==Math.floorDiv(grade,16))for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlock(x,Math.floorMod(grade,16),z,Blocks.GRASS);
                world.addChunk(new ChunkPos(cx,cy,cz),chunk);
            }
            var ground=new CitySimulation.Ground() {
                public int type(int x,int y,int z){return blocks.getOrDefault(List.of(x,y,z),y<=grade?Blocks.DIRT:0);}
                public boolean occupied(int x,int y,int z,int w,int d){return false;}
                public void apply(List<Protocol.Edit> edits){for(var e:edits){blocks.put(List.of(e.x(),e.y(),e.z()),e.type());world.apply(e);}}
            };
            var config=new GameConfig(true,false,1200,10);
            var city=new CitySimulation(config,ground,terrain,null,ProductionCatalog.toolEra());
            // This is the implementation's live simulation, rendered with the engine models.
            city.advance(.5);
            for(var chunk:world.getLoadedChunks().values())chunk.checkMesh();
            var eye=new Vector3f(37,grade+27,47);
            var projection=new Matrix4f().ortho(-22,22,-12.375f,12.375f,.1f,300);
            var view=new Matrix4f().lookAt(eye,new Vector3f(15,grade+1,23),new Vector3f(0,1,0));
            pipeline.time(config,0);pipeline.update(world,15,23);pipeline.renderShadows(world,models,eye);
            pipeline.begin(1280,720,projection,view,eye,false,shader);
            shader.setMatrix4("uProjection",projection);shader.setMatrix4("uView",view);
            shader.setInt("uVertexColor",1);shader.setInt("uInstanced",0);
            for(var entry:world.getLoadedChunks().entrySet()) {
                var pos=entry.getKey();shader.setMatrix4("uModel",new Matrix4f().translation(pos.chunkX()*16,pos.chunkY()*16,pos.chunkZ()*16));
                if(entry.getValue().getMesh()!=null)entry.getValue().getMesh().render();
            }
            var frame=city.frame();
            for(var h:frame.horses())horses.render(new Protocol.Pose(h.id(),h.x(),h.y(),h.z(),h.yaw(),0,h.phase(),1,false),shader);
            for(var c:frame.citizens())people.renderCitizen(new Protocol.Pose(c.id(),c.x(),c.y(),c.z(),c.yaw(),0,c.phase(),1,false),c.cohort(),c.horse()!=0,c.activity(),frame.elapsed(),shader,models);
            pipeline.finish();overlay.begin(1280,720);
            overlay.text("Road spacing: pedestrians 1.0 block | horses 1.4 blocks",24,24,1.6f);
            overlay.text("Supplemental offscreen engine render | synthetic flat site | live GLFW playtest pending",24,690,1.1f);
            overlay.end();SpecialBuildingsSmoke.capture(out.resolve("road-spacing-offscreen.png"));
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
            Files.writeString(out.resolve("road-spacing-render.json"),"{\"status\":\"passed\",\"platform\":\"Linux Mesa surfaceless EGL\",\"scope\":\"Supplemental native renderer and live CitySimulation on synthetic flat terrain; not a full-window gameplay test\",\"simulationSeconds\":0.5,\"citizens\":"+frame.citizens().size()+",\"horses\":"+frame.horses().size()+"}\n");
        }
    }
}
