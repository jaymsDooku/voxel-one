package dev.jayms;

import java.util.Arrays;

public final class MeshDataGenerator {

    private static final int[] FACE_INDICES = {
        0, 1, 2,
        2, 3, 0
    };

    private static final class Scratch {final FloatBuilder vertices=new FloatBuilder();final IntBuilder indices=new IntBuilder();final int[] mask=new int[256];}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    public static MeshData generate(Chunk chunk) {
        Scratch scratch=SCRATCH.get();FloatBuilder vertices=scratch.vertices;IntBuilder indices=scratch.indices;
        vertices.reset();indices.reset();

        int vertexCount = 0;
        // Fractional geometry keeps its exact sparse surface. Full cells merge by raw value,
        // so RGB LEDs, material boundaries and chunk-neighbour visibility stay intact.
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            if (chunk.getBlock(x,y,z) == dev.jayms.net.Blocks.PARTIAL)
                vertexCount = addPartial(vertices, indices, chunk, x,y,z,vertexCount);
        for (Face face : Face.values()) {
            int axis = face.dx()!=0 ? 0 : face.dy()!=0 ? 1 : 2;
            int u = (axis+1)%3, w = (axis+2)%3;
            int[] mask = scratch.mask;
            for (int slice=0; slice<16; slice++) {
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                Arrays.fill(mask,0);
                for (int j=0;j<16;j++) for (int i=0;i<16;i++) {
                    int px=axis==0?slice:u==0?i:j,py=axis==1?slice:u==1?i:j,pz=axis==2?slice:u==2?i:j;
                    int type=chunk.getBlock(px,py,pz);
                    if (type==0 || type==dev.jayms.net.Blocks.PARTIAL || dev.jayms.net.Blocks.isModel(type)) continue;
                    int n=chunk.neighbor(px+face.dx(),py+face.dy(),pz+face.dz());
                    // Water needs the opaque floor and walls behind its refracted surface.
                    boolean submerged = n==dev.jayms.net.Blocks.WATER && type!=dev.jayms.net.Blocks.WATER;
                    if (n!=0 && n!=dev.jayms.net.Blocks.PARTIAL && !dev.jayms.net.Blocks.isModel(n) && !submerged) continue;
                    mask[i+j*16]=chunk.value(px*16,py*16,pz*16);
                }
                for (int j=0;j<16;j++) for (int i=0;i<16;) {
                    int raw=mask[i+j*16]; if(raw==0) { i++; continue; }
                    int width=1, height=1;
                    while(i+width<16 && mask[i+width+j*16]==raw)width++;
                    rows: while(j+height<16) {
                        for(int k=0;k<width;k++)if(mask[i+k+(j+height)*16]!=raw)break rows;
                        height++;
                    }
                    float[] origin=new float[3], scale={1,1,1};
                    origin[axis]=slice; origin[u]=i; origin[w]=j; scale[u]=width; scale[w]=height;
                    int type=dev.jayms.net.WorldVoxels.decode(raw);
                    float[] color=dev.jayms.net.Blocks.color(type==1 && face.dy()<1?2:type);
                    for(int vtx=0;vtx<4;vtx++) {
                        for(int d=0;d<3;d++)vertices.add(origin[d]+face.vertices()[vtx*3+d]*scale[d]);
                        vertices.add((float)face.dx());vertices.add((float)face.dy());vertices.add((float)face.dz());
                        for(float component:color)vertices.add(component);
                    }
                    for(int idx:FACE_INDICES)indices.add(vertexCount+idx);
                    vertexCount+=4;
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++)mask[i+x+(j+y)*16]=0;
                    i+=width;
                }
            }
        }

        float[] v = toFloatArray(vertices);
        float[] surface = new float[v.length / 9 * 3];
        for (int base = 0; base < v.length; base += 36) {
            float cx = 0, cy = 0, cz = 0;
            for (int j = 0; j < 4; j++) {
                cx += v[base + j * 9];
                cy += v[base + j * 9 + 1];
                cz += v[base + j * 9 + 2];
            }
            int fx = (int) Math.floor((cx / 4 - v[base + 3] * .001f) * 16),
                    fy = (int) Math.floor((cy / 4 - v[base + 4] * .001f) * 16),
                    fz = (int) Math.floor((cz / 4 - v[base + 5] * .001f) * 16);
            int raw = chunk.value(fx, fy, fz), type = dev.jayms.net.WorldVoxels.decode(raw);
            for (int j = 0; j < 4; j++) {
                int i = base + j * 9;
                if (type == dev.jayms.net.Blocks.LED) {
                    int c = dev.jayms.net.WorldVoxels.lightColor(raw);
                    v[i + 6] = (c >> 16 & 255) / 255f;
                    v[i + 7] = (c >> 8 & 255) / 255f;
                    v[i + 8] = (c & 255) / 255f;
                }
                surface[i / 3] = type == dev.jayms.net.Blocks.LED ? 4 : 0;
                surface[i / 3 + 1] =
                        type == dev.jayms.net.Blocks.GLASS
                                ? .12f
                                : type == dev.jayms.net.Blocks.LED ? .25f : .85f;
                surface[i / 3 + 2] =
                        type == dev.jayms.net.Blocks.WATER ? -2 : dev.jayms.render.MaterialTextures.layer(
                                type == 1 && v[i + 4] < 1 ? 2 : type);
            }
        }
        return new MeshData(v, toIntArray(indices), surface);
    }

    private static void addFace(
            FloatBuilder vertices,
            IntBuilder indices,
            Face face,
            float blockX,
            float blockY,
            float blockZ,
            int vertexOffset,
            int type) {
        addFace(vertices, indices, face, blockX, blockY, blockZ, vertexOffset, type, 1);
    }

    private static void addFace(
            FloatBuilder vertices,
            IntBuilder indices,
            Face face,
            float blockX,
            float blockY,
            float blockZ,
            int vertexOffset,
            int type,
            float scale) {
        float[] faceVertices = face.vertices();

        for (int i = 0; i < 4; i++) {
            int positionOffset = i * 3;

            vertices.add(blockX + faceVertices[positionOffset] * scale);

            vertices.add(blockY + faceVertices[positionOffset + 1] * scale);

            vertices.add(blockZ + faceVertices[positionOffset + 2] * scale);

            vertices.add((float) face.dx());
            vertices.add((float) face.dy());
            vertices.add((float) face.dz());
            float[] color = dev.jayms.net.Blocks.color(type);
            if (type == 1 && face.dy() < 1) color = dev.jayms.net.Blocks.color(2);
            for (float component : color) vertices.add(component);
        }

        for (int index : FACE_INDICES) {
            indices.add(vertexOffset + index);
        }
    }

    private static int addPartial(
            FloatBuilder vertices,
            IntBuilder indices,
            Chunk chunk,
            int bx,
            int by,
            int bz,
            int count) {
        try {
            var geometry =
                    dev.jayms.net.model.ModelMesher.mesh(
                            16,
                            (x, y, z) ->
                                    dev.jayms.net.WorldVoxels.surfaceColor(
                                            chunk.value(bx * 16 + x, by * 16 + y, bz * 16 + z)),
                            24576);
            float[] v = geometry.vertices();
            for (int i = 0; i < v.length; i += 9) {
                vertices.add(v[i] + bx);
                vertices.add(v[i + 1] + by);
                vertices.add(v[i + 2] + bz);
                for (int j = 3; j < 9; j++) vertices.add(v[i + j]);
            }
            for (int index : geometry.indices()) indices.add(count + index);
            count += v.length / 9;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Fractional surface exceeds mesh budget", e);
        }
        return count;
    }

    private static float[] toFloatArray(FloatBuilder values) {return values.array();}
    private static int[] toIntArray(IntBuilder values) {return values.array();}
    private static final class FloatBuilder {
        private float[] data=new float[1024];private int count;
        void reset(){count=0;if(data.length>262144)data=new float[1024];}
        void add(float value){if(count==6_000_000)throw new IllegalStateException("Chunk mesh exceeds bounded vertex budget");if(count==data.length)data=Arrays.copyOf(data,Math.min(6_000_000,data.length*2));data[count++]=value;}
        float[] array(){return Arrays.copyOf(data,count);}
    }
    private static final class IntBuilder {
        private int[] data=new int[1024];private int count;
        void reset(){count=0;if(data.length>262144)data=new int[1024];}
        void add(int value){if(count==2_000_000)throw new IllegalStateException("Chunk mesh exceeds bounded index budget");if(count==data.length)data=Arrays.copyOf(data,Math.min(2_000_000,data.length*2));data[count++]=value;}
        int[] array(){return Arrays.copyOf(data,count);}
    }
}
