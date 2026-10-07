package dev.jayms.physics;

import dev.jayms.*;
import dev.jayms.net.Blocks;
import dev.jayms.recording.ScreenRecorder;
import dev.jayms.ui.Overlay;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import java.nio.file.Path;

/** Interactive engine physics playground launched with --physics-lab. F10 uses the game recorder. */
public final class PhysicsLab {
    @FunctionalInterface public interface Observer {void frame(PhysicsLab lab) throws Exception;}
    public PhysicsScene scene;public long window;public int width=960,height=640;
    public int lastKey;public volatile long inputSequence,renderedInputSequence;public boolean gpuActive;public volatile long frames;private float yaw=.7f,distance=25;
    private boolean gpuRequested;
    public void run(Observer observer) throws Exception {
        if(!glfwInit())throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        window=glfwCreateWindow(width,height,"Voxel One Physics Lab",0,0);
        if(window==0){glfwDefaultWindowHints();glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);window=glfwCreateWindow(width,height,"Voxel One Physics Lab",0,0);}
        if(window==0){glfwTerminate();throw new IllegalStateException("Physics window creation failed");}
        glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(1);
        scene=new PhysicsScene();
        Chunk cubeChunk=new Chunk();cubeChunk.setBlock(0,0,0,Blocks.STONE);cubeChunk.generateMesh();
        try(var cube=cubeChunk.getMesh();var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");var ui=new Overlay();
                var compute=new ComputeParticles();var recorder=new ScreenRecorder(Path.of(System.getProperty("user.home"),".voxel-one","recordings"))) {
            glfwSetFramebufferSizeCallback(window,(w,x,y)->{width=x;height=y;});
            glfwSetScrollCallback(window,(w,x,y)->distance=Math.max(10,Math.min(45,distance-(float)y)));
            glfwSetKeyCallback(window,(w,key,scan,action,mods)->{
                if(action!=GLFW_PRESS)return;lastKey=key;
                try {
                    if(key==GLFW_KEY_ESCAPE)glfwSetWindowShouldClose(w,true);
                    if(key>=GLFW_KEY_1&&key<=GLFW_KEY_5)scene.scene=key-GLFW_KEY_1+1;
                    if(key==GLFW_KEY_D)scene.destroySupport();if(key==GLFW_KEY_X)scene.explode();if(key==GLFW_KEY_F)scene.fracture();
                    if(key==GLFW_KEY_SPACE)scene.paused=!scene.paused;
                    if(key==GLFW_KEY_R){int tab=scene.scene;scene.close();scene=new PhysicsScene();scene.scene=tab;}
                    if(key==GLFW_KEY_P)scene.xpbd=!scene.xpbd;
                    if(key==GLFW_KEY_G)gpuRequested=!gpuRequested;
                    if(key==GLFW_KEY_B)for(var j:scene.physics.joints){if(j instanceof Constraints.Distance d)d.breakForce=1;if(j instanceof Constraints.Spring s)s.breakForce=1;}
                    if(key==GLFW_KEY_W)scene.physics.wind.x=scene.physics.wind.x==0?6:0;
                    if(key==GLFW_KEY_F10)recorder.toggle(width,height);
                } catch(Exception e){throw new IllegalStateException(e);}finally{inputSequence++;}
            });
            double before=glfwGetTime();float accumulator=0;
            while(!glfwWindowShouldClose(window)) {
                glfwPollEvents();double now=glfwGetTime();float dt=(float)Math.min(.1,now-before);before=now;
                if(glfwGetKey(window,GLFW_KEY_LEFT)==GLFW_PRESS)yaw-=dt;if(glfwGetKey(window,GLFW_KEY_RIGHT)==GLFW_PRESS)yaw+=dt;
                float drive=(glfwGetKey(window,GLFW_KEY_UP)==GLFW_PRESS?1:0)-(glfwGetKey(window,GLFW_KEY_DOWN)==GLFW_PRESS?1:0);
                Vector3f walking=new Vector3f((glfwGetKey(window,GLFW_KEY_L)==GLFW_PRESS?3:0)-(glfwGetKey(window,GLFW_KEY_J)==GLFW_PRESS?3:0),0,(glfwGetKey(window,GLFW_KEY_K)==GLFW_PRESS?3:0)-(glfwGetKey(window,GLFW_KEY_I)==GLFW_PRESS?3:0));
                accumulator+=dt;
                while(accumulator>=1f/120){scene.step(1f/120,drive,walking,glfwGetKey(window,GLFW_KEY_U)==GLFW_PRESS);accumulator-=1f/120;}
                gpuActive=gpuRequested&&compute.available();
                if(gpuRequested&&!scene.paused&&scene.scene==4) {
                    float[] data=new float[scene.particles.positions.size()*8];
                    for(int i=0;i<scene.particles.positions.size();i++){var p=scene.particles.positions.get(i);var v=scene.particles.velocities.get(i);int k=i*8;data[k]=p.x;data[k+1]=p.y;data[k+2]=p.z;data[k+3]=1;data[k+4]=v.x;data[k+5]=v.y;data[k+6]=v.z;}
                    // Zero gravity here: SPH owns forces; compute advances only the wind drift.
                    for(int i=0;i<data.length;i+=8){data[i+4]=scene.physics.wind.x*.05f;data[i+5]=0;data[i+6]=0;}
                    compute.integrate(data,dt,0);
                    for(int i=0;i<scene.particles.positions.size();i++)scene.particles.positions.get(i).set(data[i*8],data[i*8+1],data[i*8+2]);
                }
                draw(shader,cube,ui);
                if(observer!=null)observer.frame(this);frames++;renderedInputSequence=inputSequence;
                recorder.capture(width,height);glfwSwapBuffers(window);
            }
        } finally {scene.close();glfwDestroyWindow(window);glfwTerminate();}
    }
    private void box(ShaderProgram shader,Mesh cube,Vector3f p,Vector3f half,float r,float g,float b) {
        shader.setVector3("uColor",r,g,b);shader.setMatrix4("uModel",new Matrix4f().translation(p).scale(half.x*2,half.y*2,half.z*2).translate(-.5f,-.5f,-.5f));cube.render();
    }
    private void draw(ShaderProgram shader,Mesh cube,Overlay ui) {
        glViewport(0,0,width,height);glClearColor(.075f,.11f,.17f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glEnable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);
        shader.bind();
        shader.setInt("uEnvironment",0);shader.setInt("uShadow",1);shader.setInt("uIrradiance",2);shader.setInt("uFineRoots",3);shader.setInt("uFineLight",4);shader.setInt("uMaterials",5);
        // Active sampler types must use distinct units even when scene lighting is disabled.
        shader.setInt("uVoxelRadiance",6);shader.setInt("uDistanceField",7);
        for(int i=0;i<3;i++)shader.setInt("uCascadeProbes["+i+"]",8+i);
        shader.setInt("uClusters",11);shader.setInt("uLightIndices",12);shader.setInt("uLights",13);
        shader.setInt("uReflectionProbe",14);
        Vector3f eye=new Vector3f(8+(float)Math.sin(yaw)*distance,12,8+(float)Math.cos(yaw)*distance);
        shader.setMatrix4("uProjection",new Matrix4f().perspective((float)Math.toRadians(48),(float)width/Math.max(1,height),.1f,100));
        shader.setMatrix4("uView",new Matrix4f().lookAt(eye,new Vector3f(8,2,8),new Vector3f(0,1,0)));
        shader.setInt("uVertexColor",0);shader.setInt("uInstanced",0);shader.setInt("uLightingEnabled",0);shader.setFloat("uTransparency",0);shader.setVector3("uLightDirection",-.5f,-1,-.3f);
        if(scene.scene==1||scene.scene==2||scene.scene==5) {
            for(var body:scene.physics.bodies) {
                if(body.trigger)continue;
                float r=body.inverseMass==0?.3f:body.material==Blocks.BRICKS?.85f:.65f,g=body.inverseMass==0?.36f:.45f,b=body.inverseMass==0?.42f:.2f;
                if(body==scene.vehicle){r=.2f;g=.7f;b=.3f;}
                shader.setVector3("uColor",r,g,b);shader.setMatrix4("uModel",new Matrix4f().translation(body.position).rotate(body.rotation).scale(body.halfSize.x*2,body.halfSize.y*2,body.halfSize.z*2).translate(-.5f,-.5f,-.5f));cube.render();
            }
            box(shader,cube,new Vector3f(scene.character.position).add(0,.9f,0),new Vector3f(.3f,.9f,.3f),.3f,.65f,1);
            for(int i=0;i<4;i++)box(shader,cube,new Vector3f(scene.vehicle.position).add(i%2==0?-1:1,-.4f,i<2?-1.3f:1.3f),new Vector3f(.18f,.4f,.4f),.08f,.08f,.09f);
            if(scene.scene==2)for(var joint:scene.physics.joints) {
                RigidBody a=null,b=null;
                if(joint instanceof Constraints.Distance d){a=d.a;b=d.b;}if(joint instanceof Constraints.Spring s){a=s.a;b=s.b;}
                if(a!=null&&!joint.broken())for(int i=0;i<12;i++)box(shader,cube,new Vector3f(a.position).lerp(b.position,i/11f),new Vector3f(.04f),.9f,.8f,.3f);
            }
        } else {
            box(shader,cube,new Vector3f(8,.5f,8),new Vector3f(8,.5f,8),.3f,.36f,.42f);
            if(scene.scene==3) {
                for(int y=0;y<6;y++)for(int z=0;z<8;z++)for(int x=0;x<12;x++) {
                    int i=scene.fluid.index(x,y,z);float water=scene.fluid.water[i];
                    if(water>.02f)box(shader,cube,new Vector3f(x+1.5f,y+1+water*.5f,z+2.5f),new Vector3f(.48f,water*.5f,.48f),.1f,.4f,.85f);
                    if(scene.fluid.heat[i]>1)box(shader,cube,new Vector3f(x+1.5f,y+1.2f,z+2.5f),new Vector3f(.2f),1,.25f,.05f);
                    if(scene.fluid.smoke[i]>.03f)box(shader,cube,new Vector3f(x+1.5f,y+1.6f,z+2.5f),new Vector3f(.15f),.5f,.5f,.5f);
                }
                box(shader,cube,scene.boat.position,scene.boat.halfSize,.8f,.6f,.25f);
                for(int i=0;i<scene.erosionHeight.length;i++)box(shader,cube,new Vector3f(1+i%12,1+scene.erosionHeight[i]*.25f,14+i/12*.18f),new Vector3f(.45f,Math.max(.02f,scene.erosionHeight[i]*.25f),.08f),.6f,.4f,.2f);
                for(int i=0;i<scene.shallow.depth.length;i++)box(shader,cube,new Vector3f(1+i%12,1+scene.shallow.depth[i]*.5f,11+i/12*.35f),new Vector3f(.45f,Math.max(.01f,scene.shallow.depth[i]*.5f),.16f),.1f,.65f,.85f);
            } else {
                for(var deformable:java.util.List.of(scene.cloth,scene.soft)) {
                    for(var p:deformable.particles)box(shader,cube,p.position,new Vector3f(.09f),.95f,.4f,.3f);
                    for(var link:deformable.links)if(!link.broken)for(int i=1;i<5;i++)box(shader,cube,new Vector3f(deformable.particles.get(link.a).position).lerp(deformable.particles.get(link.b).position,i/5f),new Vector3f(.025f),.95f,.7f,.4f);
                }
                for(var p:scene.particles.positions)box(shader,cube,p,new Vector3f(.12f),.1f,.5f,1);
            }
        }
        ui.begin(width,height);ui.rectangle(0,0,width,105,.04f,.06f,.09f,.95f);
        ui.text("VOXEL ONE / PHYSICS LAB",18,15,2);
        ui.text("1 Collapse   2 Joints   3 Water / fire   4 Cloth / SPH   5 Vehicle",18,43,1.4f);
        ui.text("D Cut support  X Blast  F Fracture  R Reset  SPACE Pause  F10 Record",18,67,1.2f);
        ui.rectangle(0,height-67,width,67,.04f,.06f,.09f,.95f);
        ui.text("Arrows: drive / orbit   IJKL: walk   U: jump   B: break joints   W: wind   P: PBD / XPBD   G: GPU",18,height-55,1.1f);
        ui.text("Scene "+scene.scene+"   Debris "+scene.detached+"   "+(scene.paused?"PAUSED":"RUNNING")+"   "+(scene.xpbd?"XPBD":"PBD")+"   "+(gpuActive?"GPU compute":gpuRequested?"CPU fallback":"CPU")+"   Triggers "+scene.character.overlaps().size(),18,height-28,1.2f);ui.end();
    }
    public static void main(String[] args)throws Exception {new PhysicsLab().run(null);}
}
