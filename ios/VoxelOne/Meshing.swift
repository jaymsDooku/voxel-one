import Foundation

struct NativeMeshRequest:Sendable {var payload:NativeSavePayload;var atmosphere:Bool;var center:[Float];var distantRadius:Int=0}
struct NativeMeshKey:Hashable,Sendable {var color:Int;var sky:Int;var emitter:Bool}
struct NativeMeshBuffer:Sendable {
    var key:NativeMeshKey
    var positions:[Float]=[],normals:[Float]=[],colors:[Float]=[]
    var indices:[UInt32]=[]
}
enum NativeMeshEvent:Sendable {
    case index(Set<GridKey>)
    case page(GridKey,Bool,NativeMeshBuffer?)
    case distant(Bool,NativeMeshBuffer?)
    case finished
    case failed
}
struct NativeMeshResult:Sendable {var generation:UInt64;var event:NativeMeshEvent}
private struct NativeFace:Sendable {var delta:GridKey;var normal:[Float];var corners:[[Float]]}
private final class MeshAccumulator {var buffer:NativeMeshBuffer;init(_ key:NativeMeshKey){buffer=NativeMeshBuffer(key:key)}}

/// All queue state is protected by condition. Only the serial worker owns detached worlds.
final class NativeMeshWorker:@unchecked Sendable {
    static let maximumReady=4,maximumVertices=4096,maximumDrain=2
    private let condition=NSCondition()
    private let queue=DispatchQueue(label:"dev.jayms.voxelone.mesh",qos:.userInitiated)
    private var generation:UInt64=0
    private var running=false
    private var pending:(UInt64,NativeMeshRequest)?
    private var ready:[NativeMeshResult]=[]
    @discardableResult func submit(_ request:NativeMeshRequest)->UInt64 {
        condition.lock();generation &+= 1;let value=generation
        pending=(value,request);ready.removeAll();let start = !running;running=true
        condition.broadcast();condition.unlock()
        if start {queue.async{self.work()}}
        return value
    }
    func cancel(){condition.lock();generation &+= 1;pending=nil;ready.removeAll();condition.broadcast();condition.unlock()}
    func drain()->[NativeMeshResult] {
        condition.lock();defer{condition.broadcast();condition.unlock()}
        let n=min(Self.maximumDrain,ready.count),result=Array(ready.prefix(n));ready.removeFirst(n);return result
    }
    var readyCount:Int {condition.lock();defer{condition.unlock()};return ready.count}
    var isIdle:Bool {condition.lock();defer{condition.unlock()};return !running && ready.isEmpty}
    private func current(_ token:UInt64)->Bool {condition.lock();defer{condition.unlock()};return token==generation}
    private func emit(_ event:NativeMeshEvent,_ token:UInt64)->Bool {
        condition.lock();defer{condition.unlock()}
        while token==generation && ready.count>=Self.maximumReady {condition.wait()}
        guard token==generation else{return false}
        ready.append(NativeMeshResult(generation:token,event:event));return true
    }
    private func work(){
        while true {
            condition.lock()
            guard let job=pending else{running=false;condition.unlock();return}
            pending=nil;condition.unlock()
            do {
                // The mutable class is created here and never escapes this worker.
                guard job.1.center.count==3,job.1.center.allSatisfy({$0.isFinite}) else{throw GameError.invalidWorld}
                let world=try VoxelWorld(job.1.payload.value())
                guard current(job.0) else{continue}
                var index:[GridKey:[GridKey]]=[:]
                for key in world.types.keys {
                    let chunk=GridKey(Int(floor(Double(key.x)/8)),Int(floor(Double(key.y)/8)),Int(floor(Double(key.z)/8)))
                    index[chunk,default:[]].append(key)
                }
                guard emit(.index(Set(index.keys)),job.0) else{continue}
                let p=job.1.center
                func distance(_ k:GridKey)->Float {let x=Float(k.x*8+4)-p[0],y=Float(k.y*8+4)-p[1],z=Float(k.z*8+4)-p[2];return x*x+y*y+z*z}
                let chunks=index.keys.sorted{distance($0)<distance($1)}
                var complete=true
                for chunk in chunks {
                    if !current(job.0) || !build(chunk,index[chunk]!,world,job.1.atmosphere,job.0) {complete=false;break}
                }
                if complete,job.1.distantRadius>0,world.streamsTerrain {
                    var first=true
                    complete=try NativeDistantMesher.build(world.snapshot,radius:job.1.distantRadius,cancelled:{!self.current(job.0)}){buffer in
                        let accepted=self.emit(.distant(first,buffer),job.0);first=false;return accepted
                    }
                    if complete && first {_ = emit(.distant(true,nil),job.0)}
                }
                if complete {_ = emit(.finished,job.0)}
            }catch{_ = emit(.failed,job.0)}
        }
    }
    private static let faces:[NativeFace]=[
        NativeFace(delta:GridKey(1,0,0),normal:[1,0,0],corners:[[1,0,0],[1,1,0],[1,1,1],[1,0,1]]),
        NativeFace(delta:GridKey(-1,0,0),normal:[-1,0,0],corners:[[0,0,1],[0,1,1],[0,1,0],[0,0,0]]),
        NativeFace(delta:GridKey(0,1,0),normal:[0,1,0],corners:[[0,1,1],[1,1,1],[1,1,0],[0,1,0]]),
        NativeFace(delta:GridKey(0,-1,0),normal:[0,-1,0],corners:[[0,0,0],[1,0,0],[1,0,1],[0,0,1]]),
        NativeFace(delta:GridKey(0,0,1),normal:[0,0,1],corners:[[1,0,1],[1,1,1],[0,1,1],[0,0,1]]),
        NativeFace(delta:GridKey(0,0,-1),normal:[0,0,-1],corners:[[0,0,0],[0,1,0],[1,1,0],[1,0,0]])]
    private func build(_ chunk:GridKey,_ cells:[GridKey],_ world:VoxelWorld,_ outdoor:Bool,_ token:UInt64)->Bool {
        var groups:[NativeMeshKey:MeshAccumulator]=[:],visibility:[GridKey:Double]=[:],first=true
        func flush(_ a:MeshAccumulator)->Bool {
            guard !a.buffer.indices.isEmpty else{return true}
            guard emit(.page(chunk,first,a.buffer),token) else{return false}
            first=false;a.buffer=NativeMeshBuffer(key:a.buffer.key);return true
        }
        for cell in cells {
            guard current(token) else{return false}
            for box in world.boxes(cell) {
                for face in Self.faces {
                    let n=face.delta
                    let boundary=(n.x<0 && box.x==0) || (n.x>0 && box.x+box.size==1) || (n.y<0 && box.y==0) || (n.y>0 && box.y+box.size==1) || (n.z<0 && box.z==0) || (n.z>0 && box.z+box.size==1)
                    if boundary && (world.fullOpaque(cell+n) || (box.type==185 && world.type(cell+n)==185)) {continue}
                    let air=cell+n,visible:Double
                    if outdoor {
                        if let cached=visibility[air] {visible=cached}
                        else {visible=world.skyVisibility(air);visibility[air]=visible}
                    }else{visible=0}
                    let key=NativeMeshKey(color:(outdoor || box.type==180) ? box.color:0,sky:Int((visible*8).rounded()),emitter:box.type==180)
                    let a=groups[key] ?? MeshAccumulator(key);groups[key]=a
                    if a.buffer.positions.count/3+4>Self.maximumVertices && !flush(a) {return false}
                    let base=UInt32(a.buffer.positions.count/3)
                    for p in face.corners {
                        a.buffer.positions += [Float(cell.x)+box.x+p[0]*box.size,Float(cell.y)+box.y+p[1]*box.size,Float(cell.z)+box.z+p[2]*box.size]
                        a.buffer.normals += face.normal
                        a.buffer.colors += [Float((box.color>>16)&255)/255,Float((box.color>>8)&255)/255,Float(box.color&255)/255,1]
                    }
                    a.buffer.indices += [base,base+1,base+2,base,base+2,base+3]
                }
            }
        }
        for a in groups.values {if !flush(a) {return false}}
        return first ? emit(.page(chunk,true,nil),token):true
    }
}
