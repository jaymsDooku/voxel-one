import dev.jayms.render.ReflectionProbes;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
/** Real GL regression: sampler must be mip-complete before the first capture. */
public class ReflectionCompletenessSmoke {
 public static void main(String[] args)throws Exception{
  if(!glfwInit())throw new AssertionError("GLFW init");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);long w=glfwCreateWindow(64,64,"synthetic reflection completeness",0,0);if(w==0)throw new AssertionError("Window");
  try{glfwMakeContextCurrent(w);org.lwjgl.opengl.GL.createCapabilities();try(var probes=new ReflectionProbes()){
   var field=ReflectionProbes.class.getDeclaredField("cube");field.setAccessible(true);glBindTexture(GL_TEXTURE_CUBE_MAP,field.getInt(probes));
   for(int level=0,size=64;size>0;level++,size>>=1)for(int face=0;face<6;face++){
    int target=GL_TEXTURE_CUBE_MAP_POSITIVE_X+face;
    if(glGetTexLevelParameteri(target,level,GL_TEXTURE_WIDTH)!=size||glGetTexLevelParameteri(target,level,GL_TEXTURE_HEIGHT)!=size||glGetTexLevelParameteri(target,level,GL_TEXTURE_INTERNAL_FORMAT)!=GL_RGBA16F)throw new AssertionError("Incomplete probe face="+face+" level="+level);
   }
   if(glGetTexParameteri(GL_TEXTURE_CUBE_MAP,GL_TEXTURE_MIN_FILTER)!=GL_LINEAR_MIPMAP_LINEAR)throw new AssertionError("Mip filtering changed");
   if(probes.ready())throw new AssertionError("Uncaptured contents exposed");probes.invalidate();if(probes.ready())throw new AssertionError("Invalidate exposed contents");
   glGenerateMipmap(GL_TEXTURE_CUBE_MAP);if(glGetError()!=GL_NO_ERROR)throw new AssertionError("GL error");
  }if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Cleanup GL error");Files.writeString(Path.of(args[0]),"Playtest: PASS. Real OpenGL reflection probe: all42 face/mip images are RGBA16F with correct dimensions before capture; unready contents stay hidden; invalidation, mip generation and cleanup passed. Renderer="+glGetString(GL_RENDERER)+"\n");}
  finally{glfwDestroyWindow(w);glfwTerminate();}
 }
}
