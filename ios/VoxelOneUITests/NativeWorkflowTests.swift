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
    func waitHittable(_ element:XCUIElement,_ id:String){
        let expectation=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in element.exists && element.isHittable},object:element)
        XCTAssertEqual(XCTWaiter.wait(for:[expectation],timeout:10),.completed,"Unusable \(id); frame=\(element.frame); viewport=\(app.frame)")
    }
    func waitTerrain(){
        let scene=app.otherElements["worldView"]
        let ready=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in scene.value as? String == "Terrain ready"},object:scene)
        XCTAssertEqual(XCTWaiter.wait(for:[ready],timeout:30),.completed,"Native terrain did not finish: \(String(describing:scene.value))")
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
    func menu(){if app.buttons["cancelOfflineLoad"].exists {wait(app.buttons["menuButton"])};if app.buttons["menuButton"].exists {app.buttons["menuButton"].tap()};wait(app.buttons["offlineNewButton"])}
    func aimAtSolarDiscForEvidence(){
        // The synthetic offline world's authoritative fixed hour is 10.
        let targetYaw = -atan2(0.5,0.35),targetPitch=atan2(sqrt(0.75),sqrt(0.25+0.35*0.35))
        let scene=app.otherElements["worldView"];wait(scene)
        func pose()->(Double,Double)? {
            let aim=app.staticTexts["playerPosition"].label.components(separatedBy:" · Aim ").last ?? ""
            let values=aim.components(separatedBy:" · ").first?.components(separatedBy:", ") ?? []
            guard values.count==2,let yaw=Double(values[0]),let pitch=Double(values[1]) else {XCTFail("Missing solar camera pose");return nil}
            return (yaw,pitch)
        }
        func drag(_ dx:CGFloat,_ dy:CGFloat){
            guard let before=pose() else{return}
            let previous=app.staticTexts["playerPosition"].label
            let start=scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.45))
            start.press(forDuration:0.05,thenDragTo:start.withOffset(CGVector(dx:dx,dy:dy)))
            let changed=XCTNSPredicateExpectation(predicate:NSPredicate(format:"label != %@",previous),object:app.staticTexts["playerPosition"])
            let result=XCTWaiter.wait(for:[changed],timeout:5)
            XCTAssertEqual(result,.completed,"Look unchanged: aim=\(before.0),\(before.1) drag=\(dx),\(dy)")
        }
        for attempt in 0...16 {
            guard let (yaw,pitch)=pose() else{return}
            if abs(yaw-targetYaw)<0.03 && abs(pitch-targetPitch)<0.03 {capture("atmosphere-native-solar-disc");return}
            if attempt==16 {break}
            let horizontal=abs(yaw-targetYaw)>=abs(pitch-targetPitch)
            let error=horizontal ? yaw-targetYaw:pitch-targetPitch
            let limit=(horizontal ? scene.frame.width:scene.frame.height)*0.25
            let points=max(-limit,min(limit,CGFloat(error/0.004)))
            func axisDrag(_ amount:CGFloat){drag(horizontal ? amount:0,horizontal ? 0:amount)}
            if abs(error)<0.12 {
                // Both legs exceed pan recognition distance. Equal opposite legs
                // cancel recognition loss while their net motion is the correction.
                let away:CGFloat=error>0 ? -50:50
                axisDrag(away);axisDrag(points-away)
            } else {axisDrag(points)}
        }
        if let (yaw,pitch)=pose(){XCTFail("Sun aim missed: actual=\(yaw),\(pitch) target=\(targetYaw),\(targetPitch)")}
    }
    func newOffline(){menu();app.buttons["offlineNewButton"].tap();if app.alerts.buttons["New world"].waitForExistence(timeout:2){app.alerts.buttons["New world"].tap()};wait(app.buttons["breakButton"])}
    func testPlanetAtmosphereNativeToggleAndLegacyWorld()throws {
        newOffline();wait(app.buttons["atmosphereToggle"])
        if app.buttons["atmosphereToggle"].value as? String == "On" {app.buttons["atmosphereToggle"].tap()}
        app.buttons["atmosphereToggle"].tap();textContains("gameStatus","Planet sky preview on")
        aimAtHorizonForEvidence();capture("atmosphere-native-enabled")
        aimAtSolarDiscForEvidence();aimAtHorizonForEvidence()
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
        aimAtSolarDiscForEvidence()
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
    func testHeldJumpCancelsOnBackgroundAndPause()throws {
        newOffline();textContains("playerPosition","Ground")
        app.buttons["jumpButton"].press(forDuration:2.5)
        textContains("playerPosition","Ground",timeout:5)
        // A released hold must settle and stay grounded, rather than repeat forever.
        let settled=app.staticTexts["playerPosition"].label
        app.buttons["pauseButton"].tap();app.buttons["jumpButton"].press(forDuration:0.5)
        XCTAssertEqual(settled,app.staticTexts["playerPosition"].label)
        app.buttons["pauseButton"].tap()
        XCUIDevice.shared.press(.home);app.activate()
        textContains("playerPosition","Ground",timeout:5)
        app.buttons["moveRight"].press(forDuration:0.5)
        textContains("playerPosition","Ground",timeout:5)
        capture("offline-hold-jump-resumed")
    }
    func testNativeCraftingMissingIngredientsAndRestart()throws {
        newOffline();app.buttons["craftButton"].tap()
        let search=app.searchFields["craftSearch"];wait(search);search.tap();search.typeText("Saw planks")
        let planks=app.cells["recipe88"];wait(planks);planks.tap();textContains("craftStatus","Crafted 4 Planks")
        capture("offline-crafted-planks")
        search.tap();search.buttons["Clear text"].tap();search.typeText("Grow grass")
        let grass=app.cells["recipe91"];wait(grass);grass.tap();textContains("craftStatus","Missing ingredients")
        app.buttons["craftDone"].tap()
        app.terminate();app.launch();wait(app.buttons["craftButton"])
        XCTAssertTrue(app.buttons["slot2"].label.contains("31 blocks"),"Craft consumed one Wood")
        XCTAssertTrue(app.buttons["slot3"].label.contains("36 blocks"),"Craft output survives restart")
        capture("offline-crafting-restarted")
    }
    func testNativeRecipeSearchFractionalJoinAndBackground()throws {
        newOffline();app.buttons["craftButton"].tap()
        let search=app.searchFields["craftSearch"];wait(search);search.tap();search.typeText("Join Dirt")
        let join=app.cells["recipe9"];wait(join);join.tap();textContains("craftStatus","Crafted 1 Dirt")
        XCUIDevice.shared.press(.home);app.activate();wait(app.buttons["craftDone"])
        search.tap();search.buttons["Clear text"].tap();search.typeText("no matching recipe xyz")
        let emptyResults=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in
            self.app.tables["recipeTable"].cells.count==0
        },object:nil)
        XCTAssertEqual(XCTWaiter.wait(for:[emptyResults],timeout:10),.completed,"craft_book_empty_results")
        XCTAssertTrue(app.staticTexts["No matching recipes."].exists,"craft_book_empty_results")
        XCUIDevice.shared.orientation = .landscapeLeft;waitHittable(app.buttons["craftDone"],"landscape Craft Done")
        capture("native-recipe-search-empty-landscape")
        app.buttons["craftDone"].tap();wait(app.buttons["craftButton"])
        app.terminate();app.launch();wait(app.buttons["craftButton"])
        XCTAssertTrue(app.buttons["slot0"].label.contains("33 blocks"),"craft_join_saved_count")
        XCTAssertFalse(app.buttons["slot6"].label.contains("8 blocks"),"craft_join_half_consumed")
    }
    func testOfflineFlightTouchPauseBackgroundAndWalking()throws {
        newOffline();textContains("playerPosition","Ground")
        func height()->Double {
            let text=app.staticTexts["playerPosition"].label.components(separatedBy:" · ")[0]
            return Double(text.components(separatedBy:", ")[1]) ?? -1000
        }
        let ground=height();app.buttons["flyButton"].tap();textContains("playerPosition","Flying")
        app.buttons["jumpButton"].press(forDuration:0.7)
        XCTAssertGreaterThan(height(),ground+1)
        capture("offline-flying-terrain")
        app.buttons["pauseButton"].tap();let before=height()
        app.buttons["jumpButton"].press(forDuration:0.5);XCTAssertEqual(height(),before)
        app.buttons["pauseButton"].tap();XCUIDevice.shared.press(.home);app.activate()
        wait(app.buttons["flyButton"]);XCTAssertEqual(height(),before,"Released input must not rise after background")
        app.buttons["flyDown"].press(forDuration:1.5)
        XCTAssertLessThan(height(),before)
        app.buttons["flyButton"].tap();textContains("playerPosition","Ground",timeout:5)
        XCTAssertFalse(app.buttons["flyDown"].isHittable)
        app.buttons["jumpButton"].tap();textContains("gameStatus","Jumped and landed",timeout:5)
        capture("offline-flight-walk-recovered")
    }
    // Leave a useful real world view for the verifier's independent cold launch.
    func testZZFinalTerrainGameplayEvidence()throws {
        newOffline();textContains("playerPosition","Ground")
        let scene=app.otherElements["worldView"]
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.38)).press(forDuration:0.05,thenDragTo:scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.56)))
        app.buttons["breakButton"].tap();textContains("gameStatus","Block collected")
        app.buttons["placeButton"].tap();textContains("gameStatus","Placed Dirt")
        app.scrollViews["hotbarScroll"].swipeLeft();app.buttons["slot5"].tap()
        app.buttons["placeButton"].tap();textContains("gameStatus","Placed Flower pot")
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        app.buttons["offlineResumeButton"].tap();wait(app.buttons["breakButton"])
        waitTerrain();capture("final-native-terrain-edited")
    }
    func testSandboxCameraModesAimReachAndPersistence()throws {
        newOffline();textContains("playerPosition","Ground")
        func choose(_ name:String){app.buttons["cameraButton"].tap();wait(app.buttons[name]);app.buttons[name].tap();textContains("gameStatus",name+" camera")}
        choose("Third person");capture("sandbox-third-person")
        app.terminate();app.launch();wait(app.buttons["cameraButton"])
        XCTAssertEqual(app.buttons["cameraButton"].value as? String,"Third person")
        choose("Isometric");capture("sandbox-isometric")
        let scene=app.otherElements["worldView"]
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.5)).tap()
        textContains("gameStatus","Target set")
        // A far corner in the overview must not grant remote edit reach.
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.1,dy:0.35)).tap()
        app.buttons["breakButton"].tap();textContains("gameStatus","within five blocks")
        scene.pinch(withScale:1.5,velocity:1)
        app.buttons["moveRight"].press(forDuration:0.5)
        capture("sandbox-isometric-zoom-moved")
        XCUIDevice.shared.orientation = .landscapeLeft
        waitHittable(app.buttons["cameraButton"],"landscape Camera")
        choose("First person");capture("sandbox-camera-landscape")
        XCUIDevice.shared.orientation = .portrait
        waitHittable(app.buttons["cameraButton"],"portrait Camera")
        choose("Third person");choose("First person")
        app.buttons["jumpButton"].tap();textContains("gameStatus","Jumped and landed",timeout:5)
        app.buttons["pauseButton"].tap();textContains("gameStatus","Paused");app.buttons["pauseButton"].tap()
    }
    func testNativeModelAndFractionalPlacementSaveRestart()throws {
        newOffline();textContains("playerPosition","Ground")
        let scene=app.otherElements["worldView"]
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.38)).press(forDuration:0.05,thenDragTo:scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.56)))
        app.scrollViews["hotbarScroll"].swipeLeft();app.buttons["slot5"].tap()
        app.buttons["placeButton"].tap();textContains("gameStatus","Placed Flower pot")
        capture("native-flower-pot-placed")
        app.buttons["breakButton"].tap();textContains("gameStatus","Block collected")
        app.buttons["slot6"].tap();app.buttons["placeButton"].tap();textContains("gameStatus","Placed Dirt 1/2 cube")
        XCTAssertTrue(app.buttons["slot6"].label.contains("7 blocks"));capture("native-fractional-half-placed")
        app.terminate();app.launch();wait(app.buttons["placeButton"])
        app.scrollViews["hotbarScroll"].swipeLeft();XCTAssertTrue(app.buttons["slot6"].label.contains("7 blocks"))
        capture("native-fractional-save-restarted")
        app.buttons["breakButton"].tap();textContains("gameStatus","Block collected")
        XCTAssertTrue(app.buttons["slot6"].label.contains("8 blocks"))
    }
    func testSaveMenuBarrierBackgroundAndColdRestart()throws {
        newOffline();textContains("playerPosition","Ground")
        app.buttons["moveRight"].press(forDuration:0.5)
        let position=app.staticTexts["playerPosition"].label.components(separatedBy:" · ")[0]
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        app.buttons["offlineResumeButton"].tap();wait(app.buttons["breakButton"])
        XCTAssertEqual(app.staticTexts["playerPosition"].label.components(separatedBy:" · ")[0],position)
        XCUIDevice.shared.press(.home);app.activate();wait(app.buttons["breakButton"])
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        app.terminate();app.launch();wait(app.buttons["breakButton"])
        XCTAssertEqual(app.staticTexts["playerPosition"].label.components(separatedBy:" · ")[0],position)
        capture("native-save-barrier-cold-restart")
    }
    func testOfflineLoadCancelRetryAndColdRestart()throws {
        newOffline();textContains("playerPosition","Ground")
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"],20,"menu before cancellation")
        app.terminate();app.launchArguments=["--offline-load-cancel-test"];app.launch()
        wait(app.buttons["cancelOfflineLoad"],20,"cancel control during gated load")
        capture("native-offline-load-cancellable")
        app.buttons["cancelOfflineLoad"].tap()
        wait(app.buttons["offlineResumeButton"],20,"menu after cancellation")
        XCTAssertTrue(app.buttons["offlineResumeButton"].isHittable)
        app.buttons["offlineResumeButton"].tap();wait(app.buttons["breakButton"],20,"world after cancelled-load retry")
        textContains("playerPosition","Ground");capture("native-offline-load-cancel-retried")
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"],20,"menu after retry")
        app.terminate();app.launchArguments=[];app.launch();wait(app.buttons["breakButton"],20,"cold restart after retry")
        XCTAssertTrue(app.buttons["craftButton"].isHittable);capture("native-offline-async-cold-load")
    }
    func testIncrementalMeshEditsBackgroundAndRestart()throws {
        newOffline();textContains("playerPosition","Ground");waitTerrain()
        let scene=app.otherElements["worldView"]
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.38)).press(forDuration:0.05,thenDragTo:scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.56)))
        app.buttons["breakButton"].tap();textContains("gameStatus","Block collected");waitTerrain()
        app.buttons["placeButton"].tap();textContains("gameStatus","Placed Dirt");waitTerrain()
        capture("native-incremental-mesh-edited")
        XCUIDevice.shared.press(.home);app.activate();wait(app.buttons["breakButton"]);waitTerrain()
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        app.terminate();app.launch();wait(app.buttons["breakButton"]);waitTerrain()
        capture("native-mesh-cold-restart")
        app.terminate();app.launchArguments=["--mesh-memory-warning-test"];app.launch()
        wait(app.buttons["pauseButton"]);textContains("gameStatus","Memory low")
        let before=app.staticTexts["playerPosition"].label
        app.buttons["moveRight"].press(forDuration:0.5);XCTAssertEqual(app.staticTexts["playerPosition"].label,before)
        app.buttons["pauseButton"].tap();waitTerrain();capture("native-simulated-memory-warning-recovered")
        app.launchArguments=[]
    }
    func testNativeFineGridCollectFillAndPersistence()throws {
        newOffline();textContains("playerPosition","Ground");waitTerrain()
        let scene=app.otherElements["worldView"]
        scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.38)).press(forDuration:0.05,thenDragTo:scene.coordinate(withNormalizedOffset:CGVector(dx:0.5,dy:0.56)))
        app.buttons["gridButton"].tap();wait(app.buttons["1/2"]);app.buttons["1/2"].tap()
        app.buttons["breakButton"].tap();textContains("gameStatus","Block collected");waitTerrain()
        app.scrollViews["hotbarScroll"].swipeLeft();app.buttons["slot7"].tap()
        XCTAssertTrue(app.buttons["slot7"].label.contains("Grass 1/2 cube"));capture("native-fine-grid-collected")
        app.buttons["placeButton"].tap();textContains("gameStatus","Placed Grass 1/2 cube");waitTerrain()
        XCTAssertTrue(app.buttons["slot7"].label.contains("0 blocks"))
        app.buttons["gridButton"].tap();wait(app.buttons["Whole / hit piece"]);app.buttons["Whole / hit piece"].tap()
        app.buttons["menuButton"].tap();wait(app.buttons["offlineResumeButton"])
        app.terminate();app.launch();wait(app.buttons["breakButton"]);waitTerrain();capture("native-fine-grid-filled-restarted")
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
        app.buttons["graphicsButton"].tap();wait(app.segmentedControls["graphicsAA"]);app.swipeUp();wait(app.buttons["graphicsCancel"]);app.buttons["graphicsCancel"].tap();wait(app.buttons["planToggle"])
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

    func testGraphicsDraftApplyPersistenceAndLandscape(){
        newOffline();app.buttons["graphicsButton"].tap();wait(app.segmentedControls["graphicsAA"])
        app.segmentedControls["graphicsAA"].buttons["Off"].tap()
        app.swipeUp();wait(app.buttons["graphicsCancel"]);app.buttons["graphicsCancel"].tap()
        app.buttons["graphicsButton"].tap();wait(app.segmentedControls["graphicsPreset"])
        app.segmentedControls["graphicsPreset"].buttons["Low"].tap()
        app.segmentedControls["graphicsFrames"].buttons["60 FPS"].tap()
        app.segmentedControls["graphicsDetail"].buttons["64"].tap();capture("graphics-native-draft")
        app.swipeUp();wait(app.buttons["graphicsApply"]);app.buttons["graphicsApply"].tap();wait(app.buttons["breakButton"])
        app.terminate();app.launch();wait(app.buttons["graphicsButton"])
        app.buttons["graphicsButton"].tap();wait(app.segmentedControls["graphicsAA"])
        XCTAssertTrue(app.segmentedControls["graphicsAA"].buttons["Off"].isSelected)
        XCTAssertTrue(app.segmentedControls["graphicsFrames"].buttons["60 FPS"].isSelected)
        XCTAssertTrue(app.segmentedControls["graphicsDetail"].buttons["64"].isSelected)
        XCUIDevice.shared.orientation = .landscapeLeft;capture("graphics-native-landscape")
        app.swipeUp();wait(app.buttons["graphicsReset"]);app.buttons["graphicsReset"].tap()
        app.swipeUp();wait(app.buttons["graphicsCancel"]);app.buttons["graphicsCancel"].tap()
        XCUIDevice.shared.orientation = .portrait;app.buttons["graphicsButton"].tap();wait(app.segmentedControls["graphicsAA"])
        XCTAssertTrue(app.segmentedControls["graphicsAA"].buttons["Off"].isSelected,"Reset is only a draft until Apply")
        app.swipeUp();wait(app.buttons["graphicsCancel"]);app.buttons["graphicsCancel"].tap()
        app.buttons["pauseButton"].tap();textContains("gameStatus","Paused");app.buttons["pauseButton"].tap()
        // Exercise the high requested AA path; effective samples remain device limited.
        app.buttons["graphicsButton"].tap();wait(app.segmentedControls["graphicsPreset"])
        app.segmentedControls["graphicsPreset"].buttons["High"].tap();app.swipeUp();wait(app.buttons["graphicsApply"]);app.buttons["graphicsApply"].tap()
        wait(app.buttons["breakButton"]);app.buttons["graphicsButton"].tap();wait(app.segmentedControls["graphicsAA"])
        XCTAssertTrue(app.segmentedControls["graphicsAA"].buttons["4x"].isSelected)
        capture("graphics-native-high")
        // Leave a neutral native profile for the independent launch and later sky regression.
        app.segmentedControls["graphicsPreset"].buttons["Balanced"].tap();app.swipeUp();wait(app.buttons["graphicsApply"]);app.buttons["graphicsApply"].tap()
        wait(app.buttons["breakButton"]);capture("graphics-native-applied")
    }
}
