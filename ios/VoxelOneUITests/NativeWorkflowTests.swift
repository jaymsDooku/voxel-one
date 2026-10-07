import XCTest

/// Tests use normal controls in the installed native app and a fresh synthetic Java server.
final class NativeWorkflowTests:XCTestCase {
    var app:XCUIApplication!
    override func setUpWithError()throws {
        continueAfterFailure=false;XCUIDevice.shared.orientation = .portrait
        app=XCUIApplication();app.launchEnvironment["VOXEL_TEST_GATEWAY"]=ProcessInfo.processInfo.environment["VOXEL_TEST_GATEWAY"] ?? ""
        app.launch()
    }
    func wait(_ element:XCUIElement,_ seconds:TimeInterval=20){XCTAssertTrue(element.waitForExistence(timeout:seconds))}
    func textContains(_ id:String,_ value:String,timeout:TimeInterval=15){
        let predicate=NSPredicate(format:"label CONTAINS[c] %@",value)
        let expectation=XCTNSPredicateExpectation(predicate:predicate,object:app.staticTexts[id])
        XCTAssertEqual(XCTWaiter.wait(for:[expectation],timeout:timeout),.completed)
    }
    func capture(_ name:String){let attachment=XCTAttachment(screenshot:app.screenshot());attachment.name=name;attachment.lifetime = .keepAlways;add(attachment)}
    func menu(){if app.buttons["menuButton"].exists {app.buttons["menuButton"].tap()};wait(app.buttons["offlineNewButton"])}
    func newOffline(){menu();app.buttons["offlineNewButton"].tap();if app.alerts.buttons["New world"].waitForExistence(timeout:2){app.alerts.buttons["New world"].tap()};wait(app.buttons["breakButton"])}
    func testOfflineTouchBuildSavePauseAndLandscape()throws {
        newOffline();wait(app.otherElements["worldView"])
        // A ground block is in reach at the game's normal starting view.
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
        app.buttons["jumpButton"].tap();textContains("playerPosition","Air",timeout:3);capture("offline-touch")
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
        wait(app.buttons["planToggle"],40);textContains("cityStatus","roads",timeout:30)
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
        app.buttons["confirmPlan"].tap();textContains("gameStatus","Mayor paid",timeout:30)
        capture("city-road-plan")
        let cityBefore=app.staticTexts["cityStatus"].label
        app.buttons["planToggle"].tap();wait(app.buttons["breakButton"]);app.buttons["pauseButton"].tap();textContains("gameStatus","Paused");capture("city-native-walk");app.buttons["pauseButton"].tap()
        app.buttons["menuButton"].tap();wait(app.buttons["signInButton"])
        fill("password","fixture-password-123",secure:true);app.segmentedControls["gameChoice"].buttons["City"].tap();app.buttons["signInButton"].tap();wait(app.buttons["planToggle"],40)
        textContains("cityStatus","roads",timeout:30);XCTAssertFalse(cityBefore.isEmpty)
        capture("city-reconnected")
        // End in saved native sandbox so the outer verifier's independent launch shows gameplay.
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        if app.buttons["offlineResumeButton"].exists {app.buttons["offlineResumeButton"].tap()}
        if !app.buttons["breakButton"].waitForExistence(timeout:5) {app.alerts.buttons["OK"].tap();newOffline()}
        wait(app.buttons["breakButton"])
    }
}
