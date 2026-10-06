import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class CargoFinalClipCheck {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);
        try(var channel=NIOUtils.readableChannel(file.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);
            var meta=grab.getVideoTrack().getMeta();
            if(meta.getTotalFrames()<2 || meta.getTotalDuration()<=0 || Files.size(file)>=6_000_000) throw new AssertionError("Invalid clip");
            for(double fraction:new double[]{.2,.45,.8,.95}) {
                var picture=grab.seekToSecondPrecise(meta.getTotalDuration()*fraction).getNativeFrame();
                var rgb=Picture.create(picture.getWidth(),picture.getHeight(),ColorSpace.RGB);
                ColorUtil.getTransform(picture.getColor(),ColorSpace.RGB).transform(picture,rgb);
                var data=rgb.getPlaneData(0);
                var image=new BufferedImage(rgb.getWidth(),rgb.getHeight(),BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<rgb.getHeight();y++) for(int x=0;x<rgb.getWidth();x++) {
                    int i=(y*rgb.getWidth()+x)*3;
                    image.setRGB(x,y,((data[i]+128)&255)<<16|((data[i+1]+128)&255)<<8|((data[i+2]+128)&255));
                }
                ImageIO.write(image,"png",Path.of("target/cargo-final-clip-"+(int)(fraction*100)+".png").toFile());
            }
            String report="{\"status\":\"passed\",\"frames\":"+meta.getTotalFrames()+",\"seconds\":"+meta.getTotalDuration()+",\"bytes\":"+Files.size(file)+",\"decodedSamples\":[0.2,0.45,0.8,0.95]}\n";
            Files.writeString(Path.of("dashboard/evidence/cargo-final-video-check.json"),report);
            System.out.print(report);
        }
    }
}
