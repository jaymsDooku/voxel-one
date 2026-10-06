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
    /** Draw after all opaque objects, preserving depth and shader state. */
    public void glass(Jeep jeep, ShaderProgram s) {
        Matrix4f root=new Matrix4f().translate(jeep.position()).rotateY((float)Math.toRadians(-jeep.yaw()-90));
        s.setInt("uVertexColor",0); s.setInt("uInstanced",0);
        s.setVector3("uColor",Blocks.color(Blocks.GLASS)[0],Blocks.color(Blocks.GLASS)[1],Blocks.color(Blocks.GLASS)[2]);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false);
        s.setFloat("uTransparency",.72f);
        box(s,root,0,1.23f,-.5f,1.55f,1.04f,.035f);
        s.setFloat("uTransparency",0); glDepthMask(true); glDisable(GL_BLEND);
    }
    public void close() { cube.close(); }
}
