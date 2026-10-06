package dev.jayms.player;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import org.joml.Matrix4f;
import static org.lwjgl.opengl.GL33.*;

/** Open-top voxel 4x4 with four articulated block tyres and a transparent glass windshield. */
public final class JeepModel implements AutoCloseable {
    private final Mesh cube;
    public JeepModel() {
        Chunk c = new Chunk(); c.setBlock(0,0,0,Blocks.STONE); c.generateMesh(); cube=c.getMesh();
    }
    private void box(ShaderProgram s, Matrix4f root, float x,float y,float z,float w,float h,float d) {
        s.setMatrix4("uModel",new Matrix4f(root).translate(x,y,z).scale(w,h,d).translate(-.5f,0,-.5f));
        cube.render();
    }
    public void render(Jeep jeep, ShaderProgram s) {
        if (jeep.type()!=CargoVehicle.JEEP) { cargo(jeep,s); return; }
        var p=jeep.position();
        Matrix4f root=new Matrix4f().translate(p).rotateY((float)Math.toRadians(-jeep.yaw()-90));
        s.setInt("uVertexColor",0); s.setInt("uInstanced",0);
        s.setVector3("uColor",.22f,.36f,.16f);
        box(s,root,0,.45f,0,1.8f,.25f,3.6f);
        box(s,root,0,.7f,-1.1f,1.8f,.45f,1.3f);
        box(s,root,0,.7f,1.55f,1.8f,.55f,.3f);
        for (int x:new int[]{-1,1}) {
            box(s,root,x*.85f,.7f,.65f,.15f,.5f,1.5f);
            for (int z:new int[]{-1,1}) {
                box(s,root,x*.82f,.82f,z*1.2f,.4f,.15f,.95f);
                Matrix4f wheel=new Matrix4f(root).translate(x*.95f,.4f,z*1.2f).rotateX(jeep.wheelPhase());
                s.setVector3("uColor",.055f,.06f,.065f);
                box(s,wheel,0,-.3f,0,.35f,.6f,.8f);
                box(s,wheel,0,-.4f,0,.35f,.8f,.6f);
                s.setVector3("uColor",.5f,.53f,.55f);
                box(s,wheel,x*.18f,-.16f,0,.04f,.32f,.32f);
                s.setVector3("uColor",.22f,.36f,.16f);
            }
        }
        s.setVector3("uColor",.09f,.11f,.09f);
        for (int x:new int[]{-1,1}) {
            box(s,root,x*.45f,.7f,.25f,.65f,.15f,.65f);
            box(s,root,x*.45f,.85f,.55f,.65f,.6f,.15f);
        }
        box(s,root,-.45f,1.18f,-.2f,.4f,.08f,.12f);
        s.setVector3("uColor",.22f,.36f,.16f);
        for (int x:new int[]{-1,1}) box(s,root,x*.82f,1.15f,-.5f,.1f,1.2f,.1f);
        box(s,root,0,2.27f,-.5f,1.75f,.08f,.1f);
        box(s,root,0,1.15f,-.5f,1.75f,.08f,.1f);
        s.setVector3("uColor",.1f,.12f,.13f);
        box(s,root,0,.48f,-1.85f,2,.16f,.15f);
        box(s,root,0,.48f,1.85f,2,.16f,.15f);
        for(int i=-2;i<=2;i++) box(s,root,i*.15f,.78f,-1.77f,.07f,.28f,.04f);
        s.setVector3("uColor",1,.9f,.6f);
        for(int x:new int[]{-1,1}) box(s,root,x*.65f,.85f,-1.78f,.24f,.2f,.05f);
    }
    private void color(ShaderProgram s, float r,float g,float b) { s.setVector3("uColor",r,g,b); }
    private void cargo(Jeep jeep, ShaderProgram s) {
        CargoVehicle t=jeep.type(); float l=t.halfLength, w=t.halfWidth-.3f;
        Matrix4f root=new Matrix4f().translate(jeep.position()).rotateY((float)Math.toRadians(-jeep.yaw()-90));
        s.setInt("uVertexColor",0); s.setInt("uInstanced",0);
        color(s,.12f,.15f,.18f); box(s,root,0,.55f,0,w*2,.25f,l*2);
        float front=-l+1.1f;
        color(s,t==CargoVehicle.VAN?.88f:.18f,t==CargoVehicle.VAN?.85f:.38f,t==CargoVehicle.VAN?.7f:.65f);
        box(s,root,0,.8f,front,w*2,.8f,2);
        box(s,root,0,2.45f,front,w*2,.2f,1.9f);
        for(int side:new int[]{-1,1}) for(float z:new float[]{-l+.15f,-l+2f})
            box(s,root,side*(w-.06f),1.6f,z,.12f,.85f,.12f);
        for(int side:new int[]{-1,1}) {
            color(s,.12f,.15f,.18f); box(s,root,side*(w+.16f),1.8f,front-.5f,.16f,.25f,.28f);
        }
        float start=-l+2.25f, end=l-.15f, center=(start+end)/2, length=end-start;
        if(t==CargoVehicle.TANKER) {
            // Stepped voxel cross-section gives the tank a round silhouette.
            color(s,.7f,.73f,.76f);
            box(s,root,0,1.25f,center,1.5f,1.8f,length);
            box(s,root,0,1.55f,center,2.1f,1.2f,length);
            box(s,root,0,1.85f,center,2.3f,.6f,length);
            for(float z=start+.4f;z<end;z+=1.6f) {
                color(s,.35f,.4f,.45f); box(s,root,0,1.18f,z,1.6f,.12f,.12f);
                box(s,root,0,3.02f,z,1.4f,.1f,.12f);
            }
            color(s,.22f,.27f,.3f); box(s,root,0,3.05f,center,.5f,.18f,.5f);
            for(float y=1;y<3;y+=.3f) box(s,root,w+.02f,y,end-.3f,.1f,.08f,.5f);
        } else {
            color(s,t==CargoVehicle.CONTAINER?.7f:.87f,t==CargoVehicle.CONTAINER?.23f:.84f,t==CargoVehicle.CONTAINER?.12f:.68f);
            box(s,root,0,.85f,center,w*2,t.height-.95f,length);
            if(t==CargoVehicle.CONTAINER) for(float z=start+.12f;z<end;z+=.28f)
                for(int side:new int[]{-1,1}) box(s,root,side*(w+.025f),1,z,.07f,t.height-1.2f,.07f);
            color(s,.3f,.34f,.37f);
            box(s,root,0,.95f,end+.02f,.045f,t.height-1.15f,.06f);
            for(int side:new int[]{-1,1}) box(s,root,side*.35f,1.4f,end+.04f,.06f,1.1f,.06f);
        }
        float[] axles=t==CargoVehicle.VAN ? new float[]{-l+.65f,l-.65f} : new float[]{-l+.75f,l-1.5f,l-.55f};
        for(float z:axles) for(int side:new int[]{-1,1}) {
            Matrix4f wheel=new Matrix4f(root).translate(side*(w+.05f),.5f,z).rotateX(jeep.wheelPhase());
            color(s,.045f,.05f,.055f); box(s,wheel,0,-.5f,0,.3f,1,.7f); box(s,wheel,0,-.35f,0,.3f,.7f,1);
            color(s,.55f,.58f,.6f); box(s,wheel,side*.16f,-.2f,0,.05f,.4f,.4f);
        }
        color(s,.15f,.17f,.2f); box(s,root,0,.65f,-l+.06f,w*2,.2f,.12f);
        for(int side:new int[]{-1,1}) {
            color(s,1,.94f,.65f); box(s,root,side*w*.7f,1,-l+.03f,.3f,.22f,.06f);
            color(s,.85f,.08f,.06f); box(s,root,side*w*.7f,1,l-.03f,.25f,.18f,.06f);
        }
    }
    /** Draw after all opaque objects, preserving depth and shader state. */
    public void glass(Jeep jeep, ShaderProgram s) {
        Matrix4f root=new Matrix4f().translate(jeep.position()).rotateY((float)Math.toRadians(-jeep.yaw()-90));
        s.setInt("uVertexColor",0); s.setInt("uInstanced",0);
        s.setVector3("uColor",Blocks.color(Blocks.GLASS)[0],Blocks.color(Blocks.GLASS)[1],Blocks.color(Blocks.GLASS)[2]);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false);
        s.setFloat("uTransparency",.72f);
        if(jeep.type()==CargoVehicle.JEEP) box(s,root,0,1.23f,-.5f,1.55f,1.04f,.035f);
        else {
            float l=jeep.type().halfLength, w=jeep.type().halfWidth-.3f;
            box(s,root,0,1.6f,-l+.1f,w*2-.24f,.85f,.035f);
            for(int side:new int[]{-1,1}) box(s,root,side*w,1.6f,-l+1.1f,.035f,.85f,1.65f);
        }
        s.setFloat("uTransparency",0); glDepthMask(true); glDisable(GL_BLEND);
    }
    public void close() { cube.close(); }
}
