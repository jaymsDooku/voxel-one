import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;

/** Decode actual F10 evidence with the bundled codec. No game profile is read. */
public class InspectStreamingVideo {
    public static void main(String[] args)throws Exception {
        Path video=Path.of(args[0]),midpoint=Path.of(args[1]),report=Path.of(args[2]);
        long bytes=Files.size(video);if(bytes<=0||bytes>=6_000_000)throw new AssertionError("Video must be nonempty and below6MB");
        int frames=0,width=0,height=0,expected;double duration;
        try(var channel=NIOUtils.readableChannel(video.toFile())){
            var grabber=FrameGrab.createFrameGrab(channel);var meta=grabber.getVideoTrack().getMeta();
            expected=meta.getTotalFrames();duration=meta.getTotalDuration();Picture picture;
            while((picture=grabber.getNativeFrame())!=null){
                width=picture.getWidth();height=picture.getHeight();
                if(frames==expected/2){
                    var rgb=Picture.create(width,height,ColorSpace.RGB);ColorUtil.getTransform(picture.getColor(),ColorSpace.RGB).transform(picture,rgb);
                    byte[] data=rgb.getPlaneData(0);var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(x+y*width)*3;image.setRGB(x,y,((data[i]+128)&255)<<16|((data[i+1]+128)&255)<<8|((data[i+2]+128)&255));}
                    if(!ImageIO.write(image,"png",midpoint.toFile()))throw new AssertionError("PNG writer unavailable");
                }
                frames++;
            }
        }
        if(frames!=expected||frames<2||duration<=0)throw new AssertionError("Incomplete F10 decode: "+frames+"/"+expected);
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(video)));
        Files.writeString(report,String.format(Locale.ROOT,"{\"decodedFrames\":%d,\"durationSeconds\":%.6f,\"bytes\":%d,\"width\":%d,\"height\":%d,\"midpointIndex\":%d,\"videoSha256\":\"%s\",\"note\":\"Every image frame decoded with bundled JCodec; midpoint extracted without retiming. Visual inspection remains a separate step.\"}%n",frames,duration,bytes,width,height,frames/2,sha));
        System.out.println("Playtest: F10 clip decoded, frames="+frames+", bytes="+bytes+", durationSeconds="+duration);
    }
}
