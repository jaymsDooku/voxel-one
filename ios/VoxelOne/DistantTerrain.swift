import Foundation

/// Surface-only distant landscape, following desktop DistantTerrainMesher's
/// coarse-column approach. Exact model/leaf meshes, collision and picking remain
/// in the resident window. This never fabricates unseen authoritative online edits.
enum NativeDistantMesher {
    static let maximumRadius=96,step=4,maximumColumns=4096,maximumQuads=16384
    private struct Column:Hashable {var x:Int;var z:Int}
    private struct Surface {var height:Float;var color:Int}
    static func build(_ snapshot:Snapshot,radius:Int,cancelled:()->Bool,
                      emit:(NativeMeshBuffer)->Bool)throws->Bool {
        guard snapshot.game=="sandbox",snapshot.schema==2,let version=snapshot.generatorVersion,
              let seed=Int64(snapshot.seed),let edits=snapshot.terrainEdits,
              (32...maximumRadius).contains(radius),radius%step==0,snapshot.bounds.count==6 else {throw GameError.invalidWorld}
        try snapshot.validate()
        let generator=try NativeTerrain(seed:seed,version:version),b=snapshot.bounds
        guard b[0]>=(-1_000_000),b[1]<=1_000_000,b[4]>=(-1_000_000),b[5]<=1_000_000,
              b[0]<=b[1],b[4]<=b[5],b[1]-b[0]<=64,b[5]-b[4]<=64 else {throw GameError.invalidWorld}
        let cx=(b[0]+b[1])/2,cz=(b[4]+b[5])/2
        let materials=Dictionary(uniqueKeysWithValues:(snapshot.contract?.materials ?? []).map{($0.id,$0)})
        var columns:[Column:[Int:NativeTerrainEdit]]=[:]
        for edit in edits {columns[Column(x:edit.cell.x,z:edit.cell.z),default:[:]][edit.cell.y]=edit}
        var cache:[Column:Surface]=[:]
        func surface(_ x:Int,_ z:Int)throws->Surface {
            let key=Column(x:x,z:z)
            if let saved=cache[key] {return saved}
            guard cache.count<maximumColumns else {throw GameError.invalidWorld}
            let journal=columns[key] ?? [:]
            let baseHeight=generator.surfaceHeight(x,z)
            var y=max(baseHeight,journal.keys.max() ?? -32)
            y=min(95,y)
            while y>=(-32) {
                if cancelled(){return Surface(height:-32,color:0)}
                let edit=journal[y]
                // Coarse columns omit generated trees consistently, including
                // when a high journal model makes the downward scan start higher.
                if edit==nil && y>baseHeight {y-=1;continue}
                let type=edit?.type ?? generator.block(x,y,z)
                // Tiny models are omitted at distance, never replaced by cubes.
                if type==0 || (8...136).contains(type) {y-=1;continue}
                let result:Surface
                if type==255,let geometry=edit?.geometry,
                   let top=geometry.leaves.max(by:{$0[1]+$0[3]<$1[1]+$1[3]}) {
                    result=Surface(height:Float(y)+Float(top[1]+top[3])/Float(geometry.resolution),color:top[5])
                } else if let material=materials[type],material.depth>0 {
                    result=Surface(height:Float(y)+1/Float(1<<material.depth),color:material.color)
                } else {result=Surface(height:Float(y+1),color:edit?.color ?? materials[type]?.color ?? VoxelWorld.color(type))}
                cache[key]=result;return result
            }
            let empty=Surface(height:-32,color:0x7a4d2b);cache[key]=empty;return empty
        }
        func coordinates(_ center:Int,_ low:Int,_ high:Int)->[Int] {
            let start=max(-1_000_000,center-radius),end=min(1_000_001,center+radius)
            var points=Set(stride(from:start,through:end,by:step));points.insert(end)
            // Split exactly at resident boundaries: no coarse quad covers a close cell.
            if low>start && low<end {points.insert(low)}
            if high>start && high<end {points.insert(high)}
            return points.sorted()
        }
        let xs=coordinates(cx,b[0],b[1]+1),zs=coordinates(cz,b[4],b[5]+1)
        var buffer=NativeMeshBuffer(key:NativeMeshKey(color:0,sky:0,emitter:false)),quads=0
        func quad(_ points:[[Float]],_ normal:[Float],_ color:Int)throws->Bool {
            guard quads<maximumQuads else {throw GameError.invalidWorld};quads+=1
            if buffer.positions.count/3+4>NativeMeshWorker.maximumVertices {
                guard emit(buffer) else{return false};buffer=NativeMeshBuffer(key:buffer.key)
            }
            let base=UInt32(buffer.positions.count/3)
            for point in points {
                buffer.positions+=point;buffer.normals+=normal
                buffer.colors += [Float((color>>16)&255)/255,Float((color>>8)&255)/255,Float(color&255)/255,1]
            }
            buffer.indices += [base,base+1,base+2,base,base+2,base+3];return true
        }
        for i in 0..<(xs.count-1) {for j in 0..<(zs.count-1) {
            if cancelled(){return false}
            let x0=xs[i],x1=xs[i+1],z0=zs[j],z1=zs[j+1]
            let x=x0+(x1-x0-1)/2,z=z0+(z1-z0-1)/2,s=try surface(x,z),h=s.height
            let inside=x0>=b[0] && x1<=b[1]+1 && z0>=b[4] && z1<=b[5]+1
            // High flight can evict the ground vertically. Keep its distant
            // surface below the loaded band; never put coarse faces inside it.
            if inside && h>Float(b[2]) {continue}
            let a=Float(x0),c=Float(x1),d=Float(z0),e=Float(z1)
            guard try quad([[a,h,e],[c,h,e],[c,h,d],[a,h,d]],[0,1,0],s.color) else{return false}
            // Downward walls hide coarse LOD cracks; the near exclusion itself is
            // exact. Border walls also cover gaps against the resident voxel side.
            for side in 0..<4 {
                let nx:Int,nz:Int
                switch side {
                case 0:nx=i>0 ? xs[i-1]+(x0-xs[i-1]-1)/2:x0-2;nz=z
                case 1:nx=i+2<xs.count ? x1+(xs[i+2]-x1-1)/2:x1+1;nz=z
                case 2:nx=x;nz=j>0 ? zs[j-1]+(z0-zs[j-1]-1)/2:z0-2
                default:nx=x;nz=j+2<zs.count ? z1+(zs[j+2]-z1-1)/2:z1+1
                }
                let neighbor=try surface(nx,nz)
                let atNear=nx>=b[0] && nx<=b[1] && nz>=b[4] && nz<=b[5]
                let low=atNear ? min(h,neighbor.height)-4:neighbor.height
                if low>=h {continue}
                let points:[[Float]],normal:[Float]
                switch side {
                case 0:points=[[a,low,e],[a,h,e],[a,h,d],[a,low,d]];normal=[-1,0,0]
                case 1:points=[[c,low,d],[c,h,d],[c,h,e],[c,low,e]];normal=[1,0,0]
                case 2:points=[[a,low,d],[a,h,d],[c,h,d],[c,low,d]];normal=[0,0,-1]
                default:points=[[c,low,e],[c,h,e],[a,h,e],[a,low,e]];normal=[0,0,1]
                }
                guard try quad(points,normal,s.color) else{return false}
            }
        }}
        if cancelled(){return false}
        return buffer.indices.isEmpty || emit(buffer)
    }
}
