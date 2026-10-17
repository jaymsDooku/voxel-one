package dev.jayms.render;
import dev.jayms.*;
import dev.jayms.net.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
/** Exact local capture -> GPU upload fence regression. No private profiles. */
public class LocalLightingFenceSmoke {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception {
  check(glfwInit(),"GLFW");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(320,240,"Local lighting edit fence",0,0);check(window!=0,"GL3.3");glfwMakeContextCurrent(window);GL.createCapabilities();
  try(var world=new World(748291)) {
   world.apply(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0xff3040));
   var capture=new WorldLighting.Capture(world,0,0);while(!capture.pump(Long.MAX_VALUE)){}
   var volume=LightVolume.bake(0,0,0,2,2,2,(x,y,z)->0);
   try(var upload=new SceneLightingUpload(world,volume,null,true,null,capture)) {
    check(upload.valid(world),"Initial local volume valid");upload.pump(System.nanoTime()+5_000_000,16);check(!upload.ready(),"Upload remains partial under16byte cap");
    world.apply(new Protocol.Edit(100_000,1000,100_000,Blocks.STONE));
    check(upload.valid(world),"Distant edit does not discard valid local volume during GPU upload");
    world.apply(new Protocol.Edit(0,0,0,Blocks.LED).withColor(0x1234ff));check(!upload.valid(world),"Local edit invalidates in-flight GPU volume");
   }
   check(glGetError()==GL_NO_ERROR,"No GL error");
   System.out.println("Playtest: PASS. Local volume GPU fence survives distant edits and rejects a local edit during partial upload; cleanup passed; OpenGL3.3 llvmpipe.");
  }finally{glfwDestroyWindow(window);glfwTerminate();}
 }
}
