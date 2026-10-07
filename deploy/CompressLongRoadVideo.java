import org.jcodec.api.FrameGrab;
import org.jcodec.common.*;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.ColorUtil;
import java.nio.ByteBuffer;
import java.nio.file.*;
/** Resize the fresh F10 capture without changing its frame timestamps or scene order. */
public class CompressLongRoadVideo {
 public static void main(String[] a) throws Exception {
  try(var input=NIOUtils.readableChannel(Path.of(a[0]).toFile());var output=NIOUtils.writableChannel(Path.of(a[1]).toFile())) {
   var grab=FrameGrab.createFrameGrab(input);var mux=MP4Muxer.createMP4MuxerToChannel(output);
   var track=mux.addVideoTrack(Codec.H264,VideoCodecMeta.createSimpleVideoCodecMeta(new Size(720,408),ColorSpace.YUV420J));
   var encoder=H264Encoder.createH264Encoder();encoder.setKeyInterval(30);
   var small=Picture.create(720,408,ColorSpace.RGB);var yuv=Picture.create(720,408,ColorSpace.YUV420J);
   var transform=ColorUtil.getTransform(ColorSpace.RGB,ColorSpace.YUV420J);int index=0;
   for(var frame=grab.getNativeFrameWithMetadata();frame!=null;frame=grab.getNativeFrameWithMetadata()) {
    var original=frame.getPicture();var rgb=Picture.create(original.getWidth(),original.getHeight(),ColorSpace.RGB);
    ColorUtil.getTransform(original.getColor(),ColorSpace.RGB).transform(original,rgb);
    var src=rgb.getPlaneData(0);var dst=small.getPlaneData(0);
    for(int y=0;y<408;y++)for(int x=0;x<720;x++) {
     int si=((y*rgb.getHeight()/408)*rgb.getWidth()+x*rgb.getWidth()/720)*3;int di=(y*720+x)*3;
     System.arraycopy(src,si,dst,di,3);
    }
    transform.transform(small,yuv);var encoded=encoder.encodeFrame(yuv,ByteBuffer.allocate(encoder.estimateBufferSize(yuv)));
    track.addFrame(Packet.createPacket(encoded.getData(),Math.round(frame.getTimestamp()*1000),1000,Math.max(1,Math.round(frame.getDuration()*1000)),index++,encoded.isKeyFrame()?Packet.FrameType.KEY:Packet.FrameType.INTER,null));
   }
   mux.finish();System.out.println("Resized fresh capture frames: "+index);
  }
 }
}
