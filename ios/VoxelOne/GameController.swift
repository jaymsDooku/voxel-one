import UIKit
import SceneKit

@MainActor
final class GameController:UIViewController,UITextFieldDelegate {
    private let renderer=VoxelRenderer()
    private var world:VoxelWorld?
    private var gateway:Gateway?
    private var displayLink:CADisplayLink?
    private var lastTime:CFTimeInterval=0,lastPoll:CFTimeInterval=0,lastSave:CFTimeInterval=0,lastMove:CFTimeInterval=0
    private var jumpOrigin:Float?;private var jumpRose=false
    private var moveBusy=false
    private var sessionID=UUID()
    private var queuedAction:([String:Any],String)?
    private var busy=false,paused=false,inMenu=true,active=true
    private var movement:Set<String>=[]
    private var top:UIStackView?,bottom:UIStackView?,menu:UIView?
    private var status=UILabel(),positionLabel=UILabel(),cityLabel=UILabel(),crosshair=UILabel()
    private var slotButtons:[UIButton]=[]
    private var planButton:UIButton?,confirmButton:UIButton?,pauseButton:UIButton?
    private var points:[[Float]]=[];private var tool=0
    private var message="";private var messageTime:CFTimeInterval=0
    private let address=UITextField(),username=UITextField(),password=UITextField()
    private let game=UISegmentedControl(items:["Sandbox","City"])
    override var preferredStatusBarStyle:UIStatusBarStyle {.lightContent}
    override func viewDidLoad() {
        super.viewDidLoad();view.backgroundColor = .black
        renderer.view.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(renderer.view)
        NSLayoutConstraint.activate([renderer.view.topAnchor.constraint(equalTo:view.topAnchor),renderer.view.bottomAnchor.constraint(equalTo:view.bottomAnchor),renderer.view.leadingAnchor.constraint(equalTo:view.leadingAnchor),renderer.view.trailingAnchor.constraint(equalTo:view.trailingAnchor)])
        let look=UIPanGestureRecognizer(target:self,action:#selector(pan(_:)));look.maximumNumberOfTouches=1;renderer.view.addGestureRecognizer(look)
        let two=UIPanGestureRecognizer(target:self,action:#selector(panPlanning(_:)));two.minimumNumberOfTouches=2;renderer.view.addGestureRecognizer(two)
        renderer.view.addGestureRecognizer(UITapGestureRecognizer(target:self,action:#selector(tapWorld(_:))))
        renderer.view.addGestureRecognizer(UIPinchGestureRecognizer(target:self,action:#selector(pinch(_:))))
        displayLink=CADisplayLink(target:self,selector:#selector(frame(_:)));displayLink?.preferredFramesPerSecond=30;displayLink?.add(to:.main,forMode:.common)
        if FileManager.default.fileExists(atPath:OfflineSave.url.path) {
            do {try startOffline(OfflineSave.load())}catch{showMenu();notify(error.localizedDescription)}
        } else {showMenu()}
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
        try OfflineSave.write(world!);showGame()
    }
    private func showGame() {
        menu?.removeFromSuperview();menu=nil;top?.removeFromSuperview();bottom?.removeFromSuperview();crosshair.removeFromSuperview()
        inMenu=false;paused=false;jumpOrigin=nil;jumpRose=false;movement.removeAll();lastTime=0;message=""
        status=label("",id:"gameStatus");positionLabel=label("",size:11,id:"playerPosition");cityLabel=label("",size:12,id:"cityStatus")
        let menuButton=button("Menu","menuButton",#selector(openMenu))
        let pause=button("Pause","pauseButton",#selector(togglePause));pauseButton=pause
        let plan=button("Plan","planToggle",#selector(togglePlan));planButton=plan;plan.isHidden=world?.snapshot.city==nil
        let toolbar=row([menuButton,pause,plan,button("Bag","inventoryButton",#selector(inventory))])
        let t=UIStackView(arrangedSubviews:[toolbar,cityLabel,status,positionLabel]);t.axis = .vertical;t.spacing=2;top=t;overlay(t,atTop:true)
        crosshair=label("+",size:26);crosshair.textAlignment = .center;crosshair.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(crosshair)
        NSLayoutConstraint.activate([crosshair.centerXAnchor.constraint(equalTo:view.centerXAnchor),crosshair.centerYAnchor.constraint(equalTo:view.centerYAnchor)])
        slotButtons=(0..<9).map{ i in let b=button("","slot\(i)",#selector(selectSlot(_:)));b.tag=i;return b }
        let hotbar=row(slotButtons);hotbar.translatesAutoresizingMaskIntoConstraints=false
        let hotbarScroll=UIScrollView();hotbarScroll.accessibilityIdentifier="hotbarScroll";hotbarScroll.showsHorizontalScrollIndicator=true;hotbarScroll.addSubview(hotbar);hotbarScroll.heightAnchor.constraint(equalToConstant:44).isActive=true
        NSLayoutConstraint.activate([hotbar.leadingAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.leadingAnchor),hotbar.trailingAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.trailingAnchor),hotbar.topAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.topAnchor),hotbar.bottomAnchor.constraint(equalTo:hotbarScroll.contentLayoutGuide.bottomAnchor),hotbar.heightAnchor.constraint(equalTo:hotbarScroll.frameLayoutGuide.heightAnchor),hotbar.widthAnchor.constraint(greaterThanOrEqualToConstant:444)])
        for b in slotButtons {b.widthAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true}
        let left=directionButton("◀","moveLeft"),forward=directionButton("▲","moveForward"),back=directionButton("▼","moveBack"),right=directionButton("▶","moveRight")
        let directions=row([left,forward,back,right])
        let actions=row([button("Break","breakButton",#selector(breakBlock)),button("Place","placeButton",#selector(placeBlock)),button("Jump","jumpButton",#selector(jump))])
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
    @objc private func startMove(_ sender:UIButton){if !paused {movement.insert(sender.accessibilityIdentifier ?? "")}}
    @objc private func stopMove(_ sender:UIButton){movement.remove(sender.accessibilityIdentifier ?? "")}
    @objc private func selectSlot(_ sender:UIButton){world?.slot=sender.tag;refreshHUD()}
    @objc private func jump(){
        if !paused && !renderer.planning,let world=world,world.grounded {
            jumpOrigin=world.position.y;jumpRose=false;world.jump()
        }
    }
    @objc private func togglePause(){paused.toggle();movement.removeAll();pauseButton?.setTitle(paused ? "Resume":"Pause",for:.normal);notify(paused ? "Paused. The online city keeps running.":"Resumed.");saveOffline()}
    @objc private func togglePlan(){guard let world=world,world.snapshot.city != nil else{return};renderer.planning.toggle();lastPoll=0;renderer.focus=world.position;points.removeAll();renderer.mark(points,world:world);movement.removeAll();refreshHUD()}
    @objc private func chooseTool(){
        let alert=UIAlertController(title:"City tool",message:"Tap ground to choose points. Roads need two points; zones need at least three. The server checks cost and space.",preferredStyle:.actionSheet)
        for (index,title) in ["Inspect","Dirt road","Residential","Commercial","Industrial","Agricultural"].enumerated(){alert.addAction(UIAlertAction(title:title,style:.default){[weak self] _ in self?.tool=index;self?.points.removeAll();self?.notify("\(title) selected.")})}
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));anchor(alert);present(alert,animated:true)
    }
    @objc private func addCoordinatePoint(){
        guard renderer.planning else{return}
        let alert=UIAlertController(title:"Plan point",message:"Add a road endpoint or zone corner on the world grid.",preferredStyle:.alert)
        for name in ["X","Z"] {alert.addTextField{field in field.placeholder=name;field.accessibilityIdentifier="point"+name;field.keyboardType = .numbersAndPunctuation}}
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel))
        alert.addAction(UIAlertAction(title:"Add",style:.default){[weak self] _ in
            guard let self=self,let x=Float(alert.textFields?[0].text ?? ""),let z=Float(alert.textFields?[1].text ?? ""),x.isFinite,z.isFinite,abs(x)<999_970,abs(z)<999_970,self.points.count<32 else{self?.notify("Enter valid X and Z coordinates.");return}
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
        guard !inMenu,!paused,renderer.planning,let world=world,let point=renderer.point(at:gesture.location(in:renderer.view)) else{return}
        if tool==0 {
            let c=world.snapshot.city?.citizens.min{hypot($0.x-point.x,$0.z-point.z)<hypot($1.x-point.x,$1.z-point.z)}
            if let c=c,hypot(c.x-point.x,c.z-point.z)<4 {notify("\(c.name): \(c.activity), $\(Int(c.money)), hunger \(Int(c.hunger))")}
            else {notify("Ground \(Int(floor(point.x))), \(Int(floor(point.z))).")}
        } else if points.count<32 {points.append([Float(floor(point.x)),Float(floor(point.z))]);renderer.mark(points,world:world);notify("\(points.count) plan points.")}
    }
    @objc private func pan(_ gesture:UIPanGestureRecognizer){guard !inMenu,!paused,!renderer.planning else{return};let d=gesture.translation(in:renderer.view);world?.look(dx:Float(d.x),dy:Float(d.y));gesture.setTranslation(.zero,in:renderer.view)}
    @objc private func panPlanning(_ gesture:UIPanGestureRecognizer){guard !inMenu,!paused,renderer.planning else{return};let d=gesture.translation(in:renderer.view)
        renderer.focus.x-=Float(d.x+d.y)*renderer.distance/500;renderer.focus.z+=Float(d.x-d.y)*renderer.distance/500
        renderer.focus.x=min(999_970,max(-999_970,renderer.focus.x));renderer.focus.z=min(999_970,max(-999_970,renderer.focus.z));gesture.setTranslation(.zero,in:renderer.view)
    }
    @objc private func pinch(_ gesture:UIPinchGestureRecognizer){if renderer.planning {renderer.distance=min(80,max(12,renderer.distance/Float(gesture.scale)));gesture.scale=1}}
    @objc private func breakBlock(){edit(place:false)}
    @objc private func placeBlock(){edit(place:true)}
    private func edit(place:Bool){
        guard !paused,!renderer.planning,let world=world else{return}
        let type=place ? world.snapshot.inventory[world.slot][0]:0
        if place && type==0 {notify("This hotbar slot is empty.");return}
        guard let target=world.target() else{notify("Aim at a block within five blocks.");return}
        let key=place ? target.previous:target.hit
        if gateway != nil { action(["kind":"edit","blockX":key.x,"blockY":key.y,"blockZ":key.z,"type":type,"slot":world.slot],success:place ? "Placement checked by server.":"Break checked by server.") }
        else {do {try world.offlineEdit(at:key,type:type);renderer.rebuild(world);renderer.mark(points,world:world);try OfflineSave.write(world);notify(place ? "Placed \(VoxelWorld.name(type)).":"Block collected.");refreshHUD()}catch{notify(error.localizedDescription)}}
    }
    private func action(_ request:[String:Any],success:String){
        guard let gateway=gateway,let world=world else{return}
        if busy {queuedAction=(request,success);notify("Action queued.");return}
        busy=true;movement.removeAll();let id=sessionID
        Task {do {
            // Flush current player pose before the reach-checked action. Planning never moves the player.
            _ = try await gateway.state(world,focus:nil)
            let state=try await gateway.action(request);guard id==sessionID else{return}
            var visible=state
            if renderer.planning {visible=try await gateway.state(world,focus:SCNFocus(x:renderer.focus.x,z:renderer.focus.z))}
            guard id==sessionID else{return};try world.replace(visible);renderer.rebuild(world);renderer.mark(points,world:world)
            notify(state.notice.isEmpty ? success:state.notice)
        }catch{if id==sessionID {notify(error.localizedDescription)}};if id==sessionID {busy=false;refreshHUD();runQueuedAction()}}
    }
    private func runQueuedAction(){if let item=queuedAction {queuedAction=nil;action(item.0,success:item.1)}}
    @objc private func inventory(){
        guard let world=world else{return};movement.removeAll()
        let alert=UIAlertController(title:"Inventory",message:"Choose a stack to swap into hotbar slot \(world.slot+1).",preferredStyle:.actionSheet)
        for i in 0..<36 where world.snapshot.inventory[i][1]>0 {
            let item=world.snapshot.inventory[i];alert.addAction(UIAlertAction(title:"\(i+1): \(VoxelWorld.name(item[0])) ×\(item[1])",style:.default){[weak self] _ in
                guard let self=self else{return}
                if self.gateway != nil {self.action(["kind":"swap","a":i,"b":world.slot],success:"Inventory updated.")}
                else {world.snapshot.inventory.swapAt(i,world.slot);self.saveOffline();self.refreshHUD()}
            })
        }
        alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));anchor(alert);present(alert,animated:true)
    }
    private func anchor(_ alert:UIAlertController){alert.popoverPresentationController?.sourceView=view;alert.popoverPresentationController?.sourceRect=CGRect(x:view.bounds.midX,y:view.bounds.midY,width:1,height:1)}
    @objc private func frame(_ link:CADisplayLink){
        guard !inMenu,active,let world=world else{lastTime=0;return}
        let dt=lastTime==0 ? Float(1.0/30):Float(link.timestamp-lastTime);lastTime=link.timestamp
        if !paused && !renderer.planning {
            let forward:Float=(movement.contains("moveForward") ? 1:0)-(movement.contains("moveBack") ? 1:0)
            let side:Float=(movement.contains("moveRight") ? 1:0)-(movement.contains("moveLeft") ? 1:0)
            world.step(forward:forward,strafe:side,dt:dt)
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
            Task {do {try await gateway.move(world)}catch {if id==sessionID {notify(error.localizedDescription);paused=true;movement.removeAll();pauseButton?.setTitle("Resume",for:.normal)}};if id==sessionID {moveBusy=false}}
        }
        if let gateway=gateway,!busy,link.timestamp-lastPoll>1.5 {
            lastPoll=link.timestamp;busy=true;let id=sessionID
            let focus=renderer.planning ? SCNFocus(x:renderer.focus.x,z:renderer.focus.z):nil
            Task {do{let state=try await gateway.state(world,focus:focus);guard id==sessionID else{return};try world.replace(state);renderer.rebuild(world);renderer.mark(points,world:world)}catch{
                if id==sessionID {notify(error.localizedDescription);paused=true;movement.removeAll();pauseButton?.setTitle("Resume",for:.normal)}
            };if id==sessionID {busy=false;refreshHUD();runQueuedAction()}}
        }
        if gateway==nil,link.timestamp-lastSave>5 {lastSave=link.timestamp;saveOffline()}
        refreshHUD()
    }
    private func refreshHUD(){guard let world=world,!inMenu else{return}
        if inMenu {return}
        let mode=gateway==nil ? "Offline sandbox":"Online \(world.snapshot.game)"
        if CACurrentMediaTime()-messageTime<7 {status.text=message}
        else {status.text="\(mode) · HP \(world.snapshot.health) · \(renderer.planning ? "Two fingers pan; pinch zoom":"Drag world to look")"}
        positionLabel.isHidden=view.bounds.width>view.bounds.height
        status.numberOfLines=view.bounds.width>view.bounds.height ? 1:2
        cityLabel.numberOfLines=view.bounds.width>view.bounds.height ? 1:2
        positionLabel.text=String(format:"Position %.1f, %.1f, %.1f · Aim %.2f, %.2f",world.position.x,world.position.y,world.position.z,world.yaw,world.pitch)+(world.grounded ? " · Ground":" · Air")
        cityLabel.text=world.snapshot.city.map{String(format:"%@ · $%.0f · %d roads · %d zones",$0.time,$0.treasury,$0.roads.count,$0.zones)} ?? "49×49 local terrain · edits save on this phone"
        cityLabel.isHidden=world.snapshot.city==nil && view.bounds.width>view.bounds.height
        for (i,b) in slotButtons.enumerated(){let item=world.snapshot.inventory[i];b.setTitle(item[0]==0 ? "—":"\(String(VoxelWorld.name(item[0]).prefix(3)))\n\(item[1])",for:.normal);b.titleLabel?.numberOfLines=2;b.titleLabel?.textAlignment = .center;b.accessibilityLabel="\(VoxelWorld.name(item[0])) slot \(i+1), \(item[1]) blocks";b.layer.borderWidth=i==world.slot ? 2:0;b.layer.borderColor=UIColor.systemYellow.cgColor}
        if let rows=bottom?.arrangedSubviews,rows.count>=4 {rows[1].isHidden = !renderer.planning;rows[2].isHidden=renderer.planning;rows[3].isHidden=renderer.planning}
        crosshair.isHidden=renderer.planning;planButton?.setTitle(renderer.planning ? "Walk":"Plan",for:.normal)
    }
    private func notify(_ text:String){message=text;messageTime=CACurrentMediaTime();if !inMenu {status.text=text}}
    func suspend(){active=false;movement.removeAll();saveOffline()}
    func becameActive(){active=true;lastTime=0}
    func saveOffline(){guard gateway==nil,let world=world else{return};do {try OfflineSave.write(world)}catch{notify("Save failed. Keep this session open and try again.")}}
    @objc private func openMenu(){saveOffline();movement.removeAll();let previous=gateway;sessionID=UUID();queuedAction=nil;busy=false;moveBusy=false;gateway=nil;world=nil;Task{await previous?.logout()};showMenu()}
    private func showMenu(){
        inMenu=true;paused=false;top?.removeFromSuperview();bottom?.removeFromSuperview();crosshair.removeFromSuperview();menu?.removeFromSuperview();movement.removeAll()
        let scroll=UIScrollView();scroll.translatesAutoresizingMaskIntoConstraints=false;scroll.backgroundColor=UIColor(red:0.035,green:0.075,blue:0.1,alpha:0.98);view.addSubview(scroll);menu=scroll
        NSLayoutConstraint.activate([scroll.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor),scroll.bottomAnchor.constraint(equalTo:view.safeAreaLayoutGuide.bottomAnchor),scroll.leadingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.leadingAnchor),scroll.trailingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.trailingAnchor)])
        address.placeholder="https://your-game-gateway.example";address.text=ProcessInfo.processInfo.environment["VOXEL_TEST_GATEWAY"] ?? UserDefaults.standard.string(forKey:"gatewayAddress");address.accessibilityIdentifier="gatewayAddress";address.keyboardType = .URL;address.autocapitalizationType = .none;address.autocorrectionType = .no
        username.placeholder="Username";username.accessibilityIdentifier="username";username.autocapitalizationType = .none;username.autocorrectionType = .no;username.textContentType = .username
        password.placeholder="Password";password.accessibilityIdentifier="password";password.isSecureTextEntry=true;password.textContentType = .password;password.text=""
        for field in [address,username,password] {field.delegate=self;field.returnKeyType = .done;field.borderStyle = .roundedRect;field.backgroundColor = .white;field.textColor = .black;field.heightAnchor.constraint(equalToConstant:44).isActive=true}
        game.selectedSegmentIndex=0;game.accessibilityIdentifier="gameChoice";game.backgroundColor = .systemGray5
        let title=label("VOXEL ONE",size:30,id:"mainTitle")
        let stack=UIStackView(arrangedSubviews:[title,label("Native iPhone · explore, build and plan",size:15),button("Continue offline sandbox","offlineResumeButton",#selector(resumeOffline)),button("New offline sandbox","offlineNewButton",#selector(newOffline)),label("Online games",size:20),game,address,username,password,row([button("Sign in","signInButton",#selector(signIn)),button("Create account","registerButton",#selector(register))]),label("Online play needs the Voxel One HTTPS mobile gateway. City planning, citizens and economy run on the same game server.\n\nOffline sandbox is a saved local patch of Voxel One terrain. The desktop editor and fractional blocks are not available in this first phone client.",size:13)])
        stack.axis = .vertical;stack.spacing=12;stack.translatesAutoresizingMaskIntoConstraints=false;scroll.addSubview(stack)
        NSLayoutConstraint.activate([stack.topAnchor.constraint(equalTo:scroll.contentLayoutGuide.topAnchor,constant:24),stack.bottomAnchor.constraint(equalTo:scroll.contentLayoutGuide.bottomAnchor,constant:-24),stack.leadingAnchor.constraint(equalTo:scroll.contentLayoutGuide.leadingAnchor,constant:24),stack.trailingAnchor.constraint(equalTo:scroll.contentLayoutGuide.trailingAnchor,constant:-24),stack.widthAnchor.constraint(equalTo:scroll.frameLayoutGuide.widthAnchor,constant:-48)])
    }
    @objc private func resumeOffline(){do {try startOffline(OfflineSave.load())}catch{showError(error.localizedDescription)}}
    @objc private func newOffline(){
        if FileManager.default.fileExists(atPath:OfflineSave.url.path) {let alert=UIAlertController(title:"Replace offline sandbox?",message:"This deletes the local sandbox edits. Online worlds stay on their server.",preferredStyle:.alert);alert.addAction(UIAlertAction(title:"Cancel",style:.cancel));alert.addAction(UIAlertAction(title:"New world",style:.destructive){[weak self] _ in self?.createOffline()});present(alert,animated:true)}else{createOffline()}
    }
    private func createOffline(){do {try startOffline(OfflineSave.fresh())}catch{showError(error.localizedDescription)}}
    @objc private func signIn(){connect(register:false)}
    @objc private func register(){connect(register:true)}
    private func connect(register:Bool){
        guard !busy else{return}
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
