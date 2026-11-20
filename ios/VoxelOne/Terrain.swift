import Foundation

/// Native implementation of the versioned Java Terrain/Geography contract.
/// Instances belong to one generation worker. Caches never affect sample values.
enum NativeTerrainError:Error {case unsupportedVersion,invalidWindow}
final class NativeTerrain {
    static let currentVersion=3
    private struct Key:Hashable {var x:Int;var z:Int}
    private struct Column {var height:Int;var biome:Int}
    private struct Fields {var height:Int;var water:Int;var temperature:Double;var moisture:Double;var fertility:Double;var geology:Double;var rock:Bool;var settlement:Bool}
    private struct Node {var x:Double;var z:Double;var level:Double}
    private struct Reach {var a:Node;var b:Node;var width:Double}
    private let seed:UInt64
    let version:Int
    private var reaches:[Reach]=[]
    private var columns:[Key:Column]=[:]
    private var fieldCache:[Key:Fields]=[:]
    var cachedColumnCount:Int {columns.count}
    var cachedFieldCount:Int {fieldCache.count}
    init(seed:Int64,version:Int)throws {
        guard (1...Self.currentVersion).contains(version) else {throw NativeTerrainError.unsupportedVersion}
        self.seed=UInt64(bitPattern:seed);self.version=version
        if version>=2 {
            var previous:Node?
            for z in stride(from:-384,through:448,by:64) {
                let node=Node(x:64+(geoNoise(0,Double(z)/140,23)-0.5)*40,z:Double(z),level:24-Double(z+384)/83.2)
                if let previous=previous {reaches.append(Reach(a:previous,b:node,width:9))}
                if z == -128 || z == 128 {for side in [-1.0,1.0] {
                    let source=Node(x:node.x+side*300,z:Double(z-180),level:node.level+8)
                    let bend=Node(x:node.x+side*140,z:Double(z-64),level:node.level+3)
                    reaches.append(Reach(a:source,b:bend,width:7));reaches.append(Reach(a:bend,b:node,width:7))
                }}
                previous=node
            }
        }
    }
    private func value(_ x:Int,_ z:Int,_ salt:Int)->Double {
        var n=seed ^ (UInt64(bitPattern:Int64(x)) &* 341873128712) ^ (UInt64(bitPattern:Int64(z)) &* 132897987541) ^ UInt64(bitPattern:Int64(salt))
        n=(n ^ (n>>30)) &* 0xbf58476d1ce4e5b9
        n=(n ^ (n>>27)) &* 0x94d049bb133111eb
        return Double((n ^ (n>>31))>>11)/9007199254740992.0
    }
    private func noise(_ x:Double,_ z:Double,_ salt:Int)->Double {
        let ix=Int(floor(x)),iz=Int(floor(z));var a=x-Double(ix),b=z-Double(iz)
        a=a*a*(3-2*a);b=b*b*(3-2*b)
        let lo=value(ix,iz,salt)*(1-a)+value(ix+1,iz,salt)*a
        let hi=value(ix,iz+1,salt)*(1-a)+value(ix+1,iz+1,salt)*a
        return lo*(1-b)+hi*b
    }
    private func geoNoise(_ x:Double,_ z:Double,_ salt:Int)->Double {
        let ix=Int(floor(x)),iz=Int(floor(z)),a=curve(x-Double(ix)),b=curve(z-Double(iz))
        return mix(mix(value(ix,iz,salt),value(ix+1,iz,salt),a),mix(value(ix,iz+1,salt),value(ix+1,iz+1,salt),a),b)
    }
    private func clamp(_ v:Double)->Double {max(0,min(1,v))}
    private func curve(_ v:Double)->Double {let v=clamp(v);return v*v*(3-2*v)}
    private func mix(_ a:Double,_ b:Double,_ t:Double)->Double {a+(b-a)*t}
    private func valley(_ x:Double,_ z:Double)->Double {
        let hill=exp(-pow((abs(x)-210)/85,2)),pass=1-0.85*exp(-pow((z-160)/45,2))
        return 26+(geoNoise(x/180,z/180,71)-0.5)*4+hill*pass*39
    }
    private func broad(_ x:Double,_ z:Double)->Double {
        let continent=geoNoise(x/950,z/950,31),ridge=1-abs(2*geoNoise(x/290,z/290,43)-1)
        let mountains=curve((ridge-0.52)/0.48)
        return 12+curve((continent-0.22)/0.58)*15+mountains*38
    }
    private func shore(_ x:Int)->Double {225+(geoNoise(Double(x)/180,0,827)-0.5)*24}
    private func fields(_ x:Int,_ z:Int)->Fields {
        let key=Key(x:x,z:z);if let cached=fieldCache[key] {return cached}
        let dx=Double(x),dz=Double(z)
        let pilot=1-curve((max(abs(dx),abs(dz))-400)/180)
        let hill=exp(-pow((abs(dx)-210)/85,2))
        var h=mix(broad(dx,dz),valley(dx,dz),pilot)
        let slopeX=mix(broad(dx+2,dz)-broad(dx-2,dz),valley(dx+2,dz)-valley(dx-2,dz),pilot)/4
        let slopeZ=mix(broad(dx,dz+2)-broad(dx,dz-2),valley(dx,dz+2)-valley(dx,dz-2),pilot)/4
        let slope=hypot(slopeX,slopeZ)
        h += curve((slope-0.12)/0.5)*(geoNoise(dx/13,dz/13,73)-0.5)*5
        let coast=curve((dz-350)/70)*pilot;h=mix(h,9,coast)
        var distance=Double.infinity,level=14.0,width=5.0
        for reach in reaches {
            let a=reach.a,b=reach.b,rx=b.x-a.x,rz=b.z-a.z
            let t=clamp(((dx-a.x)*rx+(dz-a.z)*rz)/(rx*rx+rz*rz))
            let d=hypot(dx-mix(a.x,b.x,t),dz-mix(a.z,b.z,t))
            if d<distance {distance=d;level=mix(a.level,b.level,t);width=reach.width}
        }
        let floodplain=(1-curve((distance-width)/28))*pilot
        h=mix(h,min(h,level+2),floodplain)
        let channel=distance<=width && pilot>0
        if channel {let bed=level-3+5*curve(distance/width);h=mix(h,min(h,bed),pilot)}
        var terrace=1-curve((max(abs(dx-8),abs(dz-24))-24)/24)
        terrace *= curve((distance-width)/3);h=mix(h,26,terrace)
        let settlement=terrace==1,quarry=hypot(dx-132,dz+72)<18
        let geology=clamp(geoNoise(dx/110,dz/110,509)*0.8+hill*0.3+(quarry ? 0.5:0))
        let temperature=clamp(geoNoise(dx/420,dz/420,91)*0.9+0.2-max(0,h-40)/90)
        var moisture=clamp(geoNoise(dx/270,dz/270,173)*0.8+floodplain*0.35+hill*0.15)
        var fertility=clamp(0.25+moisture*0.65+floodplain*0.25-hill*0.5)
        if settlement {moisture=0.48;fertility=0.85}
        if version>=3 {
            let marine=curve((dz-shore(x)+32)/64)
            h=mix(h,-6+geoNoise(dx/100,dz/100,829)*7,marine)
            if marine>0 && h<14 {moisture=max(0.65,moisture);fertility *= 1-marine}
        }
        var water = -32
        if channel && terrace==0 {water=Int(floor(level))}
        if coast>0.5 && h<14 {water=max(water,14)}
        if version>=3 && dz>shore(x)-32 && h<14 {water=14}
        let result=Fields(height:Int(floor(max(-28,min(88,h)))),water:water,temperature:temperature,moisture:moisture,fertility:fertility,geology:geology,rock:quarry || slope>0.55 && h>40,settlement:settlement)
        if fieldCache.count>=8192 {fieldCache.removeAll(keepingCapacity:true)}
        fieldCache[key]=result;return result
    }
    private func column(_ x:Int,_ z:Int)->Column {
        let key=Key(x:x,z:z);if let cached=columns[key] {return cached}
        let result:Column
        if version>=2 {
            let f=fields(x,z)
            result=Column(height:f.height,biome:f.temperature<0.28 ? 3:f.moisture<0.28 ? 2:f.moisture>0.53 && f.fertility>0.3 ? 1:0)
        } else {
            let dx=Double(x),dz=Double(z),climate=noise(dx/180,dz/180,91),wet=noise(dx/150,dz/150,173)
            let biome=climate<0.30 ? 3:climate>0.66 ? 2:wet>0.51 ? 1:0
            var h=13+noise(dx/95,dz/95,31)*18+noise(dx/32,dz/32,7)*6+noise(dx/12,dz/12,11)*2
            h += max(0,(0.36-climate)/0.36)*32
            result=Column(height:Int(floor(h)),biome:biome)
        }
        if columns.count>=8192 {columns.removeAll(keepingCapacity:true)}
        columns[key]=result;return result
    }
    private func inDomain(_ x:Int,_ z:Int)->Bool {abs(Double(x))<=1_000_032 && abs(Double(z))<=1_000_032}
    func surfaceHeight(_ x:Int,_ z:Int)->Int {guard inDomain(x,z) else{return -32};let h=column(x,z).height;return version>=2 ? max(h,fields(x,z).water):h}
    func ocean(_ x:Int,_ z:Int)->Bool {inDomain(x,z) && version>=3 && Double(z)>=shore(x) && fields(x,z).height<14}
    private func floorDiv(_ n:Int,_ d:Int)->Int {let q=n/d;return n%d<0 ? q-1:q}
    func block(_ x:Int,_ y:Int,_ z:Int)->Int {
        guard inDomain(x,z),(-32...95).contains(y) else{return 0}
        let c=column(x,z),h=c.height
        if y<=h {
            if y<h-5 && y > -30 && noise(Double(x)/18+Double(y)*0.19,Double(z)/18-Double(y)*0.17,401)>0.82 && noise(Double(x)/30,Double(z)/30+Double(y)*0.11,509)>0.60 {return 0}
            if version>=2 && fields(x,z).rock && y>=h-3 {return 3}
            if version>=2 && y<h-4 && y > -30 && fields(x,z).geology>0.58 && value(floorDiv(x,5),floorDiv(z,5),719+floorDiv(y,4))>0.78 {return 186}
            if y==h {return c.biome==2 ? 4:c.biome==3 ? 5:1}
            if y>=h-3 {return c.biome==2 ? 4:2};return 3
        }
        if version>=2 && y<=fields(x,z).water {return 185}
        if y>h+9 {return 0}
        let gx=floorDiv(x,12),gz=floorDiv(z,12)
        for a in (gx-1)...(gx+1) {for b in (gz-1)...(gz+1) {
            let tx=a*12+2+Int(value(a,b,211)*8),tz=b*12+2+Int(value(a,b,277)*8)
            if abs(tx-x)>2 || abs(tz-z)>2 {continue}
            let tree=column(tx,tz)
            if version>=2 && (fields(tx,tz).water>=tree.height || fields(tx,tz).settlement || fields(tx,tz).rock) {continue}
            if tree.biome != 1 || value(a,b,307)>0.78 {continue}
            let base=tree.height+1,top=base+4+Int(value(a,b,331)*2)
            if x==tx && z==tz && y>=base && y<top {return 6}
            if y>=top-2 && y<=top+1 && abs(tx-x)+abs(tz-z)+(y==top+1 ? 1:0)<=3 {return 7}
        }}
        return 0
    }
    func window(cx:Int,cz:Int,eyeY:Int,radius:Int,colors:[Int:Int])throws->(bounds:[Int],cells:[[Int]]) {
        guard (8...24).contains(radius),abs(Double(cx))<=Double(1_000_000-radius),abs(Double(cz))<=Double(1_000_000-radius),(-32...127).contains(eyeY) else {throw NativeTerrainError.invalidWindow}
        let low=max(-32,min(87,eyeY-8)),high=min(95,eyeY+36)
        var cells:[[Int]]=[]
        for x in (cx-radius)...(cx+radius) {for z in (cz-radius)...(cz+radius) {for y in low...high {
            let type=block(x,y,z)
            if type != 0 {guard let color=colors[type] else {throw NativeTerrainError.invalidWindow};cells.append([x,y,z,type,color])}
        }}}
        return ([cx-radius,cx+radius,low,high,cz-radius,cz+radius],cells)
    }
}
