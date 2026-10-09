import Foundation
import SceneKit
import UIKit
import simd

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
    var atmosphere: AtmosphereProfile? = nil; var sun: [Double]? = nil
    func validate() throws {
        try atmosphere?.validate()
        if let sun=sun {guard sun.count==3,sun.allSatisfy({$0.isFinite}),abs(sqrt(sun.reduce(0){$0+$1*$1})-1)<0.001 else {throw GameError.invalidWorld}}
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

/// Version 1 mirrors the desktop's fixed 34-double profile payload. Missing legacy fields use Earth.
struct AtmosphereProfile:Codable,Equatable {
    var version:Int;var enabled:Bool;var values:[Double]
    static let earth=AtmosphereProfile(version:1,enabled:true,values:[6_360_000,100_000,1,24,0,0,0,0,1,0,5.8e-6,13.5e-6,33.1e-6,3.996e-6,3.996e-6,3.996e-6,4.44e-6,4.44e-6,4.44e-6,0.65e-6,1.881e-6,0.085e-6,8000,1200,25000,15000,0.76,0.1,0.1,0.1,18,18,18,0.004675])
    func validate()throws {
        guard version==1,values.count==34,values.allSatisfy({$0.isFinite}) else {throw GameError.invalidWorld}
        let v=values,minimumScale=max(1,values[0]*1e-6)
        guard (1000...1e9).contains(v[0]),(minimumScale...max(minimumScale,min(1e7,v[0]))).contains(v[1]),(0.001...1e6).contains(v[2]),abs(v[3])<=1e9,
              (4..<7).allSatisfy({abs(v[$0])<=1e12}),abs(simd_length(vector(7))-1)<1e-9,(10..<22).allSatisfy({(0...0.01).contains(v[$0])}),
              (0..<3).allSatisfy({v[13+$0]<=v[16+$0]}),(minimumScale...max(minimumScale,v[1])).contains(v[22]),(minimumScale...max(minimumScale,v[1])).contains(v[23]),
              (0...v[1]).contains(v[24]),(minimumScale...max(minimumScale,v[1])).contains(v[25]),(-0.95...0.95).contains(v[26]),
              (27..<30).allSatisfy({(0...1).contains(v[$0])}),(30..<33).allSatisfy({(0...100).contains(v[$0])}),(0.00001...0.05).contains(v[33])
        else {throw GameError.invalidWorld}
    }
    func vector(_ i:Int)->SIMD3<Double>{SIMD3(values[i],values[i+1],values[i+2])}
    func position(_ p:SCNVector3)->SIMD3<Double>{
        let up=vector(7),east=simd_normalize(abs(up.y)<0.99 ? simd_cross(SIMD3(0,1,0),up) : -simd_cross(SIMD3(0,0,1),up)),north=simd_cross(east,up)
        return east*(Double(p.x)-values[4])*values[2]+up*(values[0]+(Double(p.y)-values[5]-values[3])*values[2])+north*(Double(p.z)-values[6])*values[2]
    }
    func ray(_ v:SIMD3<Double>)->SIMD3<Double>{let up=vector(7),east=simd_normalize(abs(up.y)<0.99 ? simd_cross(SIMD3(0,1,0),up) : -simd_cross(SIMD3(0,0,1),up));return east*v.x+up*v.y+simd_cross(east,up)*v.z}
}

/// Bounded low-quality CPU precomputation for native SceneKit's Metal cube background.
/// The renderer remains opt-in until a physical-device budget is measured.
enum NativeAtmosphere {
    static func sphere(_ p:SIMD3<Double>,_ d:SIMD3<Double>,_ r:Double)->(Double,Double)? {
        let b=simd_dot(p,d),length=simd_length(p),c=(length-r)*(length+r),disc=b*b-c
        if disc<0{return nil};let root=sqrt(max(0,disc));return (-b-root,-b+root)
    }
    static func density(_ h:Double,_ profile:AtmosphereProfile)->SIMD3<Double>{let v=profile.values
        if h<0 || h>v[1] || !profile.enabled{return .zero};return SIMD3(exp(-h/v[22]),exp(-h/v[23]),max(0,1-abs(h-v[24])/v[25]))}
    static func extinction(_ rho:SIMD3<Double>,_ p:AtmosphereProfile)->SIMD3<Double>{p.vector(10)*rho.x+p.vector(16)*rho.y+p.vector(19)*rho.z}
    /// Beer-Lambert attenuation accepts positive optical depth, never a signed exponent.
    static func attenuation(_ opticalDepth:SIMD3<Double>)->SIMD3<Double>{SIMD3(exp(-opticalDepth.x),exp(-opticalDepth.y),exp(-opticalDepth.z))}
    static func span(_ p:SIMD3<Double>,_ d:SIMD3<Double>,_ profile:AtmosphereProfile)->(Double,Double)? {
        guard simd_length(p)>=profile.values[0]-0.001 else{return nil}
        let v=profile.values;guard let shell=sphere(p,d,v[0]+v[1]) else{return nil}
        let near=max(0,shell.0);var far=shell.1
        if let ground=sphere(p,d,v[0]),ground.1>0,ground.0 >= -0.001 {far=min(far,max(0,ground.0))}
        return far>near ? (near,far):nil
    }
    static func sunlight(_ p:SIMD3<Double>,_ sun:SIMD3<Double>,_ profile:AtmosphereProfile)->SIMD3<Double>{
        if simd_length(p)<profile.values[0]-0.001{return .zero}
        if let ground=sphere(p,sun,profile.values[0]),ground.1>0,ground.0 >= -0.001{return .zero}
        if !profile.enabled{return SIMD3(repeating:1)}
        guard let range=span(p,sun,profile) else{return SIMD3(repeating:1)}
        let step=(range.1-range.0)/16;var depth=SIMD3<Double>.zero
        for i in 0..<16 {depth += extinction(density(simd_length(p+sun*(range.0+(Double(i)+0.5)*step))-profile.values[0],profile),profile)*step}
        return attenuation(depth)
    }
    /// Same linear RGB top-of-shell irradiance and tangent transform as desktop.
    static func solarIrradiance(_ profile:AtmosphereProfile,_ camera:SCNVector3,_ worldSun:SIMD3<Double>)->SIMD3<Double>{
        let p=profile.position(camera),sun=simd_normalize(profile.ray(worldSun))
        return profile.vector(30)*sunlight(p,sun,profile)
    }
    static func radiance(_ p:SIMD3<Double>,_ d:SIMD3<Double>,_ sun:SIMD3<Double>,_ profile:AtmosphereProfile,includeSolarDisc:Bool=true)->SIMD3<Double>{
        let v=profile.values;var color=SIMD3<Double>.zero,depth=SIMD3<Double>.zero
        if profile.enabled,let range=span(p,d,profile) {
            let step=(range.1-range.0)/24,mu=simd_dot(d,sun),g=v[26],phaseR=3*(1+mu*mu)/(16*Double.pi),phaseM=(1-g*g)/(4*Double.pi*pow(1+g*g-2*g*mu,1.5))
            for i in 0..<24 {let q=p+d*(range.0+(Double(i)+0.5)*step),rho=density(simd_length(q)-v[0],profile),sigma=extinction(rho,profile)
                let source=(profile.vector(10)*rho.x*phaseR+profile.vector(13)*rho.y*phaseM)*profile.vector(30)
                color += attenuation(depth+sigma*step*0.5)*source*sunlight(q,sun,profile)*step;depth += sigma*step
            }
        }
        // Close the local patch with the configured diffuse virtual planetary surface.
        // This is background only: no collision or world voxels are added.
        if let ground=sphere(p,d,v[0]),ground.0>=0,ground.1>0 {
            let q=p+d*ground.0,n=simd_normalize(q)
            color += attenuation(depth)*profile.vector(27)*profile.vector(30)*sunlight(q+n*2,sun,profile)*max(0,simd_dot(n,sun))/Double.pi
        }
        if includeSolarDisc && simd_dot(d,sun)>cos(v[33]) {color += profile.vector(30)*sunlight(p,sun,profile)/(2*Double.pi*(1-cos(v[33])))}
        return color
    }
    static func cube(_ profile:AtmosphereProfile,_ camera:SCNVector3,_ worldSun:SIMD3<Double>)->[UIImage]{
        var p=profile.position(camera);if simd_length(p)<profile.values[0]+1 {p=simd_normalize(p)*(profile.values[0]+1)}
        let sun=simd_normalize(profile.ray(worldSun));let n=16
        return (0..<6).map{face in
            var pixels=[UInt8](repeating:255,count:n*n*4)
            for y in 0..<n {for x in 0..<n {let a=(Double(x)+0.5)/Double(n)*2-1,b=(Double(y)+0.5)/Double(n)*2-1;let direction:SIMD3<Double>
                switch face {case 0:direction=SIMD3(1,-b,-a);case 1:direction=SIMD3(-1,-b,a);case 2:direction=SIMD3(a,1,b);case 3:direction=SIMD3(a,-1,-b);case 4:direction=SIMD3(a,-b,1);default:direction=SIMD3(-a,-b,-1)}
                // The visible disc is separate geometry; a 16-pixel cube cannot resolve it.
                let color=radiance(p,simd_normalize(profile.ray(direction)),sun,profile,includeSolarDisc:false)
                for channel in 0..<3 {let value=max(0,color[channel]);let mapped=min(1,(value*(2.51*value+0.03))/(value*(2.43*value+0.59)+0.14));pixels[(x+y*n)*4+channel]=UInt8(min(255,max(0,pow(mapped,1/2.2)*255)))}
            }}
            let data=Data(pixels) as CFData;let provider=CGDataProvider(data:data)!
            let image=CGImage(width:n,height:n,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:n*4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.premultipliedLast.rawValue),provider:provider,decode:nil,shouldInterpolate:true,intent:.defaultIntent)!
            return UIImage(cgImage:image)
        }
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
    /// Bounded low-quality outdoor visibility. Opaque whole cells block every ray;
    /// glass transmits. Fine/model geometry is not present in schema-1 cell snapshots.
    func skyVisibility(_ cell:GridKey)->Double {
        let b=snapshot.bounds
        let directions:[SIMD3<Double>]=[SIMD3(0,1,0),SIMD3(-1,0.25,0),SIMD3(1,0.25,0),SIMD3(0,0.25,-1),SIMD3(0,0.25,1)]
        var result=0.0
        for direction in directions {
            var transmission=1.0;var previous:GridKey?=nil
            for step in 0..<260 {
                let p=SIMD3(Double(cell.x)+0.5,Double(cell.y)+0.5,Double(cell.z)+0.5)+direction*(Double(step)*0.5)
                let key=GridKey(Int(floor(p.x)),Int(floor(p.y)),Int(floor(p.z)))
                if key.x<b[0] || key.x>b[1] || key.y>b[3] || key.z<b[4] || key.z>b[5] {result+=transmission;break}
                if key==previous {continue};previous=key
                let value=type(key)
                if value==167 {transmission*=0.7}
                else if value != 0 {break}
            }
        }
        return result/Double(directions.count)
    }

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
