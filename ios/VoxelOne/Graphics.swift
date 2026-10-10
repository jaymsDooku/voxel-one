import Foundation
import UIKit
import SceneKit
import Metal

/// Per-device preferences. No world, account or server state enters this file.
struct NativeGraphics:Codable,Equatable {
    var version=2
    var preset="Balanced"
    var samples=2
    var frames=30
    var detail=180
    var bloom=false
    var atmosphere=false
    init() {}
    private enum CodingKeys:String,CodingKey {case version,preset,samples,frames,detail,bloom,atmosphere}
    init(from decoder:Decoder)throws {
        let c=try decoder.container(keyedBy:CodingKeys.self)
        version=try c.decodeIfPresent(Int.self,forKey:.version) ?? 1
        preset=try c.decodeIfPresent(String.self,forKey:.preset) ?? "Custom"
        samples=try c.decodeIfPresent(Int.self,forKey:.samples) ?? 2
        frames=try c.decodeIfPresent(Int.self,forKey:.frames) ?? 30
        detail=try c.decodeIfPresent(Int.self,forKey:.detail) ?? 180
        bloom=try c.decodeIfPresent(Bool.self,forKey:.bloom) ?? false
        atmosphere=try c.decodeIfPresent(Bool.self,forKey:.atmosphere) ?? false
    }
    static var url:URL {FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("voxel-one-graphics.json")}
    func validated()->NativeGraphics {
        var p=self
        if version != 1 && version != 2 {return NativeGraphics()}
        p.version=2
        if !["Low","Balanced","High","Custom"].contains(p.preset){p.preset="Custom"}
        if ![0,2,4].contains(p.samples){p.samples=2}
        if ![30,60].contains(p.frames){p.frames=30}
        if ![32,64,128,180].contains(p.detail){p.detail=180}
        return p
    }
    static func load()->NativeGraphics {
        guard let size=try? url.resourceValues(forKeys:[.fileSizeKey]).fileSize,size<=4096,let data=try? Data(contentsOf:url),data.count<=4096,let p=try? JSONDecoder().decode(NativeGraphics.self,from:data) else{var p=NativeGraphics();if !FileManager.default.fileExists(atPath:url.path){p.atmosphere=UserDefaults.standard.bool(forKey:"planetAtmospherePreview")};return p}
        return p.validated()
    }
    func save()throws {try FileManager.default.createDirectory(at:Self.url.deletingLastPathComponent(),withIntermediateDirectories:true);try JSONEncoder().encode(validated()).write(to:Self.url,options:.atomic)}
    static func preset(_ name:String)->NativeGraphics {
        var p=NativeGraphics();p.preset=name
        if name=="Low" {p.samples=0}
        if name=="High" {p.samples=4;p.frames=60;p.bloom=true}
        // Atmosphere stays opt-in at all presets until a physical-device budget exists.
        return p
    }
    func effective()->NativeGraphics {
        var p=validated()
        let device=MTLCreateSystemDefaultDevice()
        while p.samples>0 && !(device?.supportsTextureSampleCount(p.samples) ?? false) {p.samples=p.samples==4 ? 2:0}
        p.frames=min(p.frames,max(30,UIScreen.main.maximumFramesPerSecond))
        return p
    }
}

@MainActor
final class NativeGraphicsScreen:UIViewController {
    private var draft:NativeGraphics
    private let applyProfile:(NativeGraphics)throws->Void
    var onClose:(()->Void)?
    private let stack=UIStackView(),status=UILabel()
    init(_ profile:NativeGraphics,apply:@escaping (NativeGraphics)throws->Void){draft=profile;applyProfile=apply;super.init(nibName:nil,bundle:nil)}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    override func viewDidLoad(){
        super.viewDidLoad();view.backgroundColor = .systemBackground;isModalInPresentation=true
        let scroll=UIScrollView();scroll.accessibilityIdentifier="graphicsScroll";scroll.translatesAutoresizingMaskIntoConstraints=false;view.addSubview(scroll)
        stack.axis = .vertical;stack.spacing=16;stack.translatesAutoresizingMaskIntoConstraints=false;scroll.addSubview(stack)
        NSLayoutConstraint.activate([scroll.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor),scroll.bottomAnchor.constraint(equalTo:view.safeAreaLayoutGuide.bottomAnchor),scroll.leadingAnchor.constraint(equalTo:view.leadingAnchor),scroll.trailingAnchor.constraint(equalTo:view.trailingAnchor),stack.topAnchor.constraint(equalTo:scroll.contentLayoutGuide.topAnchor,constant:20),stack.bottomAnchor.constraint(equalTo:scroll.contentLayoutGuide.bottomAnchor,constant:-20),stack.leadingAnchor.constraint(equalTo:scroll.contentLayoutGuide.leadingAnchor,constant:20),stack.trailingAnchor.constraint(equalTo:scroll.contentLayoutGuide.trailingAnchor,constant:-20),stack.widthAnchor.constraint(equalTo:scroll.frameLayoutGuide.widthAnchor,constant:-40)])
        refresh()
    }
    private func label(_ text:String)->UILabel {let l=UILabel();l.text=text;l.numberOfLines=0;l.font = .preferredFont(forTextStyle:.body);l.adjustsFontForContentSizeCategory=true;return l}
    private func choices(_ title:String,_ id:String,_ values:[String],_ index:Int,_ action:Selector){stack.addArrangedSubview(label(title));let c=UISegmentedControl(items:values);c.accessibilityIdentifier=id;c.selectedSegmentIndex=index;c.addTarget(self,action:action,for:.valueChanged);c.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;stack.addArrangedSubview(c)}
    private func toggle(_ title:String,_ id:String,_ value:Bool,_ action:Selector){let row=UIStackView();row.axis = .horizontal;row.spacing=12;let l=label(title);let s=UISwitch();s.accessibilityIdentifier=id;s.isOn=value;s.addTarget(self,action:action,for:.valueChanged);row.addArrangedSubview(l);row.addArrangedSubview(s);stack.addArrangedSubview(row)}
    private func button(_ title:String,_ id:String,_ action:Selector){let b=UIButton(type:.system);b.setTitle(title,for:.normal);b.accessibilityIdentifier=id;b.addTarget(self,action:action,for:.touchUpInside);b.heightAnchor.constraint(greaterThanOrEqualToConstant:44).isActive=true;stack.addArrangedSubview(b)}
    private func refresh(){
        stack.arrangedSubviews.forEach{$0.removeFromSuperview()}
        let title=label("Graphics");title.font = .preferredFont(forTextStyle:.title1);stack.addArrangedSubview(title)
        stack.addArrangedSubview(label("Local SceneKit controls. Detail changes rendering only. Quality presets are provisional; frame-rate values are caps, not measured performance."))
        choices("Preset","graphicsPreset",["Low","Balanced","High","Custom"],["Low","Balanced","High","Custom"].firstIndex(of:draft.preset) ?? 3,#selector(presetChanged(_:)))
        choices("Anti-aliasing","graphicsAA",["Off","2x","4x"],[0,2,4].firstIndex(of:draft.samples) ?? 1,#selector(aaChanged(_:)))
        choices("Frame-rate cap","graphicsFrames",["30 FPS","60 FPS"],draft.frames==60 ? 1:0,#selector(framesChanged(_:)))
        choices("Detail distance (blocks)","graphicsDetail",["32","64","128","180"],[32,64,128,180].firstIndex(of:draft.detail) ?? 3,#selector(detailChanged(_:)))
        toggle("Bloom","graphicsBloom",draft.bloom,#selector(bloomChanged(_:)))
        toggle("Experimental planet atmosphere (device budget pending)","graphicsAtmosphere",draft.atmosphere,#selector(atmosphereChanged(_:)))
        stack.addArrangedSubview(label("Desktop shadows, screen GI, dynamic resolution, texture filtering and display modes are unavailable in this renderer. Solid walls still block native sky light. No simulator FPS claim applies to a physical iPhone."))
        status.numberOfLines=0;status.accessibilityIdentifier="graphicsStatus";let e=draft.effective();status.text="Draft: \(draft.samples)x AA, \(draft.frames) FPS, \(draft.detail) blocks. Effective: \(e.samples)x AA, \(e.frames) FPS. Apply saves; Cancel keeps the current settings.";if e.samples != draft.samples {status.text! += " This device does not support the requested AA sample count."};if e.frames != draft.frames {status.text! += " The display limits the effective frame-rate cap."};stack.addArrangedSubview(status)
        button("Apply","graphicsApply",#selector(apply));button("Cancel","graphicsCancel",#selector(cancel));button("Reset defaults (draft)","graphicsReset",#selector(reset))
    }
    private func edited(){draft.preset="Custom";refresh()}
    @objc private func presetChanged(_ c:UISegmentedControl){let name=["Low","Balanced","High","Custom"][c.selectedSegmentIndex];if name=="Custom"{draft.preset=name}else{draft=NativeGraphics.preset(name)};refresh()}
    @objc private func aaChanged(_ c:UISegmentedControl){draft.samples=[0,2,4][c.selectedSegmentIndex];edited()}
    @objc private func framesChanged(_ c:UISegmentedControl){draft.frames=[30,60][c.selectedSegmentIndex];edited()}
    @objc private func detailChanged(_ c:UISegmentedControl){draft.detail=[32,64,128,180][c.selectedSegmentIndex];edited()}
    @objc private func bloomChanged(_ c:UISwitch){draft.bloom=c.isOn;edited()}
    @objc private func atmosphereChanged(_ c:UISwitch){draft.atmosphere=c.isOn;edited()}
    @objc private func reset(){draft=NativeGraphics();refresh()}
    @objc private func apply(){do{try applyProfile(draft.validated());dismiss(animated:true){self.onClose?()}}catch{status.text="Graphics apply failed. Kept the current settings."}}
    @objc private func cancel(){dismiss(animated:true){self.onClose?()}}
}
