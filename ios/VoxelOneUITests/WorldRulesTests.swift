import XCTest
import SceneKit

/// The worker probe synchronizes every access; no actor-isolated test state enters the worker.
private final class SaveWorkerProbe:@unchecked Sendable {
    private let lock=NSLock();private var values:[Int]=[]
    func record(_ value:Int){lock.lock();defer{lock.unlock()};values.append(value)}
    var counts:[Int] {lock.lock();defer{lock.unlock()};return values}
}

final class WorldRulesTests:XCTestCase {
    func testNativeSkyVisibilityBlocksRoofsAndTransmitsWindows()throws {
        var enclosed=fixture()
        for x in 1...7 {for y in 1...7 {for z in 1...7 {
            if x==1 || x==7 || y==1 || y==7 || z==1 || z==7 {enclosed.cells.append([x,y,z,3,0x8899aa])}
        }}}
        let inside=GridKey(4,4,4)
        XCTAssertEqual(try VoxelWorld(enclosed).skyVisibility(inside),0,"Opaque roof/walls block native outdoor emission")
        enclosed.cells.removeAll{$0[0]==1 && ($0[1]==4 || $0[1]==5) && $0[2]==4}
        let open=try VoxelWorld(enclosed).skyVisibility(inside);XCTAssertGreaterThan(open,0)
        enclosed.cells.append([1,4,4,167,0xaaccdd]);enclosed.cells.append([1,5,4,167,0xaaccdd])
        let glass=try VoxelWorld(enclosed).skyVisibility(inside)
        XCTAssertGreaterThan(glass,0);XCTAssertLessThan(glass,open)
    }
    func testAtmosphereProfileMigrationAndNumericalBounds()throws {
        let known=NativeAtmosphere.attenuation(SIMD3<Double>(0,1,2))
        XCTAssertEqual(known.x,1,accuracy:1e-12)
        XCTAssertEqual(known.y,exp(-1),accuracy:1e-12)
        XCTAssertEqual(known.z,exp(-2),accuracy:1e-12)
        let profile=AtmosphereProfile.earth;try profile.validate()
        XCTAssertEqual(profile.position(SCNVector3(0,24,0)),SIMD3<Double>(0,6_360_000,0))
        var invalid=profile;invalid.version=2;XCTAssertThrowsError(try invalid.validate())
        invalid=profile;invalid.values[0] = .nan;XCTAssertThrowsError(try invalid.validate())
        invalid=profile;invalid.values[13]=0.01;XCTAssertThrowsError(try invalid.validate())
        invalid=profile;invalid.values[23]=1;XCTAssertThrowsError(try invalid.validate(),"Reject unresolved aerosol scales")
        invalid=profile;invalid.values[25]=1;XCTAssertThrowsError(try invalid.validate(),"Reject unresolved absorption scales")
        invalid=profile;invalid.values[4]=1e13;XCTAssertThrowsError(try invalid.validate(),"Bound GPU coordinate conversion")
        let decoded=try JSONDecoder().decode(Snapshot.self,from:JSONEncoder().encode(fixture()));XCTAssertNil(decoded.atmosphere)
        var updated=fixture();updated.atmosphere=profile;updated.sun=[0,1,0];try updated.validate()
        let restored=try JSONDecoder().decode(Snapshot.self,from:JSONEncoder().encode(updated));XCTAssertEqual(restored.atmosphere,profile)
        let p=SIMD3<Double>(0,6_360_002,0),sun=SIMD3<Double>(0,1,0)
        let t=NativeAtmosphere.sunlight(p,sun,profile);XCTAssertTrue(t.x>=0 && t.x<=1 && t.y>=0 && t.y<=1 && t.z>=0 && t.z<=1,"Upward solar transmission must remain in [0,1]")
        XCTAssertGreaterThan(t.x,t.y);XCTAssertGreaterThan(t.y,t.z,"Earth RGB molecular extinction attenuates blue most")
        XCTAssertEqual(NativeAtmosphere.sunlight(p,-sun,profile),.zero)
        let buried=SIMD3<Double>(0,profile.values[0]-2,0)
        XCTAssertNil(NativeAtmosphere.span(buried,sun,profile),"No path through the planetary interior")
        XCTAssertEqual(NativeAtmosphere.sunlight(buried,sun,profile),.zero)
        var vacuum=profile;vacuum.enabled=false;XCTAssertEqual(NativeAtmosphere.sunlight(p,sun,vacuum),SIMD3<Double>(repeating:1))
        let direct=NativeAtmosphere.solarIrradiance(profile,SCNVector3(0,26,0),sun)
        XCTAssertEqual(direct.x,18*t.x,accuracy:1e-10)
        var zeroSun=profile;zeroSun.values[30]=0;zeroSun.values[31]=0;zeroSun.values[32]=0
        XCTAssertEqual(NativeAtmosphere.solarIrradiance(zeroSun,SCNVector3(0,26,0),sun),.zero,"Profile solar irradiance controls native sunlight")
        var rotated=profile;rotated.values[7]=1;rotated.values[8]=0;rotated.values[9]=0
        let turned=NativeAtmosphere.solarIrradiance(rotated,SCNVector3(0,26,0),sun)
        XCTAssertEqual(turned.x,direct.x,accuracy:1e-10);XCTAssertEqual(turned.y,direct.y,accuracy:1e-10);XCTAssertEqual(turned.z,direct.z,accuracy:1e-10)
        let light=NativeAtmosphere.radiance(p,sun,sun,profile);XCTAssertTrue(light.x.isFinite && light.x>=0)
        let ground=NativeAtmosphere.radiance(p,-sun,sun,profile)
        XCTAssertTrue(ground.x.isFinite && ground.x>0,"Diffuse virtual surface closes the local patch background")
        var black=profile;black.values[27]=0;black.values[28]=0;black.values[29]=0
        XCTAssertGreaterThan(ground.x,NativeAtmosphere.radiance(p,-sun,sun,black).x,"Ground background follows profile albedo")
        XCTAssertEqual(NativeAtmosphere.radiance(p,-sun,-sun,profile),.zero,"Planet shadows the virtual ground at night")
    }
    func fixture() -> Snapshot {
        var inventory=Array(repeating:[0,0],count:36);inventory[0]=[2,4]
        var cells:[[Int]]=[]
        for x in -8...8 {for z in -8...8 {cells.append([x,0,z,1,0x4da636])}}
        cells.append([0,1,-2,3,0x8c949e])
        return Snapshot(schema:1,game:"sandbox",seed:"748291",bounds:[-8,8,-4,10,-8,8],pose:[0.5,1,0.5,0,0],cells:cells,inventory:inventory,health:20,notice:"")
    }
    func testCollisionJumpRayAndEditRules()throws {
        let world=try VoxelWorld(fixture());world.pitch=0
        for _ in 0..<60 {world.step(forward:1,strafe:0,dt:1.0/30)}
        XCTAssertGreaterThan(world.position.z,Float(-0.7),"A wall must stop the player.")
        XCTAssertTrue(world.grounded);world.jump();world.step(forward:0,strafe:0,dt:1.0/30);XCTAssertGreaterThan(world.position.y,1)
        world.position=SCNVector3(0.5,1,0.5);world.pitch = -0.4
        let target=try XCTUnwrap(world.target());XCTAssertEqual(target.hit,GridKey(0,1,-2))
        try world.offlineEdit(at:target.hit,type:0);XCTAssertEqual(world.type(target.hit),0)
        try world.offlineEdit(at:target.hit,type:2);XCTAssertEqual(world.type(target.hit),2);XCTAssertEqual(world.snapshot.inventory[0][1],3)
        XCTAssertThrowsError(try world.offlineEdit(at:GridKey(0,1,0),type:2),"Cannot build inside player")
        XCTAssertEqual(world.type(GridKey(0,1,0)),0)
        XCTAssertFalse(world.within(SCNVector3(8.9,1,0)))
    }
    func testFullInventoryBreakIsAtomic()throws {
        var value=fixture();value.inventory=Array(repeating:[2,64],count:36)
        let world=try VoxelWorld(value),key=GridKey(0,1,-2),revision=world.revision
        XCTAssertThrowsError(try world.offlineEdit(at:key,type:0))
        XCTAssertEqual(world.type(key),3);XCTAssertEqual(world.revision,revision)
        XCTAssertEqual(world.snapshot.inventory,value.inventory)
        world.snapshot.inventory[35]=[3,63]
        try world.offlineEdit(at:key,type:0)
        XCTAssertEqual(world.snapshot.inventory[35],[3,64]);XCTAssertEqual(world.type(key),0)
        world.snapshot.inventory[35]=[0,0]
        try world.offlineEdit(at:GridKey(1,0,1),type:0)
        XCTAssertEqual(world.snapshot.inventory[35],[1,1])
    }
    func testCraftingTransactionBoundsAndPersistence()throws {
        let recipe=NativeRecipe(id:89,name:"Make bricks",inputs:[[3,4]],output:166,count:4)
        var value=fixture();value.inventory[0]=[3,2];value.inventory[1]=[3,3];value.inventory[2]=[166,63]
        let world=try VoxelWorld(value);try world.offlineCraft(recipe)
        XCTAssertEqual(world.snapshot.inventory[0],[166,3]);XCTAssertEqual(world.snapshot.inventory[1],[3,1])
        XCTAssertEqual(world.snapshot.inventory[2],[166,64])
        let restored=try VoxelWorld(JSONDecoder().decode(Snapshot.self,from:JSONEncoder().encode(world.savedSnapshot())))
        XCTAssertEqual(restored.snapshot.inventory,world.snapshot.inventory)
        let before=world.snapshot.inventory,revision=world.revision
        XCTAssertThrowsError(try world.offlineCraft(recipe));XCTAssertEqual(world.snapshot.inventory,before)
        XCTAssertEqual(world.revision,revision)
        value.inventory=Array(repeating:[2,64],count:36);value.inventory[0]=[3,64]
        let full=try VoxelWorld(value);XCTAssertThrowsError(try full.offlineCraft(recipe))
        XCTAssertEqual(full.snapshot.inventory,value.inventory)
        var invalid=recipe;invalid.inputs=[[3,2],[3,2]];XCTAssertThrowsError(try invalid.validate())
        invalid=recipe;invalid.count=65;XCTAssertThrowsError(try invalid.validate())
        value=fixture();value.recipes=[recipe,recipe];XCTAssertThrowsError(try value.validate())
    }
    func testFlightCollisionBoundsHoverAndWalkRecovery()throws {
        var value=fixture();value.cells.append([0,4,0,3,0x8c949e])
        let world=try VoxelWorld(value);world.setFlying(true)
        for _ in 0..<60 {world.step(forward:0,strafe:0,dt:1.0/30,climb:1)}
        XCTAssertLessThanOrEqual(world.position.y,2.21,"Head must stop at roof")
        XCTAssertGreaterThan(world.position.y,1)
        let hovering=world.position.y
        for _ in 0..<60 {world.step(forward:0,strafe:0,dt:1.0/30)}
        XCTAssertEqual(world.position.y,hovering,"Released flight input must hover")
        world.position=SCNVector3(4.5,2,4.5)
        let start=world.position
        world.step(forward:1,strafe:1,dt:1,climb:1)
        let delta=SCNVector3(world.position.x-start.x,world.position.y-start.y,world.position.z-start.z)
        XCTAssertEqual(sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z),4.2*0.04,accuracy:0.0001,"Normalize flight diagonals and cap stalled frames")
        for _ in 0..<300 {world.step(forward:0,strafe:0,dt:1.0/30,climb:1)}
        XCTAssertLessThanOrEqual(world.position.y+1.8,Float(value.bounds[3]+1))
        for _ in 0..<300 {world.step(forward:0,strafe:0,dt:1.0/30,climb: -1)}
        XCTAssertGreaterThanOrEqual(world.position.y,1,"Flight cannot cross floor")
        world.position=SCNVector3(0.5,1,0.5)
        for _ in 0..<60 {world.step(forward:1,strafe:0,dt:1.0/30)}
        XCTAssertGreaterThan(world.position.z,Float(-0.7),"Flight still collides with walls")
        world.position=SCNVector3(4.5,3,4.5);world.setFlying(false)
        for _ in 0..<120 {world.step(forward:0,strafe:0,dt:1.0/30)}
        XCTAssertTrue(world.grounded);world.jump();XCTAssertGreaterThan(world.verticalSpeed,0)
        let restored=try VoxelWorld(world.savedSnapshot());XCTAssertFalse(restored.flying,"Flight is a transient local mode")
    }
    func testSharedCameraRayBoundsAndSweptLensCollision()throws {
        var value=fixture();value.cells.append([0,2,2,3,0x8c949e]);value.cells.append([0,3,2,3,0x8c949e])
        let world=try VoxelWorld(value)
        let hit=try XCTUnwrap(world.raycast(origin:SCNVector3(0.5,1.5,0.5),direction:SCNVector3(0,0,-1),reach:5))
        XCTAssertEqual(hit.hit,GridKey(0,1,-2));XCTAssertEqual(hit.previous,GridKey(0,1,-1))
        XCTAssertEqual(hit.distance,1.5,accuracy:0.001)
        XCTAssertNil(world.raycast(origin:SCNVector3(0.5,1.5,0.5),direction:SCNVector3(0,0,-1),reach:1))
        XCTAssertNil(world.raycast(origin:world.eye,direction:SCNVector3Zero,reach:5))
        XCTAssertNil(world.raycast(origin:SCNVector3(Float.infinity,1,0),direction:SCNVector3(0,0,-1),reach:5))
        let lens=world.cameraPosition(toward:SCNVector3(0.5,world.eye.y,4.5))
        XCTAssertGreaterThan(lens.z,world.eye.z);XCTAssertLessThan(lens.z,1.86,"Swept lens stays in front of wall")
        XCTAssertEqual(world.cameraPosition(toward:SCNVector3(0.5,world.eye.y,50)).z,world.eye.z,"Bound camera work")
        XCTAssertNil(NativeCameraMode(rawValue:"unknown"));XCTAssertEqual(NativeCameraMode.allCases.count,3)
    }
    func testDetailContractGeometryCollisionPickingAndPersistence()throws {
        let url=try XCTUnwrap(Bundle(for:WorldRulesTests.self).url(forResource:"contract",withExtension:"json"))
        let contract=try JSONDecoder().decode(NativeContract.self,from:Data(contentsOf:url));try contract.validate()
        var value=fixture();value.schema=2;value.contract=contract
        let geometry=NativeGeometry(resolution:16,leaves:[[0,0,0,8,3,0x8c949e]])
        value.cells.removeAll{$0[0]==0 && $0[1]==1 && $0[2] == -2}
        value.cells.append([0,1,-2,255,0]);value.details=[NativeDetail(cell:[0,1,-2],geometry:geometry)]
        let world=try VoxelWorld(value)
        XCTAssertEqual(world.material(at:SCNVector3(0.25,1.25,-1.75)),3)
        XCTAssertEqual(world.material(at:SCNVector3(0.75,1.25,-1.75)),0,"Fractional empty corner remains air")
        XCTAssertNil(world.raycast(origin:SCNVector3(0.75,1.25,0.5),direction:SCNVector3(0,0,-1),reach:5))
        XCTAssertEqual(world.raycast(origin:SCNVector3(0.25,1.25,0.5),direction:SCNVector3(0,0,-1),reach:5)?.hit,GridKey(0,1,-2))
        XCTAssertFalse(world.collides(SCNVector3(0.82,1,-1.7)),"Empty side is passable")
        XCTAssertTrue(world.collides(SCNVector3(0.25,1,-1.7)))
        var overlap=geometry;overlap.leaves.append(overlap.leaves[0]);XCTAssertThrowsError(try overlap.validate())
        var broken=value;broken.details=[];XCTAssertThrowsError(try broken.validate())
        broken=value;broken.contract=nil;XCTAssertThrowsError(try broken.validate())
        broken=value
        for i in 0..<27 {broken.cells.append([i%9-4,3,i/9+3,8,0])}
        XCTAssertThrowsError(try broken.validate(),"Expanded model instances must fit the leaf cap")
        var invalid=geometry;invalid.leaves=[[Int.max,0,0,8,3,0]]
        XCTAssertThrowsError(try invalid.validate(),"Huge coordinates reject without integer overflow")
        world.snapshot.inventory[0]=[8,2];try world.offlineEdit(at:GridKey(2,1,-2),type:8)
        XCTAssertGreaterThan(world.boxes(GridKey(2,1,-2)).count,1,"Model is not a full cube")
        let restored=try VoxelWorld(JSONDecoder().decode(Snapshot.self,from:JSONEncoder().encode(world.savedSnapshot())))
        XCTAssertEqual(restored.boxes(GridKey(2,1,-2)).count,world.boxes(GridKey(2,1,-2)).count)
        XCTAssertEqual(restored.type(GridKey(0,1,-2)),255)
        try restored.offlineEdit(at:GridKey(0,1,-2),type:0)
        XCTAssertEqual(restored.type(GridKey(0,1,-2)),0,"Single fractional leaf collects without losing detail")
        try restored.savedSnapshot().validate()
    }
    func testBoundedSaveQueueIsolationAndAtomicFailure()throws {
        let root=FileManager.default.temporaryDirectory.appendingPathComponent("voxel-save-test-"+UUID().uuidString)
        defer {try? FileManager.default.removeItem(at:root)}
        let url=root.appendingPathComponent("world.json"),finished=expectation(description:"all save phases finished")
        DispatchQueue.main.async {
            do {
                let world=try VoxelWorld(self.fixture()),first=world.savePayload()
                let gate=DispatchSemaphore(value:0),probe=SaveWorkerProbe()
                let writer=NativeSaveWriter(destination:url){payload,destination in
                    XCTAssertFalse(Thread.isMainThread,"Validation, encoding and IO must run off main")
                    if probe.counts.isEmpty {guard gate.wait(timeout:.now()+10) == .success else {throw GameError.invalidWorld}}
                    probe.record(payload.snapshot.inventory[0][1]);try payload.persist(to:destination)
                }
                var acknowledged=false
                writer.onIdle={
                    do {
                        XCTAssertTrue(acknowledged);XCTAssertEqual(probe.counts,[4,2],"Only active and latest payloads may be written")
                        let saved=try Data(contentsOf:url),decoded=try JSONDecoder().decode(Snapshot.self,from:saved)
                        XCTAssertTrue(decoded.cells.contains{$0[0]==3 && $0[1]==1 && $0[2]==3})
                        writer.onIdle=nil;var invalid=world.savePayload();invalid.snapshot.health=21
                        writer.submit(invalid){result in
                            XCTAssertEqual(result,.failed)
                            do {XCTAssertEqual(try Data(contentsOf:url),saved,"Invalid save preserves the atomic file")}catch{XCTFail("Saved fixture unavailable")}
                            let secondGate=DispatchSemaphore(value:0),secondProbe=SaveWorkerProbe()
                            let reentrant=NativeSaveWriter(destination:url){payload,destination in
                                if secondProbe.counts.isEmpty {guard secondGate.wait(timeout:.now()+10) == .success else {throw GameError.invalidWorld}}
                                secondProbe.record(payload.snapshot.inventory[0][1]);try payload.persist(to:destination)
                            }
                            reentrant.onIdle={XCTAssertEqual(secondProbe.counts,[4,1],"Reentrant newer save wins");finished.fulfill()}
                            reentrant.submit(first)
                            var newest=world.savePayload();newest.snapshot.inventory[0]=[2,1]
                            reentrant.submit(world.savePayload(),important:true){result in
                                if result == .superseded {reentrant.submit(newest,important:true)}
                            }
                            reentrant.submit(world.savePayload(),important:true);secondGate.signal()
                        }
                    }catch{XCTFail("Save fixture round trip failed");finished.fulfill()}
                }
                writer.submit(first)
                try world.offlineEdit(at:GridKey(2,1,2),type:2)
                writer.submit(world.savePayload(),important:true){result in XCTAssertEqual(result,.saved);acknowledged=true}
                try world.offlineEdit(at:GridKey(3,1,3),type:2)
                writer.submit(world.savePayload())
                XCTAssertNil(first.types[GridKey(2,1,2)],"Capture is isolated from later edits")
                gate.signal()
            }catch{XCTFail("Save fixture setup failed");finished.fulfill()}
        }
        wait(for:[finished],timeout:60)
    }
    func testOfflineLoaderBoundsCancellationMigrationAndFailure()throws {
        let root=FileManager.default.temporaryDirectory.appendingPathComponent("voxel-load-test-"+UUID().uuidString)
        try FileManager.default.createDirectory(at:root,withIntermediateDirectories:true)
        defer{try? FileManager.default.removeItem(at:root)}
        let url=root.appendingPathComponent("world.json"),original=try JSONEncoder().encode(fixture())
        try original.write(to:url)
        let resource=try XCTUnwrap(Bundle(for:WorldRulesTests.self).url(forResource:"contract",withExtension:"json"))
        let contract=try JSONDecoder().decode(NativeContract.self,from:Data(contentsOf:resource))
        let finished=expectation(description:"load cancellation, retry and malformed source checked")
        DispatchQueue.main.async {
            let loader=NativeOfflineLoader(),gate=DispatchSemaphore(value:0)
            var cancelled=false
            loader.onIdle={
                loader.onIdle=nil;XCTAssertTrue(cancelled)
                XCTAssertTrue(loader.load(operation:{
                    XCTAssertFalse(Thread.isMainThread)
                    return try OfflineSave.migrate(OfflineSave.read(at:url),contract:contract)
                }){result in
                    switch result {
                    case .loaded(let value):
                        XCTAssertEqual(value.schema,2);XCTAssertEqual(value.inventory[0],[2,4])
                        do {
                            XCTAssertEqual(try Data(contentsOf:url),original,"Migration must not rewrite the source")
                            let corrupt=Data([0xff,0x00]);try corrupt.write(to:url)
                            XCTAssertTrue(loader.load(operation:{try OfflineSave.read(at:url)}){failed in
                                if case .failed=failed {}else{XCTFail("Malformed save must fail safely")}
                                do {XCTAssertEqual(try Data(contentsOf:url),corrupt)}catch{XCTFail("Failure changed source")}
                                finished.fulfill()
                            })
                        }catch{XCTFail("Load fixture IO failed");finished.fulfill()}
                    default:XCTFail("Valid migration did not finish");finished.fulfill()
                    }
                })
            }
            XCTAssertTrue(loader.load(operation:{
                XCTAssertFalse(Thread.isMainThread)
                guard gate.wait(timeout:.now()+10) == .success else{throw GameError.invalidWorld}
                return try OfflineSave.read(at:url)
            }){result in if case .cancelled=result {cancelled=true}else{XCTFail("Stale load must be cancelled")}})
            XCTAssertFalse(loader.load(operation:{try OfflineSave.read(at:url)}){_ in XCTFail("Second worker started")})
            loader.cancel();gate.signal()
        }
        wait(for:[finished],timeout:60)
        let handle=try FileHandle(forWritingTo:url);try handle.truncate(atOffset:UInt64(OfflineSave.maximumBytes+1));try handle.close()
        XCTAssertThrowsError(try OfflineSave.read(at:url),"Oversize saves must be rejected before decoding")
    }
    func testBoundedMeshPagingCancellationAndGeometry()throws {
        let worker=NativeMeshWorker(),world=try VoxelWorld(fixture())
        defer{worker.cancel()}
        worker.submit(NativeMeshRequest(payload:world.savePayload(),atmosphere:false,center:[0,1,0]))
        let full=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in worker.readyCount==NativeMeshWorker.maximumReady},object:nil)
        XCTAssertEqual(XCTWaiter.wait(for:[full],timeout:10),.completed,"Producer reaches bounded backpressure")
        XCTAssertLessThanOrEqual(worker.drain().count,2);worker.cancel();XCTAssertEqual(worker.readyCount,0)
        func prepare(_ value:Snapshot)throws->[NativeMeshResult] {
            let input=try VoxelWorld(value)
            let token=worker.submit(NativeMeshRequest(payload:input.savePayload(),atmosphere:false,center:[0,1,0]))
            var results:[NativeMeshResult]=[]
            let done=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in
                let pages=worker.drain();XCTAssertLessThanOrEqual(pages.count,2);results+=pages
                return results.contains{if case .finished=$0.event {return true};return false}
            },object:nil)
            XCTAssertEqual(XCTWaiter.wait(for:[done],timeout:15),.completed)
            XCTAssertTrue(results.allSatisfy{$0.generation==token},"Cancelled generations cannot leak pages")
            return results
        }
        var value=fixture();value.cells=[[0,1,-2,3,0x8c949e],[1,1,-2,3,0x8c949e]]
        let pages=try prepare(value).compactMap{event->NativeMeshBuffer? in if case .page(_,_,let buffer)=event.event {return buffer};return nil}
        XCTAssertEqual(pages.reduce(0){$0+$1.positions.count/3},40,"Adjacent cubes hide their shared faces")
        XCTAssertTrue(pages.allSatisfy{$0.positions.count/3<=4096 && $0.normals.count==$0.positions.count && $0.colors.count/4==$0.positions.count/3})
        let url=try XCTUnwrap(Bundle(for:WorldRulesTests.self).url(forResource:"contract",withExtension:"json"))
        value.schema=2;value.contract=try JSONDecoder().decode(NativeContract.self,from:Data(contentsOf:url))
        value.cells=[[0,1,-2,255,0]];value.details=[NativeDetail(cell:[0,1,-2],geometry:NativeGeometry(resolution:16,leaves:[[0,0,0,8,2,0x7a4d2b]]))]
        let fine=try prepare(value).compactMap{event->NativeMeshBuffer? in if case .page(_,_,let buffer)=event.event {return buffer};return nil}
        XCTAssertEqual(fine.reduce(0){$0+$1.positions.count/3},24)
        let xs=fine.flatMap{$0.positions.enumerated().filter{$0.offset%3==0}.map{$0.element}}
        XCTAssertEqual(xs.max(),0.5,"Prepared fractional mesh keeps its true dimensions")
        let revision=world.geometryRevision;try world.replace(world.savedSnapshot())
        XCTAssertEqual(world.geometryRevision,revision,"Unchanged network snapshots do not invalidate geometry")
        try world.offlineEdit(at:GridKey(2,1,2),type:2);XCTAssertGreaterThan(world.geometryRevision,revision)
        worker.cancel()
    }
    func testSnapshotValidationAndSaveRoundTrip()throws {
        let world=try VoxelWorld(fixture());try world.offlineEdit(at:GridKey(2,1,2),type:2)
        let data=try JSONEncoder().encode(world.savedSnapshot()),decoded=try JSONDecoder().decode(Snapshot.self,from:data)
        let restored=try VoxelWorld(decoded);XCTAssertEqual(restored.type(GridKey(2,1,2)),2);XCTAssertEqual(restored.snapshot.inventory[0][1],3)
        var invalid=fixture();invalid.cells.append(invalid.cells[0]);XCTAssertThrowsError(try invalid.validate())
        invalid=fixture();invalid.inventory[0]=[2,65];XCTAssertThrowsError(try invalid.validate())
        invalid=fixture();invalid.pose[0] = .nan;XCTAssertThrowsError(try invalid.validate())
        invalid=fixture();invalid.bounds=[0];XCTAssertThrowsError(try invalid.validate())
    }
}
