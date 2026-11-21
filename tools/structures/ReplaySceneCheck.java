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

public final class ReplaySceneCheck {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void fails(Class<? extends Throwable> expected,org.junit.jupiter.api.function.Executable call){org.junit.jupiter.api.Assertions.assertThrows(expected,call);}
 static long deadline(){return System.nanoTime()+100000000;}
 static void finish(RegionReplayChunkUpload upload)throws Exception {int steps=0;while(!upload.ready()&&steps++<10000){int sent=upload.pump(64,deadline(),()->false);check(sent>=0&&sent<=64,"Combined lanes share byte cap");glfwPollEvents();}check(upload.ready(),"Both lanes complete");}
 static RegionReplayWorker.Request request(RegionReplayWorker.Binding binding,World world)throws Exception{return ReplayModelGpuCheck.request(binding,world);}
 static void capture(RegionReplayChunkUpload.Resources resources,Path image)throws Exception {
  int vs=ReplayGpuUploadCheck.shader(GL_VERTEX_SHADER,"#version 330 core\nlayout(location=0)in vec3 p;layout(location=1)in vec3 n;layout(location=2)in vec3 c;layout(location=3)in vec3 off;out vec3 color;void main(){vec3 v=p+off;gl_Position=vec4((v.x-v.z)*.13,(v.y*.27-(v.x+v.z)*.075)+.65,(v.x+v.z-v.y)*.02,1);color=c*(.55+.45*abs(n.y));}");
  int fs=ReplayGpuUploadCheck.shader(GL_FRAGMENT_SHADER,"#version 330 core\nin vec3 color;out vec4 frag;void main(){frag=vec4(color,1);}");int program=glCreateProgram();
  try {
   glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(program));glViewport(0,0,640,480);glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glClearColor(.04f,.07f,.12f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(program);
   resources.ordinary().render();for(var batch:resources.models().batches())batch.mesh().renderPreparedInstances();glFinish();var data=MemoryUtil.memAlloc(640*480*4);
   try {glReadPixels(0,0,640,480,GL_RGBA,GL_UNSIGNED_BYTE,data);var output=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);int red=0,blue=0,gray=0;
    for(int y=0;y<480;y++)for(int x=0;x<640;x++){int i=(y*640+x)*4,r=data.get(i)&255,g=data.get(i+1)&255,b=data.get(i+2)&255;if(r>b*2&&r>50)red++;if(b>r*2&&b>50)blue++;if(r>50&&g>50&&b>50&&Math.abs(r-g)<30&&Math.abs(r-b)<30)gray++;output.setRGB(x,479-y,(r<<16)|(g<<8)|b);}
    check(red>100&&blue>100&&gray>100,"Ordinary and both model types rendered; red="+red+" blue="+blue+" gray="+gray);check(javax.imageio.ImageIO.write(output,"png",image.toFile()),"PNG capture");
   }finally{MemoryUtil.memFree(data);}
  }finally{glUseProgram(0);glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);}
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),report=Path.of(args[1]),image=Path.of(args[2]);if(Files.exists(root)||Files.exists(report)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
  Path path=ReplayModelGpuCheck.fixture(root);var loaded=new LocalGame(path,0);var rows=new WorldVoxels.History();loaded.edits.values().forEach(e->WorldVoxels.remember(rows,e));WorldVoxels.remember(rows,new Protocol.Edit(0,160,0,Blocks.STONE));
  try(var save=StagedOfflineSave.stageRegions(path,loaded.seed,loaded.generatorVersion,loaded.models,rows,()->false,n->{})){save.publish(loaded.inventory,loaded.health,loaded.drops.values(),loaded.drops.size());}
  byte[] original=Files.readAllBytes(path);Path spool=Files.createDirectory(root.resolve("spool"));check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(640,480,"Voxel One replay scene publication",0,0);check(window!=0,"GLFW window");glfwMakeContextCurrent(window);GL.createCapabilities();
  String renderer=glGetString(GL_RENDERER),version=glGetString(GL_VERSION);long baseline=Mesh.estimatedResidentBytes();
  var worker=new RegionReplayWorker(spool,(cancel,progress)->LocalGame.openRegionalReadback(path,spool,RegionReplayWorkerTest.REV,cancel,progress),1,1,140,5);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(RegionReplayWorkerTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var scene=binding.openScene(world,1,8192);var region=new ChunkPos(0,10,0);
    for(int run=0;run<3;run++) {
     scene.desired(List.of(region));long end=System.nanoTime()+10_000_000_000L;
     while(scene.status().data().ready()!=1&&System.nanoTime()<end){scene.tick();Thread.sleep(5);}
     check(scene.status().data().ready()==1&&world.replayDataReady(0,160,0),"CPU data ready");check(!world.isLoaded(0,160,0),"CPU alone cannot grant mesh readiness");check(BlockRaycaster.trace(world,new org.joml.Vector3f(.5f,160.5f,.5f),new org.joml.Vector3f(0,1,0),1).unavailable()!=null,"CPU ray stops at unavailable mesh");check(world.sample(0,160,0)==Blocks.STONE,"Exact scene cell");
     RegionReplayChunkUpload.Resources resources;
     try(var upload=scene.beginUpload(region)){finish(upload);resources=upload.take(()->false);}
     fails(CancellationException.class,()->scene.publish(resources,()->true));check(!world.isLoaded(0,160,0),"Cancelled publication stays private");resources.validate();
     if(run==0) {
      scene.desired(List.of());fails(IOException.class,()->scene.publish(resources,()->false));resources.close();check(Mesh.estimatedResidentBytes()==baseline,"Evicted staged resources remain caller owned");continue;
     }
     var executor=Executors.newSingleThreadExecutor();try{executor.submit(()->{fails(IOException.class,()->scene.publish(resources,()->false));fails(IOException.class,scene::close);}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
     scene.publish(resources,()->false);check(world.isLoaded(0,160,0)&&scene.resources(region)==resources,"Complete GPU map grants readiness");fails(IOException.class,()->scene.publish(resources,()->false));
     check(BlockRaycaster.trace(world,new org.joml.Vector3f(.5f,160.5f,.5f),new org.joml.Vector3f(0,1,0),1).hit()!=null,"Published scene ray hits stone");ReplayModelGpuCheck.bytes(resources.models(),resources.snapshot());if(run==1){capture(scene.resources(region),image);glfwSwapBuffers(window);}
     check(world.getLoadedChunks().isEmpty()&&world.editsVersion()==0&&!world.nearbyReady(0,160,1),"No resident history or startup promotion");fails(IOException.class,local::save);
     if(run==2){world.close();check(Mesh.estimatedResidentBytes()==baseline,"World close retires populated scene");break;}scene.desired(List.of());check(!world.isLoaded(0,160,0)&&!world.replayDataReady(0,160,0),"Eviction removes CPU and GPU readiness");check(Mesh.estimatedResidentBytes()==baseline,"Scene evicts all GPU resources");fails(IOException.class,resources::validate);
    }
    scene.close();scene.close();check(Arrays.equals(original,Files.readAllBytes(path)),"Source unchanged");
   }
   check(glGetError()==GL_NO_ERROR&&Mesh.estimatedResidentBytes()==baseline,"GL cleanup");Files.writeString(report,"{\"status\":\"passed\",\"renderer\":\""+renderer+"\",\"gl_version\":\""+version+"\",\"qualification\":false}\n");
  }finally{RegionReplayWorkerTest.closed(worker);glfwDestroyWindow(window);glfwTerminate();}
 }
}
