import Foundation
import UIKit

/// Per-device touch preferences. No world, network or account state is stored.
struct NativeControls:Codable,Equatable {
    var version=1
    var sensitivity=1.0
    var invertY=false
    var bindings=NativeControls.actions
    static let actions=["moveLeft","moveForward","moveBack","moveRight"]
    static let names=["Left","Forward","Back","Right"]
    static let arrows=["◀","▲","▼","▶"]
    static var url:URL {FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("voxel-one-controls.json")}
    func validated()->NativeControls {
        guard version==1 else{return NativeControls()}
        var value=self
        if ![0.5,1,2].contains(sensitivity){value.sensitivity=1}
        if bindings.count != 4 || Set(bindings) != Set(Self.actions){value.bindings=Self.actions}
        return value
    }
    mutating func bind(slot:Int,to action:String){
        self=validated()
        guard bindings.indices.contains(slot),let other=bindings.firstIndex(of:action) else{return}
        bindings.swapAt(slot,other)
    }
    func lookDelta(dx:Float,dy:Float)->SIMD2<Float> {
        guard dx.isFinite,dy.isFinite else{return .zero}
        let scale:Float=[0.5,1,2].contains(sensitivity) ? Float(sensitivity):1
        return SIMD2(min(4096,max(-4096,dx))*scale,min(4096,max(-4096,dy))*scale*(invertY ? -1:1))
    }
    static func load(from url:URL=NativeControls.url)->NativeControls {
        guard let size=try? url.resourceValues(forKeys:[.fileSizeKey]).fileSize,size<=4096,
              let data=try? Data(contentsOf:url),data.count<=4096,
              let value=try? JSONDecoder().decode(NativeControls.self,from:data) else{return NativeControls()}
        return value.validated()
    }
    func save(to url:URL=NativeControls.url)throws {
        try FileManager.default.createDirectory(at:url.deletingLastPathComponent(),withIntermediateDirectories:true)
        try JSONEncoder().encode(validated()).write(to:url,options:.atomic)
    }
}

@MainActor
final class NativeControlsScreen:UIViewController {
    private var draft:NativeControls
    private let applyProfile:(NativeControls)throws->Void
    var onClose:(()->Void)?
    private let stack=UIStackView(),status=UILabel()
    init(_ profile:NativeControls,apply:@escaping (NativeControls)throws->Void){draft=profile.validated();applyProfile=apply;super.init(nibName:nil,bundle:nil)}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    override func viewDidLoad(){
        super.viewDidLoad();view.backgroundColor = .systemBackground;isModalInPresentation=true
        let scroll=UIScrollView();scroll.accessibilityIdentifier="controlsScroll";scroll.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(scroll)
        stack.axis = .vertical;stack.spacing=12;stack.translatesAutoresizingMaskIntoConstraints=false;scroll.addSubview(stack)
        NSLayoutConstraint.activate([scroll.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor),scroll.bottomAnchor.constraint(equalTo:view.safeAreaLayoutGuide.bottomAnchor),scroll.leadingAnchor.constraint(equalTo:view.leadingAnchor),scroll.trailingAnchor.constraint(equalTo:view.trailingAnchor),stack.topAnchor.constraint(equalTo:scroll.contentLayoutGuide.topAnchor,constant:20),stack.bottomAnchor.constraint(equalTo:scroll.contentLayoutGuide.bottomAnchor,constant:-20),stack.leadingAnchor.constraint(equalTo:scroll.contentLayoutGuide.leadingAnchor,constant:20),stack.trailingAnchor.constraint(equalTo:scroll.contentLayoutGuide.trailingAnchor,constant:-20),stack.widthAnchor.constraint(equalTo:scroll.frameLayoutGuide.widthAnchor,constant:-40)])
        refresh()
    }
    private func label(_ text:String)->UILabel {let l=UILabel();l.text=text;l.numberOfLines=0;l.font = .preferredFont(forTextStyle:.body);l.adjustsFontForContentSizeCategory=true;return l}
    private func button(_ title:String,_ id:String,_ action:Selector){let b=UIButton(type:.system);b.setTitle(title,for:.normal);b.accessibilityIdentifier=id;b.addTarget(self,action:action,for:.touchUpInside);b.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;stack.addArrangedSubview(b)}
    private func refresh(){
        stack.arrangedSubviews.forEach{$0.removeFromSuperview()}
        let title=label("Touch controls");title.font = .preferredFont(forTextStyle:.title1);stack.addArrangedSubview(title)
        stack.addArrangedSubview(label("Drag the world to look. Hold movement and Jump together. Release, pause or leave the app to stop held input."))
        stack.addArrangedSubview(label("Look sensitivity"))
        let sensitivity=UISegmentedControl(items:["0.5x","1x","2x"]);sensitivity.accessibilityIdentifier="controlsSensitivity";sensitivity.selectedSegmentIndex=[0.5,1,2].firstIndex(of:draft.sensitivity) ?? 1;sensitivity.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;sensitivity.addTarget(self,action:#selector(sensitivityChanged(_:)),for:.valueChanged);stack.addArrangedSubview(sensitivity)
        let row=UIStackView();row.spacing=12;row.addArrangedSubview(label("Invert vertical look"));let invert=UISwitch();invert.accessibilityIdentifier="controlsInvertY";invert.isOn=draft.invertY;invert.addTarget(self,action:#selector(invertChanged(_:)),for:.valueChanged);row.addArrangedSubview(invert);stack.addArrangedSubview(row)
        stack.addArrangedSubview(label("Movement buttons, left to right. Choosing an action swaps its old button, so all four directions remain available. Jump, Down, Break and Place keep their own buttons."))
        for slot in 0..<4 {
            stack.addArrangedSubview(label("Button \(slot+1)"))
            let binding=UISegmentedControl(items:NativeControls.names);binding.accessibilityIdentifier="controlsBinding\(slot)";binding.tag=slot;binding.selectedSegmentIndex=NativeControls.actions.firstIndex(of:draft.bindings[slot]) ?? slot;binding.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;binding.addTarget(self,action:#selector(bindingChanged(_:)),for:.valueChanged);stack.addArrangedSubview(binding)
        }
        status.numberOfLines=0;status.accessibilityIdentifier="controlsStatus";status.text="Draft · \(draft.sensitivity)x look · \(draft.invertY ? "Inverted":"Normal") vertical. Apply saves on this phone. Cancel keeps current controls.";stack.addArrangedSubview(status)
        button("Apply","controlsApply",#selector(apply));button("Cancel","controlsCancel",#selector(cancel));button("Reset defaults (draft)","controlsReset",#selector(reset))
    }
    @objc private func sensitivityChanged(_ sender:UISegmentedControl){draft.sensitivity=[0.5,1,2][sender.selectedSegmentIndex];refresh()}
    @objc private func invertChanged(_ sender:UISwitch){draft.invertY=sender.isOn;refresh()}
    @objc private func bindingChanged(_ sender:UISegmentedControl){draft.bind(slot:sender.tag,to:NativeControls.actions[sender.selectedSegmentIndex]);refresh()}
    @objc private func reset(){draft=NativeControls();refresh()}
    @objc private func apply(){do{try applyProfile(draft.validated());dismiss(animated:true){self.onClose?()}}catch{status.text="Could not save controls. Kept current controls. Try Apply again or Cancel."}}
    @objc private func cancel(){dismiss(animated:true){self.onClose?()}}
}
