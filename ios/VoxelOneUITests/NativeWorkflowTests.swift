import XCTest

/// Tests use normal controls in the installed native app and a fresh synthetic Java server.
final class NativeWorkflowTests:XCTestCase {
    var app:XCUIApplication!
    override func setUpWithError()throws {
        continueAfterFailure=false;XCUIDevice.shared.orientation = .portrait
        app=XCUIApplication();app.launchEnvironment["VOXEL_TEST_GATEWAY"]=ProcessInfo.processInfo.environment["VOXEL_TEST_GATEWAY"] ?? ""
        app.launch()
    }
    func wait(_ element:XCUIElement,_ seconds:TimeInterval=20,_ id:String="requested control"){
        guard element.waitForExistence(timeout:seconds) else {
            var category="no error alert"
            let alert=app.alerts["Voxel One"]
            if alert.exists {
                // Inspect only this synthetic game's error alert. Never read or log form values.
                let text=alert.staticTexts.allElementsBoundByIndex.map{$0.label.lowercased()}.joined(separator:" ")
                category="game error alert"
                if text.contains("timed out") {category="game request timeout"}
                else if text.contains("service unavailable") || text.contains("server unavailable") {category="game service unavailable"}
                else if text.contains("cannot reach") {category="gateway unreachable"}
                else if text.contains("synthetic server") {
                    let codes=["read_timeout","invalid_movement","message_rate","unknown_message","output_queue","write_io","read_io"]
                    category="synthetic server "+(codes.first{text.contains($0)} ?? "unclassified")
                }
                else if text.contains("world") {category="world validation error"}
                else if text.contains("data") || text.contains("decode") {category="response decoding error"}
                else if text.contains("sign-in") || text.contains("account") {category="authentication error"}
                else if text.contains("connection") || text.contains("network") || text.contains("offline") {category="network error"}
                else if text.contains("https") || text.contains("address") {category="gateway address error"}
                else if text.contains("password") || text.contains("username") {category="input validation error"}
            }
            XCTFail("Missing \(id); \(category)");return
        }
    }
    func textContains(_ id:String,_ value:String,timeout:TimeInterval=15){
        let predicate=NSPredicate(format:"label CONTAINS[c] %@",value)
        let expectation=XCTNSPredicateExpectation(predicate:predicate,object:app.staticTexts[id])
        if XCTWaiter.wait(for:[expectation],timeout:timeout) != .completed {
            XCTFail("HUD observed: \(app.staticTexts[id].label.prefix(80)); expected: \(value.prefix(25))")
        }
    }
    func roadCount()->Int {
        let parts=app.staticTexts["cityStatus"].label.components(separatedBy:" · ")
        return parts.compactMap{part -> Int? in part.hasSuffix(" roads") ? Int(part.components(separatedBy:" ").first ?? "") : nil}.first ?? -1
    }
    func capture(_ name:String){let attachment=XCTAttachment(screenshot:app.screenshot());attachment.name=name;attachment.lifetime = .keepAlways;add(attachment)}
    func aimAtHorizonForEvidence(){
        let scene=app.otherElements["worldView"];wait(scene)
        for _ in 0..<3 {
            let aim=app.staticTexts["playerPosition"].label.components(separatedBy:" · Aim ").last ?? ""
            let values=aim.components(separatedBy:" · ").first?.components(separatedBy:", ") ?? []
            guard values.count==2,let pitch=Double(values[1]) else {XCTFail("Missing camera pitch");return}
            if abs(pitch + 0.12)<0.05 {break}
            let requested=CGFloat((pitch+0.12)/0.004)
            let dy=max(-scene.frame.height*0.25,min(scene.frame.height*0.25,requested))
            let start=scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.55))
            start.press(forDuration:0.05,thenDragTo:start.withOffset(CGVector(dx:0,dy:dy)))
        }
        capture("offline-horizon")
    }
    func menu(){if app.buttons["menuButton"].exists {app.buttons["menuButton"].tap()};wait(app.buttons["offlineNewButton"])}
    func newOffline(){menu();app.buttons["offlineNewButton"].tap();if app.alerts.buttons["New world"].waitForExistence(timeout:2){app.alerts.buttons["New world"].tap()};wait(app.buttons["breakButton"])}
    func testPlanetAtmosphereNativeToggleAndLegacyWorld()throws {
        newOffline();wait(app.buttons["atmosphereToggle"])
        if app.buttons["atmosphereToggle"].value as? String == "On" {app.buttons["atmosphereToggle"].tap()}
        app.buttons["atmosphereToggle"].tap();textContains("gameStatus","Planet sky preview on")
        aimAtHorizonForEvidence();capture("atmosphere-native-enabled")
        XCTAssertTrue(app.buttons["breakButton"].exists,"World controls remain available")
        app.buttons["atmosphereToggle"].tap();textContains("gameStatus","Planet sky preview off");capture("atmosphere-native-fallback")
        app.buttons["atmosphereToggle"].tap();textContains("gameStatus","Planet sky preview on")
        aimAtHorizonForEvidence()
        app.buttons["pauseButton"].tap();textContains("gameStatus","Paused")
        app.buttons["pauseButton"].tap()
        app.terminate();app.launch();wait(app.buttons["atmosphereToggle"])
        XCTAssertEqual(app.buttons["atmosphereToggle"].value as? String,"On","Local sky opt-in survives restart")
        XCTAssertTrue(app.buttons["breakButton"].exists,"World remains playable after restart")
        capture("atmosphere-native-enabled-restarted")
    }
    func testOfflineTouchBuildSavePauseAndLandscape()throws {
        newOffline();wait(app.otherElements["worldView"])
        // Settle on the ground and aim down with the normal look gesture.
        textContains("playerPosition","Ground")
        let initialScene=app.otherElements["worldView"]
        initialScene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.38)).press(forDuration:0.05,thenDragTo:initialScene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.56)))
        capture("offline-aimed")
        app.buttons["breakButton"].tap();textContains("gameStatus","Block collected")
        app.buttons["placeButton"].tap();textContains("gameStatus","Placed Dirt")
        XCTAssertTrue(app.buttons["slot0"].label.contains("31 blocks"))
        capture("offline-built")
        let before=app.staticTexts["playerPosition"].label
        app.buttons["moveRight"].press(forDuration:1.0)
        XCTAssertNotEqual(before,app.staticTexts["playerPosition"].label,"Hold control must move the player")
        let scene=app.otherElements["worldView"]
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.55,dy:0.5)).press(forDuration:0.05,thenDragTo:scene.coordinate(withNormalizedOffset:CGVector(dx:0.8,dy:0.48)))
        textContains("playerPosition","Ground")
        app.buttons["jumpButton"].tap();textContains("gameStatus","Jumped and landed",timeout:5);textContains("playerPosition","Ground");capture("offline-touch")
        app.buttons["pauseButton"].tap();textContains("gameStatus","Paused")
        let paused=app.staticTexts["playerPosition"].label;app.buttons["moveForward"].press(forDuration:0.8);XCTAssertEqual(paused,app.staticTexts["playerPosition"].label)
        app.buttons["pauseButton"].tap()
        // No loaded item in the ninth slot: verify the actual edge-case message.
        app.scrollViews["hotbarScroll"].swipeLeft();app.buttons["slot8"].tap();app.buttons["placeButton"].tap();textContains("gameStatus","empty")
        app.scrollViews["hotbarScroll"].swipeRight();app.buttons["slot0"].tap();app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"]);app.buttons["offlineResumeButton"].tap();wait(app.buttons["breakButton"])
        let persisted=app.staticTexts["playerPosition"].label
        app.terminate();app.launch();wait(app.buttons["breakButton"]);XCTAssertTrue(app.buttons["slot0"].label.contains("31 blocks"));XCTAssertEqual(persisted.components(separatedBy:" · ").first,app.staticTexts["playerPosition"].label.components(separatedBy:" · ").first,"Offline pose must survive restart")
        XCUIDevice.shared.orientation = .landscapeLeft
        wait(app.buttons["placeButton"]);XCTAssertTrue(app.buttons["placeButton"].isHittable);capture("offline-landscape")
        XCUIDevice.shared.orientation = .portrait
    }
    func fill(_ id:String,_ text:String,secure:Bool=false){let field=secure ? app.secureTextFields[id]:app.textFields[id];wait(field);field.tap();field.typeText(text+"\n")}
    func testOnlineLoginCityPlanningAndReconnect()throws {
        menu()
        let address=app.textFields["gatewayAddress"];wait(address);XCTAssertFalse(address.value as? String == "")
        fill("username","ios_fixture");fill("password","fixture-password-123",secure:true)
        app.segmentedControls["gameChoice"].buttons["City"].tap();app.buttons["signInButton"].tap()
        wait(app.buttons["planToggle"],80,"initial city login");textContains("cityStatus","roads",timeout:30)
        app.buttons["planToggle"].tap() // Enter planning if the initial game view starts in walking mode.
        if app.buttons["planToggle"].label=="Plan" {app.buttons["planToggle"].tap()}
        app.buttons["toolButton"].tap();app.buttons["Dirt road"].tap()
        app.buttons["confirmPlan"].tap();textContains("gameStatus","two points")
        let scene=app.otherElements["worldView"];wait(scene)
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.40,dy:0.46)).tap()
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.62,dy:0.48)).tap()
        textContains("gameStatus","2 plan points")
        app.buttons["undoPoint"].tap();app.buttons["undoPoint"].tap()
        for pair in [("40","10"),("46","10")] {
            app.buttons["pointCoordinateButton"].tap();wait(app.alerts["Plan point"])
            app.alerts.textFields["pointX"].tap();app.alerts.textFields["pointX"].typeText(pair.0)
            app.alerts.textFields["pointZ"].tap();app.alerts.textFields["pointZ"].typeText(pair.1)
            app.alerts.buttons["Add"].tap()
        }
        let roadsBefore=roadCount();XCTAssertGreaterThanOrEqual(roadsBefore,0)
        XCTAssertTrue(app.buttons["confirmPlan"].isEnabled,"Plan blocked: \(app.staticTexts["gameStatus"].label.prefix(65))")
        app.buttons["confirmPlan"].tap();textContains("gameStatus","Mayor paid",timeout:120)
        let roadAdded=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in self.roadCount()>roadsBefore},object:app)
        XCTAssertEqual(XCTWaiter.wait(for:[roadAdded],timeout:30),.completed,"Authoritative road count must increase")
        capture("city-road-plan")
        let cityBefore=app.staticTexts["cityStatus"].label
        app.buttons["planToggle"].tap();wait(app.buttons["breakButton"]);app.buttons["pauseButton"].tap();textContains("gameStatus","Paused");capture("city-native-walk");app.buttons["pauseButton"].tap()
        app.buttons["menuButton"].tap();wait(app.buttons["signInButton"])
        fill("password","fixture-password-123",secure:true);app.segmentedControls["gameChoice"].buttons["City"].tap();app.buttons["signInButton"].tap();wait(app.buttons["planToggle"],80,"reconnected city login")
        textContains("cityStatus","roads",timeout:30);XCTAssertFalse(cityBefore.isEmpty)
        capture("city-reconnected")
        // End in saved native sandbox so the outer verifier's independent launch shows gameplay.
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        if app.buttons["offlineResumeButton"].exists {app.buttons["offlineResumeButton"].tap()}
        if !app.buttons["breakButton"].waitForExistence(timeout:5) {app.alerts.buttons["OK"].tap();newOffline()}
        wait(app.buttons["breakButton"])
        aimAtHorizonForEvidence()
        // Save this normal-control camera pose for the trusted verifier's independent launch.
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        app.buttons["offlineResumeButton"].tap();wait(app.buttons["breakButton"])
    }
}
