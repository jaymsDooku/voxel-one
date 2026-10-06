import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;

/** Independently decode native F10 evidence and export a representative recorded frame. */
public final class RoadSpacingMediaCheck {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]),image=Path.of(args[1]),report=Path.of(args[2]);
        if(Files.size(file)>=6_000_000)throw new AssertionError("Clip exceeds 6 MB");
        try(var channel=NIOUtils.readableChannel(file.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();
            if(meta.getTotalFrames()<3 || meta.getTotalDuration()<3)throw new AssertionError("Recording too short");
            BufferedImage result=null;
            for(double fraction:new double[]{0,.5,.95}) {
                grab.seekToSecondPrecise(meta.getTotalDuration()*fraction);
                var decoded=grab.getNativeFrame();if(decoded==null)throw new AssertionError("Undecodable frame");
                var rgb=Picture.create(decoded.getWidth(),decoded.getHeight(),ColorSpace.RGB);
                ColorUtil.getTransform(decoded.getColor(),ColorSpace.RGB).transform(decoded,rgb);
                if(fraction==.5) {
                    result=new BufferedImage(rgb.getWidth(),rgb.getHeight(),BufferedImage.TYPE_INT_RGB);
                    var pixels=rgb.getPlaneData(0);
                    for(int y=0;y<rgb.getHeight();y++)for(int x=0;x<rgb.getWidth();x++) {
                        int i=(y*rgb.getWidth()+x)*3;
                        result.setRGB(x,y,((pixels[i]+128)<<16)|((pixels[i+1]+128)<<8)|(pixels[i+2]+128));
                    }
                }
            }
            ImageIO.write(result,"png",image.toFile());
            Files.writeString(report,"{\"status\":\"passed\",\"frames\":"+meta.getTotalFrames()+",\"seconds\":"+meta.getTotalDuration()+",\"bytes\":"+Files.size(file)+",\"width\":"+result.getWidth()+",\"height\":"+result.getHeight()+",\"decodedFractions\":[0,0.5,0.95]}\n");
        }
    }
}
