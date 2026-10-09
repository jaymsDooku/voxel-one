import XCTest
import SceneKit

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
