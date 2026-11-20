package dev.jayms.combat.hcf;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;

/** Decode every frame of an actual native capture and export frames for independent inspection. */
public final class HcfMediaCheck {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static Picture rgb(Picture frame) {
        var rgb=Picture.create(frame.getWidth(),frame.getHeight(),ColorSpace.RGB);
        ColorUtil.getTransform(frame.getColor(),ColorSpace.RGB).transform(frame,rgb);return rgb;
    }
    private static void write(Picture p,Path file,int width,int height) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);byte[] bytes=p.getPlaneData(0);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int n=(y*p.getWidth()+x)*3;image.setRGB(x,y,((bytes[n]+128)<<16)|((bytes[n+1]+128)<<8)|(bytes[n+2]+128));}
        ImageIO.write(image,"png",file.toFile());
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("MP4 report-json first-png last-png");
        Path clip=Path.of(args[0]);long size=Files.size(clip);check(size>0 && size<6_000_000,"Clip below six MB");
        try(var channel=NIOUtils.readableChannel(clip.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();check(meta.getCodec()==Codec.H264,"H264");
            Picture first=null,last=null;int frames=0,w=0,h=0;long firstCrc=0,lastCrc=0;
            for(Picture p;(p=grab.getNativeFrame())!=null;) {
                var decoded=rgb(p);var crc=new CRC32();crc.update(decoded.getPlaneData(0));
                if(first==null){first=decoded;firstCrc=crc.getValue();w=p.getCroppedWidth();h=p.getCroppedHeight();}
                last=decoded;lastCrc=crc.getValue();frames++;
            }
            check(frames>2 && frames==meta.getTotalFrames(),"Every frame decoded");check(firstCrc!=lastCrc,"Capture changes over time");
            write(first,Path.of(args[2]),w,h);write(last,Path.of(args[3]),w,h);
            String report="{\"bytes\":"+size+",\"codec\":\"H264\",\"width\":"+w+",\"height\":"+h+",\"frames\":"+frames+",\"seconds\":"+meta.getTotalDuration()+",\"allFramesDecoded\":true,\"changingFrames\":true}\n";
            Files.writeString(Path.of(args[1]),report);System.out.print(report);
        }
    }
}
