import Foundation

struct NativeTerrainTarget:Equatable,Sendable {
    var x:Int;var y:Int;var z:Int
    /// Eight-column steps leave twelve columns of overlap before generation begins.
    static func needed(bounds:[Int],x:Float,y:Float,z:Float)->NativeTerrainTarget? {
        guard bounds.count==6,[x,y,z].allSatisfy({$0.isFinite && abs($0)<=1_000_000}) else{return nil}
        let cx=(bounds[0]+bounds[1])/2,cz=(bounds[4]+bounds[5])/2
        let low=max(-32,min(87,Int(floor(y))-8)),high=min(95,Int(floor(y))+36)
        let horizontal=abs(x-Float(cx))>=12 || abs(z-Float(cz))>=12
        let vertical=(y<Float(bounds[2])+4 || y>Float(bounds[3])-8) && (low != bounds[2] || high != bounds[3])
        guard horizontal || vertical else{return nil}
        let target=NativeTerrainTarget(x:max(-999976,min(999976,Int(floor(x/8))*8)),y:max(-32,min(95,Int(floor(y)))),z:max(-999976,min(999976,Int(floor(z/8))*8)))
        // At the finite safety domain, keep collision boundaries instead of retrying forever.
        guard target.x != cx || target.z != cz || low != bounds[2] || high != bounds[3] else{return nil}
        return target
    }
}

/// Fully prepared values cross the worker boundary; SceneKit nodes never do.
struct NativeTerrainRegion:Sendable {
    var snapshot:Snapshot;var types:[GridKey:Int];var colors:[GridKey:Int];var shapes:[GridKey:[VoxelBox]]
    static func inside(_ key:GridKey,_ b:[Int])->Bool {
        key.x>=b[0] && key.x<=b[1] && key.y>=b[2] && key.y<=b[3] && key.z>=b[4] && key.z<=b[5]
    }
    /// One-time migration preserves air holes, color overrides and exact partial cells.
    /// Unversioned legacy patches remain finite; their generator cannot be inferred safely.
    static func migrateJournal(_ value:Snapshot)throws->Snapshot {
        try value.validate()
        guard value.game=="sandbox",let version=value.generatorVersion,value.terrainEdits==nil else{return value}
        guard let seed=Int64(value.seed) else{throw GameError.invalidWorld}
        let generator=try NativeTerrain(seed:seed,version:version),b=value.bounds
        let cells=Dictionary(uniqueKeysWithValues:value.cells.map{(GridKey($0[0],$0[1],$0[2]),$0)})
        let details=Dictionary(uniqueKeysWithValues:(value.details ?? []).map{(GridKey($0.cell[0],$0.cell[1],$0.cell[2]),$0.geometry)})
        let colors=Dictionary(uniqueKeysWithValues:(value.contract?.materials ?? []).map{($0.id,$0.color)})
        var edits:[NativeTerrainEdit]=[]
        for x in b[0]...b[1] {for z in b[4]...b[5] {for y in b[2]...b[3] {
            let key=GridKey(x,y,z),saved=cells[key],type=saved?[3] ?? 0,color=saved?[4] ?? 0,base=generator.block(x,y,z)
            if type != base || (type != 0 && color != colors[base]) {
                edits.append(NativeTerrainEdit(cell:key,type:type,color:color,geometry:details[key]))
                guard edits.count<=NativeTerrainEdit.maximumCells else {
                    // Preserve the original finite world if it cannot migrate within the cap.
                    // Do not discard edits or make an old save unloadable.
                    var finite=value;finite.generatorVersion=nil;return finite
                }
            }
        }}}
        var result=value;result.terrainEdits=edits;try result.validate();return result
    }
    static func build(_ payload:NativeSavePayload,target:NativeTerrainTarget)throws->NativeTerrainRegion {
        var next=try migrateJournal(payload.value())
        guard let version=next.generatorVersion,let seed=Int64(next.seed),let edits=next.terrainEdits else {throw GameError.invalidWorld}
        let generator=try NativeTerrain(seed:seed,version:version)
        let colors=Dictionary(uniqueKeysWithValues:(next.contract?.materials ?? []).map{($0.id,$0.color)})
        let generated=try generator.window(cx:target.x,cz:target.z,eyeY:target.y,radius:24,colors:colors)
        var cells=Dictionary(uniqueKeysWithValues:generated.cells.map{(GridKey($0[0],$0[1],$0[2]),$0)})
        var details:[NativeDetail]=[]
        for edit in edits where inside(edit.cell,generated.bounds) {
            if edit.type==0 {cells.removeValue(forKey:edit.cell)}
            else {cells[edit.cell]=[edit.cell.x,edit.cell.y,edit.cell.z,edit.type,edit.color]}
            if let geometry=edit.geometry {details.append(NativeDetail(cell:[edit.cell.x,edit.cell.y,edit.cell.z],geometry:geometry))}
        }
        next.cells=Array(cells.values);next.bounds=generated.bounds;next.details=details
        return try VoxelWorld.prepareTerrain(next)
    }
}

enum NativeTerrainStreamResult:Sendable {case prepared(NativeTerrainRegion),failed,cancelled}
/// One request executes at a time. The next frame chooses the newest position;
/// there is no queued chain of obsolete regions after movement or cancellation.
@MainActor final class NativeTerrainStreamer {
    private let queue=DispatchQueue(label:"dev.jayms.voxelone.terrain-stream",qos:.utility)
    private var generation:UInt64=0
    private(set) var isBusy=false
    func cancel(){generation &+= 1}
    @discardableResult func request(payload:NativeSavePayload,target:NativeTerrainTarget,
        operation:@escaping @Sendable (NativeSavePayload,NativeTerrainTarget)throws->NativeTerrainRegion={try NativeTerrainRegion.build($0,target:$1)},
        completion:@escaping (NativeTerrainStreamResult)->Void)->Bool {
        guard !isBusy else{return false};isBusy=true;let request=generation
        queue.async {
            let result:NativeTerrainStreamResult
            do {result = .prepared(try operation(payload,target))}catch{result = .failed}
            DispatchQueue.main.async {
                self.isBusy=false
                completion(request==self.generation ? result:.cancelled)
            }
        }
        return true
    }
}
