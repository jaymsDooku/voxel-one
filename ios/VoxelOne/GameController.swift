import UIKit
import SceneKit

@MainActor
final class GameController:UIViewController,UITextFieldDelegate,UIGestureRecognizerDelegate {
    private let renderer=VoxelRenderer()
    private var world:VoxelWorld?
    private var gateway:Gateway?
    private var displayLink:CADisplayLink?
    private var lastTime:CFTimeInterval=0,lastPoll:CFTimeInterval=0,lastSave:CFTimeInterval=0,lastMove:CFTimeInterval=0
    private var networkFailureText:String?
    private var jumpOrigin:Float?;private var jumpRose=false
    private var moveBusy=false
    private var sessionID=UUID()
    private var queuedAction:([String:Any],String)?
    private var busy=false,paused=false,inMenu=true,active=true
    #if targetEnvironment(simulator)
    private var loadTestGate:DispatchSemaphore?
    private var loadTestGateUsed=false
    private var meshMemoryWarningUsed=false
    #endif
    private var loadingOffline=false
    private var pendingOfflineFresh:Bool?
    private var loadingOverlay:UIView?
    private var closingMenu=false
    private var backgroundSave:UIBackgroundTaskIdentifier = .invalid
    private struct HUDState:Equatable {
        var status:String;var position:String;var city:String;var inventory:[[Int]]
        var slot:Int;var revision:Int;var planning:Bool;var paused:Bool;var flying:Bool
        var camera:String;var depth:Int;var size:CGSize
    }
    private var lastHUDState:HUDState?
    private var graphicsOpen=false
    private weak var craftingScreen:NativeCraftingScreen?
    private var craftSaving=false
    private var movement:Set<String>=[]
    private var hudLandscape:Bool?
    private var top:UIStackView?,bottom:UIStackView?,menu:UIView?
    private var status=UILabel(),positionLabel=UILabel(),cityLabel=UILabel(),crosshair=UILabel()
    private var slotButtons:[UIButton]=[]
    private var aimPoint:CGPoint?
    private var cameraButton:UIButton?
    private var gridButton:UIButton?;private var breakDepth=0
    private var isometric:Bool {world?.snapshot.city==nil && renderer.sandboxCamera == .isometric}
    private var flyButton:UIButton?,descendButton:UIButton?
    private var planButton:UIButton?,confirmButton:UIButton?,pauseButton:UIButton?
    private var points:[[Float]]=[];private var tool=0
    private var message="";private var messageTime:CFTimeInterval=0;private var messagePinned=false
    private let address=UITextField(),username=UITextField(),password=UITextField()
    private let game=UISegmentedControl(items:["Sandbox","City"])
    override var preferredStatusBarStyle:UIStatusBarStyle {.lightContent}
    override func viewDidLoad() {
        super.viewDidLoad();view.backgroundColor = .black
        renderer.view.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(renderer.view)
        NSLayoutConstraint.activate([renderer.view.topAnchor.constraint(equalTo:view.topAnchor),renderer.view.bottomAnchor.constraint(equalTo:view.bottomAnchor),renderer.view.leadingAnchor.constraint(equalTo:view.leadingAnchor),renderer.view.trailingAnchor.constraint(equalTo:view.trailingAnchor)])
        let look=UIPanGestureRecognizer(target:self,action:#selector(pan(_:)));look.maximumNumberOfTouches=1;addWorldGesture(look)
        let two=UIPanGestureRecognizer(target:self,action:#selector(panPlanning(_:)));two.minimumNumberOfTouches=2;addWorldGesture(two)
        addWorldGesture(UITapGestureRecognizer(target:self,action:#selector(tapWorld(_:))))
        addWorldGesture(UIPinchGestureRecognizer(target:self,action:#selector(pinch(_:))))
        displayLink=CADisplayLink(target:self,selector:#selector(frame(_:)));displayLink?.preferredFramesPerSecond=renderer.graphics.effective().frames;displayLink?.add(to:.main,forMode:.common)
        OfflineSave.writer.onIdle={[weak self] in self?.beginPendingOfflineLoad()}
        OfflineSave.loader.onIdle={[weak self] in self?.beginPendingOfflineLoad()}
        showMenu()
        if FileManager.default.fileExists(atPath:OfflineSave.url.path) {loadOffline(fresh:false)}
    }
    override func viewDidAppear(_ animated:Bool) {
        super.viewDidAppear(animated)
        #if targetEnvironment(simulator)
        // Scoped simulator evidence uses the same actual Graphics route as the toolbar.
        if ProcessInfo.processInfo.arguments.contains("--graphics-evidence") && world != nil && !graphicsOpen {
            openGraphics()
        }
        #endif
    }
    override func viewWillTransition(to size:CGSize,with coordinator:UIViewControllerTransitionCoordinator){
        movement.removeAll();resetAim();applyHUDLayout(size);super.viewWillTransition(to:size,with:coordinator)
        coordinator.animate(alongsideTransition:nil){[weak self] _ in
            guard let self=self else{return};self.applyHUDLayout(self.view.bounds.size);self.view.layoutIfNeeded()
        }
    }
    override func viewDidLayoutSubviews(){
        super.viewDidLayoutSubviews();applyHUDLayout(view.bounds.size)
    }
    private func applyHUDLayout(_ size:CGSize){
        guard !inMenu else{return};let landscape=size.width>size.height
        guard hudLandscape != landscape else{return};hudLandscape=landscape
        positionLabel.isHidden=landscape;status.numberOfLines=landscape ? 1:2;cityLabel.numberOfLines=landscape ? 1:2
        cityLabel.isHidden=world?.snapshot.city==nil && landscape
    }
    override func didReceiveMemoryWarning(){
        super.didReceiveMemoryWarning();movement.removeAll()
        if !inMenu,let world=world {
            renderer.trimMeshes(around:world);paused=true;pauseButton?.setTitle("Resume",for:.normal)
            notify("Memory low. Paused. Resume to rebuild terrain.");saveOffline()
        } else {renderer.cancelMeshing()}
    }
    // HUD labels and empty stack space share world gestures. Controls keep their
    // own touches, including movement buttons and the scrolling hotbar.
    private func addWorldGesture(_ gesture:UIGestureRecognizer) {
        gesture.delegate=self;view.addGestureRecognizer(gesture)
    }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldReceive touch:UITouch)->Bool {
        guard !inMenu,!paused,!graphicsOpen else{return false}
        var target=touch.view
        while let current=target {
            if current is UIControl || current is UIScrollView {return false}
            if current === view {break}
            target=current.superview
        }
        return true
    }
    private func label(_ text:String,size:CGFloat=14,id:String="")->UILabel {
        let l=UILabel();l.text=text;l.textColor = .white;l.font = .systemFont(ofSize:size,weight:.semibold);l.numberOfLines=2;l.accessibilityIdentifier=id;return l
    }
    private func button(_ text:String,_ id:String,_ action:Selector)->UIButton {
        let b=UIButton(type:.system);b.setTitle(text,for:.normal);b.accessibilityIdentifier=id;b.setTitleColor(.white,for:.normal)
        b.backgroundColor=UIColor(red:0.08,green:0.16,blue:0.21,alpha:0.88);b.layer.cornerRadius=10;b.titleLabel?.font = .systemFont(ofSize:14,weight:.semibold)
        b.addTarget(self,action:action,for:.touchUpInside);b.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;return b
    }
    private func row(_ views:[UIView])->UIStackView {let r=UIStackView(arrangedSubviews:views);r.axis = .horizontal;r.spacing=6;r.distribution = .fillEqually;return r}
    private func overlay(_ stack:UIStackView,atTop:Bool) {
        stack.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.leadingAnchor,constant:10),stack.trailingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.trailingAnchor,constant:-10)])
        if atTop {stack.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor,constant:6).isActive=true}
        else {stack.bottomAnchor.constraint(equalTo:view.safeAreaLayoutGuide.bottomAnchor,constant:-6).isActive=true}
    }
    private func startOffline(_ snapshot:Snapshot)throws {
        sessionID=UUID();busy=false;moveBusy=false;queuedAction=nil;world=try VoxelWorld(snapshot);gateway=nil;renderer.planning=false;renderer.focus=world!.position;points.removeAll();renderer.rebuild(world!);renderer.mark(points,world:world!)
        showGame();saveOffline()
    }
    private func showGame() {
        lastHUDState=nil
        menu?.removeFromSuperview();menu=nil;top?.removeFromSuperview();bottom?.removeFromSuperview();crosshair.removeFromSuperview()
        aimPoint=nil;hudLandscape=nil;breakDepth=0
        inMenu=false;paused=false;networkFailureText=nil;jumpOrigin=nil;jumpRose=false;movement.removeAll();lastTime=0;message="";messagePinned=false
        status=label("",id:"gameStatus");positionLabel=label("",size:11,id:"playerPosition");cityLabel=label("",size:12,id:"cityStatus")
        let menuButton=button("Menu","menuButton",#selector(openMenu))
        let pause=button("Pause","pauseButton",#selector(togglePause));pauseButton=pause
        let plan=button("Plan","planToggle",#selector(togglePlan));planButton=plan;plan.isHidden=world?.snapshot.city==nil
        let sky=button("Sky","atmosphereToggle",#selector(toggleAtmosphere(_:)))
        sky.accessibilityValue=renderer.atmospherePreview ? "On":"Off"
        let toolbar=row([menuButton,pause,plan,button("Bag","inventoryButton",#selector(inventory)),sky])
        let fly=button("Fly","flyButton",#selector(toggleFly));flyButton=fly;fly.isHidden=gateway != nil
        let camera=button("Camera","cameraButton",#selector(chooseCamera));cameraButton=camera;camera.isHidden=world?.snapshot.city != nil
        let graphicsToolbar=row([button("Graphics","graphicsButton",#selector(openGraphics)),button("Craft","craftButton",#selector(crafting)),fly,camera])
        let t=UIStackView(arrangedSubviews:[toolbar,graphicsToolbar,cityLabel,status,positionLabel]);t.axis = .vertical;t.spacing=2;top=t;overlay(t,atTop:true)
        crosshair=label("+",size:26);crosshair.textAlignment = .center;crosshair.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(crosshair)
        NSLayoutConstraint.activate([crosshair.centerXAnchor.constraint(equalTo:view.centerXAnchor),crosshair.centerYAnchor.constraint(equalTo:view.centerYAnchor)])
        slotButtons=(0..<9).map{ i in let b=button("","slot\(i)",#selector(selectSlot(_:)));b.tag=i;return b }
        let hotbar=row(slotButtons);hotbar.translatesAutoresizingMaskIntoConstraints=false
        let hotbarScroll=UIScrollView();hotbarScroll.accessibilityIdentifier="hotbarScroll";hotbarScroll.showsHorizontalScrollIndicator=true;hotbarScroll.addSubview(hotbar);hotbarScroll.heightAnchor.constraint(equalToConstant:44).isActive=true
        NSLayoutConstraint.activate([hotbar.leadingAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.leadingAnchor),hotbar.trailingAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.trailingAnchor),hotbar.topAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.topAnchor),hotbar.bottomAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.bottomAnchor),hotbar.heightAnchor.constraint(equalTo:hotbarScroll.frameLayoutGuide.heightAnchor),hotbar.widthAnchor.constraint(greaterThanOrEqualToConstant:444)])
        for b in slotButtons {b.widthAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true}
        let left=directionButton("◀","moveLeft"),forward=directionButton("▲","moveForward"),back=directionButton("▼","moveBack"),right=directionButton("▶","moveRight")
        let directions=row([left,forward,back,right])
        let down=directionButton("Down","flyDown");descendButton=down;down.isHidden=true
        let grid=button("Grid","gridButton",#selector(chooseGrid));gridButton=grid
        grid.isHidden=world?.snapshot.contract==nil || (gateway != nil && world?.snapshot.contract?.fineEdits != true)
        let actions=row([button("Break","breakButton",#selector(breakBlock)),button("Place","placeButton",#selector(placeBlock)),directionButton("Jump","jumpButton"),down,grid])
        let planning=row([button("Tool","toolButton",#selector(chooseTool)),button("Point","pointCoordinateButton",#selector(addCoordinatePoint)),button("Undo","undoPoint",#selector(undoPoint)),button("Confirm","confirmPlan",#selector(confirmPlan))]);confirmButton=planning.arrangedSubviews.last as? UIButton
        planning.accessibilityIdentifier="planningControls";planning.isHidden=true
        let b=UIStackView(arrangedSubviews:[hotbarScroll,planning,actions,directions]);b.axis = .vertical;b.spacing=5;bottom=b;overlay(b,atTop:false)
        refreshHUD()
    }
    private func directionButton(_ text:String,_ id:String)->UIButton {
        let b=button(text,id,#selector(stopMove(_:)));b.removeTarget(self,action:#selector(stopMove(_:)),for:.touchUpInside)
        b.addTarget(self,action:#selector(startMove(_:)),for:.touchDown)
        b.addTarget(self,action:#selector(stopMove(_:)),for:[.touchUpInside,.touchUpOutside,.touchCancel,.touchDragExit])
        b.isMultipleTouchEnabled=true;return b
    }
    @objc private func startMove(_ sender:UIButton){
        guard !paused,active,!inMenu,!graphicsOpen,!closingMenu,!renderer.planning else{return}
        let id=sender.accessibilityIdentifier ?? "";movement.insert(id)
        if id=="jumpButton" {jump()}
    }
    @objc private func stopMove(_ sender:UIButton){movement.remove(sender.accessibilityIdentifier ?? "")}
    @objc private func selectSlot(_ sender:UIButton){world?.slot=sender.tag;refreshHUD()}
    @objc private func jump(){
        if !paused && !renderer.planning,let world=world,world.grounded,!world.flying {
            jumpOrigin=world.position.y;jumpRose=false;world.jump()
        }
    }
    private func resetAim(){aimPoint=nil;crosshair.transform = .identity}
    @objc private func chooseCamera(){
        guard !paused,let world=world,world.snapshot.city==nil else{return};movement.removeAll()
        let alert=UIAlertController(title:"Camera",message:"Drag to look. In Isometric, use two fingers to pan and pinch to zoom. Tap terrain to aim; edits still need player reach.",preferredStyle:.actionSheet)
        for mode in NativeCameraMode.allCases {
            alert.addAction(UIAlertAction(title:mode.title,style:.default){[weak self] _ in
                guard let self=self else{return}
                self.renderer.sandboxCamera=mode;UserDefaults.standard.set(mode.rawValue,forKey:"sandboxCameraMode")
                self.renderer.focus=world.position;self.resetAim();self.renderer.updateCamera(world)
                self.notify("\(mode.title) camera.");self.refreshHUD()
            })
        }
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));anchor(alert);present(alert,animated:true)
    }
    @objc private func toggleFly(){
        guard gateway==nil,!paused,let world=world else{return}
        movement.removeAll();jumpOrigin=nil;jumpRose=false;world.setFlying(!world.flying)
        notify(world.flying ? "Flying. Hold Jump to rise; Down to descend.":"Walking. Gravity on.");refreshHUD()
    }
    @objc private func togglePause(){guard !closingMenu else{return};paused.toggle();movement.removeAll();if !paused,let world=world {renderer.rebuild(world)};pauseButton?.setTitle(paused ? "Resume":"Pause",for:.normal);notify(paused ? "Paused. The online city keeps running.":"Resumed.");saveOffline()}
    @objc private func togglePlan(){guard let world=world,world.snapshot.city != nil else{return};renderer.planning.toggle();lastPoll=0;renderer.focus=world.position;points.removeAll();renderer.mark(points,world:world);movement.removeAll();refreshHUD()}
    @objc private func openGraphics(){
        movement.removeAll();graphicsOpen=true
        let screen=NativeGraphicsScreen(renderer.graphics){[weak self] p in
            guard let self=self else{return}
            // Save before changing the working renderer. Failed persistence keeps the old profile.
            try p.save();self.renderer.applyGraphics(p);self.displayLink?.preferredFramesPerSecond=p.effective().frames
            if let world=self.world {self.renderer.rebuild(world);self.renderer.updateCamera(world)}
        }
        screen.onClose={[weak self] in self?.graphicsOpen=false;self?.lastTime=0;self?.movement.removeAll()}
        present(screen,animated:true)
    }
    @objc private func toggleAtmosphere(_ sender:UIButton){var p=renderer.graphics;p.atmosphere = !renderer.atmospherePreview;p.preset="Custom";do{try p.save();renderer.applyGraphics(p)}catch{notify("Could not save graphics. Kept the current sky.");return};sender.accessibilityValue=renderer.atmospherePreview ? "On":"Off";if let world=world {renderer.rebuild(world);renderer.updateCamera(world)};notify(renderer.atmospherePreview ? "Planet sky preview on (low quality).":"Planet sky preview off.")}
    @objc private func chooseTool(){
        let alert=UIAlertController(title:"City tool",message:"Tap ground to choose points. Roads need two points; zones need at least three. The server checks cost and space.",preferredStyle:.actionSheet)
        for (index,title) in ["Inspect","Dirt road","Residential","Commercial","Industrial","Agricultural"].enumerated(){alert.addAction(UIAlertAction(title:title,style:.default){[weak self] _ in self?.tool=index;self?.points.removeAll();self?.notify("\(title) selected.")})}
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));anchor(alert);present(alert,animated:true)
    }
    @objc private func addCoordinatePoint(){
        guard !paused,renderer.planning else{return}
        let alert=UIAlertController(title:"Plan point",message:"Add a road endpoint or zone corner on the world grid.",preferredStyle:.alert)
        for name in ["X","Z"] {alert.addTextField{field in field.placeholder=name;field.accessibilityIdentifier="point"+name;field.keyboardType = .numbersAndPunctuation}}
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel))
        alert.addAction(UIAlertAction(title:"Add",style:.default){[weak self] _ in
            guard let self=self,!self.paused,let x=Float(alert.textFields?[0].text ?? ""),let z=Float(alert.textFields?[1].text ?? ""),x.isFinite,z.isFinite,abs(x)<999_970,abs(z)<999_970,self.points.count<32 else{self?.notify("Enter valid X and Z coordinates.");return}
            self.points.append([Float(floor(x)),Float(floor(z))]);self.renderer.focus.x=x;self.renderer.focus.z=z;self.lastPoll=0
            if let world=self.world {self.renderer.mark(self.points,world:world)};self.notify("\(self.points.count) plan points.")
        });present(alert,animated:true)
    }
    @objc private func undoPoint(){if !points.isEmpty {points.removeLast()};if let world=world{renderer.mark(points,world:world)};notify("\(points.count) plan points.")}
    @objc private func confirmPlan(){
        guard !paused,renderer.planning else{return}
        if tool==0 || (tool==1 && points.count != 2) || (tool>=2 && points.count<3){notify("Road needs two points. A zone needs at least three.");return}
        let command=tool==1 ? 1:2,value=tool==1 ? 0:tool-2
        action(["kind":"city","command":command,"value":value,"points":points],success:"City plan checked by server.")
        points.removeAll();if let world=world{renderer.mark(points,world:world)}
    }
    @objc private func tapWorld(_ gesture:UITapGestureRecognizer){
        guard !inMenu,!paused,let world=world else{return}
        if !renderer.planning && world.snapshot.city==nil && renderer.sandboxCamera != .first {
            movement.removeAll();let point=gesture.location(in:renderer.view);aimPoint=point
            crosshair.transform=CGAffineTransform(translationX:point.x-view.bounds.midX,y:point.y-view.bounds.midY)
            notify("Target set. Break or Place within five blocks.");return
        }
        guard renderer.planning,let point=renderer.point(at:gesture.location(in:renderer.view),world:world) else{return}
        if tool==0 {
            let c=world.snapshot.city?.citizens.min{hypot($0.x-point.x,$0.z-point.z)<hypot($1.x-point.x,$1.z-point.z)}
            if let c=c,hypot(c.x-point.x,c.z-point.z)<4 {notify("\(c.name): \(c.activity), $\(Int(c.money)), hunger \(Int(c.hunger))")}
            else {notify("Ground \(Int(floor(point.x))), \(Int(floor(point.z))).")}
        } else if points.count<32 {points.append([Float(floor(point.x)),Float(floor(point.z))]);renderer.mark(points,world:world);notify("\(points.count) plan points.")}
    }
    @objc private func pan(_ gesture:UIPanGestureRecognizer){guard !inMenu,!paused,!renderer.planning,!isometric else{return};resetAim();let d=gesture.translation(in:renderer.view);world?.look(dx:Float(d.x),dy:Float(d.y));gesture.setTranslation(.zero,in:renderer.view)}
    @objc private func panPlanning(_ gesture:UIPanGestureRecognizer){guard !inMenu,!paused,(renderer.planning || isometric) else{return};resetAim();let d=gesture.translation(in:renderer.view)
        renderer.focus.x-=Float(d.x+d.y)*renderer.distance/500;renderer.focus.z+=Float(d.x-d.y)*renderer.distance/500
        if isometric,let world=world {
            let b=world.snapshot.bounds;renderer.focus.x=min(Float(b[1]),max(Float(b[0]),renderer.focus.x));renderer.focus.z=min(Float(b[5]),max(Float(b[4]),renderer.focus.z))
        } else {renderer.focus.x=min(999_970,max(-999_970,renderer.focus.x));renderer.focus.z=min(999_970,max(-999_970,renderer.focus.z))}
        gesture.setTranslation(.zero,in:renderer.view)
    }
    @objc private func pinch(_ gesture:UIPinchGestureRecognizer){if !inMenu,!paused,(renderer.planning || isometric) {resetAim();renderer.distance=min(80,max(12,renderer.distance/Float(gesture.scale)));gesture.scale=1}}
    @objc private func chooseGrid(){
        guard !paused,!renderer.planning else{return};movement.removeAll()
        let alert=UIAlertController(title:"Break grid",message:"Choose the cube size to collect. Placement uses the selected item's size. Models use Whole.",preferredStyle:.actionSheet)
        for depth in 0...4 {
            alert.addAction(UIAlertAction(title:depth==0 ? "Whole / hit piece":"1/\(1<<depth)",style:.default){[weak self] _ in
                self?.breakDepth=depth;self?.notify(depth==0 ? "Whole-block or hit-piece grid.":"Break grid 1/\(1<<depth).");self?.refreshHUD()
            })
        }
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));anchor(alert);present(alert,animated:true)
    }
    @objc private func breakBlock(){edit(place:false)}
    @objc private func placeBlock(){edit(place:true)}
    private func edit(place:Bool){
        guard !paused,!renderer.planning,let world=world else{return}
        let type=place ? world.snapshot.inventory[world.slot][0]:0
        if place && type==0 {notify("This hotbar slot is empty.");return}
        let target:VoxelHit?
        if world.snapshot.city==nil && renderer.sandboxCamera != .first {
            renderer.updateCamera(world)
            target=renderer.target(at:aimPoint ?? CGPoint(x:renderer.view.bounds.midX,y:renderer.view.bounds.midY),world:world)
        } else {target=world.detailedTarget()}
        guard let target=target else{notify("Aim at a visible block within five blocks of your player.");return}
        do {
            let address=try world.editAddress(target,place:place,breakDepth:breakDepth),key=address.cell
            if gateway != nil {
                guard address.depth==0 || world.snapshot.contract?.fineEdits==true else{notify("This gateway needs an update before fine edits.");return}
                action(["kind":"edit","blockX":key.x,"blockY":key.y,"blockZ":key.z,"type":type,"slot":world.slot,"depth":address.depth,"ix":address.ix,"iy":address.iy,"iz":address.iz],success:place ? "Placement checked by server.":"Break checked by server.")
            } else {
                try world.offlineRegionEdit(address,type:type);renderer.rebuild(world);renderer.mark(points,world:world)
                let text=place ? "Placed \(world.itemName(type)).":"Block collected."
                saveOffline{[weak self] result in if result == .saved {self?.notify(text)}};refreshHUD()
            }
        }catch{notify(error.localizedDescription)}
    }
    private func action(_ request:[String:Any],success:String){
        guard let gateway=gateway,let world=world else{return}
        if busy {queuedAction=(request,success);notify("Action queued.");return}
        busy=true;movement.removeAll();let id=sessionID
        notify("Checking action with the game server…",persistent:true)
        Task {do {
            // Flush current player pose before the reach-checked action. Planning never moves the player.
            _ = try await gateway.state(world,focus:nil)
            let state=try await gateway.action(request);guard id==sessionID else{return}
            var visible=state
            if renderer.planning {visible=try await gateway.state(world,focus:SCNFocus(x:renderer.focus.x,z:renderer.focus.z))}
            guard id==sessionID else{return};try world.replace(visible);renderer.rebuild(world);renderer.mark(points,world:world)
            notify(state.notice.isEmpty ? success:state.notice,persistent:true)
        }catch{if id==sessionID && !paused {notify(error.localizedDescription,persistent:true)}};if id==sessionID {busy=false;refreshHUD();runQueuedAction()}}
    }
    private func runQueuedAction(){if let item=queuedAction {queuedAction=nil;action(item.0,success:item.1)}}
    @objc private func inventory(){
        guard let world=world else{return};movement.removeAll()
        let alert=UIAlertController(title:"Inventory",message:"Choose a stack to swap into hotbar slot \(world.slot+1).",preferredStyle:.actionSheet)
        for i in 0..<36 where world.snapshot.inventory[i][1]>0 {
            let item=world.snapshot.inventory[i];alert.addAction(UIAlertAction(title:"\(i+1): \(world.itemName(item[0])) ×\(item[1])",style:.default){[weak self] _ in
                guard let self=self else{return}
                if self.gateway != nil {self.action(["kind":"swap","a":i,"b":world.slot],success:"Inventory updated.")}
                else {world.snapshot.inventory.swapAt(i,world.slot);self.saveOffline();self.refreshHUD()}
            })
        }
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));anchor(alert);present(alert,animated:true)
    }
    @objc private func crafting(){
        guard !paused,!graphicsOpen,let world=world else{return};movement.removeAll()
        do {
            // Offline saves may contain an older catalog. Current app data is authoritative offline.
            let recipes:[NativeRecipe]
            if gateway==nil {recipes=try NativeRecipe.bundled()}
            else if let supplied=world.snapshot.recipes {recipes=supplied}
            else {recipes=try NativeRecipe.bundled()}
            let screen=NativeCraftingScreen(recipes:recipes,maximumBatch:gateway==nil || world.snapshot.contract?.batchCrafting==true ? 64:1,name:{world.itemName($0)})
            let session=sessionID;graphicsOpen=true;craftingScreen=screen
            screen.modalPresentationStyle = .fullScreen
            screen.onClose={[weak self] in self?.graphicsOpen=false;self?.lastTime=0;self?.movement.removeAll();self?.craftingScreen=nil}
            screen.onCraft={[weak self] recipe,quantity in
                guard let self=self,self.sessionID==session,!self.craftSaving,!self.busy,self.active,!self.paused else{return}
                if self.gateway != nil {self.action(["kind":"craft","recipe":recipe.id,"quantity":quantity],success:"Craft checked by server.")}
                else {do {
                    try world.offlineCraft(recipe,quantity:quantity);self.craftSaving=true;self.notify("Saving crafted items…",persistent:true)
                    let text="Crafted \(recipe.count*quantity) \(world.itemName(recipe.output))."
                    self.saveOffline{[weak self] result in
                        guard let self=self,self.sessionID==session else{return};self.craftSaving=false
                        if result == .saved {self.notify(text)}
                        self.refreshHUD()
                    }
                }catch{self.notify(error.localizedDescription)}}
                self.refreshHUD()
            }
            screen.update(inventory:world.snapshot.inventory,message:"Choose a recipe. Ingredients come from your bag.",pending:busy || craftSaving)
            present(screen,animated:true)
        }catch{notify("Crafting catalog could not be read safely.")}
    }
    private func anchor(_ alert:UIAlertController){alert.popoverPresentationController?.sourceView=view;alert.popoverPresentationController?.sourceRect=CGRect(x:view.bounds.midX,y:view.bounds.midY,width:1,height:1)}
    @objc private func frame(_ link:CADisplayLink){
        guard !inMenu,active,let world=world else{lastTime=0;return}
        renderer.advanceMeshing()
        #if targetEnvironment(simulator)
        // Exercise the actual memory-warning handler; this is not a hardware pressure measurement.
        if !meshMemoryWarningUsed && ProcessInfo.processInfo.arguments.contains("--mesh-memory-warning-test") && renderer.view.accessibilityValue=="Terrain ready" {
            meshMemoryWarningUsed=true;didReceiveMemoryWarning()
        }
        #endif
        let dt=lastTime==0 ? Float(1.0/30):Float(link.timestamp-lastTime);lastTime=link.timestamp
        if !paused && !graphicsOpen && !renderer.planning {
            let forward:Float=(movement.contains("moveForward") ? 1:0)-(movement.contains("moveBack") ? 1:0)
            let side:Float=(movement.contains("moveRight") ? 1:0)-(movement.contains("moveLeft") ? 1:0)
            if movement.contains("jumpButton") {jump()}
            let climb:Float=(movement.contains("jumpButton") ? 1:0)-(movement.contains("flyDown") ? 1:0)
            world.step(forward:forward,strafe:side,dt:dt,climb:climb)
            if isometric && (forward != 0 || side != 0 || climb != 0) {renderer.focus=world.position;resetAim()}
            if let origin=jumpOrigin {
                if world.position.y>origin+0.2 {jumpRose=true}
                if world.grounded {
                    if jumpRose {notify("Jumped and landed.")}
                    jumpOrigin=nil;jumpRose=false
                }
            }
        }
        renderer.updateCamera(world)
        if let gateway=gateway,!moveBusy,!paused,link.timestamp-lastMove>0.1 {
            lastMove=link.timestamp;moveBusy=true;let id=sessionID
            Task {do {try await gateway.move(world)}catch {if id==sessionID && !paused {failNetwork(error.localizedDescription)}};if id==sessionID {moveBusy=false}}
        }
        if let gateway=gateway,!busy,!paused,link.timestamp-lastPoll>1.5 {
            lastPoll=link.timestamp;busy=true;let id=sessionID
            let focus=renderer.planning ? SCNFocus(x:renderer.focus.x,z:renderer.focus.z):nil
            Task {do{let state=try await gateway.state(world,focus:focus);guard id==sessionID else{return};try world.replace(state);renderer.rebuild(world);renderer.mark(points,world:world)}catch{
                if id==sessionID && !paused {failNetwork(error.localizedDescription)}
            };if id==sessionID {busy=false;refreshHUD();runQueuedAction()}}
        }
        if gateway==nil,link.timestamp-lastSave>5 {lastSave=link.timestamp;saveOffline()}
        refreshHUD()
    }
    private func refreshHUD(){guard let world=world,!inMenu else{return}
        if inMenu {return}
        let mode=gateway==nil ? "Offline sandbox":"Online \(world.snapshot.game)"
        let defaultStatus=renderer.planning && !points.isEmpty ? "\(points.count) plan points.":"\(mode) · HP \(world.snapshot.health) · \(renderer.planning ? "Two fingers pan; pinch zoom":"Drag world to look")"
        let statusText=paused || messagePinned || CACurrentMediaTime()-messageTime<7 ? message:defaultStatus
        let positionText=String(format:"Position %.1f, %.1f, %.1f · Aim %.2f, %.2f",world.position.x,world.position.y,world.position.z,world.yaw,world.pitch)+(world.flying ? " · Flying":world.grounded ? " · Ground":" · Air")
        let cityText=world.snapshot.city.map{String(format:"%@ · $%.0f · %d roads · %d zones",$0.time,$0.treasury,$0.roads.count,$0.zones)} ?? "49×49 local terrain · edits save on this phone"
        craftingScreen?.update(inventory:world.snapshot.inventory,message:statusText,pending:busy || craftSaving || paused || !active)
        applyHUDLayout(view.bounds.size)
        let next=HUDState(status:statusText,position:positionText,city:cityText,inventory:world.snapshot.inventory,slot:world.slot,revision:world.revision,planning:renderer.planning,paused:paused,flying:world.flying,camera:renderer.sandboxCamera.title,depth:breakDepth,size:view.bounds.size)
        guard next != lastHUDState else{return};lastHUDState=next
        status.text=statusText;positionLabel.text=positionText;cityLabel.text=cityText
        gridButton?.setTitle(breakDepth==0 ? "Grid":"1/\(1<<breakDepth)",for:.normal)
        cameraButton?.accessibilityValue=renderer.sandboxCamera.title
        flyButton?.setTitle(world.flying ? "Walk":"Fly",for:.normal)
        flyButton?.accessibilityValue=world.flying ? "On":"Off"
        descendButton?.isHidden = !world.flying
        for (i,b) in slotButtons.enumerated(){let item=world.snapshot.inventory[i];b.setTitle(item[0]==0 ? "—":"\(String(world.itemName(item[0]).prefix(3)))\n\(item[1])",for:.normal);b.titleLabel?.numberOfLines=2;b.titleLabel?.textAlignment = .center;b.accessibilityLabel="\(world.itemName(item[0])) slot \(i+1), \(item[1]) blocks";b.layer.borderWidth=i==world.slot ? 2:0;b.layer.borderColor=UIColor.systemYellow.cgColor}
        if let rows=bottom?.arrangedSubviews,rows.count>=4 {rows[1].isHidden = !renderer.planning;rows[2].isHidden=renderer.planning;rows[3].isHidden=renderer.planning}
        if let controls=bottom?.arrangedSubviews[1] as? UIStackView {
            for case let control as UIButton in controls.arrangedSubviews {control.isEnabled = !paused}
        }
        crosshair.isHidden=renderer.planning;planButton?.setTitle(renderer.planning ? "Walk":"Plan",for:.normal)
    }
    private func failNetwork(_ text:String){
        guard networkFailureText==nil else{return}
        networkFailureText=text;paused=true;movement.removeAll();queuedAction=nil
        message=text;messagePinned=true;messageTime=CACurrentMediaTime();status.text=text
        pauseButton?.setTitle("Disconnected",for:.normal);pauseButton?.isEnabled=false
    }
    private func notify(_ text:String,persistent:Bool=false){
        guard networkFailureText==nil else{return}
        message=text;messagePinned=persistent;messageTime=CACurrentMediaTime();if !inMenu {status.text=text}
    }
    func suspend(){active=false;movement.removeAll();renderer.cancelMeshing();saveOffline()}
    func becameActive(){active=true;lastTime=0;if let world=world {renderer.rebuild(world)}}
    private func endBackgroundSave(){
        if backgroundSave != .invalid {UIApplication.shared.endBackgroundTask(backgroundSave);backgroundSave = .invalid}
    }
    func saveOffline(completion:((NativeSaveResult)->Void)?=nil){
        guard gateway==nil,let world=world else{return}
        if !active && backgroundSave == .invalid {
            backgroundSave=UIApplication.shared.beginBackgroundTask(withName:"Save Sandbox"){[weak self] in self?.endBackgroundSave()}
        }
        let payload=world.savePayload(),id=sessionID
        OfflineSave.writer.submit(payload,important:completion != nil){[weak self] result in
            guard let self=self else{return}
            if result == .failed && id==self.sessionID {self.notify("Save failed. Keep this session open and try again.")}
            if OfflineSave.writer.isIdle {self.endBackgroundSave()}
            if id==self.sessionID {completion?(result)}
        }
    }
    @objc private func openMenu(){
        guard !closingMenu else{return};movement.removeAll()
        if gateway==nil,world != nil {
            let wasPaused=paused;closingMenu=true;paused=true
            saveOffline{[weak self] result in
                guard let self=self,result != .superseded else{return}
                if result == .saved {self.finishOpenMenu()}
                else {self.closingMenu=false;self.paused=wasPaused}
            }
        } else {finishOpenMenu()}
    }
    private func finishOpenMenu(){
        renderer.cancelMeshing();closingMenu=false;movement.removeAll();let previous=gateway;sessionID=UUID();queuedAction=nil;busy=false;moveBusy=false;gateway=nil;world=nil
        Task{await previous?.logout()};showMenu()
    }
    private func showMenu(){
        inMenu=true;paused=false;networkFailureText=nil;top?.removeFromSuperview();bottom?.removeFromSuperview();crosshair.removeFromSuperview();menu?.removeFromSuperview();movement.removeAll()
        let scroll=UIScrollView();scroll.translatesAutoresizingMaskIntoConstraints=false;scroll.backgroundColor=UIColor(red:0.035,green:0.075,blue:0.1,alpha:0.98);view.addSubview(scroll);menu=scroll
        NSLayoutConstraint.activate([scroll.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor),scroll.bottomAnchor.constraint(equalTo:view.safeAreaLayoutGuide.bottomAnchor),scroll.leadingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.leadingAnchor),scroll.trailingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.trailingAnchor)])
        address.placeholder="https://your-game-gateway.example";address.text=ProcessInfo.processInfo.environment["VOXEL_TEST_GATEWAY"] ?? UserDefaults.standard.string(forKey:"gatewayAddress");address.accessibilityIdentifier="gatewayAddress";address.keyboardType = .URL;address.autocapitalizationType = .none;address.autocorrectionType = .no
        username.placeholder="Username";username.accessibilityIdentifier="username";username.autocapitalizationType = .none;username.autocorrectionType = .no;username.textContentType = .username
        password.placeholder="Password";password.accessibilityIdentifier="password";password.isSecureTextEntry=true;password.textContentType = .password;password.text=""
        for field in [address,username,password] {field.delegate=self;field.returnKeyType = .done;field.borderStyle = .roundedRect;field.backgroundColor = .white;field.textColor = .black;field.heightAnchor.constraint(equalToConstant:44).isActive=true}
        game.selectedSegmentIndex=0;game.accessibilityIdentifier="gameChoice";game.backgroundColor = .systemGray5
        let title=label("VOXEL ONE",size:30,id:"mainTitle")
        let stack=UIStackView(arrangedSubviews:[title,label("Native iPhone · explore, build and plan",size:15),button("Continue offline sandbox","offlineResumeButton",#selector(resumeOffline)),button("New offline sandbox","offlineNewButton",#selector(newOffline)),label("Online games",size:20),game,address,username,password,row([button("Sign in","signInButton",#selector(signIn)),button("Create account","registerButton",#selector(register))]),label("Online play needs the Voxel One HTTPS mobile gateway. City planning, citizens and economy run on the same game server.\n\nOffline builds save on this iPhone. Flower pots and fractional pieces are supported; the full desktop editor is still being ported.",size:13)])
        stack.axis = .vertical;stack.spacing=12;stack.translatesAutoresizingMaskIntoConstraints=false;scroll.addSubview(stack)
        NSLayoutConstraint.activate([stack.topAnchor.constraint(equalTo:scroll.contentLayoutGuide.topAnchor,constant:24),stack.bottomAnchor.constraint(equalTo:scroll.contentLayoutGuide.bottomAnchor,constant:-24),stack.leadingAnchor.constraint(equalTo:scroll.contentLayoutGuide.leadingAnchor,constant:24),stack.trailingAnchor.constraint(equalTo:scroll.contentLayoutGuide.trailingAnchor,constant:-24),stack.widthAnchor.constraint(equalTo:scroll.frameLayoutGuide.widthAnchor,constant:-48)])
    }
    @objc private func resumeOffline(){loadOffline(fresh:false)}
    private func finishOfflineLoad(){
        loadingOffline=false;pendingOfflineFresh=nil;loadingOverlay?.removeFromSuperview();loadingOverlay=nil;menu?.isUserInteractionEnabled=true
    }
    @objc private func cancelOfflineLoad(){
        guard loadingOffline else{return};sessionID=UUID();OfflineSave.loader.cancel()
        #if targetEnvironment(simulator)
        loadTestGate?.signal();loadTestGate=nil
        #endif
        finishOfflineLoad()
    }
    private func loadOffline(fresh:Bool){
        guard inMenu,!busy,!loadingOffline else{return}
        movement.removeAll();view.endEditing(true);sessionID=UUID();loadingOffline=true;pendingOfflineFresh=fresh
        menu?.isUserInteractionEnabled=false
        let panel=UIStackView(arrangedSubviews:[label(fresh ? "Creating Sandbox…":"Loading Sandbox…",size:18,id:"offlineLoadStatus"),button("Cancel","cancelOfflineLoad",#selector(cancelOfflineLoad))])
        panel.axis = .vertical;panel.spacing=12;panel.backgroundColor=UIColor(red:0.05,green:0.12,blue:0.16,alpha:1)
        panel.layer.cornerRadius=16;panel.isLayoutMarginsRelativeArrangement=true;panel.layoutMargins=UIEdgeInsets(top:20,left:20,bottom:20,right:20)
        panel.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(panel);loadingOverlay=panel
        NSLayoutConstraint.activate([panel.centerXAnchor.constraint(equalTo:view.safeAreaLayoutGuide.centerXAnchor),panel.centerYAnchor.constraint(equalTo:view.safeAreaLayoutGuide.centerYAnchor),panel.widthAnchor.constraint(equalTo:view.safeAreaLayoutGuide.widthAnchor,multiplier:0.85)])
        beginPendingOfflineLoad()
    }
    private func beginPendingOfflineLoad(){
        guard loadingOffline,let fresh=pendingOfflineFresh,OfflineSave.writer.isIdle,!OfflineSave.loader.isBusy else{return}
        pendingOfflineFresh=nil;let id=sessionID
        #if targetEnvironment(simulator)
        // One-shot fault injection keeps the real loading UI stable until normal Cancel.
        let gate:DispatchSemaphore?
        if ProcessInfo.processInfo.arguments.contains("--offline-load-cancel-test") && !loadTestGateUsed {
            gate=DispatchSemaphore(value:0);loadTestGate=gate;loadTestGateUsed=true
        } else {gate=nil}
        #else
        let gate:DispatchSemaphore?=nil
        #endif
        OfflineSave.loader.load(operation:{
            if let gate=gate,gate.wait(timeout:.now()+30) != .success {throw GameError.invalidWorld}
            if fresh {return try OfflineSave.fresh()}
            return try OfflineSave.load()
        }){[weak self] result in
            guard let self=self,self.loadingOffline,id==self.sessionID else{return}
            #if targetEnvironment(simulator)
            self.loadTestGate=nil
            #endif
            self.finishOfflineLoad()
            switch result {
            case .loaded(let value):
                do {try self.startOffline(value)
                    #if targetEnvironment(simulator)
                    if ProcessInfo.processInfo.arguments.contains("--graphics-evidence") && !self.graphicsOpen {self.openGraphics()}
                    #endif
                }catch{self.showError("This sandbox could not be opened safely.")}
            case .failed:self.showError("Could not load this sandbox. Try again or create a new world.")
            case .cancelled:break
            }
        }
    }
    @objc private func newOffline(){
        guard !busy,!loadingOffline else{return}
        if FileManager.default.fileExists(atPath:OfflineSave.url.path) {let alert=UIAlertController(title:"Replace offline sandbox?",message:"This deletes the local sandbox edits. Online worlds stay on their server.",preferredStyle:.alert);alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));alert.addAction(UIAlertAction(title:"New world",style:.destructive){[weak self] _ in self?.createOffline()});present(alert,animated:true)}else{createOffline()}
    }
    private func createOffline(){loadOffline(fresh:true)}
    @objc private func signIn(){connect(register:false)}
    @objc private func register(){connect(register:true)}
    private func connect(register:Bool){
        guard !busy,!loadingOffline else{return}
        do {
            let service=try Gateway(address:address.text ?? ""),name=username.text ?? "",secret=password.text ?? "",selected=game.selectedSegmentIndex==1 ? "city":"sandbox"
            guard name.range(of:"^[A-Za-z0-9_]{3,16}$",options:.regularExpression) != nil, secret.count>=10,secret.count<=128 else{throw GameError.message("Use a 3–16 character username and a 10–128 character password.")}
            password.text="";sessionID=UUID();let id=sessionID;busy=true;view.endEditing(true)
            Task {do {
                let snapshot=try await service.login(game:selected,username:name,password:secret,register:register)
                guard id==sessionID else{await service.logout();return}
                let loaded=try VoxelWorld(snapshot);queuedAction=nil;moveBusy=false;world=loaded;gateway=service;renderer.focus=loaded.position;renderer.planning=selected=="city";renderer.rebuild(loaded)
                UserDefaults.standard.set(address.text,forKey:"gatewayAddress");showGame()
            }catch{await service.logout();if id==sessionID {showError(error.localizedDescription)}};if id==sessionID {busy=false}}
        }catch{showError(error.localizedDescription)}
    }
    func textFieldShouldReturn(_ textField:UITextField)->Bool {textField.resignFirstResponder();return true}
    private func showError(_ text:String){let alert=UIAlertController(title:"Voxel One",message:text,preferredStyle:.alert);alert.addAction(UIAlertAction(title:"OK",style:.default));present(alert,animated:true)}
}
