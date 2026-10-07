import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Independently decodes every frame of the fresh production F10 clip. */
public class VerifyRenderingMedia {
    public static void main(String[] args)throws Exception {
        Path clip=Path.of(args[0]);long bytes=Files.size(clip);
        if(bytes<=0||bytes>6000000)throw new AssertionError("Artifact size limit");
        try(var channel=NIOUtils.readableChannel(clip.toFile())){
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();
            if(meta.getCodec()!=Codec.H264)throw new AssertionError("Expected H264");
            int frames=0,width=0,height=0;long first=0,last=0;
            for(var picture=grab.getNativeFrame();picture!=null;picture=grab.getNativeFrame()){
                width=picture.getWidth();height=picture.getHeight();long hash=1;for(byte b:picture.getPlaneData(0))hash=31*hash+b;
                if(frames==0)first=hash;last=hash;
                if(frames==meta.getTotalFrames()/2){
                    var rgb=Picture.create(width,height,ColorSpace.RGB);ColorUtil.getTransform(picture.getColor(),ColorSpace.RGB).transform(picture,rgb);
                    var pixels=rgb.getPlaneData(0);var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(x+y*width)*3;image.setRGB(x,y,(pixels[i]+128)<<16|(pixels[i+1]+128)<<8|pixels[i+2]+128);}
                    ImageIO.write(image,"png",Path.of(args[2]).toFile());
                }
                frames++;
            }
            if(frames<8||frames!=meta.getTotalFrames()||first==last||meta.getTotalDuration()<=0)throw new AssertionError("Incomplete or static clip");
            Files.writeString(Path.of(args[1]),"{\"status\":\"passed\",\"source\":\"fresh production Main F10 rendering workflow\",\"codec\":\"H264\",\"bytes\":"+bytes+",\"frames\":"+frames+",\"width\":"+width+",\"height\":"+height+",\"seconds\":"+meta.getTotalDuration()+",\"allFramesDecoded\":true,\"firstAndLastDiffer\":true}\n");
            System.out.println("PASS: "+frames+" decoded frames; "+bytes+" bytes");
        }
    }
}
