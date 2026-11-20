import Foundation

/// Optional saved offline clock. Defaults match Java GameConfig.sandbox():
/// fixed10:00,1200 seconds/day. Only active foreground time may advance it.
struct NativeWorldTime:Codable,Equatable,Sendable {
    var version=1;var day=1;var hour=10.0;var daySeconds=1200.0;var cycling=false
    func validate()throws {
        guard version==1,(1...1_000_000_000).contains(day),hour.isFinite,(0..<24).contains(hour),
              daySeconds.isFinite,(60...86400).contains(daySeconds) else {throw GameError.invalidWorld}
    }
    mutating func advance(seconds:Double){
        guard cycling,seconds.isFinite,seconds>0,hour.isFinite,(0..<24).contains(hour),
              daySeconds.isFinite,(60...86400).contains(daySeconds) else{return}
        let next=hour+min(1,seconds)*24/daySeconds
        if next>=24 {
            guard day<1_000_000_000 else{cycling=false;return}
            day+=1;hour=next-24
        }else{hour=next}
    }
    /// Same normalized vector as Java MobileSnapshot; no desktop runtime required.
    var sun:[Double] {
        let angle=(hour-6)/24*2*Double.pi,length=sqrt(1+0.35*0.35)
        return [cos(angle)/length,sin(angle)/length,-0.35/length]
    }
    var period:String {hour<6 || hour>=19 ? "Night":hour<8 ? "Dawn":hour>=17 ? "Dusk":"Daylight"}
    var label:String {String(format:"Day %d · %02d:%02d",day,Int(hour),Int(hour.truncatingRemainder(dividingBy:1)*60))}
    static func preserving(_ sun:[Double]?)->NativeWorldTime {
        var value=NativeWorldTime()
        if let sun=sun,sun.count==3 {
            value.hour=(atan2(sun[1],sun[0])*24/(2*Double.pi)+6+24).truncatingRemainder(dividingBy:24)
        }
        return value
    }
}

/// Cheap fallback lighting, independent of optional planetary scattering.
/// These artistic intensities preserve night visibility; they are not photometry.
struct NativeDaylight {
    var ambient:Double;var direct:Double;var sky:[Double];var tint:[Double]
    init(height:Double){
        let h=height.isFinite ? height:0,daylight=min(1,max(0,(h+0.08)/0.38))
        ambient=75+575*daylight;direct=950*max(0,min(1,h/0.45))
        let night=[0.015,0.025,0.06],day=[0.48,0.73,0.9]
        sky=(0..<3).map{night[$0]+(day[$0]-night[$0])*daylight}
        let warm=1-min(1,max(0,h/0.4))
        tint=[1,1-0.25*warm,1-0.55*warm]
    }
}
