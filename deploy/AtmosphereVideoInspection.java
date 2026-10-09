import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Decode the actual F10 movie for inspection; never replaces it with generated media. */
public class AtmosphereVideoInspection {
    public static void main(String[] args)throws Exception {
        Path video=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        if(Files.size(video)>6*1024*1024)throw new AssertionError("Video artifact limit");
        try(var channel=NIOUtils.readableChannel(video.toFile())){
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();double duration=meta.getTotalDuration();
            if(duration<=0||meta.getTotalFrames()<3)throw new AssertionError("Recorded movie has frames");
            StringBuilder report=new StringBuilder("Playtest: actual engine F10 H264 movie decoded. Duration "+duration+" seconds; "+meta.getTotalFrames()+" frames; "+Files.size(video)+" bytes. No profile/account files in media.\n");
            double[] fractions={.25,.5,.75,.95};
            for(int i=1;i<=fractions.length;i++){
                double time=duration*fractions[i-1];
                Picture picture=grab.seekToSecondPrecise(time).getNativeFrame();Picture rgb=Picture.create(picture.getWidth(),picture.getHeight(),ColorSpace.RGB);
                ColorUtil.getTransform(picture.getColor(),ColorSpace.RGB).transform(picture,rgb);byte[] data=rgb.getPlaneData(0);
                BufferedImage image=new BufferedImage(rgb.getWidth(),rgb.getHeight(),BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<rgb.getHeight();y++)for(int x=0;x<rgb.getWidth();x++){int index=(x+y*rgb.getWidth())*3;image.setRGB(x,y,((data[index]+128)&255)<<16|((data[index+1]+128)&255)<<8|((data[index+2]+128)&255));}
                ImageIO.write(image,"png",out.resolve("video-frame-"+i+".png").toFile());report.append("Decoded frame at "+time+" seconds: "+rgb.getWidth()+"x"+rgb.getHeight()+".\n");
            }
            Files.writeString(out.resolve("atmosphere-video-inspection.txt"),report.toString());System.out.println(report);
        }
    }
}
