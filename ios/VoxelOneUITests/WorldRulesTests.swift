import XCTest
import SceneKit

final class WorldRulesTests:XCTestCase {
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
