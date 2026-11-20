import UIKit
import SceneKit
import simd

/// Native SceneKit/Metal rendering. Meshes contain exposed faces, grouped in 8-block chunks.
@MainActor final class VoxelRenderer {
    let view=SCNView(); let scene=SCNScene(); let camera=SCNNode(); let terrain=SCNNode();let people=SCNNode();let markers=SCNNode();let zones=SCNNode()
    private(set) var graphics=NativeGraphics.load()
    var sandboxCamera=NativeCameraMode(rawValue:UserDefaults.standard.string(forKey:"sandboxCameraMode") ?? "") ?? .first
    private let localPlayer=SCNNode()
    var planning=false;var focus=SCNVector3Zero;var distance:Float=38
    /// Local opt-in. Missing preference remains disabled until a device budget exists.
    var atmospherePreview:Bool {
        get {UserDefaults.standard.bool(forKey:"planetAtmospherePreview")}
        set {UserDefaults.standard.set(newValue,forKey:"planetAtmospherePreview")}
    }
    private var atmosphereKey:String?
    private let sunNode=SCNNode(),sunDisc=SCNNode(),ambientNode=SCNNode()
    private var skyDiffuse=SIMD3<Double>.zero
    private var renderChunks:[GridKey:[SCNNode]]=[:]
    private let meshWorker=NativeMeshWorker()
    private var meshGeneration:UInt64=0
    private var meshWorld:ObjectIdentifier?
    private var meshRevision = -1
    private var meshOutdoor=false
    private var distantNodes:[SCNNode]=[]
    private var distantBounds:[Int]=[]
    private var distantRadius=0
    private var distantComplete=false
    private var skyMaterials:[(SCNMaterial,Int,Double,Bool)]=[]
    init() {
        let body=SCNCapsule(capRadius:0.28,height:1.8);body.firstMaterial?.diffuse.contents=UIColor.systemCyan
        localPlayer.geometry=body;scene.rootNode.addChildNode(localPlayer)
        view.scene=scene;view.pointOfView=camera;view.isPlaying=true;view.preferredFramesPerSecond=30
        view.backgroundColor=UIColor(red:0.48,green:0.73,blue:0.9,alpha:1);view.antialiasingMode = .multisampling2X
        view.accessibilityIdentifier="worldView";view.isAccessibilityElement=true;view.accessibilityLabel="Voxel world. Drag to look; two fingers pan the planning view."
        camera.camera=SCNCamera();camera.camera?.zNear=0.05;camera.camera?.zFar=180;camera.camera?.fieldOfView=72
        scene.rootNode.addChildNode(camera);scene.rootNode.addChildNode(terrain);scene.rootNode.addChildNode(people);scene.rootNode.addChildNode(markers);scene.rootNode.addChildNode(zones)
        let ambient=ambientNode;ambient.light=SCNLight();ambient.light?.type = .ambient;ambient.light?.intensity=650;scene.rootNode.addChildNode(ambient)
        let sun=sunNode;sun.light=SCNLight();sun.light?.type = .directional;sun.light?.intensity=950;sun.eulerAngles=SCNVector3(-0.7,-0.6,0);scene.rootNode.addChildNode(sun)
        sunDisc.isHidden=true;sunDisc.castsShadow=false;sunDisc.categoryBitMask=2;scene.rootNode.addChildNode(sunDisc)
        scene.fogStartDistance=65;scene.fogEndDistance=110;scene.fogColor=view.backgroundColor
        applyGraphics(graphics)
    }
    func applyGraphics(_ requested:NativeGraphics) {
        graphics=requested.validated();let p=graphics.effective()
        view.preferredFramesPerSecond=p.frames
        view.antialiasingMode=p.samples==4 ? .multisampling4X:p.samples==2 ? .multisampling2X:.none
        camera.camera?.zFar=Double(p.detail);camera.camera?.wantsHDR=p.bloom
        camera.camera?.bloomIntensity=p.bloom ? 0.35:0
        atmospherePreview=p.atmosphere
        if !p.atmosphere {scene.background.contents=nil;scene.lightingEnvironment.contents=nil;scene.fogStartDistance=65;scene.fogEndDistance=110;scene.fogColor=view.backgroundColor;sunNode.light?.color=UIColor.white;sunNode.light?.intensity=950;sunNode.eulerAngles=SCNVector3(-0.7,-0.6,0);sunDisc.isHidden=true;sunNode.light?.castsShadow=false;skyDiffuse = .zero}
        atmosphereKey=nil
    }
    func cancelMeshing(){meshWorker.cancel();meshRevision = -1;view.accessibilityValue="Terrain paused"}
    func trimMeshes(around world:VoxelWorld){
        cancelMeshing();clearDistant()
        for key in Array(renderChunks.keys) {
            let x=Float(key.x*8+4)-world.position.x,y=Float(key.y*8+4)-world.position.y,z=Float(key.z*8+4)-world.position.z
            if x*x+y*y+z*z>16*16 {dropChunk(key)}
        }
    }
    private func dropChunk(_ key:GridKey){
        let nodes=renderChunks.removeValue(forKey:key) ?? []
        let ids=Set(nodes.compactMap{$0.geometry?.firstMaterial}.map{ObjectIdentifier($0)})
        skyMaterials.removeAll{ids.contains(ObjectIdentifier($0.0))}
        nodes.forEach{$0.removeFromParentNode()}
    }
    /// SceneKit objects are created only here on the main actor, at most two small pages per frame.
    func advanceMeshing(){
        for result in meshWorker.drain() where result.generation==meshGeneration {
            switch result.event {
            case .index(let keys):for key in Array(renderChunks.keys) where !keys.contains(key) {dropChunk(key)}
            case .page(let chunk,let first,let page):
                if first {dropChunk(chunk)}
                guard let page=page else{continue}
                let node=meshNode(page,outdoor:meshOutdoor);terrain.addChildNode(node);renderChunks[chunk,default:[]].append(node)
            case .distant(let first,let page):
                if first {clearDistant()}
                if let page=page {
                    let node=meshNode(page,outdoor:false);node.name="distant-terrain-preview";node.castsShadow=false
                    terrain.addChildNode(node);distantNodes.append(node)
                    view.accessibilityLabel="Voxel world. Drag to look; distant landscape prepared."
                }
            case .finished:distantComplete = !distantNodes.isEmpty;view.accessibilityValue="Terrain ready"
            case .failed:view.accessibilityValue="Terrain failed"
            }
        }
    }
    private func clearDistant(){
        distantNodes.forEach{$0.removeFromParentNode()};distantNodes.removeAll();distantComplete=false
        view.accessibilityLabel="Voxel world. Drag to look; two fingers pan the planning view."
    }
    private func meshNode(_ page:NativeMeshBuffer,outdoor:Bool)->SCNNode {
        func source(_ values:[Float],_ semantic:SCNGeometrySource.Semantic,_ count:Int)->SCNGeometrySource {
            let data=values.withUnsafeBytes{Data($0)}
            return SCNGeometrySource(data:data,semantic:semantic,vectorCount:values.count/count,usesFloatComponents:true,componentsPerVector:count,bytesPerComponent:4,dataOffset:0,dataStride:count*4)
        }
        let indexData=page.indices.withUnsafeBytes{Data($0)}
        let element=SCNGeometryElement(data:indexData,primitiveType:.triangles,primitiveCount:page.indices.count/3,bytesPerIndex:4)
        let geometry=SCNGeometry(sources:[source(page.positions,.vertex,3),source(page.normals,.normal,3),source(page.colors,.color,4)],elements:[element])
        let material=SCNMaterial();material.diffuse.contents=UIColor.white;material.lightingModel = .lambert;material.isDoubleSided=false
        if outdoor || page.key.emitter {
            skyMaterials.append((material,page.key.color,Double(page.key.sky)/8,page.key.emitter))
            setSkyEmission(material,page.key.color,Double(page.key.sky)/8,page.key.emitter)
        }
        geometry.materials=[material];return SCNNode(geometry:geometry)
    }
    func rebuild(_ world:VoxelWorld) {
        let identity=ObjectIdentifier(world),outdoor=atmospherePreview
        let radius=world.streamsTerrain ? min(NativeDistantMesher.maximumRadius,graphics.detail):0
        if meshWorld != identity || distantBounds != world.snapshot.bounds || distantRadius != radius {clearDistant()}
        distantBounds=world.snapshot.bounds
        if meshWorld != identity || meshRevision != world.geometryRevision || meshOutdoor != outdoor || distantRadius != radius {
            if meshWorld != identity {for key in Array(renderChunks.keys) {dropChunk(key)}}
            meshWorld=identity;meshRevision=world.geometryRevision;meshOutdoor=outdoor;distantRadius=radius
            let p=planning ? focus:world.position
            meshGeneration=meshWorker.submit(NativeMeshRequest(payload:world.savePayload(),atmosphere:outdoor,center:[p.x,p.y,p.z],distantRadius:radius,reuseDistant:distantComplete))
            view.accessibilityValue="Terrain loading"
        }
        zones.childNodes.forEach{$0.removeFromParentNode()}
        for zone in world.snapshot.city?.zonePolygons ?? [] {
            guard zone.points.count>=3,zone.points.count<=32,zone.points.allSatisfy({$0.count==2 && $0.allSatisfy{$0.isFinite}}) else{continue}
            let grade=Float(world.snapshot.city?.roads.first?[1] ?? Int(world.position.y)-1)+1.06
            let vertices=zone.points.map{SCNVector3($0[0],grade,$0[1])}
            var indices:[UInt32]=[];for i in 1..<(vertices.count-1){indices += [0,UInt32(i),UInt32(i+1)]}
            let data=indices.withUnsafeBytes{Data($0)}
            let geometry=SCNGeometry(sources:[SCNGeometrySource(vertices:vertices)],elements:[SCNGeometryElement(data:data,primitiveType:.triangles,primitiveCount:indices.count/3,bytesPerIndex:4)])
            let material=SCNMaterial();material.diffuse.contents=[UIColor.systemGreen,.systemBlue,.systemOrange,.systemYellow][min(3,max(0,zone.type))];material.isDoubleSided=true;material.lightingModel = .constant;material.writesToDepthBuffer=false;geometry.materials=[material]
            let node=SCNNode(geometry:geometry);node.opacity=0.24;zones.addChildNode(node)
        }
        updatePeople(world.snapshot)
    }
    func updatePeople(_ snapshot:Snapshot) {
        people.childNodes.forEach{$0.removeFromParentNode()}
        func avatar(x:Float,y:Float,z:Float,color:UIColor,name:String) {
            let body=SCNNode(geometry:SCNCapsule(capRadius:0.23,height:1.35));body.geometry?.firstMaterial?.diffuse.contents=color
            body.position=SCNVector3(x,y+0.8,z);body.name=name;people.addChildNode(body)
            let head=SCNNode(geometry:SCNSphere(radius:0.23));head.geometry?.firstMaterial?.diffuse.contents=UIColor(red:0.88,green:0.7,blue:0.52,alpha:1);head.position=SCNVector3(x,y+1.6,z);people.addChildNode(head)
        }
        for c in snapshot.city?.citizens ?? [] {avatar(x:c.x,y:c.y,z:c.z,color:.systemOrange,name:c.name)}
        for p in snapshot.players ?? [] {avatar(x:p.x,y:p.y,z:p.z,color:.systemCyan,name:p.name)}
        for d in snapshot.drops ?? [] {let block=SCNNode(geometry:SCNBox(width:0.2,height:0.2,length:0.2,chamferRadius:0));block.position=SCNVector3(d.x,d.y,d.z);block.geometry?.firstMaterial?.diffuse.contents=UIColor.systemYellow;people.addChildNode(block)}
    }
    func updateCamera(_ world:VoxelWorld) {
        zones.isHidden = !planning
        let mode=world.snapshot.city==nil ? sandboxCamera:NativeCameraMode.first
        localPlayer.isHidden=planning || mode == .first
        localPlayer.position=SCNVector3(world.position.x,world.position.y+0.9,world.position.z)
        if planning || mode == .isometric {
            camera.camera?.usesOrthographicProjection=true;camera.camera?.orthographicScale=Double(distance)
            camera.position=SCNVector3(focus.x+distance,focus.y+distance,focus.z+distance);camera.look(at:focus)
        } else if mode == .third {
            camera.camera?.usesOrthographicProjection=false
            let eye=world.eye,d=world.direction
            camera.position=world.cameraPosition(toward:SCNVector3(eye.x-d.x*4,eye.y-d.y*4+0.8,eye.z-d.z*4))
            camera.look(at:SCNVector3(eye.x+d.x*3,eye.y+d.y*3,eye.z+d.z*3))
        } else {
            camera.camera?.usesOrthographicProjection=false;camera.position=world.eye;camera.eulerAngles=SCNVector3(world.pitch,world.yaw,0)
        }
        if mode == .third && !planning {
            let dx=camera.position.x-world.eye.x,dy=camera.position.y-world.eye.y,dz=camera.position.z-world.eye.z
            localPlayer.isHidden=dx*dx+dy*dy+dz*dz<1
        }
        let detail=Float(graphics.detail)
        let eye=SIMD3<Float>(camera.position.x,camera.position.y,camera.position.z)
        for (chunk,nodes) in renderChunks {
            let center=SIMD3<Float>(Float(chunk.x*8+4),Float(chunk.y*8+4),Float(chunk.z*8+4))
            let hidden=simd_distance(center,eye)>detail+14
            for node in nodes {node.isHidden=hidden}
        }
        updateAtmosphere(world.snapshot)
    }
    private func setSkyEmission(_ material:SCNMaterial,_ colour:Int,_ visibility:Double,_ emitter:Bool){
        let albedo=SIMD3(Double((colour>>16)&255)/255,Double((colour>>8)&255)/255,Double(colour&255)/255)
        let value=albedo*(skyDiffuse*visibility+SIMD3<Double>(repeating:emitter ? 4:0))
        material.emission.contents=UIColor(cgColor:CGColor(colorSpace:CGColorSpace(name:CGColorSpace.extendedLinearSRGB)!,components:[CGFloat(value.x),CGFloat(value.y),CGFloat(value.z),1])!)
    }
    private func updateAtmosphere(_ snapshot:Snapshot) {
        guard atmospherePreview else {
            skyDiffuse = .zero;ambientNode.light?.intensity=650;scene.lightingEnvironment.intensity=1
            sunDisc.isHidden=true;sunNode.light?.castsShadow=false
            if atmosphereKey != nil {scene.background.contents=nil;sunNode.light?.color=UIColor.white;scene.lightingEnvironment.contents=nil;scene.fogStartDistance=65;scene.fogEndDistance=110;scene.fogColor=view.backgroundColor;sunNode.light?.intensity=950;sunNode.eulerAngles=SCNVector3(-0.7,-0.6,0);atmosphereKey=nil}
            return
        }
        ambientNode.light?.intensity=0;scene.lightingEnvironment.intensity=0
        let profile=snapshot.atmosphere ?? .earth
        let value=snapshot.sun ?? [0.45,0.78,-0.45],sun=simd_normalize(SIMD3<Double>(value[0],value[1],value[2]))
        sunDisc.position=SCNVector3(camera.position.x+Float(sun.x*80),camera.position.y+Float(sun.y*80),camera.position.z+Float(sun.z*80))
        let altitude=simd_length(profile.position(camera.position))-profile.values[0]
        // Quantized view updates bound CPU work on the low-quality native path.
        let key="\(profile)|\(Int(altitude/100))|\(Int(sun.x*100))|\(Int(sun.y*100))|\(Int(sun.z*100))"
        guard key != atmosphereKey else{return};atmosphereKey=key
        let cube=NativeAtmosphere.cube(profile,camera.position,sun);scene.background.contents=cube;scene.lightingEnvironment.contents=cube
        var p=profile.position(camera.position);if simd_length(p)<profile.values[0]+1 {p=simd_normalize(p)*(profile.values[0]+1)}
        let planetSun=simd_normalize(profile.ray(sun));skyDiffuse = .zero
        for direction in [SIMD3<Double>(-1,1,-1),SIMD3<Double>(1,1,-1),SIMD3<Double>(-1,1,1),SIMD3<Double>(1,1,1)] {
            skyDiffuse += NativeAtmosphere.radiance(p,simd_normalize(profile.ray(direction)),planetSun,profile,includeSolarDisc:false)/4
        }
        for (material,colour,visible,emitter) in skyMaterials {setSkyEmission(material,colour,visible,emitter)}
        let irradiance=NativeAtmosphere.solarIrradiance(profile,camera.position,sun),peak=max(irradiance.x,max(irradiance.y,irradiance.z))
        let rgb=peak>0 ? irradiance/peak:SIMD3<Double>.zero
        let colour=UIColor(cgColor:CGColor(colorSpace:CGColorSpace(name:CGColorSpace.linearSRGB)!,components:[CGFloat(rgb.x),CGFloat(rgb.y),CGFloat(rgb.z),1])!)
        sunNode.light?.color=colour;sunNode.light?.castsShadow=true;sunNode.light?.shadowMapSize=CGSize(width:512,height:512)
        sunNode.light?.intensity=950*peak/18;
        let sphere=SCNSphere(radius:CGFloat(80*tan(profile.values[33])));sphere.segmentCount=12
        let material=SCNMaterial();material.lightingModel = .constant
        let disc=irradiance/(2*Double.pi*(1-cos(profile.values[33])))
        material.diffuse.contents=UIColor(red:CGFloat(disc.x/(1+disc.x)),green:CGFloat(disc.y/(1+disc.y)),blue:CGFloat(disc.z/(1+disc.z)),alpha:1);material.writesToDepthBuffer=false;material.readsFromDepthBuffer=true;sphere.materials=[material]
        sunDisc.geometry=sphere;sunDisc.isHidden=peak<=0
        sunNode.position=SCNVector3Zero;sunNode.look(at:SCNVector3(Float(-sun.x),Float(-sun.y),Float(-sun.z)))
        // SceneKit's low-quality single fog composition has no depth volume. Terrain, models,
        // water and glass use its one built-in fog stage; UIKit HUD remains clear.
        let density=NativeAtmosphere.extinction(NativeAtmosphere.density(max(0,altitude),profile),profile)
        let sigma=(density.x+density.y+density.z)/3*profile.values[2]
        if profile.enabled && sigma>0 {scene.fogStartDistance=0;scene.fogEndDistance=1/sigma;let l=NativeAtmosphere.radiance(p,simd_normalize(profile.ray(SIMD3(1,0,0))),planetSun,profile,includeSolarDisc:false);scene.fogColor=UIColor(red:CGFloat(l.x/(1+l.x)),green:CGFloat(l.y/(1+l.y)),blue:CGFloat(l.z/(1+l.z)),alpha:1)}
        else {scene.fogStartDistance=1e8;scene.fogEndDistance=1e9}
    }
    /// Pick from the displayed camera, then enforce player reach and line of sight.
    func target(at location:CGPoint,world:VoxelWorld)->VoxelHit? {
        let near=view.unprojectPoint(SCNVector3(Float(location.x),Float(location.y),0))
        let far=view.unprojectPoint(SCNVector3(Float(location.x),Float(location.y),1))
        let delta=SIMD3<Float>(far.x-near.x,far.y-near.y,far.z-near.z)
        guard simd_length(delta)>0 else{return nil};let d=simd_normalize(delta)
        guard let pick=world.raycast(origin:near,direction:SCNVector3(d.x,d.y,d.z),reach:180) else{return nil}
        let point=SCNVector3(near.x+d.x*(pick.distance+0.005),near.y+d.y*(pick.distance+0.005),near.z+d.z*(pick.distance+0.005))
        let eye=world.eye,aim=SIMD3<Float>(point.x-eye.x,point.y-eye.y,point.z-eye.z),length=simd_length(aim)
        guard length>0,length<=5 else{return nil};let direction=aim/length
        guard let seen=world.raycast(origin:eye,direction:SCNVector3(direction.x,direction.y,direction.z),reach:min(5,length+0.01)),seen.hit==pick.hit,abs(seen.distance-length)<0.03 else{return nil}
        return pick
    }
    func point(at location:CGPoint,world:VoxelWorld) -> SCNVector3? {
        // City planning must use the received world, including while chunk meshes install.
        let near=view.unprojectPoint(SCNVector3(Float(location.x),Float(location.y),0))
        let far=view.unprojectPoint(SCNVector3(Float(location.x),Float(location.y),1))
        let delta=SIMD3<Float>(far.x-near.x,far.y-near.y,far.z-near.z)
        let length=simd_length(delta)
        guard length.isFinite,length>0 else{return nil};let d=delta/length
        return world.raycast(origin:near,direction:SCNVector3(d.x,d.y,d.z),reach:180)?.point
    }
    func mark(_ points:[[Float]],world:VoxelWorld) {
        markers.childNodes.forEach{$0.removeFromParentNode()}
        for p in points {let node=SCNNode(geometry:SCNSphere(radius:0.6));node.geometry?.firstMaterial?.diffuse.contents=UIColor.systemYellow
            let x=Int(floor(p[0])),z=Int(floor(p[1]));let y=world.types.keys.filter{$0.x==x && $0.z==z}.map{$0.y}.max() ?? Int(focus.y)
            node.position=SCNVector3(p[0],Float(y)+1.5,p[1]);markers.addChildNode(node)
        }
    }
}
