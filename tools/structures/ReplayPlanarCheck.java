package dev.jayms.net;
import dev.jayms.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

public final class ReplayPlanarCheck {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void fails(Class<? extends Throwable> expected,org.junit.jupiter.api.function.Executable call){org.junit.jupiter.api.Assertions.assertThrows(expected,call);}
 static long deadline(){return System.nanoTime()+100000000;}
 static void finish(RegionReplayChunkUpload upload)throws Exception {int steps=0;while(!upload.ready()&&steps++<10000){int sent=upload.pump(64,deadline(),()->false);check(sent>=0&&sent<=64,"Combined lanes share byte cap");glfwPollEvents();}check(upload.ready(),"Both lanes complete");}
 static RegionReplayWorker.Request request(RegionReplayWorker.Binding binding,World world)throws Exception{return ReplayModelGpuCheck.request(binding,world);}
 static org.joml.Matrix4f projection(){return new org.joml.Matrix4f().zero().m00(.13f).m20(-.13f).m01(-.075f).m11(.27f).m21(-.075f).m02(.02f).m12(-.02f).m22(.02f).m31(.65f).m33(1);}
 static org.joml.FrustumIntersection visible(){return new org.joml.FrustumIntersection(new org.joml.Matrix4f().ortho(-32,32,128,192,-32,32));}
 static void draw(World world,ShaderProgram shader)throws Exception {
  shader.bind();shader.setMatrix4("uProjection",projection());shader.setMatrix4("uView",new org.joml.Matrix4f().translation(0,-160,0));
  Mesh.beginFrame();check(world.renderReplayGeometry(visible(),shader)==1,"World ordinary draw bridge");
  try(var models=new VoxelModelRenderer(world.models())){models.render(world,visible(),shader);}
  check(Mesh.drawCalls()==3,"One ordinary and two prepared-instance draws");
  var field=ShaderProgram.class.getDeclaredField("programId");field.setAccessible(true);int id=field.getInt(shader);
  check(glGetUniformi(id,glGetUniformLocation(id,"uInstanced"))==0,"Instancing state restored");
 }
 static void water(World world)throws Exception {
  try(var water=new dev.jayms.render.WaterRenderer();var models=new VoxelModelRenderer(world.models())){
   water.optics(false);water.capture(world,models,projection(),new org.joml.Matrix4f().translation(0,-160,0),new org.joml.Vector3f(4,170,1),640,480,0,new org.joml.Vector3f(0,1,0),.5f,1f);check(water.found(),"Production water pass finds private scene");
   water.render(world,projection(),new org.joml.Matrix4f().translation(0,-160,0),new org.joml.Vector3f(4,170,1),0,0,0,640,480,0,0);int error=glGetError();check(error==GL_NO_ERROR,"Water draw GL error "+error);
  }
 }
 static int[] state(){int[] viewport=new int[4];glGetIntegerv(GL_VIEWPORT,viewport);return new int[]{glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),glGetInteger(GL_READ_FRAMEBUFFER_BINDING),viewport[0],viewport[1],viewport[2],viewport[3],glGetInteger(GL_FRONT_FACE),glGetInteger(GL_CURRENT_PROGRAM),glGetInteger(GL_VERTEX_ARRAY_BINDING),glGetInteger(GL_ACTIVE_TEXTURE),glIsEnabled(GL_DEPTH_TEST)?1:0,glIsEnabled(GL_CULL_FACE)?1:0,glIsEnabled(GL_CLIP_DISTANCE0)?1:0};}
 static void planar(World world,ShaderProgram shader,Path image)throws Exception {
  int ownVao=glGenVertexArrays();
  try(var water=new dev.jayms.render.WaterRenderer();var models=new VoxelModelRenderer(world.models())) {
   glViewport(7,9,620,450);glDisable(GL_DEPTH_TEST);glEnable(GL_CULL_FACE);glDisable(GL_CLIP_DISTANCE0);glFrontFace(GL_CCW);shader.bind();glBindVertexArray(ownVao);glActiveTexture(GL_TEXTURE7);int[] before=state();
   var view=new org.joml.Matrix4f().translation(0,-160,0);var eye=new org.joml.Vector3f(4,170,1);var sun=new org.joml.Vector3f(0,1,0);
   Mesh.beginFrame();water.capture(world,models,projection(),view,eye,640,480,0,sun,1f,1f);check(water.found()&&water.planarReady()&&Mesh.drawCalls()>0,"Replay geometry participates in enabled planar capture");check(Arrays.equals(before,state()),"Successful planar capture restores draw state");
   var denied=new RuntimeException("Synthetic lighting capture failure");check(org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,()->water.capture(world,models,projection(),view,eye,640,480,0,sun,1f,1f,p->{throw denied;}))==denied,"Capture failure preserved");check(water.found()&&!water.planarReady()&&Arrays.equals(before,state()),"Failed capture restores draw state and invalidates readiness");
   water.render(world,projection(),view,eye,0,0,0,640,480,0,0);int program=glGetInteger(GL_CURRENT_PROGRAM);check(glGetUniformi(program,glGetUniformLocation(program,"uPlanarReady"))==0,"Failed capture renders without stale planar texture");check(glGetError()==GL_NO_ERROR,"Fallback water GL state");
   before=state();Mesh.beginFrame();water.capture(world,models,projection(),view,eye,640,480,0,sun,1f,1f);check(water.planarReady()&&Mesh.drawCalls()>0&&Arrays.equals(before,state()),"Planar retry succeeds and restores state");
   var field=dev.jayms.render.WaterRenderer.class.getDeclaredField("fbo");field.setAccessible(true);int prior=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);glBindFramebuffer(GL_READ_FRAMEBUFFER,field.getInt(water));glFinish();var data=MemoryUtil.memAlloc(320*240*4);
   try {glReadPixels(0,0,320,240,GL_RGBA,GL_UNSIGNED_BYTE,data);var output=new BufferedImage(320,240,BufferedImage.TYPE_INT_RGB);int lit=0,max=0;
    for(int y=0;y<240;y++)for(int x=0;x<320;x++){int p=(y*320+x)*4,r=data.get(p)&255,g=data.get(p+1)&255,b=data.get(p+2)&255;max=Math.max(max,Math.max(r,Math.max(g,b)));if(r>30&&g>30&&b>30)lit++;output.setRGB(x,239-y,(r<<16)|(g<<8)|b);}
    check(lit>20,"Above-water replay geometry appears in reflected framebuffer; lit="+lit+",max="+max);check(javax.imageio.ImageIO.write(output,"png",image.toFile()),"Planar PNG capture");
   } finally {MemoryUtil.memFree(data);glBindFramebuffer(GL_READ_FRAMEBUFFER,prior);}
   water.optics(false);check(!water.planarReady(),"Disabling optics invalidates readiness");water.optics(true);check(!water.planarReady(),"Reenable requires fresh capture");
   water.capture(world,models,projection(),view,new org.joml.Vector3f(4,160,1),640,480,0,sun,1f,1f);check(water.found()&&!water.planarReady(),"Underwater selection does not advertise planar capture");check(glGetError()==GL_NO_ERROR,"Planar checks GL state");
  } finally {glBindVertexArray(0);glDeleteVertexArrays(ownVao);glActiveTexture(GL_TEXTURE0);glDisable(GL_CLIP_DISTANCE0);glFrontFace(GL_CCW);}
 }
 static void capture(World world,ShaderProgram shader,Path image)throws Exception {
  glViewport(0,0,640,480);glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glClearColor(.04f,.07f,.12f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
  draw(world,shader);water(world);glFinish();var data=MemoryUtil.memAlloc(640*480*4);
  try {glReadPixels(0,0,640,480,GL_RGBA,GL_UNSIGNED_BYTE,data);var output=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);int red=0,blue=0,gray=0,water=0;
   for(int y=0;y<480;y++)for(int x=0;x<640;x++){int i=(y*640+x)*4,r=data.get(i)&255,g=data.get(i+1)&255,b=data.get(i+2)&255;if(r<=5&&g>=15&&g<=25&&b>=25&&b<=35)water++;if(r>b*2&&r>50)red++;if(b>r*2&&b>50)blue++;if(r>50&&g>50&&b>50&&Math.abs(r-g)<30&&Math.abs(r-b)<30)gray++;output.setRGB(x,479-y,(r<<16)|(g<<8)|b);}
   check(red>100&&blue>100&&gray>100&&water>100,"Ordinary/models/production water visible; water="+water);check(javax.imageio.ImageIO.write(output,"png",image.toFile()),"PNG capture");
  }finally{MemoryUtil.memFree(data);}
 }
 static void cpu(RegionReplayScene scene,int count)throws Exception {
  long end=System.nanoTime()+10_000_000_000L;while(scene.status().data().ready()!=count&&System.nanoTime()<end){scene.tick();Thread.sleep(5);}check(scene.status().data().ready()==count,"CPU chunks ready");
 }
 static void gpu(RegionReplayScene scene,int count)throws Exception {
  int steps=0;while(scene.status().gpuEntries()!=count&&steps++<10000){int n=scene.pumpGpu(64,deadline(),()->false);check(n>=0&&n<=64,"Shared per-call byte cap");glfwPollEvents();}check(scene.status().gpuEntries()==count,"Automatic GPU publication");
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),report=Path.of(args[1]),image=Path.of(args[2]);if(Files.exists(root)||Files.exists(report)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
  Path path=ReplayModelGpuCheck.fixture(root);var loaded=new LocalGame(path,0);var rows=new WorldVoxels.History();loaded.edits.values().forEach(e->WorldVoxels.remember(rows,e));
  for(var edit:List.of(new Protocol.Edit(0,160,0,Blocks.STONE),new Protocol.Edit(4,160,1,Blocks.WATER),new Protocol.Edit(4,162,1,Blocks.SNOW),new Protocol.Edit(48,160,0,Blocks.STONE)))WorldVoxels.remember(rows,edit);
  try(var save=StagedOfflineSave.stageRegions(path,loaded.seed,loaded.generatorVersion,loaded.models,rows,()->false,n->{})){save.publish(loaded.inventory,loaded.health,loaded.drops.values(),loaded.drops.size());}
  byte[] original=Files.readAllBytes(path);Path spool=Files.createDirectory(root.resolve("spool"));check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(640,480,"Voxel One bounded replay scene pump",0,0);check(window!=0,"GLFW window");glfwMakeContextCurrent(window);GL.createCapabilities();
  String renderer=glGetString(GL_RENDERER),version=glGetString(GL_VERSION);long baseline=Mesh.estimatedResidentBytes();
  Files.writeString(root.getParent().resolve("replay-scene-render.vert"),"#version 330 core\nlayout(location=0)in vec3 p;layout(location=1)in vec3 n;layout(location=2)in vec3 c;layout(location=3)in vec3 off;layout(location=4)in vec3 surface;out vec3 color;flat out float material;uniform mat4 uProjection,uView,uModel;uniform int uInstanced;void main(){material=surface.z;gl_Position=uProjection*uView*uModel*vec4(p+(uInstanced==1?off:vec3(0)),1);color=c*(.55+.45*abs(n.y));}");
  Files.writeString(root.getParent().resolve("replay-scene-render.frag"),"#version 330 core\nin vec3 color;flat in float material;out vec4 frag;void main(){if(material==-2.)discard;frag=vec4(color,1);}");
  var shader=new ShaderProgram("replay-scene-render.vert","replay-scene-render.frag");
  var worker=new RegionReplayWorker(spool,(cancel,progress)->LocalGame.openRegionalReadback(path,spool,RegionReplayWorkerTest.REV,cancel,progress),1,1,196,7);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(RegionReplayWorkerTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var scene=worker.bindTarget(local,world).openScene(world,2,8192);var a=new ChunkPos(0,10,0);var b=new ChunkPos(3,10,0);
    scene.desired(List.of(a,b));cpu(scene,2);check(scene.pumpGpu(0,deadline(),()->false)==0&&scene.pumpGpu(64,System.nanoTime()-1,()->false)==0&&scene.status().uploading()==null,"Zero/expired budget does no staging");
    scene.pumpGpu(4,deadline(),()->false);check(a.equals(scene.status().uploading())&&!world.isLoaded(0,160,0)&&Mesh.estimatedResidentBytes()>baseline,"One private upload, no partial readiness");fails(IOException.class,()->scene.beginUpload(b));
    fails(IllegalArgumentException.class,()->scene.pumpGpu(262145,deadline(),()->false));
    var executor=Executors.newSingleThreadExecutor();try{executor.submit(()->fails(IOException.class,()->scene.pumpGpu(64,deadline(),()->false))).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
    fails(CancellationException.class,()->scene.pumpGpu(64,deadline(),()->true));check(scene.status().uploading()==null&&Mesh.estimatedResidentBytes()==baseline&&scene.status().data().ready()==2,"Cancel closes stage, preserves CPU data");
    scene.pumpGpu(4,deadline(),()->false);scene.desired(List.of(b));check(scene.status().uploading()==null&&Mesh.estimatedResidentBytes()==baseline&&!world.replayDataReady(0,160,0),"Desired eviction aborts private upload");
    scene.desired(List.of(a,b));cpu(scene,2);gpu(scene,1);check(world.isLoaded(0,160,0)&&!world.isLoaded(48,160,0),"First ready chunk publishes independently");long first=Mesh.estimatedResidentBytes();
    fails(RuntimeException.class,()->scene.pumpGpu(64,deadline(),()->{try{scene.desired(List.of());}catch(IOException e){throw new RuntimeException(e);}return false;}));check(world.isLoaded(0,160,0)&&Mesh.estimatedResidentBytes()==first,"Reentrant eviction rejected, published entry retained");
    scene.pumpGpu(4,deadline(),()->false);check(b.equals(scene.status().uploading()),"Only second chunk staged");fails(CancellationException.class,()->scene.pumpGpu(64,deadline(),()->true));check(world.isLoaded(0,160,0)&&!world.isLoaded(48,160,0)&&Mesh.estimatedResidentBytes()==first,"Second cancellation preserves first GPU entry");
    gpu(scene,2);check(world.isLoaded(48,160,0)&&world.nearestReplayWater(4,170,1).height()==161,"Two published chunks with water");long complete=Mesh.estimatedResidentBytes();check(scene.pumpGpu(64,deadline(),()->false)==0&&Mesh.estimatedResidentBytes()==complete,"Complete scene allocates nothing");planar(world,shader,image);capture(world,shader,image.resolveSibling("structure-replay-planar-regression.png"));glfwSwapBuffers(window);
    scene.desired(List.of(b));check(!world.isLoaded(0,160,0)&&world.isLoaded(48,160,0)&&world.nearestReplayWater(4,170,1)==null,"Eviction keeps other published chunk");long retained=Mesh.estimatedResidentBytes();
    scene.desired(List.of(a,b));cpu(scene,2);scene.pumpGpu(4,deadline(),()->false);local.models=local.models.copy();fails(IOException.class,()->scene.pumpGpu(64,deadline(),()->false));check(Mesh.estimatedResidentBytes()==retained,"Stale source closes active stage");fails(IllegalStateException.class,()->world.isLoaded(48,160,0));
    check(world.getLoadedChunks().isEmpty()&&world.editsVersion()==0,"No resident/history promotion");fails(IOException.class,local::save);world.close();check(Mesh.estimatedResidentBytes()==baseline,"World closes remaining GPU entry");scene.close();check(Arrays.equals(original,Files.readAllBytes(path)),"Source unchanged");
   }
   Path closeSpool=Files.createDirectory(root.resolve("close-spool"));
   var closeWorker=new RegionReplayWorker(closeSpool,(cancel,progress)->LocalGame.openRegionalReadback(path,closeSpool,RegionReplayWorkerTest.REV,cancel,progress),1,1,196,7);
   try {
    ReadbackMetadataTest.ready(closeWorker);var closeLocal=closeWorker.metadataResult(RegionReplayWorkerTest.REV);
    try(var closeWorld=new World(closeLocal.seed,closeLocal.models,closeLocal.generatorVersion)) {
     var closeScene=closeWorker.bindTarget(closeLocal,closeWorld).openScene(closeWorld,1,8192);closeScene.desired(List.of(new ChunkPos(0,10,0)));cpu(closeScene,1);closeScene.pumpGpu(4,deadline(),()->false);
     check(closeScene.status().uploading()!=null&&Mesh.estimatedResidentBytes()>baseline,"Close fixture has active upload");closeScene.close();closeScene.close();check(Mesh.estimatedResidentBytes()==baseline,"Scene close releases active upload");
    }
   }finally{RegionReplayWorkerTest.closed(closeWorker);}
   check(glGetError()==GL_NO_ERROR&&Mesh.estimatedResidentBytes()==baseline,"GL cleanup");Files.writeString(report,"{\"status\":\"passed\",\"published_chunks\":2,\"max_owned_uploads\":1,\"renderer\":\""+renderer+"\",\"gl_version\":\""+version+"\",\"qualification\":false}\n");
  }finally{RegionReplayWorkerTest.closed(worker);shader.close();glfwDestroyWindow(window);glfwTerminate();}
 }
}
