import dev.jayms.*;import dev.jayms.render.*;import dev.jayms.net.city.GameConfig;import dev.jayms.window.Window;
import java.nio.file.*;import java.lang.reflect.*;import java.util.function.Consumer;import org.joml.Vector3f;
import static org.lwjgl.opengl.GL33.*;import static org.lwjgl.glfw.GLFW.*;
/** Real terrain capture compares shared RGBA storage with the prior private-program RGB format. */
public class ReflectionProgramSmoke {
 static Object get(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[0]);Files.createDirectories(out);GraphicsProfile.preset(GraphicsProfile.Preset.BALANCED).with(GraphicsProfile.Key.RESOLUTION,"640x480@60").save(dev.jayms.ui.Controls.directory().resolve("graphics.properties"));Main game=new Main();set(game,"offlineSave",out.resolve("synthetic.dat"));set(game,"seed",748291L);set(game,"gameConfig",new GameConfig(false,false,1200,10));
  game.run(new Main.FrameObserver(){public void started(Main main)throws Exception{
   var world=(World)get(main,"world");var renderer=(RenderPipeline)get(main,"rendering");var models=(VoxelModelRenderer)get(main,"modelRenderer");var eye=new Vector3f(((dev.jayms.player.Player)get(main,"player")).position());int environment=(int)get(renderer,"environment");var shared=(ShaderProgram)get(main,"shader");
   int meshed=0;for(var e:world.getLoadedChunks().entrySet().stream().sorted(java.util.Comparator.comparingDouble(e->new Vector3f(e.getKey().chunkX()*16+8,e.getKey().chunkY()*16+8,e.getKey().chunkZ()*16+8).distanceSquared(eye))).toList())if(!e.getValue().isEmpty()&&meshed<4){e.getValue().generateMesh();meshed++;}if(meshed==0)throw new AssertionError("No terrain fixture");
   Method bind=RenderPipeline.class.getDeclaredMethod("bindSceneLighting",ShaderProgram.class);bind.setAccessible(true);Consumer<ShaderProgram> lighting=shader->{try{bind.invoke(renderer,shader);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}};
   long before=Mesh.estimatedResidentBytes();int compared=0;int sharedProgram;
   shared.bind();sharedProgram=glGetInteger(GL_CURRENT_PROGRAM);
   try(var candidate=new ReflectionProbes(shared);var control=new ReflectionProbes()){
    glBindTexture(GL_TEXTURE_CUBE_MAP,(int)get(control,"cube"));for(int level=0,size=64;size>0;level++,size>>=1)for(int face=0;face<6;face++)glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,level,GL_RGB16F,size,size,0,GL_RGB,GL_FLOAT,(java.nio.ByteBuffer)null);
    for(int face=0;face<6;face++){
     candidate.capture(world,models,eye,environment,lighting);control.capture(world,models,eye,environment,lighting);
     if(candidate.ready()!=(face==5)||control.ready()!=(face==5))throw new AssertionError("Premature readiness");
     float[][] data=new float[2][64*64*3];int n=0;for(var probes:new ReflectionProbes[]{candidate,control}){glBindTexture(GL_TEXTURE_CUBE_MAP,(int)get(probes,"cube"));glGetTexImage(GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,0,GL_RGB,GL_FLOAT,data[n++]);}
     for(int i=0;i<data[0].length;i++){if(Float.floatToIntBits(data[0][i])!=Float.floatToIntBits(data[1][i]))throw new AssertionError("Capture changed face="+face+" channel="+i);compared++;}
    }
    candidate.invalidate();if(candidate.ready())throw new AssertionError("Invalidation");candidate.capture(world,models,eye,environment,lighting);
   }
   if(!glIsProgram(sharedProgram))throw new AssertionError("Borrowed program closed");shared.bind();
   var graphics=(GraphicsController)get(main,"graphics");var original=graphics.transaction.requested();
   for(var profile:new GraphicsProfile[]{original.with(GraphicsProfile.Key.FRAME_CAP,"60"),original}){
    if(!graphics.transaction.apply(profile,System.nanoTime()))throw new AssertionError("Graphics apply: "+graphics.transaction.message());
    var active=(RenderPipeline)get(main,"rendering");
    if(get(get(active,"probes"),"voxel")!=shared||!glIsProgram(sharedProgram))throw new AssertionError("Graphics rebuild lost borrowed shader");
   }
   var active=(RenderPipeline)get(main,"rendering");var replacement=RenderPipeline.class.getDeclaredMethod("replacement",GraphicsProfile.class);replacement.setAccessible(true);
   try(var rejected=(RenderPipeline)replacement.invoke(active,original)){
    if(get(get(rejected,"probes"),"voxel")!=shared)throw new AssertionError("Rollback candidate lost borrowed shader");
   }
   if(!glIsProgram(sharedProgram))throw new AssertionError("Rejected candidate closed main shader");shared.bind();
   if(Mesh.estimatedResidentBytes()!=before||glGetError()!=GL_NO_ERROR)throw new AssertionError("Resource/GL cleanup");
   Files.writeString(out.resolve("result.txt"),"Playtest: PASS. Shared-program RGBA16F and private-program RGB16F terrain captures: "+compared+" bit-identical RGB channels; six-face readiness, invalidation, borrowed-program ownership, actual graphics rebuild/restore, discarded-candidate cleanup passed. Terrain meshes="+meshed+"; Renderer="+glGetString(GL_RENDERER)+"\n");glfwSetWindowShouldClose(((Window)get(main,"window")).getHandle(),true);
  }});
 }
}
