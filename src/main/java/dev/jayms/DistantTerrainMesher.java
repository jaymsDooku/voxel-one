package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.StressGrid;
import dev.jayms.render.MaterialTextures;

import java.util.*;

/**
 * Surface-only voxel terrain. Finest tiles include trees and known edits; far tiles use broad
 * cells.
 */
public final class DistantTerrainMesher {
    private record Position(int x, int y, int z) {}

    private record Column(int x, int z) {}

    private final Terrain terrain;
    // All ordinary vertex colors are nonnegative. Mark only native grid tops
    // for world-space material evaluation, without adding a per-vertex buffer.
    static final int GRID_TOP = -1;
    private static final int MIXED = -2;
    private static final float[] GRID_COLOR = {-1,0,0};
    private static final float PAVEMENT_BRIGHTNESS=MaterialTextures.meanBrightness(MaterialTextures.layer(Blocks.ASPHALT));
    private final List<Map<Column,Integer>> gridEdits=new ArrayList<>();
    private final Map<Position, Integer> edits = new HashMap<>();
    private final Map<Column, List<Protocol.Edit>> columns = new HashMap<>();

    public DistantTerrainMesher(Terrain terrain, Collection<Protocol.Edit> snapshot) {
        this.terrain = terrain;
        for (var edit : snapshot) {
            if (edit.depth() != 0) continue;
            edits.put(new Position(edit.x(), edit.y(), edit.z()), edit.type());
            columns.computeIfAbsent(new Column(edit.x(), edit.z()), k -> new ArrayList<>())
                    .add(edit);
        }
        if (terrain.stressGrid()!=null) indexGridEdits(terrain.stressGrid());
    }

    // Collapse uniform areas of authored paving. A coarse tile must neither
    // stretch one edited block over a whole cell nor paint grid roads onto it.
    private void indexGridEdits(StressGrid grid) {
        var leaves=new HashMap<Column,Integer>();
        edits.forEach((p,type)->{
            if(p.y==grid.grade() && grid.contains(p.x,p.z))
                leaves.put(new Column(p.x,p.z),type);
        });
        gridEdits.add(leaves);
        for(int size=2;size<=DistantTerrainPlan.ROOT_SIZE/16;size*=2){
            var children=gridEdits.get(gridEdits.size()-1);
            var parents=new HashMap<Column,Integer>();
            for(var child:children.keySet()){
                var parent=new Column(Math.floorDiv(child.x,2),Math.floorDiv(child.z,2));
                if(parents.containsKey(parent)) continue;
                int x=parent.x*2,z=parent.z*2;
                int type=children.getOrDefault(new Column(x,z),GRID_TOP);
                if(type!=children.getOrDefault(new Column(x+1,z),GRID_TOP)
                        || type!=children.getOrDefault(new Column(x,z+1),GRID_TOP)
                        || type!=children.getOrDefault(new Column(x+1,z+1),GRID_TOP)) type=MIXED;
                parents.put(parent,type);
            }
            gridEdits.add(parents);
        }
    }

    private int block(int x, int y, int z) {
        Integer edited = edits.get(new Position(x, y, z));
        return edited == null ? terrain.block(x, y, z) : edited;
    }

    private int ground(int x, int z, boolean fine) {
        int h = terrain.surfaceHeight(x, z);
        if (fine && columns.containsKey(new Column(x, z))) {
            while (h >= Terrain.MIN_Y && (block(x, h, z) == 0 || Blocks.isModel(block(x, h, z))))
                h--;
        }
        return h + 1;
    }

    private record Patch(int x,int z,int size,int top,int type,Patch[] children) {
        Patch at(int bx,int bz) {
            if(children==null) return this;
            int child=(bx-x>=size/2 ? 2 : 0)+(bz-z>=size/2 ? 1 : 0);
            return children[child].at(bx,bz);
        }
    }

    private Patch patch(int x,int z,int size,boolean fine) {
        StressGrid grid=terrain.stressGrid();
        if(grid!=null && x+size>StressGrid.MIN_X && z+size>StressGrid.MIN_Z
                && x<StressGrid.MIN_X+StressGrid.EXTENT && z<StressGrid.MIN_Z+StressGrid.EXTENT){
            boolean inside=grid.contains(x,z) && grid.contains(x+size-1,z+size-1);
            int material=inside ? gridEdits.get(Integer.numberOfTrailingZeros(size))
                    .getOrDefault(new Column(Math.floorDiv(x,size),Math.floorDiv(z,size)),GRID_TOP) : MIXED;
            if(material==MIXED || size>1 && (material==Blocks.AIR || Blocks.isModel(material))){
                int half=size/2;
                Patch[] children={patch(x,z,half,fine),patch(x,z+half,half,fine),
                        patch(x+half,z,half,fine),patch(x+half,z+half,half,fine)};
                return new Patch(x,z,size,0,0,children);
            }
            if(material==Blocks.AIR || Blocks.isModel(material)){
                int h=ground(x,z,true);
                return new Patch(x,z,size,h,block(x,h-1,z),null);
            }
            return new Patch(x,z,size,grid.grade()+1,material,null);
        }
        int cx=x+size/2,cz=z+size/2;
        int h=ground(cx,cz,fine);
        return new Patch(x,z,size,h,fine?block(cx,h-1,cz):terrain.block(cx,h-1,cz),null);
    }

    public MeshData build(DistantTerrainPlan.Tile tile) {
        Builder mesh=new Builder();
        int step=tile.step();
        Patch[][] cells=new Patch[16][16];
        for(int a=0;a<16;a++) for(int b=0;b<16;b++)
            cells[a][b]=patch(tile.x()+a*step,tile.z()+b*step,step,step==1);
        for(Patch[] row:cells) for(Patch p:row) emit(mesh,tile,cells,p);
        return mesh.finish();
    }

    private void emit(Builder mesh,DistantTerrainPlan.Tile tile,Patch[][] cells,Patch p) {
        if(p.children!=null){
            for(Patch child:p.children) emit(mesh,tile,cells,child);
            return;
        }
        if(p.top>Terrain.MIN_Y) mesh.face(Face.TOP,p.x-tile.x(),p.top,p.z-tile.z(),p.size,0,p.size,p.type);
        edge(mesh,tile,cells,p,Face.LEFT);
        edge(mesh,tile,cells,p,Face.RIGHT);
        edge(mesh,tile,cells,p,Face.BACK);
        edge(mesh,tile,cells,p,Face.FRONT);
        if(tile.step()==1){
            var c=terrain.column(p.x,p.z);
            var columnEdits=columns.get(new Column(p.x,p.z));
            if(c.biome()==Terrain.Biome.FOREST || columnEdits!=null){
                for(int y=Math.max(p.top,c.height()+1);y<=c.height()+9;y++) addVoxel(mesh,tile,p.x,y,p.z);
                if(columnEdits!=null) for(var edit:columnEdits)
                    if(edit.y()>c.height()+9) addVoxel(mesh,tile,p.x,edit.y(),p.z);
            }
        }
    }

    // Emit the exposed part of a riser even if the adjacent patch subdivided
    // at an excavation or at the finite grid edge. Only tile borders need skirts.
    private void edge(Builder mesh,DistantTerrainPlan.Tile tile,Patch[][] cells,Patch p,Face face) {
        boolean alongZ=face.dx()!=0;
        int start=alongZ?p.z:p.x,end=start+p.size;
        int fixed=alongZ?p.x:p.z;
        boolean positive=face.dx()+face.dz()>0;
        if(positive) fixed+=p.size;
        for(int pos=start;pos<end;){
            int adjacent=fixed+(positive?0:-1);
            int x=alongZ?adjacent:pos, z=alongZ?pos:adjacent;
            int bottom=Terrain.MIN_Y,next=end;
            if(x>=tile.x() && x<tile.x()+tile.size() && z>=tile.z() && z<tile.z()+tile.size()){
                Patch n=cells[(x-tile.x())/tile.step()][(z-tile.z())/tile.step()].at(x,z);
                bottom=n.top;
                next=Math.min(end,(alongZ?n.z:n.x)+n.size);
            }
            if(p.top>bottom){
                int type=p.type==GRID_TOP || p.type==Blocks.GRASS?Blocks.DIRT:p.type;
                mesh.face(face,(alongZ?fixed:pos)-tile.x(),bottom,(alongZ?pos:fixed)-tile.z(),
                        alongZ?0:next-pos,p.top-bottom,alongZ?next-pos:0,type);
            }
            pos=next;
        }
    }

    private void addVoxel(Builder mesh, DistantTerrainPlan.Tile tile, int x, int y, int z) {
        int type = block(x, y, z);
        if (type == 0 || Blocks.isModel(type)) return;
        for (Face face : Face.values()) {
            int neighbor = block(x + face.dx(), y + face.dy(), z + face.dz());
            if (neighbor == 0 || Blocks.isModel(neighbor))
                mesh.face(face, x - tile.x(), y, z - tile.z(), 1, 1, 1, type);
        }
    }

    private static final class Builder {
        private float[] vertices = new float[16384];
        private int[] indices = new int[4096];
        private int v, i;

        void face(Face face, float x, float y, float z, float w, float h, float d, int type) {
            if (v + 36 > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
            if (i + 6 > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
            int first = v / 9;
            float[] color = type==GRID_TOP?GRID_COLOR:Blocks.color(type), corners = face.vertices();
            if(type==Blocks.ASPHALT || type==Blocks.ROAD_LINE_X || type==Blocks.ROAD_LINE_Z)
                for(int i=0;i<color.length;i++) color[i]*=PAVEMENT_BRIGHTNESS;
            for (int a = 0; a < 4; a++) {
                vertices[v++] = x + corners[a * 3] * w;
                vertices[v++] = y + corners[a * 3 + 1] * h;
                vertices[v++] = z + corners[a * 3 + 2] * d;
                vertices[v++] = face.dx();
                vertices[v++] = face.dy();
                vertices[v++] = face.dz();
                for (float channel : color) vertices[v++] = channel;
            }
            for (int index : new int[] {0, 1, 2, 2, 3, 0}) indices[i++] = first + index;
        }

        MeshData finish() {
            return new MeshData(Arrays.copyOf(vertices, v), Arrays.copyOf(indices, i));
        }
    }
}
