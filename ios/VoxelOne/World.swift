import Foundation
import SceneKit
import UIKit
import simd

enum NativeCameraMode:String,CaseIterable {
    case first,third,isometric
    var title:String {switch self {case .first:return "First person";case .third:return "Third person";case .isometric:return "Isometric"}}
}
struct VoxelHit {let hit:GridKey;let previous:GridKey;let distance:Float;let normal:GridKey;let point:SCNVector3;let box:VoxelBox}
struct NativeVoxelAddress:Equatable,Sendable {
    var cell:GridKey;var depth:Int;var ix:Int;var iy:Int;var iz:Int
    func validate()throws {
        guard (0...4).contains(depth),[cell.x,cell.y,cell.z].allSatisfy({abs(Double($0))<=1_000_000}),
              [ix,iy,iz].allSatisfy({$0>=0 && $0<(1<<depth)}) else{throw GameError.invalidWorld}
    }
}
private struct NativeVoxelSample:Equatable {var material:Int;var color:Int}
private enum NativeVoxelCodec {
    static func compress(_ grid:[NativeVoxelSample])->NativeGeometry {
        var leaves:[[Int]]=[]
        func visit(_ x:Int,_ y:Int,_ z:Int,_ size:Int){
            let first=grid[x+16*(y+16*z)];var uniform=true
            outer: for a in x..<(x+size) {for b in y..<(y+size) {for c in z..<(z+size) {
                if grid[a+16*(b+16*c)] != first {uniform=false;break outer}
            }}}
            if uniform {if first.material != 0 {leaves.append([x,y,z,size,first.material,first.color])};return}
            let h=size/2
            for i in 0..<8 {visit(x+((i&1)==0 ? 0:h),y+((i&2)==0 ? 0:h),z+((i&4)==0 ? 0:h),h)}
        }
        visit(0,0,0,16);return NativeGeometry(resolution:16,leaves:leaves)
    }
}
struct GridKey: Hashable, Codable, Sendable {
    var x: Int; var y: Int; var z: Int
    init(_ x: Int, _ y: Int, _ z: Int) { self.x=x; self.y=y; self.z=z }
    static func + (a: GridKey, b: GridKey) -> GridKey { GridKey(a.x+b.x,a.y+b.y,a.z+b.z) }
}
struct Citizen: Codable, Sendable {
    var id: Int; var name: String; var x: Float; var y: Float; var z: Float
    var activity: String; var money: Float; var hunger: Float
}
struct CityState: Codable, Sendable {
    var time: String; var treasury: Double; var roads: [[Int]]; var zones: Int
    var citizens: [Citizen]; var buildings: [Building]; var zonePolygons: [ZonePolygon]?
    struct ZonePolygon: Codable, Sendable { var type:Int; var points:[[Float]] }
    struct Building: Codable, Sendable { var id: Int; var type: Int; var x: Int; var y: Int; var z: Int; var stock: Int }
}
struct OtherPlayer: Codable, Sendable { var id: Int; var name: String; var x: Float; var y: Float; var z: Float }
struct GroundDrop: Codable, Sendable { var id: Int; var type: Int; var x: Float; var y: Float; var z: Float }
struct NativeRecipe: Codable, Sendable {
    var id:Int;var name:String;var inputs:[[Int]];var output:Int;var count:Int;var category:String?=nil
    func validate()throws {
        guard (0...103).contains(id),!name.isEmpty,name.count<=80,
              category == nil || ["All","Tools","Materials","Building","Decorations","Lighting"].contains(category!),
              (1...189).contains(output),(1...64).contains(count),
              !inputs.isEmpty,inputs.count<=8,
              inputs.allSatisfy({$0.count==2 && (1...189).contains($0[0]) && (1...64).contains($0[1])}),
              Set(inputs.map{$0[0]}).count==inputs.count else {throw GameError.invalidWorld}
    }
    static func bundled()throws->[NativeRecipe] {
        guard let url=Bundle.main.url(forResource:"recipes",withExtension:"json") else {throw GameError.invalidWorld}
        let values=try JSONDecoder().decode([NativeRecipe].self,from:Data(contentsOf:url))
        guard values.count<=104,Set(values.map{$0.id}).count==values.count else {throw GameError.invalidWorld}
        for value in values {try value.validate()};return values
    }
}
struct NativeGeometry:Codable,Sendable {
    var resolution:Int;var leaves:[[Int]]
    func validate()throws {
        guard [8,16,32].contains(resolution),!leaves.isEmpty,leaves.count<=32768 else {throw GameError.invalidWorld}
        var occupied=Set<Int>()
        for b in leaves {
            guard b.count==6,[1,2,4,8,16,32].contains(b[3]),b[3]<=resolution,
                  (0..<3).allSatisfy({b[$0]>=0 && b[$0]<=resolution-b[3] && b[$0]%b[3]==0}),
                  (1...189).contains(b[4]),(0...0xffffff).contains(b[5]) else {throw GameError.invalidWorld}
            for x in b[0]..<(b[0]+b[3]) {for y in b[1]..<(b[1]+b[3]) {for z in b[2]..<(b[2]+b[3]) {
                guard occupied.insert(x+resolution*(y+resolution*z)).inserted else {throw GameError.invalidWorld}
            }}}
        }
    }
}
struct NativeMaterial:Codable,Sendable {var id:Int;var name:String;var material:Int;var depth:Int;var color:Int}
struct NativeModel:Codable,Sendable {var id:Int;var fingerprint:String;var geometry:NativeGeometry}
struct NativeDetail:Codable,Sendable {var cell:[Int];var geometry:NativeGeometry}
struct NativeContract:Codable,Sendable {
    var version:Int;var materials:[NativeMaterial];var models:[NativeModel];var fineEdits:Bool? = nil
    func validate()throws {
        guard version==1,materials.count==189,Set(materials.map{$0.id})==Set(1...189),models.count<=129,
              Set(models.map{$0.id}).count==models.count,
              materials.allSatisfy({(1...189).contains($0.material) && (0...4).contains($0.depth) && (0...0xffffff).contains($0.color) && !$0.name.isEmpty && $0.name.count<=80}),
              models.reduce(0,{$0+$1.geometry.leaves.count})<=32768 else {throw GameError.invalidWorld}
        for model in models {
            guard (8...136).contains(model.id),model.fingerprint.count==64,
                  model.fingerprint.allSatisfy({"0123456789abcdef".contains($0)}) else {throw GameError.invalidWorld}
            try model.geometry.validate()
        }
    }
    static func bundled()throws->NativeContract {
        guard let url=Bundle.main.url(forResource:"contract",withExtension:"json") else {throw GameError.invalidWorld}
        let value=try JSONDecoder().decode(NativeContract.self,from:Data(contentsOf:url));try value.validate();return value
    }
}
struct VoxelBox:Equatable,Sendable {
    var x:Float;var y:Float;var z:Float;var size:Float;var type:Int;var color:Int
}
struct Snapshot: Codable, Sendable {
    var schema: Int; var game: String; var seed: String; var bounds: [Int]; var pose: [Float]
    var cells: [[Int]]; var inventory: [[Int]]; var health: Int; var notice: String
    var resetPose: Bool?; var city: CityState?; var players: [OtherPlayer]?; var drops: [GroundDrop]?
    var contract:NativeContract? = nil;var details:[NativeDetail]? = nil
    var recipes:[NativeRecipe]? = nil
    var atmosphere: AtmosphereProfile? = nil; var sun: [Double]? = nil
    func validate() throws {
        if let recipes=recipes {
            guard recipes.count<=104,Set(recipes.map{$0.id}).count==recipes.count else {throw GameError.invalidWorld}
            for recipe in recipes {try recipe.validate()}
        }
        try atmosphere?.validate()
        if let sun=sun {guard sun.count==3,sun.allSatisfy({$0.isFinite}),abs(sqrt(sun.reduce(0){$0+$1*$1})-1)<0.001 else {throw GameError.invalidWorld}}
        guard [1,2].contains(schema), ["sandbox","city"].contains(game), bounds.count==6, pose.count==5,
              pose.allSatisfy({$0.isFinite}), bounds.allSatisfy({abs(Double($0))<=1_000_100}), (game=="city")==((city != nil)), abs(pose[0])<=1_000_000, abs(pose[1])<=1_000_000, abs(pose[2])<=1_000_000,
              bounds[0]<=bounds[1], bounds[2]<=bounds[3], bounds[4]<=bounds[5],
              bounds[1]-bounds[0]<=64, bounds[5]-bounds[4]<=64, bounds[3]-bounds[2]<=128,
              bounds[2]>=(-32), bounds[3]<=95, inventory.count==36, cells.count<=120_000,
              (0...20).contains(health), inventory.allSatisfy({$0.count==2 && (0...189).contains($0[0]) && (0...64).contains($0[1]) && ($0[0]==0)==($0[1]==0)}),
              cells.allSatisfy({$0.count==5 && ((1...189).contains($0[3]) || (schema==2 && $0[3]==255)) && (0...0xffffff).contains($0[4]) && $0[0]>=bounds[0] && $0[0]<=bounds[1] && $0[1]>=bounds[2] && $0[1]<=bounds[3] && $0[2]>=bounds[4] && $0[2]<=bounds[5]})
        else { throw GameError.invalidWorld }
        guard Set(cells.map{GridKey($0[0],$0[1],$0[2])}).count==cells.count else { throw GameError.invalidWorld }
        if schema==2 {
            guard let contract=contract else {throw GameError.invalidWorld};try contract.validate()
            let detail=details ?? [];guard detail.count<=512 else {throw GameError.invalidWorld}
            var keys=Set<GridKey>(),expanded=0
            for d in detail {
                guard d.cell.count==3,d.geometry.resolution==16 else {throw GameError.invalidWorld}
                let key=GridKey(d.cell[0],d.cell[1],d.cell[2]);guard keys.insert(key).inserted else {throw GameError.invalidWorld}
                try d.geometry.validate();expanded+=d.geometry.leaves.count
            }
            let partial=Set(cells.filter{$0[3]==255}.map{GridKey($0[0],$0[1],$0[2])})
            guard keys==partial else {throw GameError.invalidWorld}
            for c in cells where (8...136).contains(c[3]) {
                guard let model=contract.models.first(where:{$0.id==c[3]}) else {throw GameError.invalidWorld}
                expanded+=model.geometry.leaves.count
            }
            guard expanded<=32768 else {throw GameError.invalidWorld}
        } else if contract != nil || !(details ?? []).isEmpty {throw GameError.invalidWorld}
    }
}

/// Version 1 mirrors the desktop's fixed 34-double profile payload. Missing legacy fields use Earth.
struct AtmosphereProfile:Codable,Equatable,Sendable {
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

/// Native leaf-geometry sandbox rules. Online decisions always come from the Java server.
final class VoxelWorld {
    var snapshot: Snapshot
    var types: [GridKey:Int]=[:]
    var colors: [GridKey:Int]=[:]
    private var shapes:[GridKey:[VoxelBox]]=[:]
    private var materials:[Int:NativeMaterial]=[:];private var models:[Int:NativeGeometry]=[:]
    var position=SCNVector3Zero; var yaw: Float=0; var pitch: Float = -0.55
    private(set) var flying=false
    var verticalSpeed: Float=0; var grounded=false
    var slot=0; var revision=0;private(set) var geometryRevision=0
    init(_ snapshot: Snapshot) throws { self.snapshot=snapshot; try replace(snapshot,resetPose:true) }
    func replace(_ value: Snapshot, resetPose: Bool=false) throws {
        try value.validate();let oldTypes=types,oldColors=colors,oldShapes=shapes;snapshot=value
        types.removeAll(keepingCapacity:true); colors.removeAll(keepingCapacity:true)
        for cell in value.cells { let key=GridKey(cell[0],cell[1],cell[2]); types[key]=cell[3]; colors[key]=cell[4] }
        materials=Dictionary(uniqueKeysWithValues:(value.contract?.materials ?? []).map{($0.id,$0)})
        models=Dictionary(uniqueKeysWithValues:(value.contract?.models ?? []).map{($0.id,$0.geometry)})
        shapes.removeAll(keepingCapacity:true)
        for d in value.details ?? [] {shapes[GridKey(d.cell[0],d.cell[1],d.cell[2])]=Self.boxes(d.geometry)}
        for (key,type) in types where type != 255 {if let geometry=itemBoxes(type) {shapes[key]=geometry}}
        if resetPose || value.resetPose==true { position=SCNVector3(value.pose[0],value.pose[1],value.pose[2]); yaw=value.pose[3]; pitch=value.pose[4]==0 ? -0.55 : value.pose[4]; verticalSpeed=0 }
        if oldTypes != types || oldColors != colors || oldShapes != shapes {geometryRevision+=1}
        revision += 1
    }
    func type(_ key: GridKey) -> Int { types[key] ?? 0 }
    static func boxes(_ geometry:NativeGeometry)->[VoxelBox] {
        let r=Float(geometry.resolution)
        return geometry.leaves.map{VoxelBox(x:Float($0[0])/r,y:Float($0[1])/r,z:Float($0[2])/r,size:Float($0[3])/r,type:$0[4],color:$0[5])}
    }
    private func itemBoxes(_ type:Int)->[VoxelBox]? {
        if let geometry=models[type] {return Self.boxes(geometry)}
        if let m=materials[type],m.depth>0 {return [VoxelBox(x:0,y:0,z:0,size:1/Float(1<<m.depth),type:m.material,color:m.color)]}
        return nil
    }
    func boxes(_ key:GridKey)->[VoxelBox] {
        if let value=shapes[key] {return value}
        let t=type(key);return t==0 ? []:[VoxelBox(x:0,y:0,z:0,size:1,type:t,color:colors[key] ?? Self.color(t))]
    }
    func material(at p:SCNVector3)->Int {
        let key=GridKey(Int(floor(p.x)),Int(floor(p.y)),Int(floor(p.z)))
        let x=p.x-Float(key.x),y=p.y-Float(key.y),z=p.z-Float(key.z)
        return boxes(key).first(where:{x >= $0.x && x < $0.x+$0.size && y >= $0.y && y < $0.y+$0.size && z >= $0.z && z < $0.z+$0.size})?.type ?? 0
    }
    func fullOpaque(_ key:GridKey)->Bool {
        if let b=shapes[key] {return b.count==1 && b[0].size==1 && b[0].type != 185}
        let t=type(key);return t != 0 && t != 185
    }
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
                if key==previous && shapes[key]==nil {continue};previous=key
                let value=material(at:SCNVector3(Float(p.x),Float(p.y),Float(p.z)))
                if value==167 {transmission*=0.7}
                else if value != 0 {break}
            }
        }
        return result/Double(directions.count)
    }

    func within(_ p: SCNVector3) -> Bool { let b=snapshot.bounds
        return p.x-0.3>=Float(b[0]) && p.x+0.3<Float(b[1]+1) && p.z-0.3>=Float(b[4]) && p.z+0.3<Float(b[5]+1) && p.y>=Float(b[2])+0.01 && (!flying || p.y+1.8<=Float(b[3]+1))
    }
    func collides(_ p: SCNVector3) -> Bool {
        if !within(p) { return true }
        for x in Int(floor(p.x-0.3))...Int(floor(p.x+0.299)) {
            for y in Int(floor(p.y+0.001))...Int(floor(p.y+1.799)) {
                for z in Int(floor(p.z-0.3))...Int(floor(p.z+0.299)) {
                    for b in boxes(GridKey(x,y,z)) where b.type != 185 {
                        if p.x+0.299>Float(x)+b.x && p.x-0.3<Float(x)+b.x+b.size && p.y+1.799>Float(y)+b.y && p.y+0.001<Float(y)+b.y+b.size && p.z+0.299>Float(z)+b.z && p.z-0.3<Float(z)+b.z+b.size {return true}
                    }
                }
            }
        }
        return false
    }
    func setFlying(_ enabled:Bool) {
        flying=enabled && snapshot.game=="sandbox"
        if flying && !within(position) {flying=false}
        verticalSpeed=0;grounded=false
    }
    func step(forward: Float, strafe: Float, dt: Float, climb:Float=0) {
        let dt=min(max(dt,0),0.04),up=flying ? min(1,max(-1,climb)):0
        let length=max(1,sqrt(forward*forward+strafe*strafe+up*up))
        let dx=(-sin(yaw)*forward+cos(yaw)*strafe)*4.2*dt/length
        let dz=(-cos(yaw)*forward-sin(yaw)*strafe)*4.2*dt/length
        var next=position; next.x+=dx; if !collides(next) {position.x=next.x}
        next=position; next.z+=dz; if !collides(next) {position.z=next.z}
        verticalSpeed=flying ? up*4.2/length:max(-20,verticalSpeed-20*dt)
        let dy=verticalSpeed*dt
        // Small substeps keep a falling player from crossing a one-block floor.
        let steps=max(1,Int(ceil(abs(dy)/0.1)))
        grounded=false
        for _ in 0..<steps { next=position; next.y+=dy/Float(steps)
            if collides(next) { if verticalSpeed<0 {grounded=true};verticalSpeed=0;break } else {position.y=next.y}
        }
    }
    func jump() { if grounded && !flying { verticalSpeed=7.2; grounded=false } }
    func look(dx: Float, dy: Float) { yaw-=dx*0.004; pitch=min(1.45,max(-1.45,pitch-dy*0.004)) }
    var direction: SCNVector3 { SCNVector3(-sin(yaw)*cos(pitch),sin(pitch),-cos(yaw)*cos(pitch)) }
    var eye: SCNVector3 { SCNVector3(position.x,position.y+1.62,position.z) }
    func detailedTarget()->VoxelHit? {raycast(origin:eye,direction:direction,reach:5)}
    func target() -> (hit: GridKey, previous: GridKey)? {
        guard let result=detailedTarget() else{return nil}
        return (result.hit,result.previous)
    }
    /// Shared camera and player traversal. Direction must be normalized by the caller.
    func raycast(origin o:SCNVector3,direction d:SCNVector3,reach:Float)->VoxelHit? {
        guard [o.x,o.y,o.z,d.x,d.y,d.z,reach].allSatisfy({$0.isFinite}),[o.x,o.y,o.z].allSatisfy({abs($0)<=1_000_300}),reach>0,reach<=256,
              abs(d.x*d.x+d.y*d.y+d.z*d.z-1)<0.001 else{return nil}
        // Amanatides/Woo grid ray traversal: no missed thin faces at diagonal angles.
        var cell=GridKey(Int(floor(o.x)),Int(floor(o.y)),Int(floor(o.z))), previous=cell
        let step=GridKey(d.x>=0 ? 1 : -1,d.y>=0 ? 1 : -1,d.z>=0 ? 1 : -1)
        func start(_ o:Float,_ d:Float,_ c:Int,_ s:Int)->Float { if abs(d)<0.000001{return .infinity};return (Float(c+(s>0 ? 1:0))-o)/d }
        var tx=start(o.x,d.x,cell.x,step.x),ty=start(o.y,d.y,cell.y,step.y),tz=start(o.z,d.z,cell.z,step.z)
        let dx=abs(d.x)<0.000001 ? Float.infinity : abs(1/d.x)
        let dy=abs(d.y)<0.000001 ? Float.infinity : abs(1/d.y)
        let dz=abs(d.z)<0.000001 ? Float.infinity : abs(1/d.z)
        var distance:Float=0
        while distance<=reach {
            var nearest:Float?=nil,selected:VoxelBox?=nil;var selectedNormal=GridKey(0,0,0)
            for b in boxes(cell) where b.type != 185 {
                var near:Float=0,far=reach;var normal=GridKey(0,0,0)
                let lo=[Float(cell.x)+b.x,Float(cell.y)+b.y,Float(cell.z)+b.z],a=[o.x,o.y,o.z],v=[d.x,d.y,d.z]
                for axis in 0..<3 {
                    if abs(v[axis])<0.000001 {if a[axis]<lo[axis] || a[axis]>=lo[axis]+b.size {far = -1}}
                    else {let t0=(lo[axis]-a[axis])/v[axis],t1=(lo[axis]+b.size-a[axis])/v[axis],entry=min(t0,t1)
                        if entry>near {near=entry;let sign=v[axis]>0 ? -1:1;normal=GridKey(axis==0 ? sign:0,axis==1 ? sign:0,axis==2 ? sign:0)}
                        far=min(far,max(t0,t1))}
                }
                if far>=near,near>=distance-0.0001,near<=reach,nearest==nil || near<nearest! {nearest=near;selected=b;selectedNormal=normal}
            }
            if let hit=nearest,let box=selected {return VoxelHit(hit:cell,previous:previous,distance:hit,normal:selectedNormal,point:SCNVector3(o.x+d.x*hit,o.y+d.y*hit,o.z+d.z*hit),box:box)}
            previous=cell
            if tx<ty && tx<tz {cell.x+=step.x;distance=tx;tx+=dx}
            else if ty<tz {cell.y+=step.y;distance=ty;ty+=dy}
            else {cell.z+=step.z;distance=tz;tz+=dz}
        }
        return nil
    }
    /// Short swept camera volume; never put the third-person lens through terrain.
    func cameraPosition(toward desired:SCNVector3)->SCNVector3 {
        let start=eye,dx=desired.x-start.x,dy=desired.y-start.y,dz=desired.z-start.z
        let length=sqrt(dx*dx+dy*dy+dz*dz)
        guard length.isFinite,length>0,length<=12 else{return start}
        let count=max(1,Int(ceil(length/0.1)));var safe=start
        for step in 1...count {
            let t=Float(step)/Float(count),p=SCNVector3(start.x+dx*t,start.y+dy*t,start.z+dz*t)
            var blocked=false
            for x in [-Float(0.15),Float(0.15)] {for y in [-Float(0.15),Float(0.15)] {for z in [-Float(0.15),Float(0.15)] {
                let k=GridKey(Int(floor(p.x+x)),Int(floor(p.y+y)),Int(floor(p.z+z))),b=snapshot.bounds
                let material=self.material(at:SCNVector3(p.x+x,p.y+y,p.z+z))
                if k.x<b[0] || k.x>b[1] || k.y<b[2] || k.y>b[3] || k.z<b[4] || k.z>b[5] || (material != 0 && material != 185) {blocked=true}
            }}}
            if blocked {break};safe=p
        }
        return safe
    }
    func editAddress(_ hit:VoxelHit,place:Bool,breakDepth:Int)throws->NativeVoxelAddress {
        guard (0..<9).contains(slot),hit.box.size.isFinite,hit.box.size>0,hit.box.size<=1,
              [hit.hit.x,hit.hit.y,hit.hit.z].allSatisfy({abs(Double($0))<=1_000_000}),
              [hit.box.x,hit.box.y,hit.box.z].allSatisfy({$0.isFinite && $0>=0 && $0<=1-hit.box.size}),
              [hit.normal.x,hit.normal.y,hit.normal.z].allSatisfy({(-1...1).contains($0)}),
              [hit.point.x,hit.point.y,hit.point.z].allSatisfy({$0.isFinite}) else{throw GameError.invalidWorld}
        let item=place ? snapshot.inventory[slot][0]:0
        let model=(8...136).contains(type(hit.hit))
        var depth=place ? (materials[item]?.depth ?? 0):breakDepth
        if !place && depth==0 && !model {depth=min(4,max(0,Int(round(-log2(hit.box.size)))))}
        guard (0...4).contains(depth),abs(hit.normal.x)+abs(hit.normal.y)+abs(hit.normal.z)==1 else {throw GameError.message("Aim at a visible voxel face.")}
        if model && !place && depth != 0 {throw GameError.message("Use the whole-block grid for models.")}
        if place && depth==0 {let address=NativeVoxelAddress(cell:hit.hit+hit.normal,depth:0,ix:0,iy:0,iz:0);try address.validate();return address}
        let point=[Double(hit.point.x)-Double(hit.hit.x),Double(hit.point.y)-Double(hit.hit.y),Double(hit.point.z)-Double(hit.hit.z)]
        let low=[Double(hit.box.x),Double(hit.box.y),Double(hit.box.z)],n=[hit.normal.x,hit.normal.y,hit.normal.z],base=[hit.hit.x,hit.hit.y,hit.hit.z]
        var cell:[Int]=[],index:[Int]=[]
        for axis in 0..<3 {
            let epsilon=0.0001,upper=low[axis]+Double(hit.box.size)
            var q=min(upper-epsilon,max(low[axis]+epsilon,point[axis]))
            if place && n[axis] != 0 {q=(n[axis]>0 ? upper:low[axis])+Double(n[axis])*epsilon}
            let carry=Int(floor(q)),local=q-Double(carry)
            cell.append(base[axis]+carry);index.append(min((1<<depth)-1,max(0,Int(floor(local*Double(1<<depth))))))
        }
        let address=NativeVoxelAddress(cell:GridKey(cell[0],cell[1],cell[2]),depth:depth,ix:index[0],iy:index[1],iz:index[2]);try address.validate();return address
    }
    /// A bounded16^3 scratch transaction. Commit terrain, detail and inventory together.
    func offlineRegionEdit(_ address:NativeVoxelAddress,type:Int)throws {
        try address.validate();let key=address.cell,b=snapshot.bounds
        guard (0..<9).contains(slot),key.x>=b[0],key.x<=b[1],key.y>=b[2],key.y<=b[3],key.z>=b[4],key.z<=b[5],snapshot.schema==2 else {throw GameError.message("Outside the loaded world.")}
        if (8...136).contains(self.type(key)) || (8...136).contains(type) {
            guard address.depth==0 else {throw GameError.message("Models use whole-block placement.")}
            guard type==0 || models[type] != nil else {throw GameError.message("Model definition unavailable.")}
            try offlineEdit(at:key,type:type);return
        }
        let side=16>>address.depth,x=address.ix*side,y=address.iy*side,z=address.iz*side
        var grid=Array(repeating:NativeVoxelSample(material:0,color:0),count:4096)
        for box in boxes(key) {
            let a=Int((box.x*16).rounded()),b=Int((box.y*16).rounded()),c=Int((box.z*16).rounded()),s=Int((box.size*16).rounded())
            for i in a..<(a+s) {for j in b..<(b+s) {for k in c..<(c+s) {grid[i+16*(j+16*k)]=NativeVoxelSample(material:box.type,color:box.color)}}}
        }
        let old=grid[x+16*(y+16*z)]
        for i in x..<(x+side) {for j in y..<(y+side) {for k in z..<(z+side) {
            guard grid[i+16*(j+16*k)]==old else {throw GameError.message("Choose a smaller grid region.")}
        }}}
        var inventory=snapshot.inventory;let replacement:NativeVoxelSample
        if type==0 {
            guard old.material != 0,let item=materials.values.first(where:{$0.material==old.material && $0.depth==address.depth}),
                  address.depth==0 || [1,2,3,4,5,6,7,165,166,167,180].contains(old.material) else {throw GameError.message("No collectable voxel in this region.")}
            guard let slot=inventory.firstIndex(where:{$0[0]==item.id && $0[1]<64}) ?? inventory.firstIndex(where:{$0[1]==0}) else {throw GameError.message("Inventory full. Free a slot before collecting.")}
            inventory[slot]=[item.id,inventory[slot][1]+1];replacement=NativeVoxelSample(material:0,color:0)
        } else {
            guard old.material==0,let item=materials[type],item.depth==address.depth,inventory[slot][0]==type,inventory[slot][1]>0 else {throw GameError.message("No space or no matching pieces in this slot.")}
            inventory[slot][1]-=1;if inventory[slot][1]==0 {inventory[slot][0]=0}
            replacement=NativeVoxelSample(material:item.material,color:item.color)
        }
        for i in x..<(x+side) {for j in y..<(y+side) {for k in z..<(z+side) {grid[i+16*(j+16*k)]=replacement}}}
        let geometry=NativeVoxelCodec.compress(grid),proposed=Self.boxes(geometry)
        let uniformFull=geometry.leaves.count==1 && geometry.leaves[0][3]==16
        let total=shapes.values.reduce(0,{$0+$1.count})-(shapes[key]?.count ?? 0)+(uniformFull ? 0:proposed.count)
        guard total<=32768 else {throw GameError.message("This area has reached its mobile detail limit.")}
        if type != 0 {
            for box in proposed where box.type != 185 {
                let p=position
                if p.x+0.299>Float(key.x)+box.x && p.x-0.3<Float(key.x)+box.x+box.size && p.y+1.799>Float(key.y)+box.y && p.y+0.001<Float(key.y)+box.y+box.size && p.z+0.299>Float(key.z)+box.z && p.z-0.3<Float(key.z)+box.z+box.size {throw GameError.message("Cannot place a voxel inside your player.")}
            }
        }
        var detail=(snapshot.details ?? []).filter{$0.cell != [key.x,key.y,key.z]}
        if proposed.isEmpty {types.removeValue(forKey:key);colors.removeValue(forKey:key);shapes.removeValue(forKey:key)}
        else if uniformFull {types[key]=geometry.leaves[0][4];colors[key]=geometry.leaves[0][5];shapes.removeValue(forKey:key)}
        else {
            guard detail.count<512 else {throw GameError.message("This area has reached its partial-cell limit.")}
            types[key]=255;colors[key]=0;shapes[key]=proposed;detail.append(NativeDetail(cell:[key.x,key.y,key.z],geometry:geometry))
        }
        snapshot.details=detail;snapshot.inventory=inventory;revision+=1;geometryRevision+=1
    }
    func offlineEdit(at key: GridKey, type: Int) throws {
        if type==0 {
            guard var old=types[key] else {throw GameError.message("Aim at a block within five blocks.")}
            if old==255 {
                let boxes=self.boxes(key)
                guard boxes.count==1,let b=boxes.first,
                      let item=materials.values.first(where:{$0.material==b.type && abs(1/Float(1<<$0.depth)-b.size)<0.0001})
                else {throw GameError.message("This mixed cell needs the precision editor.")}
                old=item.id
            }
            // Reserve storage before removing terrain. A full bag must never destroy a block.
            guard let destination=snapshot.inventory.firstIndex(where:{$0[0]==old && $0[1]<64})
                ?? snapshot.inventory.firstIndex(where:{$0[1]==0})
            else {throw GameError.message("Inventory full. Free a slot before collecting this block.")}
            if snapshot.inventory[destination][1]==0 {snapshot.inventory[destination]=[old,1]}
            else {snapshot.inventory[destination][1]+=1}
            types.removeValue(forKey:key);colors.removeValue(forKey:key);shapes.removeValue(forKey:key)
        } else {
            guard types[key]==nil, snapshot.inventory[slot][0]==type, snapshot.inventory[slot][1]>0,
                  key.x>=snapshot.bounds[0], key.x<=snapshot.bounds[1], key.y>=snapshot.bounds[2], key.y<=snapshot.bounds[3], key.z>=snapshot.bounds[4], key.z<=snapshot.bounds[5]
            else {throw GameError.message("No space or no blocks in this slot.")}
            let geometry=itemBoxes(type)
            guard shapes.values.reduce(0,{$0+$1.count})+(geometry?.count ?? 0)<=32768 else {throw GameError.message("This area has reached its mobile detail limit.")}
            types[key]=type;shapes[key]=geometry
            if collides(position) {types.removeValue(forKey:key);shapes.removeValue(forKey:key);throw GameError.message("Cannot place a block inside your player.")}
            colors[key]=Self.color(type);snapshot.inventory[slot][1]-=1
            if snapshot.inventory[slot][1]==0 {snapshot.inventory[slot][0]=0}
        }
        revision+=1;geometryRevision+=1
    }
    /// Commit only after both ingredient consumption and all output storage succeed.
    func offlineCraft(_ recipe:NativeRecipe)throws {
        try recipe.validate()
        var next=snapshot.inventory
        for ingredient in recipe.inputs {
            var needed=ingredient[1]
            for i in next.indices where next[i][0]==ingredient[0] {
                let used=min(needed,next[i][1]);next[i][1]-=used;needed-=used
                if next[i][1]==0 {next[i][0]=0}
            }
            guard needed==0 else {throw GameError.message("Missing ingredients for \(recipe.name).")}
        }
        var remaining=recipe.count
        for i in next.indices where next[i][0]==recipe.output && next[i][1]<64 {
            let added=min(remaining,64-next[i][1]);next[i][1]+=added;remaining-=added
        }
        for i in next.indices where next[i][1]==0 && remaining>0 {
            let added=min(remaining,64);next[i]=[recipe.output,added];remaining-=added
        }
        guard remaining==0 else {throw GameError.message("Inventory full. Free space before crafting.")}
        snapshot.inventory=next;revision+=1
    }
    func savePayload()->NativeSavePayload {
        var save=snapshot;save.pose=[position.x,position.y,position.z,yaw,pitch]
        return NativeSavePayload(snapshot:save,types:types,colors:colors)
    }
    func savedSnapshot() -> Snapshot {savePayload().value()}
    func itemName(_ type:Int)->String {materials[type]?.name ?? Self.name(type)}
    static func color(_ type:Int)->Int {
        switch type {case 1:return 0x4da636;case 2:return 0x7a4d2b;case 3:return 0x8c949e;case 4:return 0xe0c47a;case 5:return 0xe8f5ff;case 6:return 0x73471f;case 7:return 0x29732e;case 165:return 0xb8824a;case 166:return 0x9c4536;case 167:return 0xa8dee8;case 185:return 0x266ead;case 187:return 0x55575a;default:return 0x9966b3}
    }
    static func name(_ type:Int)->String {
        switch type {case 0:return "Empty";case 1:return "Grass";case 2:return "Dirt";case 3:return "Stone";case 4:return "Sand";case 5:return "Snow";case 6:return "Wood";case 7:return "Leaves";case 165:return "Planks";case 166:return "Bricks";case 167:return "Glass";default:return "Block \(type)"}
    }
}

/// Value-only capture. Dictionary storage is retained with Swift copy-on-write.
struct NativeSavePayload:Sendable {
    var snapshot:Snapshot;var types:[GridKey:Int];var colors:[GridKey:Int]
    func value()->Snapshot {
        var save=snapshot
        save.cells=types.map{[$0.key.x,$0.key.y,$0.key.z,$0.value,colors[$0.key] ?? VoxelWorld.color($0.value)]}
        save.details=snapshot.details?.filter{$0.cell.count==3 && types[GridKey($0.cell[0],$0.cell[1],$0.cell[2])]==255}
        return save
    }
    func persist(to url:URL)throws {
        let save=value();try save.validate()
        let data=try JSONEncoder().encode(save)
        guard data.count<=OfflineSave.maximumBytes else {throw GameError.invalidWorld}
        try FileManager.default.createDirectory(at:url.deletingLastPathComponent(),withIntermediateDirectories:true)
        try data.write(to:url,options:.atomic)
    }
}
enum NativeSaveResult:Equatable,Sendable {case saved,superseded,failed}
/// At most one executing payload and one latest pending payload. No mutable world crosses queues.
@MainActor final class NativeSaveWriter {
    private let queue=DispatchQueue(label:"dev.jayms.voxelone.offline-save",qos:.utility)
    private let destination:URL
    private let write:@Sendable (NativeSavePayload,URL)throws->Void
    private var pending:(NativeSavePayload,((NativeSaveResult)->Void)?,Bool)?
    private(set) var isIdle=true
    var onIdle:(()->Void)?
    init(destination:URL,write:@escaping @Sendable (NativeSavePayload,URL)throws->Void={try $0.persist(to:$1)}) {
        self.destination=destination;self.write=write
    }
    func submit(_ payload:NativeSavePayload,important:Bool=false,completion:((NativeSaveResult)->Void)?=nil) {
        if !isIdle {
            // An autosave must retain a pending user action's acknowledgement.
            let keep=pending?.2==true && !important
            let callback=keep ? pending?.1:completion
            let displaced=keep ? nil:pending?.1
            pending=(payload,callback,keep || important)
            // Publish the replacement first: a callback may submit an even newer save.
            displaced?(.superseded);return
        }
        begin(payload,completion)
    }
    private func begin(_ payload:NativeSavePayload,_ completion:((NativeSaveResult)->Void)?) {
        isIdle=false;let write=self.write,url=destination
        queue.async {
            let outcome:NativeSaveResult
            do {try write(payload,url);outcome = .saved}catch{outcome = .failed}
            DispatchQueue.main.async {
                self.isIdle=true
                if let next=self.pending {self.pending=nil;self.begin(next.0,next.1)}
                completion?(outcome)
                if self.isIdle {self.onIdle?()}
            }
        }
    }
}
enum NativeLoadResult:Sendable {case loaded(Snapshot),failed,cancelled}
/// Single executing load; cancellation fences its result without queuing another worker.
@MainActor final class NativeOfflineLoader {
    private let queue=DispatchQueue(label:"dev.jayms.voxelone.offline-load",qos:.utility)
    private var generation:UInt64=0
    private(set) var isBusy=false
    var onIdle:(()->Void)?
    func cancel(){generation &+= 1}
    @discardableResult func load(operation:@escaping @Sendable ()throws->Snapshot,completion:@escaping (NativeLoadResult)->Void)->Bool {
        guard !isBusy else{return false};isBusy=true;let request=generation
        queue.async {
            let result:NativeLoadResult
            do {result = .loaded(try operation())}catch{result = .failed}
            DispatchQueue.main.async {
                self.isBusy=false
                completion(request==self.generation ? result:.cancelled)
                if !self.isBusy {self.onIdle?()}
            }
        }
        return true
    }
}
final class OfflineSave {
    static let maximumBytes=16_000_000
    @MainActor static let loader=NativeOfflineLoader()
    @MainActor static let writer=NativeSaveWriter(destination:url)
    static var url: URL { FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("sandbox-v1.json") }
    static func read(at url:URL)throws->Snapshot {
        let handle=try FileHandle(forReadingFrom:url);defer{try? handle.close()}
        var data=Data()
        while data.count<=maximumBytes {
            let part=try handle.read(upToCount:min(65536,maximumBytes+1-data.count)) ?? Data()
            if part.isEmpty {break};data.append(part)
        }
        guard data.count<=maximumBytes else {throw GameError.invalidWorld}
        let value=try JSONDecoder().decode(Snapshot.self,from:data);try value.validate();return value
    }
    static func migrate(_ value:Snapshot,contract:NativeContract)throws->Snapshot {
        try value.validate();var result=value
        if result.schema==1 {result.schema=2;result.contract=contract;result.details=[]}
        try result.validate();return result
    }
    static func load() throws -> Snapshot {
        let value=try read(at:url)
        if value.schema==1 {return try migrate(value,contract:NativeContract.bundled())}
        return value
    }
    static func fresh() throws -> Snapshot {
        guard let url=Bundle.main.url(forResource:"sandbox",withExtension:"json") else {throw GameError.invalidWorld}
        var value=try read(at:url);value.schema=2;value.contract=try NativeContract.bundled();value.details=[]
        value.inventory[5]=[8,2];value.inventory[6]=[141,8]
        try value.validate();return value
    }
}
