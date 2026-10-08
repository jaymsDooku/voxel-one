import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import java.nio.file.*;

/** Independently decode every frame of the finished native F10 clip. */
public final class VerifyMissileMedia {
    public static void main(String[] args) throws Exception {
        Path clip=Path.of(args[0]);long bytes=Files.size(clip);
        if(bytes<=0 || bytes>6_000_000)throw new AssertionError("Clip size outside artifact limit");
        try(var channel=NIOUtils.readableChannel(clip.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();
            if(meta.getCodec()!=Codec.H264 || meta.getTotalDuration()<=0)throw new AssertionError("Invalid MP4 codec or duration");
            int frames=0,width=0,height=0;long first=0,last=0;
            for(var picture=grab.getNativeFrame();picture!=null;picture=grab.getNativeFrame()) {
                width=picture.getWidth();height=picture.getHeight();long hash=1;
                for(byte value:picture.getPlaneData(0))hash=31*hash+value;
                if(frames==0)first=hash;last=hash;
                if(frames==meta.getTotalFrames()/2) {
                    var rgb=org.jcodec.common.model.Picture.create(width,height,org.jcodec.common.model.ColorSpace.RGB);
                    org.jcodec.scale.ColorUtil.getTransform(picture.getColor(),rgb.getColor()).transform(picture,rgb);
                    var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);
                    byte[] data=rgb.getPlaneData(0);
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                        int k=(y*width+x)*3;
                        image.setRGB(x,y,((data[k]+128)&255)<<16|((data[k+1]+128)&255)<<8|((data[k+2]+128)&255));
                    }
                    javax.imageio.ImageIO.write(image,"png",Path.of(args[1]).resolveSibling("city-nuke-video-frame.png").toFile());
                }
                frames++;
            }
            if(frames<3 || frames!=meta.getTotalFrames() || first==last)throw new AssertionError("Missing or static decoded frames");
            Files.writeString(Path.of(args[1]),"{\"status\":\"passed\",\"source\":\"production F10 recorder in native City Builder missile playtest\",\"codec\":\"H264\",\"bytes\":"+bytes+",\"decodedFrames\":"+frames+",\"width\":"+width+",\"height\":"+height+",\"durationSeconds\":"+meta.getTotalDuration()+",\"checks\":[\"Every frame decoded\",\"Frame count matches MP4 metadata\",\"First and last images differ\",\"Below 6 MB artifact limit\"]}\n");
            System.out.println("Media PASS: "+frames+" frames decoded, "+bytes+" bytes");
        }
    }
}
