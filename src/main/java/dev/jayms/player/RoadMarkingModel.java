package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.city.*;
import org.joml.Matrix4f;
import java.util.List;

/** Cache paint until the road layout changes; simulation frames also change every tick. */
public final class RoadMarkingModel implements AutoCloseable {
    private Mesh mesh;
    private List<CityFrame.Road> roads;
    private CityAddresses.State addresses;
    public void render(CityFrame city, ShaderProgram shader) {
        if (!city.roads().equals(roads) || !city.addresses().equals(addresses)) {
            close();
            roads=city.roads(); addresses=city.addresses();
            var data=RoadMarkings.mesh(city);
            if(data.indices().length>0)mesh=new Mesh(data);
        }
        if(mesh==null)return;
        shader.setInt("uVertexColor",1);
        shader.setInt("uInstanced",0);
        shader.setMatrix4("uModel",new Matrix4f());
        mesh.render();
    }
    public void close(){if(mesh!=null){mesh.close();mesh=null;}}
}
