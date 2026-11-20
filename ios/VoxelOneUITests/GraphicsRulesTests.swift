import XCTest
final class GraphicsRulesTests:NativeEvidenceTestCase {
    func testTouchControlsValidationBindingsLookAndAtomicPersistence()throws {
        let initial=NativeControls();XCTAssertEqual(initial.bindings,NativeControls.actions)
        var value=initial;value.bind(slot:0,to:"moveForward")
        XCTAssertEqual(value.bindings,["moveForward","moveLeft","moveBack","moveRight"])
        value.bind(slot:-1,to:"moveRight");value.bind(slot:3,to:"unknown")
        XCTAssertEqual(Set(value.bindings),Set(NativeControls.actions))
        value.sensitivity=2;value.invertY=true
        XCTAssertEqual(value.lookDelta(dx:10,dy:20),SIMD2<Float>(20,-40))
        XCTAssertEqual(initial.lookDelta(dx:10,dy:20),SIMD2<Float>(10,20))
        XCTAssertEqual(value.lookDelta(dx:.infinity,dy:20),.zero)
        XCTAssertEqual(value.lookDelta(dx:Float.greatestFiniteMagnitude,dy:0),SIMD2<Float>(8192,0))
        var invalid=value;invalid.bindings=["moveLeft","moveLeft"];invalid.sensitivity=Double.nan
        XCTAssertEqual(invalid.validated().bindings,initial.bindings);XCTAssertEqual(invalid.validated().sensitivity,1)
        invalid.version=2;XCTAssertEqual(invalid.validated(),initial)
        let folder=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),url=folder.appendingPathComponent("controls.json")
        defer{try? FileManager.default.removeItem(at:folder)}
        XCTAssertEqual(NativeControls.load(from:url),initial)
        try value.save(to:url);XCTAssertEqual(NativeControls.load(from:url),value)
        let before=try Data(contentsOf:url)
        let blocked=folder.appendingPathComponent("blocked");try Data("file".utf8).write(to:blocked)
        XCTAssertThrowsError(try initial.save(to:blocked.appendingPathComponent("controls.json")))
        XCTAssertEqual(try Data(contentsOf:url),before,"Failed save must preserve active settings")
        try Data(repeating:32,count:4097).write(to:url);XCTAssertEqual(NativeControls.load(from:url),initial)
        try Data("invalid".utf8).write(to:url);XCTAssertEqual(NativeControls.load(from:url),initial)
        try Data("{\"version\":999,\"sensitivity\":2,\"invertY\":true,\"bindings\":[]}".utf8).write(to:url)
        XCTAssertEqual(NativeControls.load(from:url),initial)
    }
    func testNativeGraphicsValidationMigrationAndPresets()throws {
        let old=try JSONDecoder().decode(NativeGraphics.self,from:Data("{\"version\":1,\"samples\":999,\"frames\":-1,\"detail\":9999}".utf8)).validated()
        XCTAssertEqual(old.version,2);XCTAssertEqual(old.samples,2);XCTAssertEqual(old.frames,30);XCTAssertEqual(old.detail,180);XCTAssertFalse(old.distant)
        for name in ["Low","Balanced","High"] {let p=NativeGraphics.preset(name);XCTAssertEqual(p.detail,180);XCTAssertFalse(p.atmosphere);XCTAssertFalse(p.distant)}
        let p=NativeGraphics.preset("High");XCTAssertEqual(try JSONDecoder().decode(NativeGraphics.self,from:JSONEncoder().encode(p)),p)
        var enabled=p;enabled.distant=true
        XCTAssertTrue(try JSONDecoder().decode(NativeGraphics.self,from:JSONEncoder().encode(enabled)).distant)
        var future=p;future.version=999;XCTAssertEqual(future.validated(),NativeGraphics())
    }
}
