import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Decode every F10 frame and extract a contact sheet for human inspection. */
public class VerifyMusicMedia {
    public static void main(String[]args)throws Exception {
        Path clip=Path.of(args[0]);long bytes=Files.size(clip);if(bytes<=0||bytes>6_000_000)throw new AssertionError("MP4 size");
        try(var channel=NIOUtils.readableChannel(clip.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();if(meta.getCodec()!=Codec.H264)throw new AssertionError("H264 required");
            int frames=0,width=0,height=0,tiles=0;long first=0,last=0;BufferedImage sheet=null;
            for(var picture=grab.getNativeFrame();picture!=null;picture=grab.getNativeFrame()) {
                width=picture.getCroppedWidth();height=picture.getCroppedHeight();long hash=1;for(byte b:picture.getPlaneData(0))hash=31*hash+b;if(frames==0)first=hash;last=hash;
                if(frames==meta.getTotalFrames()/10||frames==meta.getTotalFrames()*4/10||frames==meta.getTotalFrames()*7/10||frames==meta.getTotalFrames()*9/10){
                    var rgb=Picture.create(picture.getWidth(),picture.getHeight(),ColorSpace.RGB);ColorUtil.getTransform(picture.getColor(),ColorSpace.RGB).transform(picture,rgb);byte[] pixels=rgb.getPlaneData(0);
                    if(sheet==null)sheet=new BufferedImage(width*2,height*2,BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*rgb.getWidth()+x)*3;sheet.setRGB((tiles%2)*width+x,(tiles/2)*height+y,((pixels[i]+128)&255)<<16|((pixels[i+1]+128)&255)<<8|((pixels[i+2]+128)&255));}tiles++;
                }
                frames++;
            }
            if(frames!=meta.getTotalFrames()||frames<30||meta.getTotalDuration()<8||first==last||tiles!=4)throw new AssertionError("Missing/static MP4 frames: decoded="+frames+" metadata="+meta.getTotalFrames()+" same="+(first==last)+" tiles="+tiles);
            ImageIO.write(sheet,"png",Path.of(args[2]).toFile());
            Files.writeString(Path.of(args[1]),"{\"status\":\"passed\",\"source\":\"Production MusicVisualizer F10 recording; controlled synthetic passages; silent shared recorder\",\"codec\":\"H264\",\"bytes\":"+bytes+",\"decodedFrames\":"+frames+",\"width\":"+width+",\"height\":"+height+",\"durationSeconds\":"+meta.getTotalDuration()+",\"checks\":[\"Every frame decoded\",\"Metadata frame count matched\",\"First and last image hashes differ\",\"Four actual decoded frames extracted for inspection\",\"Below 6 MB\"]}\n");
            System.out.println("PASS: F10 media "+frames+" frames, "+bytes+" bytes, "+meta.getTotalDuration()+" s");
        }
    }
}
