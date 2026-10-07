package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.city.*;
import org.joml.Matrix4f;
import java.util.List;
import java.util.Set;

/** Cache paint until the road layout changes; simulation frames also change every tick. */
public final class RoadMarkingModel implements AutoCloseable {
    private Mesh mesh, gridMesh;
    private StressGrid grid;
    private Set<ChunkPos> columns=Set.of();
    private List<CityFrame.Road> roads;
    private CityAddresses.State addresses;
    public void render(CityFrame city, Set<ChunkPos> detailed, ShaderProgram shader) {
        if (!city.roads().equals(roads) || !city.addresses().equals(addresses)) {
            if(mesh!=null){mesh.close();mesh=null;}
            roads=city.roads(); addresses=city.addresses();
            var data=RoadMarkings.mesh(city);
            if(data.indices().length>0)mesh=new Mesh(data);
        }
        if(!java.util.Objects.equals(grid,city.stressGrid()) || !columns.equals(detailed)) {
            if(gridMesh!=null){gridMesh.close();gridMesh=null;}
            grid=city.stressGrid();columns=Set.copyOf(detailed);
            if(grid!=null){var data=RoadMarkings.stressMesh(grid,columns);if(data.indices().length>0)gridMesh=new Mesh(data);}
        }
        if(mesh==null&&gridMesh==null)return;
        shader.setInt("uVertexColor",1);
        shader.setInt("uInstanced",0);
        shader.setMatrix4("uModel",new Matrix4f());
        if(mesh!=null)mesh.render();
        if(gridMesh!=null)gridMesh.render();
    }
    public void close(){if(mesh!=null){mesh.close();mesh=null;}if(gridMesh!=null){gridMesh.close();gridMesh=null;}}
}
