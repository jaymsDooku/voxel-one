import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Decode actual captured media; save a final frame for visual inspection. */
public class VerifyRoadVideo {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);
        try(var channel=NIOUtils.readableChannel(file.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);
            var meta=grab.getVideoTrack().getMeta();
            if(meta.getCodec()!=Codec.H264 || meta.getTotalFrames()<2) throw new AssertionError("H264 video missing frames");
            double duration=meta.getTotalDuration();
            int w=0,h=0;
            for(double t:new double[]{0,duration/2,Math.max(0,duration-2)}) {
                grab.seekToSecondPrecise(t);
                var decoded=grab.getNativeFrame();
                if(decoded==null) throw new AssertionError("Video did not decode at "+t);
                w=decoded.getWidth();h=decoded.getHeight();
                var rgb=Picture.create(w,h,ColorSpace.RGB);
                ColorUtil.getTransform(decoded.getColor(),ColorSpace.RGB).transform(decoded,rgb);
                byte[] bytes=rgb.getPlaneData(0);
                var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                    int i=(y*w+x)*3;
                    image.setRGB(x,y,(bytes[i]+128)<<16 | (bytes[i+1]+128)<<8 | bytes[i+2]+128);
                }
                ImageIO.write(image,"png",Path.of(args[1]).toFile());
            }
            String report="{\"status\":\"passed\",\"codec\":\"H264\",\"width\":"+w+",\"height\":"+h+",\"frames\":"+meta.getTotalFrames()+",\"seconds\":"+duration+",\"bytes\":"+Files.size(file)+",\"decoded\":\"start, middle, two seconds before end\"}\n";
            Files.writeString(Path.of(args[2]),report);
            System.out.println(report);
        }
    }
}
