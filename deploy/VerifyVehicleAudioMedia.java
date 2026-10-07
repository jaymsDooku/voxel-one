import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.scale.ColorUtil;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import java.nio.file.*;
import java.util.Arrays;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Decode every F10 frame; export first/middle/last frames for visual inspection. */
public final class VerifyVehicleAudioMedia {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectories(output);
        long size=Files.size(file);if(size==0||size>6_000_000)throw new AssertionError("Clip exceeds artifact limit or is empty");
        try(var channel=NIOUtils.readableChannel(file.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();
            if(meta.getCodec()!=Codec.H264||meta.getTotalDuration()<=0)throw new AssertionError("Invalid MP4 metadata");
            int decoded=0,changes=0,previous=0,width=0,height=0;
            for(var frame=grab.getNativeFrame();frame!=null;frame=grab.getNativeFrame()) {
                width=frame.getCroppedWidth();height=frame.getCroppedHeight();int hash=Arrays.hashCode(frame.getPlaneData(0));
                if(decoded>0&&hash!=previous)changes++;previous=hash;
                if(decoded==0||decoded==meta.getTotalFrames()/2||decoded==meta.getTotalFrames()-1){
                    var rgb=Picture.create(frame.getWidth(),frame.getHeight(),ColorSpace.RGB);ColorUtil.getTransform(frame.getColor(),ColorSpace.RGB).transform(frame,rgb);
                    byte[] pixels=rgb.getPlaneData(0);BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*rgb.getWidth()+x)*3;image.setRGB(x,y,((pixels[i]+128)<<16)|((pixels[i+1]+128)<<8)|(pixels[i+2]+128));}
                    ImageIO.write(image,"png",output.resolve("frame-"+decoded+".png").toFile());
                }
                decoded++;
            }
            if(decoded!=meta.getTotalFrames()||decoded<2||changes==0)throw new AssertionError("Clip incomplete or static");
            System.out.println("{\"status\":\"passed\",\"codec\":\"H264\",\"width\":"+width+",\"height\":"+height+",\"frames\":"+decoded+",\"changedFrames\":"+changes+",\"durationSeconds\":"+meta.getTotalDuration()+",\"bytes\":"+size+"}");
        }
    }
}
