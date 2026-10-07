import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Decode the finished native F10 recording and export a frame for manual inspection. */
public final class VerifyStressGridMedia {
    public static void main(String[] args) throws Exception {
        Path clip=Path.of(args[0]); long size=Files.size(clip);
        if(size<=0 || size>6000000) throw new AssertionError("Recording outside artifact size limit");
        try(var channel=NIOUtils.readableChannel(clip.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);var metadata=grab.getVideoTrack().getMeta();
            if(metadata.getCodec()!=Codec.H264 || metadata.getTotalDuration()<=0) throw new AssertionError("Invalid MP4 metadata");
            int frames=0,width=0,height=0;long first=0,last=0;
            for(var picture=grab.getNativeFrame();picture!=null;picture=grab.getNativeFrame()) {
                width=picture.getWidth();height=picture.getHeight();long hash=1;
                for(byte b:picture.getPlaneData(0)) hash=31*hash+b;
                if(frames==0) first=hash;last=hash;
                if(frames==metadata.getTotalFrames()/2) {
                    var rgb=Picture.create(width,height,ColorSpace.RGB);
                    ColorUtil.getTransform(picture.getColor(),ColorSpace.RGB).transform(picture,rgb);
                    byte[] bytes=rgb.getPlaneData(0);var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                        int i=(y*width+x)*3;image.setRGB(x,y,(bytes[i]+128)<<16 | (bytes[i+1]+128)<<8 | bytes[i+2]+128);
                    }
                    ImageIO.write(image,"png",Path.of(args[2]).toFile());
                }
                frames++;
            }
            if(frames<3 || frames!=metadata.getTotalFrames() || first==last) throw new AssertionError("Missing or static decoded frames");
            String report="{\"status\":\"passed\",\"source\":\"production F10 recorder in native Stress Grid playtest\",\"codec\":\"H264\",\"bytes\":"+size+",\"frames\":"+frames+",\"width\":"+width+",\"height\":"+height+",\"seconds\":"+metadata.getTotalDuration()+",\"checks\":[\"Every frame decoded\",\"Frame count matches metadata\",\"First and last images differ\",\"Below 6 MB\"]}\n";
            Files.writeString(Path.of(args[1]),report);System.out.println("PASS: decoded "+frames+" frames, "+size+" bytes");
        }
    }
}
