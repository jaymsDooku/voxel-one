import dev.jayms.audio.VehicleAudio;
import dev.jayms.net.city.*;
import org.joml.Vector3f;

/** Explicitly unavailable output must not prevent update or cleanup. */
public final class VehicleAudioDeviceCheck {
    public static void main(String[] args) {
        try(var audio=new VehicleAudio()) {
            if(audio.available())throw new AssertionError("This check requires the intentionally unavailable output driver");
            audio.update(null,null,new Vector3f(),false);
            audio.close();audio.close();
        }
        System.out.println("PASS: missing output device is nonfatal; update and repeated close succeed");
    }
}
