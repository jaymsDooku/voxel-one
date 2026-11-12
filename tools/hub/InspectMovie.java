import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class InspectMovie {
 public static void main(String[] args)throws Exception {
  try(var channel=NIOUtils.readableChannel(Path.of(args[0]).toFile())){
   var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();
   System.out.println("Actual movie: "+meta.getTotalFrames()+" frames, "+meta.getTotalDuration()+" seconds");
   Picture p=grab.getNativeFrame();if(p==null)throw new AssertionError("No decodable frame");
   Picture rgb=Picture.create(p.getWidth(),p.getHeight(),ColorSpace.RGB);ColorUtil.getTransform(p.getColor(),ColorSpace.RGB).transform(p,rgb);
   byte[] data=rgb.getPlaneData(0);BufferedImage img=new BufferedImage(rgb.getWidth(),rgb.getHeight(),BufferedImage.TYPE_INT_RGB);
   for(int y=0;y<rgb.getHeight();y++)for(int x=0;x<rgb.getWidth();x++){int i=(x+y*rgb.getWidth())*3;img.setRGB(x,y,((data[i]+128)&255)<<16|((data[i+1]+128)&255)<<8|((data[i+2]+128)&255));}
   ImageIO.write(img,"png",Path.of(args[1]).toFile());
  }
 }
}
