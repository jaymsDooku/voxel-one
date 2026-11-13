package dev.jayms.net;
import dev.jayms.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

public final class ReplayModelGpuCheck {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void fails(Class<? extends Throwable> expected,org.junit.jupiter.api.function.Executable call){org.junit.jupiter.api.Assertions.assertThrows(expected,call);}
 static long deadline(){return System.nanoTime()+100000000;}
 static RegionReplayWorker.Request request(RegionReplayWorker.Binding binding,World world)throws Exception {var r=binding.submitChunk(new ChunkPos(0,10,0),8192,world);RegionReplayWorkerTest.done(r);return r;}
 static void finish(RegionReplayModelUpload upload)throws Exception{int steps=0;while(!upload.ready()&&steps++<10000){int sent=upload.pump(64,deadline(),()->false);check(sent>=0&&sent<=64,"Model shared byte budget");glfwPollEvents();}check(upload.ready(),"Models complete");}
 static Path fixture(Path folder)throws Exception {
  var models=new ModelLibrary();var red=new SparseVoxelOctree(8);red.fill(0,0,0,8,8,8,0xffed593c);models.accept(new ModelLibrary.Entry(9,"synthetic",new ModelDefinition("Red8",red)));
  var blue=new SparseVoxelOctree(32);blue.fill(0,0,0,32,8,32,0xff3b91f4);blue.fill(0,0,0,8,32,32,0xff3b91f4);int id=models.stageStructureModel(new ModelDefinition("Blue32",blue)).id();check(id==1024,"Synthetic detailed dependency");
  var rows=new WorldVoxels.History();for(int[] cell:new int[][]{{2,2,9},{6,2,9},{2,6,id},{6,6,id}})WorldVoxels.remember(rows,new Protocol.Edit(cell[0],160,cell[1],cell[2]));
  Path path=folder.resolve("world.dat");try(var save=StagedOfflineSave.stageRegions(path,42,Terrain.CURRENT_VERSION,models,rows,()->false,n->{})){save.publish(new Inventory(),20,List.of(),0);}return path;
 }
 static void bytes(RegionReplayModelUpload.Resources resources,RegionReplayChunk.Snapshot snapshot)throws Exception {
  check(resources.batches().size()==2,"Two model types transferred");
  for(int i=0;i<2;i++) {
   var actual=resources.batches().get(i);var expected=snapshot.modelDraws().batches().get(i);check(actual.type()==expected.type()&&actual.instances()==2,"Type and instance count");
   check(actual.definition().fingerprint().equals(expected.definition().fingerprint()),"Definition preserved");check(actual.definition().voxels().size()==(actual.type()==9?8:32),"Model resolution preserved");
   float[] vertices=new float[expected.geometry().vertices().length],positions=new float[6];int[] indices=new int[expected.geometry().indices().length];
   glBindBuffer(GL_COPY_READ_BUFFER,ReplayGpuUploadCheck.id(actual.mesh(),"vbo"));glGetBufferSubData(GL_COPY_READ_BUFFER,0,vertices);glBindBuffer(GL_COPY_READ_BUFFER,ReplayGpuUploadCheck.id(actual.mesh(),"ebo"));glGetBufferSubData(GL_COPY_READ_BUFFER,0,indices);
   glBindBuffer(GL_COPY_READ_BUFFER,ReplayGpuUploadCheck.id(actual.mesh(),"instances"));glGetBufferSubData(GL_COPY_READ_BUFFER,0,positions);glBindBuffer(GL_COPY_READ_BUFFER,0);
   check(Arrays.equals(vertices,expected.geometry().vertices())&&Arrays.equals(indices,expected.geometry().indices()),"Model mesh GPU bytes exact");
   for(int j=0;j<2;j++){int key=expected.cells()[j];check(positions[j*3]==(key&15)&&positions[j*3+1]==(key>>>8)&&positions[j*3+2]==((key>>>4)&15),"Local instance GPU positions exact");}
  }
 }
 static void capture(RegionReplayModelUpload.Resources resources,Path image)throws Exception {
  int vs=ReplayGpuUploadCheck.shader(GL_VERTEX_SHADER,"#version 330 core\nlayout(location=0)in vec3 p;layout(location=1)in vec3 n;layout(location=2)in vec3 c;layout(location=3)in vec3 off;out vec3 color;void main(){vec3 v=p+off;gl_Position=vec4((v.x-v.z)*.13,(v.y*.27-(v.x+v.z)*.075)+.65,(v.x+v.z-v.y)*.02,1);color=c*(.55+.45*abs(n.y));}");
  int fs=ReplayGpuUploadCheck.shader(GL_FRAGMENT_SHADER,"#version 330 core\nin vec3 color;out vec4 frag;void main(){frag=vec4(color,1);}");int program=glCreateProgram();
  try {
   glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(program));glViewport(0,0,640,480);glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glClearColor(.04f,.07f,.12f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(program);
   for(var batch:resources.batches())batch.mesh().renderPreparedInstances();glFinish();var data=MemoryUtil.memAlloc(640*480*4);
   try {glReadPixels(0,0,640,480,GL_RGBA,GL_UNSIGNED_BYTE,data);var output=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);int red=0,blue=0;
    for(int y=0;y<480;y++)for(int x=0;x<640;x++){int i=(y*640+x)*4,r=data.get(i)&255,g=data.get(i+1)&255,b=data.get(i+2)&255;if(r>b*2&&r>50)red++;if(b>r*2&&b>50)blue++;output.setRGB(x,479-y,(r<<16)|(g<<8)|b);}
    check(red>100&&blue>100,"Both detailed model types rendered");check(javax.imageio.ImageIO.write(output,"png",image.toFile()),"PNG capture");
   }finally{MemoryUtil.memFree(data);}
  }finally{glUseProgram(0);glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);}
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),report=Path.of(args[1]),image=Path.of(args[2]);if(Files.exists(root)||Files.exists(report)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);Path path=fixture(root),spool=Files.createDirectory(root.resolve("spool"));byte[] original=Files.readAllBytes(path);
  check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long window=glfwCreateWindow(640,480,"Voxel One private model GPU staging",0,0);check(window!=0,"GL window");glfwMakeContextCurrent(window);GL.createCapabilities();
  String vendor=glGetString(GL_VENDOR),renderer=glGetString(GL_RENDERER),version=glGetString(GL_VERSION);long baseline=Mesh.estimatedResidentBytes();
  var worker=new RegionReplayWorker(spool,(cancel,progress)->LocalGame.openRegionalReadback(path,spool,RegionReplayWorkerTest.REV,cancel,progress),1,1,112,4);
  try {
   ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(RegionReplayWorkerTest.REV);
   try(var world=new World(local.seed,local.models,local.generatorVersion)) {
    var binding=worker.bindTarget(local,world);var request=request(binding,world);
    for(int mode=0;mode<3;mode++)try(var upload=new RegionReplayModelUpload(binding,request,world)) {
     check(upload.pump(0,deadline(),()->false)==0&&upload.pump(64,System.nanoTime()-1,()->false)==0,"No eager GL allocation");check(Mesh.estimatedResidentBytes()==baseline,"Zero/expired budget no allocation");
     int steps=0;
     if(mode==1){while(!upload.progress().instances()&&steps++<10000)upload.pump(64,deadline(),()->false);check(upload.progress().instances(),"Entered instance phase");}
     if(mode==2){while(upload.progress().completed()<1&&steps++<10000)upload.pump(64,deadline(),()->false);check(upload.progress().completed()==1,"Completed first batch retained privately");}
     check(upload.pump(4,deadline(),()->false)==4,"Partial mesh/instance slice");fails(CancellationException.class,()->upload.pump(64,deadline(),()->true));
    }
    check(Mesh.estimatedResidentBytes()==baseline,"All cancel phases close private meshes and instances");
    try(var upload=new RegionReplayModelUpload(binding,request,world)) {
     upload.pump(4,deadline(),()->false);world.addChunk(new ChunkPos(1,10,0),ChunkGenerator.generate(new ChunkPos(1,10,0),world.terrain()));fails(IOException.class,upload::ready);
    }
    check(Mesh.estimatedResidentBytes()==baseline,"Stale neighbor cleans model stage");request=request(binding,world);
    RegionReplayModelUpload.Resources retained;
    try(var upload=new RegionReplayModelUpload(binding,request,world)) {
     fails(IllegalArgumentException.class,()->upload.pump(262145,deadline(),()->false));
     var executor=Executors.newSingleThreadExecutor();try{executor.submit(()->{fails(IOException.class,()->upload.pump(64,deadline(),()->false));fails(IOException.class,upload::close);}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
     finish(upload);retained=upload.take(()->false);upload.close();bytes(retained,binding.chunkResult(request,world));capture(retained,image);glfwSwapBuffers(window);fails(IOException.class,()->upload.take(()->false));
    }
    try(retained){check(Mesh.estimatedResidentBytes()>baseline,"Transferred resources retained by caller");var executor=Executors.newSingleThreadExecutor();try{executor.submit(()->fails(IOException.class,retained::close)).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}}
    check(Mesh.estimatedResidentBytes()==baseline,"Caller resource close restores accounting");
    var empty=binding.submitChunk(new ChunkPos(0,11,0),8192,world);RegionReplayWorkerTest.done(empty);try(var upload=new RegionReplayModelUpload(binding,empty,world)){check(upload.ready(),"Empty model batch ready");try(var resources=upload.take(()->false)){check(resources.batches().isEmpty(),"Empty resources");}}
    check(Mesh.estimatedResidentBytes()==baseline,"Empty models no allocation");check(world.getLoadedChunks().size()==1&&world.editsVersion()==0&&!local.replayComplete(),"No replay publication/promotion");check(Arrays.equals(original,Files.readAllBytes(path)),"Source unchanged");
   }
   check(glGetError()==GL_NO_ERROR,"No GL errors");check(Mesh.estimatedResidentBytes()==baseline,"Final GL accounting");String json="{\"status\":\"passed\",\"model_types\":2,\"instances\":4,\"model_resolutions\":[8,32],\"mesh_instance_byte_readback\":true,\"cancel_phases\":3,\"stale_wrong_owner_empty\":true,\"vendor\":\""+vendor+"\",\"renderer\":\""+renderer+"\",\"gl_version\":\""+version+"\",\"qualification\":false}\n";Files.writeString(report,json);System.out.println(json);
  }finally{RegionReplayWorkerTest.closed(worker);try(var files=Files.list(spool)){check(files.count()==0,"Spool cleanup");}glfwDestroyWindow(window);glfwTerminate();}
 }
}
