package dev.jayms.net;

import dev.jayms.*;
import dev.jayms.net.model.*;
import org.joml.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Controlled material diagnostic using the shipped voxel shader, not Main readiness. */
public final class ModelColourCheck {
    static final int PANEL=144,ROWS=5,W=3*PANEL,H=ROWS*PANEL;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int program(ShaderProgram shader)throws Exception {
        var field=ShaderProgram.class.getDeclaredField("programId");field.setAccessible(true);return field.getInt(shader);
    }
    static boolean sampler(int type){return type==GL_SAMPLER_2D||type==GL_SAMPLER_3D||type==GL_SAMPLER_CUBE
            ||type==GL_SAMPLER_2D_ARRAY||type==GL_INT_SAMPLER_3D||type==GL_INT_SAMPLER_BUFFER||type==GL_SAMPLER_BUFFER;}
    static int samplers(ShaderProgram shader)throws Exception {
        int program=program(shader),unit=0,irradiance=-1;
        try(var stack=MemoryStack.stackPush()) {
            var size=stack.mallocInt(1);var type=stack.mallocInt(1);
            for(int i=0;i<glGetProgrami(program,GL_ACTIVE_UNIFORMS);i++) {
                String name=glGetActiveUniform(program,i,256,size,type);
                if(!sampler(type.get(0)))continue;
                int[] units=new int[size.get(0)];for(int j=0;j<units.length;j++)units[j]=unit++;
                glUniform1iv(glGetUniformLocation(program,name),units);
                if(name.equals("uIrradiance"))irradiance=units[0];
            }
        }
        check(unit<=glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)&&irradiance>=0,"Sampler unit budget");return irradiance;
    }
    static void capture(Path image,int[][] counts)throws Exception {
        var pixels=MemoryUtil.memAlloc(W*H*4);
        try {
            glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);var output=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                int i=(y*W+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
                int row=ROWS-1-y/PANEL,column=x/PANEL;
                boolean expected=row>=3?r+g+b<6:column==1?b>r*2&&b>50:r>b*2&&r>50;
                if(expected)counts[row][column]++;
                output.setRGB(x,H-y-1,(r<<16)|(g<<8)|b);
            }
            for(int row=0;row<ROWS;row++)for(int column=0;column<3;column++)check(counts[row][column]>100,"Expected colour/light control row="+row+" column="+column);
            check(javax.imageio.ImageIO.write(output,"png",image.toFile()),"PNG capture");
        }finally{MemoryUtil.memFree(pixels);}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=4||!args[0].equals("--display")||!args[1].equals(System.getenv("DISPLAY")))throw new IllegalArgumentException("Use inherited --display, report and image paths");
        Path report=Path.of(args[2]),image=Path.of(args[3]);check(!Files.exists(report)&&!Files.exists(image),"Fresh outputs required");
        check(glfwInit(),"GLFW init");glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(W,H,"Voxel One production shader colour controls",0,0);check(window!=0,"GLFW window");glfwMakeContextCurrent(window);GL.createCapabilities();long baseline=Mesh.estimatedResidentBytes();
        var red=new SparseVoxelOctree(8);red.fill(0,0,0,8,8,8,0xffed593c);
        var blue=new SparseVoxelOctree(32);blue.fill(0,0,0,32,8,32,0xff3b91f4);blue.fill(0,0,0,8,32,32,0xff3b91f4);
        var ordinary=new Chunk();ordinary.setBlock(0,0,0,Blocks.STONE);
        int texture=glGenTextures();int[][] counts=new int[ROWS][3];String renderer=glGetString(GL_RENDERER),version=glGetString(GL_VERSION);
        try(var shader=new ShaderProgram("shaders/voxel.vert","shaders/voxel.frag");
                var redMesh=VoxelModelRenderer.mesh(new ModelDefinition("Synthetic red8",red));
                var blueMesh=VoxelModelRenderer.mesh(new ModelDefinition("Synthetic blue32",blue));
                var blockMesh=new Mesh(MeshDataGenerator.generate(ordinary))) {
            shader.bind();int irradiance=samplers(shader);glActiveTexture(GL_TEXTURE0+irradiance);glBindTexture(GL_TEXTURE_3D,texture);
            glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            shader.setMatrix4("uProjection",new Matrix4f().ortho(-.85f,.85f,-.85f,.85f,.1f,10));
            shader.setMatrix4("uView",new Matrix4f().lookAt(new Vector3f(2,2,3),new Vector3f(.5f,.5f,.5f),new Vector3f(0,1,0)));shader.setMatrix4("uModel",new Matrix4f());
            shader.setVector3("uLightDirection",-1,-2,-3);shader.setVector3("uColor",237/255f,89/255f,60/255f);shader.setVector3("uCameraPosition",0,0,0);
            shader.setVector3("uVolumeOrigin",-.5f,-.5f,-.5f);shader.setVector3("uVolumeSize",1,1,1);shader.setInt("uRootBuffer",2);
            shader.setFloat("uAmbient",1);shader.setFloat("uDaylight",1);shader.setInt("uOutputTone",1);shader.setFloat("uOutputExposure",1);
            glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glClearColor(.06f,.06f,.06f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            try(var stack=MemoryStack.stackPush()) {
                ByteBuffer light=stack.malloc(4);
                for(int row=0;row<ROWS;row++) {
                    boolean dark=row>=3;light.clear().put((byte)(dark?0:64)).put((byte)(dark?0:64)).put((byte)(dark?0:64)).put((byte)(dark?128:255)).flip();
                    glTexImage3D(GL_TEXTURE_3D,0,GL_RGBA8,1,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,light);
                    shader.setInt("uLightingEnabled",row==0?0:1);shader.setInt("uHeld",row>=2?1:0);shader.setInt("uPlanetLighting",row>=2?1:0);shader.setInt("uHasIrradiance",row>=2&&row<4?1:0);shader.setInt("uSkyColumns",0);
                    for(int column=0;column<3;column++) {
                        glViewport(column*PANEL,(ROWS-1-row)*PANEL,PANEL,PANEL);shader.setInt("uVertexColor",column==2?0:1);shader.setInt("uInstanced",column==0?1:0);
                        if(column==0)redMesh.renderInstanced(new float[]{0,0,0});else if(column==1)blueMesh.render();else blockMesh.render();
                    }
                }
            }
            glFinish();check(glGetError()==GL_NO_ERROR,"Production shader GL error");capture(image,counts);glfwSwapBuffers(window);
        }finally{glDeleteTextures(texture);glfwDestroyWindow(window);glfwTerminate();}
        check(Mesh.estimatedResidentBytes()==baseline,"Mesh cleanup accounting");
        Files.writeString(report,"{\"status\":\"passed\",\"renderer\":\""+renderer+"\",\"gl_version\":\""+version+"\",\"colour_pixels\":"+java.util.Arrays.deepToString(counts)+",\"qualification\":false}\n");
        System.out.println("Playtest: production shader red8 instanced, blue32 and ordinary uniform-colour controls passed; zero-light control stays black.");
    }
}
