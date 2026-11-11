package dev.jayms.net;
import dev.jayms.*;
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

public final class ReplayGpuUploadCheck {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void fails(Class<? extends Throwable> expected,org.junit.jupiter.api.function.Executable call){org.junit.jupiter.api.Assertions.assertThrows(expected,call);}
 static int id(Mesh mesh,String name)throws Exception{var f=Mesh.class.getDeclaredField(name);f.setAccessible(true);return f.getInt(mesh);}
 static RegionReplayWorker.Request request(RegionReplayWorker.Binding binding,World world)throws Exception {var r=binding.submitChunk(RegionReplayBindingTest.REGION,8192,world);RegionReplayWorkerTest.done(r);return r;}
 static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);check(glGetShaderi(s,GL_COMPILE_STATUS)==GL_TRUE,glGetShaderInfoLog(s));return s;}
 static void capture(Mesh mesh,Path image)throws Exception {
  int vs=shader(GL_VERTEX_SHADER,"#version 330 core\nlayout(location=0)in vec3 p;layout(location=2)in vec3 c;out vec3 color;void main(){gl_Position=vec4((p.x-p.z)*.6,(p.y*.9-(p.x+p.z)*.3)-.15,(p.x+p.z-p.y)*.1,1);color=c;}");
  int fs=shader(GL_FRAGMENT_SHADER,"#version 330 core\nin vec3 color;out vec4 frag;void main(){frag=vec4(color,1);}");int program=glCreateProgram();
  try {
   glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,glGetProgramInfoLog(program));
   glViewport(0,0,640,480);glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glClearColor(.04f,.07f,.12f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(program);mesh.render();glFinish();
   var bytes=MemoryUtil.memAlloc(640*480*4);
   try {
    glReadPixels(0,0,640,480,GL_RGBA,GL_UNSIGNED_BYTE,bytes);var output=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);int changed=0;
    for(int y=0;y<480;y++)for(int x=0;x<640;x++){int i=(y*640+x)*4,r=bytes.get(i)&255,g=bytes.get(i+1)&255,b=bytes.get(i+2)&255;if(r>40||g>40||b>50)changed++;output.setRGB(x,479-y,(r<<16)|(g<<8)|b);}
    check(changed>1000,"Uploaded ordinary geometry rendered into framebuffer");check(javax.imageio.ImageIO.write(output,"png",image.toFile()),"PNG writer");
   }finally{MemoryUtil.memFree(bytes);}
  }finally{glUseProgram(0);glDeleteProgram(program);glDeleteShader(vs);glDeleteShader(fs);}
 }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]),report=Path.of(args[1]),image=Path.of(args[2]);if(Files.exists(root)||Files.exists(report)||Files.exists(image))throw new IllegalArgumentException("Fresh outputs required");Files.createDirectory(root);
  check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  long window=glfwCreateWindow(640,480,"Voxel One private replay GPU upload",0,0);check(window!=0,"GLFW window");glfwMakeContextCurrent(window);GL.createCapabilities();
  String vendor=glGetString(GL_VENDOR),renderer=glGetString(GL_RENDERER),version=glGetString(GL_VERSION);long baseline=Mesh.estimatedResidentBytes();
  try {
   var fixture=new ReadbackMetadataTest();fixture.folder=Files.createDirectory(root.resolve("source"));var path=fixture.fixture();byte[] original=Files.readAllBytes(path);var worker=fixture.worker(path);
   try {
    ReadbackMetadataTest.ready(worker);var local=worker.metadataResult(ReadbackMetadataTest.REV);
    try(var world=new World(local.seed,local.models,local.generatorVersion)) {
     var binding=worker.bindTarget(local,world);var request=request(binding,world);
     try(var upload=new RegionReplayMeshUpload(binding,request,world)) {
      check(upload.pump(0,System.nanoTime()+1000000,()->false)==0,"Zero budget does not allocate");check(upload.pump(64,System.nanoTime()-1,()->false)==0,"Expired deadline does not allocate");check(Mesh.estimatedResidentBytes()==baseline,"No eager GPU allocation");
      fails(IllegalArgumentException.class,()->upload.pump(RegionReplayMeshUpload.MAX_PUMP_BYTES+1,System.nanoTime()+1000000,()->false));
      fails(IOException.class,()->upload.take(()->false));
     }
     try(var upload=new RegionReplayMeshUpload(binding,request,world)) {
      check(upload.pump(4,System.nanoTime()+100000000,()->false)==4&&!upload.ready(),"Partial four-byte upload");
      fails(CancellationException.class,()->upload.pump(64,System.nanoTime()+100000000,()->true));
     }
     check(Mesh.estimatedResidentBytes()==baseline,"Cancelled upload releases owned GL buffers");
     try(var upload=new RegionReplayMeshUpload(binding,request,world)) {
      upload.pump(4,System.nanoTime()+100000000,()->false);world.addChunk(new ChunkPos(1,10,0),ChunkGenerator.generate(new ChunkPos(1,10,0),world.terrain()));
      fails(IOException.class,()->upload.pump(64,System.nanoTime()+100000000,()->false));
     }
     check(Mesh.estimatedResidentBytes()==baseline,"Stale-neighbor upload releases buffers");request=request(binding,world);
     try(var upload=new RegionReplayMeshUpload(binding,request,world)) {
      upload.pump(4,System.nanoTime()+100000000,()->false);var executor=Executors.newSingleThreadExecutor();
      try{executor.submit(()->{fails(IOException.class,()->upload.pump(64,System.nanoTime()+1000000,()->false));fails(IOException.class,upload::close);}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
      check(!upload.ready()&&Mesh.estimatedResidentBytes()>baseline,"Wrong owner preserves GL stage");
      int steps=0;while(!upload.ready()&&steps++<1000){int sent=upload.pump(64,System.nanoTime()+100000000,()->false);check(sent>=0&&sent<=64,"Slice byte budget");glfwPollEvents();}
      check(upload.ready(),"Bounded upload complete");Mesh mesh=upload.take(()->false);
      try {
       check(mesh!=null&&glIsBuffer(id(mesh,"vbo")),"Transferred mesh live");var data=binding.chunkResult(request,world).mesh();
       float[] vertices=new float[data.vertices().length];int[] indices=new int[data.indices().length];
       glBindBuffer(GL_COPY_READ_BUFFER,id(mesh,"vbo"));glGetBufferSubData(GL_COPY_READ_BUFFER,0,vertices);glBindBuffer(GL_COPY_READ_BUFFER,id(mesh,"ebo"));glGetBufferSubData(GL_COPY_READ_BUFFER,0,indices);glBindBuffer(GL_COPY_READ_BUFFER,0);
       check(Arrays.equals(vertices,data.vertices())&&Arrays.equals(indices,data.indices()),"GPU vertex/index bytes match source");
       if(data.surface()!=null){float[] surface=new float[data.surface().length];glBindBuffer(GL_COPY_READ_BUFFER,id(mesh,"surfaceBuffer"));glGetBufferSubData(GL_COPY_READ_BUFFER,0,surface);glBindBuffer(GL_COPY_READ_BUFFER,0);check(Arrays.equals(surface,data.surface()),"GPU surface bytes match source");}
       upload.close();check(glIsBuffer(id(mesh,"vbo")),"Stage close preserves transferred ownership");capture(mesh,image);glfwSwapBuffers(window);
       fails(IOException.class,()->upload.take(()->false));
      }finally{mesh.close();}
     }
     check(Mesh.estimatedResidentBytes()==baseline,"Caller closes transferred mesh");
     var fresh=request(binding,world);
     try(var upload=new RegionReplayMeshUpload(binding,fresh,world)) {
      upload.pump(4,System.nanoTime()+100000000,()->false);binding.chunkResult(fresh,world).detached().chunk().setBlock(0,0,0,Blocks.GLASS);fails(IOException.class,upload::ready);
     }
     check(Mesh.estimatedResidentBytes()==baseline,"Detached mutation cleans GL stage");
     var empty=binding.submitChunk(new ChunkPos(0,11,0),8192,world);RegionReplayWorkerTest.done(empty);
     try(var upload=new RegionReplayMeshUpload(binding,empty,world)){check(upload.ready(),"Empty ordinary mesh ready without allocation");check(upload.pump(64,System.nanoTime()+100000000,()->false)==0,"Empty pump writes zero bytes");check(upload.take(()->false)==null,"Empty transfer has no Mesh handle");}
     check(Mesh.estimatedResidentBytes()==baseline,"Empty mesh allocates no GL buffers");check(world.getLoadedChunks().size()==1&&world.editsVersion()==0,"No replay World publication");check(!local.replayComplete(),"No partial authority promotion");check(Arrays.equals(original,Files.readAllBytes(path)),"Source bytes unchanged");
    }
   }finally{RegionReplayWorkerTest.closed(worker);fixture.noSpools();}
   check(glGetError()==GL_NO_ERROR,"No GL errors");check(Mesh.estimatedResidentBytes()==baseline,"GL accounting restored");
   String json="{\"status\":\"passed\",\"checks\":\"slice budgets, cancel, stale neighbor, wrong owner, take ownership, byte readback, draw, detached mutation, empty mesh, unchanged World/save\",\"vendor\":\""+vendor+"\",\"renderer\":\""+renderer+"\",\"gl_version\":\""+version+"\",\"qualification\":false}\n";Files.writeString(report,json);System.out.println(json);
  }finally{glfwDestroyWindow(window);glfwTerminate();}
 }
}
