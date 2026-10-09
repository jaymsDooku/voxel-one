import UIKit
import SceneKit
import simd

/// Native SceneKit/Metal rendering. Meshes contain exposed faces, grouped in 8-block chunks.
final class VoxelRenderer {
    let view=SCNView(); let scene=SCNScene(); let camera=SCNNode(); let terrain=SCNNode();let people=SCNNode();let markers=SCNNode();let zones=SCNNode()
    var planning=false;var focus=SCNVector3Zero;var distance:Float=38
    var atmospherePreview=false
    private var atmosphereKey:String?
    private let sunNode=SCNNode()
    private let faces:[(GridKey,SCNVector3,[SCNVector3])]=[
        (GridKey(1,0,0),SCNVector3(1,0,0),[SCNVector3(1,0,0),SCNVector3(1,1,0),SCNVector3(1,1,1),SCNVector3(1,0,1)]),
        (GridKey(-1,0,0),SCNVector3(-1,0,0),[SCNVector3(0,0,1),SCNVector3(0,1,1),SCNVector3(0,1,0),SCNVector3(0,0,0)]),
        (GridKey(0,1,0),SCNVector3(0,1,0),[SCNVector3(0,1,1),SCNVector3(1,1,1),SCNVector3(1,1,0),SCNVector3(0,1,0)]),
        (GridKey(0,-1,0),SCNVector3(0,-1,0),[SCNVector3(0,0,0),SCNVector3(1,0,0),SCNVector3(1,0,1),SCNVector3(0,0,1)]),
        (GridKey(0,0,1),SCNVector3(0,0,1),[SCNVector3(1,0,1),SCNVector3(1,1,1),SCNVector3(0,1,1),SCNVector3(0,0,1)]),
        (GridKey(0,0,-1),SCNVector3(0,0,-1),[SCNVector3(0,0,0),SCNVector3(0,1,0),SCNVector3(1,1,0),SCNVector3(1,0,0)])]
    init() {
        view.scene=scene;view.pointOfView=camera;view.isPlaying=true;view.preferredFramesPerSecond=30
        view.backgroundColor=UIColor(red:0.48,green:0.73,blue:0.9,alpha:1);view.antialiasingMode = .multisampling2X
        view.accessibilityIdentifier="worldView";view.isAccessibilityElement=true;view.accessibilityLabel="Voxel world. Drag to look; two fingers pan the planning view."
        camera.camera=SCNCamera();camera.camera?.zNear=0.05;camera.camera?.zFar=180;camera.camera?.fieldOfView=72
        scene.rootNode.addChildNode(camera);scene.rootNode.addChildNode(terrain);scene.rootNode.addChildNode(people);scene.rootNode.addChildNode(markers);scene.rootNode.addChildNode(zones)
        let ambient=SCNNode();ambient.light=SCNLight();ambient.light?.type = .ambient;ambient.light?.intensity=650;scene.rootNode.addChildNode(ambient)
        let sun=sunNode;sun.light=SCNLight();sun.light?.type = .directional;sun.light?.intensity=950;sun.eulerAngles=SCNVector3(-0.7,-0.6,0);scene.rootNode.addChildNode(sun)
        scene.fogStartDistance=65;scene.fogEndDistance=110;scene.fogColor=view.backgroundColor
    }
    private final class Mesh {var vertices:[SCNVector3]=[];var normals:[SCNVector3]=[];var colors:[Float]=[];var indices:[UInt32]=[]}
    func rebuild(_ world:VoxelWorld) {
        var groups:[GridKey:Mesh]=[:]
        for (key,type) in world.types {
            let chunk=GridKey(Int(floor(Double(key.x)/8)),Int(floor(Double(key.y)/8)),Int(floor(Double(key.z)/8)))
            let mesh=groups[chunk] ?? Mesh()
            let color=world.colors[key] ?? VoxelWorld.color(type)
            for (neighbor,normal,corners) in faces {
                let adjacent=world.type(key+neighbor)
                if adjacent != 0 && (adjacent != 185 || type==185) {continue}
                let base=UInt32(mesh.vertices.count)
                for p in corners {mesh.vertices.append(SCNVector3(Float(key.x)+p.x,Float(key.y)+p.y,Float(key.z)+p.z));mesh.normals.append(normal)
                    mesh.colors += [Float((color>>16)&255)/255,Float((color>>8)&255)/255,Float(color&255)/255,1]
                }
                mesh.indices += [base,base+1,base+2,base,base+2,base+3]
            }
            groups[chunk]=mesh
        }
        terrain.childNodes.forEach{$0.removeFromParentNode()}
        for mesh in groups.values where !mesh.indices.isEmpty {
            let vertex=SCNGeometrySource(vertices:mesh.vertices),normal=SCNGeometrySource(normals:mesh.normals)
            let colorData=mesh.colors.withUnsafeBytes{Data($0)}
            let color=SCNGeometrySource(data:colorData,semantic:.color,vectorCount:mesh.vertices.count,usesFloatComponents:true,componentsPerVector:4,bytesPerComponent:4,dataOffset:0,dataStride:16)
            let indices=mesh.indices.withUnsafeBytes{Data($0)}
            let element=SCNGeometryElement(data:indices,primitiveType:.triangles,primitiveCount:mesh.indices.count/3,bytesPerIndex:4)
            let geometry=SCNGeometry(sources:[vertex,normal,color],elements:[element])
            let material=SCNMaterial();material.diffuse.contents=UIColor.white;material.lightingModel = .lambert;material.isDoubleSided=false
            geometry.materials=[material];terrain.addChildNode(SCNNode(geometry:geometry))
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
        if planning {
            camera.camera?.usesOrthographicProjection=true;camera.camera?.orthographicScale=Double(distance)
            camera.position=SCNVector3(focus.x+distance,focus.y+distance,focus.z+distance);camera.look(at:focus)
        } else {
            camera.camera?.usesOrthographicProjection=false;camera.position=world.eye;camera.eulerAngles=SCNVector3(world.pitch,world.yaw,0)
        }
        updateAtmosphere(world.snapshot)
    }
    private func updateAtmosphere(_ snapshot:Snapshot) {
        guard atmospherePreview else {
            if atmosphereKey != nil {scene.background.contents=nil;scene.lightingEnvironment.contents=nil;scene.fogStartDistance=65;scene.fogEndDistance=110;scene.fogColor=view.backgroundColor;sunNode.light?.intensity=950;sunNode.eulerAngles=SCNVector3(-0.7,-0.6,0);atmosphereKey=nil}
            return
        }
        let profile=snapshot.atmosphere ?? .earth
        let value=snapshot.sun ?? [0.45,0.78,-0.45],sun=simd_normalize(SIMD3<Double>(value[0],value[1],value[2]))
        let altitude=simd_length(profile.position(camera.position))-profile.values[0]
        // Quantized view updates bound CPU work on the low-quality native path.
        let key="\(profile)|\(Int(altitude/100))|\(Int(sun.x*100))|\(Int(sun.y*100))|\(Int(sun.z*100))"
        guard key != atmosphereKey else{return};atmosphereKey=key
        let cube=NativeAtmosphere.cube(profile,camera.position,sun);scene.background.contents=cube;scene.lightingEnvironment.contents=cube
        let p=profile.position(camera.position),t=NativeAtmosphere.sunlight(p,sun,profile)
        sunNode.light?.intensity=950*max(0,min(1,(t.x+t.y+t.z)/3));sunNode.position=SCNVector3Zero;sunNode.look(at:SCNVector3(Float(-sun.x),Float(-sun.y),Float(-sun.z)))
        // SceneKit's low-quality single fog composition has no depth volume. Terrain, models,
        // water and glass use its one built-in fog stage; UIKit HUD remains clear.
        let density=NativeAtmosphere.extinction(NativeAtmosphere.density(max(0,altitude),profile),profile)
        let sigma=(density.x+density.y+density.z)/3*profile.values[2]
        if profile.enabled && sigma>0 {scene.fogStartDistance=0;scene.fogEndDistance=1/sigma;let l=NativeAtmosphere.radiance(p,SIMD3(1,0,0),sun,profile);scene.fogColor=UIColor(red:CGFloat(l.x/(1+l.x)),green:CGFloat(l.y/(1+l.y)),blue:CGFloat(l.z/(1+l.z)),alpha:1)}
        else {scene.fogStartDistance=1e8;scene.fogEndDistance=1e9}
    }
    func point(at location:CGPoint) -> SCNVector3? {
        let hits=view.hitTest(location,options:[.rootNode:terrain,.firstFoundOnly:true])
        return hits.first?.worldCoordinates
    }
    func mark(_ points:[[Float]],world:VoxelWorld) {
        markers.childNodes.forEach{$0.removeFromParentNode()}
        for p in points {let node=SCNNode(geometry:SCNSphere(radius:0.6));node.geometry?.firstMaterial?.diffuse.contents=UIColor.systemYellow
            let x=Int(floor(p[0])),z=Int(floor(p[1]));let y=world.types.keys.filter{$0.x==x && $0.z==z}.map{$0.y}.max() ?? Int(focus.y)
            node.position=SCNVector3(p[0],Float(y)+1.5,p[1]);markers.addChildNode(node)
        }
    }
}
