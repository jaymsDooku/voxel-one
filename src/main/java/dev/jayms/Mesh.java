package dev.jayms;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class Mesh implements AutoCloseable {

    private final int vao;
    private final int vbo;
    private final int ebo;
    private int surfaceBuffer;

    private final int indexCount;
    private final long bytes;
    private long instanceBytes;
    private boolean closed;
    private static long residentBytes,drawCalls,triangles;
    public static void beginFrame(){drawCalls=triangles=0;}
    public static long drawCalls(){return drawCalls;}public static long triangles(){return triangles;}
    public static long estimatedResidentBytes(){return residentBytes;}
    private int instances;
    private FloatBuffer instanceData;

    public Mesh(MeshData meshData) {
        this.indexCount = meshData.indices().length;
        bytes=DetailedMeshScheduler.bytes(meshData);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        uploadVertices(meshData.vertices());
        uploadIndices(meshData.indices());

        int stride = 9 * Float.BYTES;

        // Position: layout location 0
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);

        // Normal: layout location 1
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        if (meshData.surface() != null) {
            if (meshData.surface().length != meshData.vertices().length / 9 * 3)
                throw new IllegalArgumentException("Surface attribute count");
            surfaceBuffer = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, surfaceBuffer);
            glBufferData(GL_ARRAY_BUFFER, meshData.surface(), GL_STATIC_DRAW);
            glVertexAttribPointer(4, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(4);
        }
        glBindVertexArray(0);residentBytes+=bytes;
    }

    private Mesh(MeshData data, boolean staged) {
        indexCount=data.indices().length;bytes=DetailedMeshScheduler.bytes(data);vao=glGenVertexArrays();vbo=glGenBuffers();ebo=glGenBuffers();
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,(long)data.vertices().length*4,GL_STATIC_DRAW);
        glVertexAttribPointer(0,3,GL_FLOAT,false,36,0L);glEnableVertexAttribArray(0);
        glVertexAttribPointer(1,3,GL_FLOAT,false,36,12L);glEnableVertexAttribArray(1);
        glVertexAttribPointer(2,3,GL_FLOAT,false,36,24L);glEnableVertexAttribArray(2);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);glBufferData(GL_ELEMENT_ARRAY_BUFFER,(long)data.indices().length*4,GL_STATIC_DRAW);
        if(data.surface()!=null){surfaceBuffer=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,surfaceBuffer);glBufferData(GL_ARRAY_BUFFER,(long)data.surface().length*4,GL_STATIC_DRAW);glVertexAttribPointer(4,3,GL_FLOAT,false,12,0L);glEnableVertexAttribArray(4);}
        glBindVertexArray(0);residentBytes+=bytes;
    }
    /** One upload in flight. Buffer copies are split into bounded slices on the GL thread. */
    public static final class Upload implements AutoCloseable {
        private final Mesh mesh;private final MeshData data;
        private final java.nio.ByteBuffer scratch;
        private int buffer,offset;private boolean taken,closed;
        public Upload(MeshData data){this.data=data;mesh=new Mesh(data,true);try{if(org.lwjgl.opengl.GL11.glGetError()!=org.lwjgl.opengl.GL11.GL_NO_ERROR)throw new IllegalStateException("Mesh buffer allocation failed");scratch=MemoryUtil.memAlloc(256*1024);}catch(RuntimeException e){mesh.close();throw e;}}
        public int pump(int byteBudget,long deadline) {
            int uploaded=0;
            while(buffer<3 && uploaded<byteBudget && System.nanoTime()<deadline) {
                int length=buffer==0?data.vertices().length:buffer==1?data.indices().length:data.surface()==null?0:data.surface().length;
                if(offset==length){buffer++;offset=0;continue;}
                int count=Math.min(Math.min(scratch.capacity()/4,(byteBudget-uploaded)/4),length-offset);
                if(count==0)break;scratch.clear();
                if(buffer==1)scratch.asIntBuffer().put(data.indices(),offset,count);
                else scratch.asFloatBuffer().put(buffer==0?data.vertices():data.surface(),offset,count);
                scratch.limit(count*4);
                // EBO uploads use COPY_WRITE_BUFFER so VAO element bindings stay unchanged.
                glBindBuffer(GL_COPY_WRITE_BUFFER,buffer==0?mesh.vbo:buffer==1?mesh.ebo:mesh.surfaceBuffer);
                glBufferSubData(GL_COPY_WRITE_BUFFER,(long)offset*4,scratch);offset+=count;uploaded+=count*4;
            }
            glBindBuffer(GL_COPY_WRITE_BUFFER,0);
            if(org.lwjgl.opengl.GL11.glGetError()!=org.lwjgl.opengl.GL11.GL_NO_ERROR)throw new IllegalStateException("Mesh buffer upload failed");
            return uploaded;
        }
        public boolean ready(){return buffer==3 || buffer==2 && data.surface()==null;}
        public Mesh take(){if(!ready())throw new IllegalStateException("Upload incomplete");taken=true;return mesh;}
        public void close(){if(closed)return;closed=true;MemoryUtil.memFree(scratch);if(!taken)mesh.close();}
    }

    private void uploadVertices(float[] vertices) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);

        try {
            buffer.put(vertices).flip();

            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private void uploadIndices(int[] indices) {
        IntBuffer buffer = MemoryUtil.memAllocInt(indices.length);

        try {
            buffer.put(indices).flip();

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public void render() {
        drawCalls++;triangles+=indexCount/3;
        glBindVertexArray(vao);
        if (surfaceBuffer == 0) org.lwjgl.opengl.GL20.glVertexAttrib3f(4, 0, .85f, -1);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);

        glBindVertexArray(0);
    }

    public long byteSize(){return bytes;}
    public int indexCount() { return indexCount; }
    public void renderIndirect(int command) {
        drawCalls++;triangles+=indexCount/3;
        glBindVertexArray(vao);
        if (surfaceBuffer == 0) org.lwjgl.opengl.GL20.glVertexAttrib3f(4, 0, .85f, -1);
        glBindBuffer(org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER, command);
        org.lwjgl.opengl.GL40.glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L);
        glBindBuffer(org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void renderInstanced(float[] positions) {
        if (positions.length == 0) return;
        drawCalls++;triangles+=(long)indexCount/3*(positions.length/3);
        residentBytes+=(long)positions.length*4-instanceBytes;instanceBytes=(long)positions.length*4;
        if (surfaceBuffer == 0) org.lwjgl.opengl.GL20.glVertexAttrib3f(4, 0, .85f, -1);
        glBindVertexArray(vao);
        if (instances == 0) {
            instances = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, instances);
            glVertexAttribPointer(3, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(3);
            org.lwjgl.opengl.GL33.glVertexAttribDivisor(3, 1);
        }
        if (instanceData == null || instanceData.capacity() < positions.length) {
            if (instanceData != null) MemoryUtil.memFree(instanceData);
            instanceData = MemoryUtil.memAllocFloat(Math.max(64, positions.length));
        }
        instanceData.clear().put(positions).flip();
        glBindBuffer(GL_ARRAY_BUFFER, instances);
        glBufferData(GL_ARRAY_BUFFER, instanceData, GL_STREAM_DRAW);
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(
                GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L, positions.length / 3);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        if(closed)return;closed=true;residentBytes-=bytes+instanceBytes;
        if (instances != 0) glDeleteBuffers(instances);
        if (instanceData != null) MemoryUtil.memFree(instanceData);
        if (surfaceBuffer != 0) glDeleteBuffers(surfaceBuffer);
        glDeleteBuffers(ebo);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
