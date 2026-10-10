import XCTest
final class GraphicsRulesTests:XCTestCase {
    func testNativeGraphicsValidationMigrationAndPresets()throws {
        let old=try JSONDecoder().decode(NativeGraphics.self,from:Data("{\"version\":1,\"samples\":999,\"frames\":-1,\"detail\":9999}".utf8)).validated()
        XCTAssertEqual(old.version,2);XCTAssertEqual(old.samples,2);XCTAssertEqual(old.frames,30);XCTAssertEqual(old.detail,180)
        for name in ["Low","Balanced","High"] {let p=NativeGraphics.preset(name);XCTAssertEqual(p.detail,180);XCTAssertFalse(p.atmosphere)}
        let p=NativeGraphics.preset("High");XCTAssertEqual(try JSONDecoder().decode(NativeGraphics.self,from:JSONEncoder().encode(p)),p)
        var future=p;future.version=999;XCTAssertEqual(future.validated(),NativeGraphics())
    }
}
