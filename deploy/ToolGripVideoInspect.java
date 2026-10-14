import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.scale.ColorUtil;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
/** Decode the recorded implementation MP4 for a contact sheet and frame-count verification. */
public class ToolGripVideoInspect {
 public static void main(String[] args)throws Exception {
  try(var channel=NIOUtils.readableChannel(new File(args[0]))) {
   var grab=FrameGrab.createFrameGrab(channel);
   BufferedImage sheet=new BufferedImage(1280,960,BufferedImage.TYPE_INT_RGB);
   var graphics=sheet.createGraphics();int index=0,selected=0;
   int step=args.length>2?Integer.parseInt(args[2]):15,start=args.length>3?Integer.parseInt(args[3]):0;
   for(Picture frame=grab.getNativeFrame();frame!=null;frame=grab.getNativeFrame()) {
    if(index>=start&&(index-start)%step==0&&selected<16) {
     Picture rgb=Picture.create(frame.getWidth(),frame.getHeight(),ColorSpace.RGB);
     ColorUtil.getTransform(frame.getColor(),ColorSpace.RGB).transform(frame,rgb);
     BufferedImage image=new BufferedImage(rgb.getWidth(),rgb.getHeight(),BufferedImage.TYPE_INT_RGB);
     byte[] data=rgb.getPlaneData(0);
     for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
      int i=(y*image.getWidth()+x)*3;image.setRGB(x,y,((data[i]+128)<<16)|((data[i+1]+128)<<8)|(data[i+2]+128));
     }
     int x=selected%4*320,y=selected/4*240;
     graphics.drawImage(image,x,y,320,240,null);graphics.setColor(java.awt.Color.WHITE);graphics.drawString("Frame "+index,x+8,y+16);selected++;
    }index++;
   }
   graphics.dispose();ImageIO.write(sheet,"png",new File(args[1]));
   System.out.println("Decoded MP4: "+index+" frames; sampled "+selected+" frames.");
  }
 }
}
