package dev.jayms;

import dev.jayms.building.BuildingCompiler.Building;
import dev.jayms.net.Blocks;
import dev.jayms.net.Protocol;

/** Compiles the unsaved inspection plot directly into its resident chunk octrees. */
final class BuildingPreviewPlot {
    @FunctionalInterface
    interface Progress { void poll() throws Exception; }

    static void install(World world, Building building, int grade, Progress progress) throws Exception {
        int grid=building.unitsPerBlock();
        int width=(building.width()+grid-1)/grid;
        int height=(building.height()+grid-1)/grid;
        int depth=(building.depth()+grid-1)/grid;
        int maxY=world.trackPreviewMeshes(width,depth,grade+height);
        // Include the paved margin around the model. These columns serve as
        // the terrain base until the unsaved preview is closed.
        int minX=-1, minZ=-2;
        int sizeX=(width+7)/16-minX+1, sizeZ=(depth+4)/16-minZ+1;
        Chunk[][][] columns=new Chunk[sizeX][sizeZ][maxY-World.MIN_CHUNK_Y+1];
        for(int ix=0;ix<sizeX;ix++) for(int iz=0;iz<sizeZ;iz++){
            for(int y=World.MIN_CHUNK_Y;y<=maxY;y++){
                var position=new ChunkPos(ix+minX,y,iz+minZ);
                columns[ix][iz][y-World.MIN_CHUNK_Y]=ChunkGenerator.generate(position,world.terrain());
            }
            progress.poll();
        }
        for(int z=-19;z<=depth+4;z++) {
            int material=z < -6 ? Blocks.ASPHALT : z <= -4 ? Blocks.SNOW : Blocks.STONE;
            for(int x=-7;x<=width+7;x++) {
                var chunk=columns[Math.floorDiv(x,16)-minX][Math.floorDiv(z,16)-minZ][grade/16-World.MIN_CHUNK_Y];
                chunk.setBlock(Math.floorMod(x,16),grade%16,Math.floorMod(z,16),material);
            }
        }
        int written=0;
        for(var e:building.edits()) {
            int y=e.y()+grade+1;
            var chunk=columns[e.x()/16-minX][e.z()/16-minZ][y/16-World.MIN_CHUNK_Y];
            chunk.apply(new Protocol.Edit(e.x(),y,e.z(),e.type(),e.depth(),e.ix(),e.iy(),e.iz(),e.color()));
            if((++written & 4095)==0) progress.poll();
        }
        // Attach each finished octree once. Lighting and collision read the
        // same resident geometry; millions of transient placement edits never
        // enter incremental replay or per-cell sky journals.
        for(int ix=0;ix<sizeX;ix++) for(int iz=0;iz<sizeZ;iz++) {
            world.retainPreviewColumn(ix+minX,iz+minZ);
            for(int y=World.MIN_CHUNK_Y;y<=maxY;y++)
                world.addChunk(new ChunkPos(ix+minX,y,iz+minZ),columns[ix][iz][y-World.MIN_CHUNK_Y]);
            progress.poll();
        }
    }

    private BuildingPreviewPlot() {}
}
