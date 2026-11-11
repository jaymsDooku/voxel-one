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

public final class ReplayChunkGpuCheck {
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
  byte[] original=Files.readAllBytes(path);Path spool=Files.createDirectory(root.resolve("spool"));check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(640,480,"Voxel One combined replay GPU resources",0,0);check(window!=0,"GLFW window");glfwMakeContextCurrent(window);GL.createCapabilities();
  String vendor=glGetString(GL_VENDOR),renderer=glGetString(GL_RENDERER),version=glGetString(GL_VERSION);long baseline=Mesh.estimatedResidentBytes();
  var worker=new RegionReplayWorker(spool,(cancel,progress)->LocalGame.openRegionalReadback(path,spool,RegionReplayWorkerTest.REV,cancel,progress),1,1,140,5);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(RegionReplayWorkerTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var request=request(binding,world);
    try(var upload=new RegionReplayChunkUpload(binding,request,world)){check(upload.pump(0,deadline(),()->false)==0&&upload.pump(64,System.nanoTime()-1,()->false)==0,"No eager allocation");check(Mesh.estimatedResidentBytes()==baseline,"Zero budget allocates nothing");fails(IOException.class,()->upload.take(()->false));}
    try(var upload=new RegionReplayChunkUpload(binding,request,world)) {
     int steps=0;while(!upload.progress().ordinaryReady()&&steps++<10000)upload.pump(64,deadline(),()->false);
     check(upload.progress().ordinaryReady()&&!upload.ready(),"Ordinary complete stays private");upload.pump(4,deadline(),()->false);fails(CancellationException.class,()->upload.pump(64,deadline(),()->true));
    }
    check(Mesh.estimatedResidentBytes()==baseline,"Model failure closes ordinary and model lanes");
    for(int stop:new int[]{4,6})try(var upload=new RegionReplayChunkUpload(binding,request,world)) {
     finish(upload);var calls=new AtomicInteger();fails(CancellationException.class,()->upload.take(()->calls.incrementAndGet()>=stop));check(calls.get()==stop,"Transfer cutpoint reached");
    }
    check(Mesh.estimatedResidentBytes()==baseline,"Between/after transfer cancellation closes all handles");
    try(var upload=new RegionReplayChunkUpload(binding,request,world)){upload.pump(4,deadline(),()->false);world.addChunk(new ChunkPos(1,10,0),ChunkGenerator.generate(new ChunkPos(1,10,0),world.terrain()));fails(IOException.class,upload::ready);}
    check(Mesh.estimatedResidentBytes()==baseline,"Stale stage cleanup");request=request(binding,world);
    RegionReplayChunkUpload.Resources resources;
    try(var upload=new RegionReplayChunkUpload(binding,request,world)) {
     fails(IllegalArgumentException.class,()->upload.pump(262145,deadline(),()->false));var executor=Executors.newSingleThreadExecutor();
     try{executor.submit(()->{fails(IOException.class,()->upload.pump(64,deadline(),()->false));fails(IOException.class,upload::close);}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
     finish(upload);resources=upload.take(()->false);upload.close();fails(IOException.class,()->upload.take(()->false));
    }
    try(resources) {
     resources.validate();ReplayModelGpuCheck.bytes(resources.models(),resources.snapshot());var cpu=resources.snapshot().mesh();float[] vertices=new float[cpu.vertices().length];
     glBindBuffer(GL_COPY_READ_BUFFER,ReplayGpuUploadCheck.id(resources.ordinary(),"vbo"));glGetBufferSubData(GL_COPY_READ_BUFFER,0,vertices);glBindBuffer(GL_COPY_READ_BUFFER,0);check(Arrays.equals(vertices,cpu.vertices()),"Combined ordinary GPU bytes exact");
     capture(resources,image);glfwSwapBuffers(window);var executor=Executors.newSingleThreadExecutor();
     try{executor.submit(()->{fails(IOException.class,resources::ordinary);fails(IOException.class,resources::close);}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
     resources.validate();check(world.getLoadedChunks().size()==1&&world.editsVersion()==0,"No replay World publication");world.getLoadedChunks().get(new ChunkPos(1,10,0)).markDirty();fails(IOException.class,resources::validate);
    }
    check(Mesh.estimatedResidentBytes()==baseline,"Resource owner closes ordinary and models");
    var empty=binding.submitChunk(new ChunkPos(0,11,0),8192,world);RegionReplayWorkerTest.done(empty);try(var upload=new RegionReplayChunkUpload(binding,empty,world)){check(upload.ready(),"Empty combined stage ready");try(var result=upload.take(()->false)){check(result.ordinary()==null&&result.models().batches().isEmpty(),"Empty transfer");}}
    check(Mesh.estimatedResidentBytes()==baseline,"Empty combined transfer no GL allocation");check(!local.replayComplete()&&Arrays.equals(original,Files.readAllBytes(path)),"No source/save promotion");
   }
   check(glGetError()==GL_NO_ERROR,"No GL errors");String json="{\"status\":\"passed\",\"ordinary_meshes\":1,\"model_types\":2,\"model_instances\":4,\"transfer_cancel_cutpoints\":[4,6],\"stale_wrong_owner_empty\":true,\"vendor\":\""+vendor+"\",\"renderer\":\""+renderer+"\",\"gl_version\":\""+version+"\",\"qualification\":false}\n";Files.writeString(report,json);System.out.println(json);
  }finally{RegionReplayWorkerTest.closed(worker);try(var files=Files.list(spool)){check(files.count()==0,"Spool cleanup");}glfwDestroyWindow(window);glfwTerminate();}
 }
}
