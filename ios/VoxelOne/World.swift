import Foundation
import SceneKit
import UIKit

struct GridKey: Hashable, Codable {
    var x: Int; var y: Int; var z: Int
    init(_ x: Int, _ y: Int, _ z: Int) { self.x=x; self.y=y; self.z=z }
    static func + (a: GridKey, b: GridKey) -> GridKey { GridKey(a.x+b.x,a.y+b.y,a.z+b.z) }
}
struct Citizen: Codable {
    var id: Int; var name: String; var x: Float; var y: Float; var z: Float
    var activity: String; var money: Float; var hunger: Float
}
struct CityState: Codable {
    var time: String; var treasury: Double; var roads: [[Int]]; var zones: Int
    var citizens: [Citizen]; var buildings: [Building]; var zonePolygons: [ZonePolygon]?
    struct ZonePolygon: Codable { var type:Int; var points:[[Float]] }
    struct Building: Codable { var id: Int; var type: Int; var x: Int; var y: Int; var z: Int; var stock: Int }
}
struct OtherPlayer: Codable { var id: Int; var name: String; var x: Float; var y: Float; var z: Float }
struct GroundDrop: Codable { var id: Int; var type: Int; var x: Float; var y: Float; var z: Float }
struct Snapshot: Codable {
    var schema: Int; var game: String; var seed: String; var bounds: [Int]; var pose: [Float]
    var cells: [[Int]]; var inventory: [[Int]]; var health: Int; var notice: String
    var resetPose: Bool?; var city: CityState?; var players: [OtherPlayer]?; var drops: [GroundDrop]?
    func validate() throws {
        guard schema==1, ["sandbox","city"].contains(game), bounds.count==6, pose.count==5,
              pose.allSatisfy({$0.isFinite}), bounds.allSatisfy({abs(Double($0))<=1_000_100}), (game=="city")==((city != nil)), abs(pose[0])<=1_000_000, abs(pose[2])<=1_000_000,
              bounds[0]<=bounds[1], bounds[2]<=bounds[3], bounds[4]<=bounds[5],
              bounds[1]-bounds[0]<=64, bounds[5]-bounds[4]<=64, bounds[3]-bounds[2]<=128,
              bounds[2]>=(-32), bounds[3]<=95, inventory.count==36, cells.count<=120_000,
              (0...20).contains(health), inventory.allSatisfy({$0.count==2 && (0...189).contains($0[0]) && (0...64).contains($0[1]) && ($0[0]==0)==($0[1]==0)}),
              cells.allSatisfy({$0.count==5 && (1...189).contains($0[3]) && (0...0xffffff).contains($0[4]) && $0[0]>=bounds[0] && $0[0]<=bounds[1] && $0[1]>=bounds[2] && $0[1]<=bounds[3] && $0[2]>=bounds[4] && $0[2]<=bounds[5]})
        else { throw GameError.invalidWorld }
        guard Set(cells.map{GridKey($0[0],$0[1],$0[2])}).count==cells.count else { throw GameError.invalidWorld }
    }
}
enum GameError: LocalizedError {
    case invalidWorld, noSave, invalidAddress, message(String)
    var errorDescription: String? {
        switch self {
        case .invalidWorld: return "This world could not be read safely. Keep the save and try again."
        case .noSave: return "No offline world yet. Start a new sandbox."
        case .invalidAddress: return "Enter an HTTPS gateway address. Local testing also accepts http://127.0.0.1."
        case .message(let text): return text
        }
    }
}

/// Native whole-block sandbox rules. Online decisions always come from the Java server.
final class VoxelWorld {
    var snapshot: Snapshot
    var types: [GridKey:Int]=[:]
    var colors: [GridKey:Int]=[:]
    var position=SCNVector3Zero; var yaw: Float=0; var pitch: Float = -0.55
    var verticalSpeed: Float=0; var grounded=false
    var slot=0; var revision=0
    init(_ snapshot: Snapshot) throws { self.snapshot=snapshot; try replace(snapshot,resetPose:true) }
    func replace(_ value: Snapshot, resetPose: Bool=false) throws {
        try value.validate(); snapshot=value
        types.removeAll(keepingCapacity:true); colors.removeAll(keepingCapacity:true)
        for cell in value.cells { let key=GridKey(cell[0],cell[1],cell[2]); types[key]=cell[3]; colors[key]=cell[4] }
        if resetPose || value.resetPose==true { position=SCNVector3(value.pose[0],value.pose[1],value.pose[2]); yaw=value.pose[3]; pitch=value.pose[4]==0 ? -0.55 : value.pose[4]; verticalSpeed=0 }
        revision += 1
    }
    func type(_ key: GridKey) -> Int { types[key] ?? 0 }
    func within(_ p: SCNVector3) -> Bool { let b=snapshot.bounds
        return p.x-0.3>=Float(b[0]) && p.x+0.3<Float(b[1]+1) && p.z-0.3>=Float(b[4]) && p.z+0.3<Float(b[5]+1) && p.y>=Float(b[2])+0.01
    }
    func collides(_ p: SCNVector3) -> Bool {
        if !within(p) { return true }
        for x in Int(floor(p.x-0.3))...Int(floor(p.x+0.299)) {
            for y in Int(floor(p.y+0.001))...Int(floor(p.y+1.799)) {
                for z in Int(floor(p.z-0.3))...Int(floor(p.z+0.299)) { let t=type(GridKey(x,y,z)); if t != 0 && t != 185 { return true } }
            }
        }
        return false
    }
    func step(forward: Float, strafe: Float, dt: Float) {
        let dt=min(max(dt,0),0.04), length=max(1,sqrt(forward*forward+strafe*strafe))
        let dx=(-sin(yaw)*forward+cos(yaw)*strafe)*4.2*dt/length
        let dz=(-cos(yaw)*forward-sin(yaw)*strafe)*4.2*dt/length
        var next=position; next.x+=dx; if !collides(next) {position.x=next.x}
        next=position; next.z+=dz; if !collides(next) {position.z=next.z}
        verticalSpeed=max(-20,verticalSpeed-20*dt)
        let dy=verticalSpeed*dt
        // Small substeps keep a falling player from crossing a one-block floor.
        let steps=max(1,Int(ceil(abs(dy)/0.1)))
        grounded=false
        for _ in 0..<steps { next=position; next.y+=dy/Float(steps)
            if collides(next) { if verticalSpeed<0 {grounded=true};verticalSpeed=0;break } else {position.y=next.y}
        }
    }
    func jump() { if grounded { verticalSpeed=7.2; grounded=false } }
    func look(dx: Float, dy: Float) { yaw-=dx*0.004; pitch=min(1.45,max(-1.45,pitch-dy*0.004)) }
    var direction: SCNVector3 { SCNVector3(-sin(yaw)*cos(pitch),sin(pitch),-cos(yaw)*cos(pitch)) }
    var eye: SCNVector3 { SCNVector3(position.x,position.y+1.62,position.z) }
    func target() -> (hit: GridKey, previous: GridKey)? {
        // Amanatides/Woo grid ray traversal: no missed thin faces at diagonal angles.
        let o=eye,d=direction
        var cell=GridKey(Int(floor(o.x)),Int(floor(o.y)),Int(floor(o.z))), previous=cell
        let step=GridKey(d.x>=0 ? 1 : -1,d.y>=0 ? 1 : -1,d.z>=0 ? 1 : -1)
        func start(_ o:Float,_ d:Float,_ c:Int,_ s:Int)->Float { if abs(d)<0.000001{return .infinity};return (Float(c+(s>0 ? 1:0))-o)/d }
        var tx=start(o.x,d.x,cell.x,step.x),ty=start(o.y,d.y,cell.y,step.y),tz=start(o.z,d.z,cell.z,step.z)
        let dx=abs(d.x)<0.000001 ? Float.infinity : abs(1/d.x)
        let dy=abs(d.y)<0.000001 ? Float.infinity : abs(1/d.y)
        let dz=abs(d.z)<0.000001 ? Float.infinity : abs(1/d.z)
        var distance:Float=0
        while distance<=5 {
            let t=type(cell);if t != 0 && t != 185 {return (cell,previous)}
            previous=cell
            if tx<ty && tx<tz {cell.x+=step.x;distance=tx;tx+=dx}
            else if ty<tz {cell.y+=step.y;distance=ty;ty+=dy}
            else {cell.z+=step.z;distance=tz;tz+=dz}
        }
        return nil
    }
    func offlineEdit(at key: GridKey, type: Int) throws {
        if type==0 {
            guard let old=types.removeValue(forKey:key) else {throw GameError.message("Aim at a block within five blocks.")}
            colors.removeValue(forKey:key)
            var amount=1
            for i in 0..<36 where snapshot.inventory[i][0]==old && snapshot.inventory[i][1]<64 {snapshot.inventory[i][1]+=1;amount=0;break}
            if amount>0, let i=snapshot.inventory.firstIndex(where:{$0[1]==0}) {snapshot.inventory[i]=[old,1]}
        } else {
            guard types[key]==nil, snapshot.inventory[slot][0]==type, snapshot.inventory[slot][1]>0,
                  key.x>=snapshot.bounds[0], key.x<=snapshot.bounds[1], key.y>=snapshot.bounds[2], key.y<=snapshot.bounds[3], key.z>=snapshot.bounds[4], key.z<=snapshot.bounds[5]
            else {throw GameError.message("No space or no blocks in this slot.")}
            types[key]=type
            if collides(position) {types.removeValue(forKey:key);throw GameError.message("Cannot place a block inside your player.")}
            colors[key]=Self.color(type);snapshot.inventory[slot][1]-=1
            if snapshot.inventory[slot][1]==0 {snapshot.inventory[slot][0]=0}
        }
        revision+=1
    }
    func savedSnapshot() -> Snapshot {
        var save=snapshot
        save.cells=types.map{[$0.key.x,$0.key.y,$0.key.z,$0.value,colors[$0.key] ?? Self.color($0.value)]}
        save.pose=[position.x,position.y,position.z,yaw,pitch]
        return save
    }
    static func color(_ type:Int)->Int {
        switch type {case 1:return 0x4da636;case 2:return 0x7a4d2b;case 3:return 0x8c949e;case 4:return 0xe0c47a;case 5:return 0xe8f5ff;case 6:return 0x73471f;case 7:return 0x29732e;case 165:return 0xb8824a;case 166:return 0x9c4536;case 167:return 0xa8dee8;case 185:return 0x266ead;case 187:return 0x55575a;default:return 0x9966b3}
    }
    static func name(_ type:Int)->String {
        switch type {case 0:return "Empty";case 1:return "Grass";case 2:return "Dirt";case 3:return "Stone";case 4:return "Sand";case 5:return "Snow";case 6:return "Wood";case 7:return "Leaves";case 165:return "Planks";case 166:return "Bricks";case 167:return "Glass";default:return "Block \(type)"}
    }
}

final class OfflineSave {
    static var url: URL { FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("sandbox-v1.json") }
    static func load() throws -> Snapshot { let value=try JSONDecoder().decode(Snapshot.self,from:Data(contentsOf:url)); try value.validate(); return value }
    static func write(_ world:VoxelWorld) throws {
        try FileManager.default.createDirectory(at:url.deletingLastPathComponent(),withIntermediateDirectories:true)
        try JSONEncoder().encode(world.savedSnapshot()).write(to:url,options:.atomic)
    }
    static func fresh() throws -> Snapshot {
        guard let url=Bundle.main.url(forResource:"sandbox",withExtension:"json") else {throw GameError.invalidWorld}
        let value=try JSONDecoder().decode(Snapshot.self,from:Data(contentsOf:url));try value.validate();return value
    }
}
