import org.jcodec.api.FrameGrab;
import org.jcodec.common.*;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.*;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.ColorUtil;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

/** Cut brief scenes from this run's production F10 capture; never invent or reorder frames. */
public class RecipeVideoHighlights {
 public static void main(String[] a) throws Exception {
  List<double[]> ranges=new ArrayList<>();
  for(String line:Files.readAllLines(Path.of(a[2]))) {double time=Double.parseDouble(line.split("\\t")[1]);ranges.add(new double[]{Math.max(0,time-3),time+1});}
  ranges.sort(Comparator.comparingDouble(x->x[0]));
  try(var input=NIOUtils.readableChannel(Path.of(a[0]).toFile());var output=NIOUtils.writableChannel(Path.of(a[1]).toFile())) {
   var grab=FrameGrab.createFrameGrab(input);var size=grab.getVideoTrack().getMeta().getVideoCodecMeta().getSize();
   var mux=MP4Muxer.createMP4MuxerToChannel(output);var track=mux.addVideoTrack(Codec.H264,VideoCodecMeta.createSimpleVideoCodecMeta(size,ColorSpace.YUV420J));
   var encoder=H264Encoder.createH264Encoder();encoder.setKeyInterval(15);
   int frames=0,decoded=0;long pts=0;Set<Integer> seen=new HashSet<>();
   for(var frame=grab.getNativeFrameWithMetadata();frame!=null;frame=grab.getNativeFrameWithMetadata()) {
    decoded++;int scene=-1;for(int i=0;i<ranges.size();i++)if(frame.getTimestamp()>=ranges.get(i)[0]&&frame.getTimestamp()<=ranges.get(i)[1]){scene=i;break;}
    if(scene<0)continue;seen.add(scene);
    var original=frame.getPicture().cropped();var rgb=Picture.create(original.getWidth(),original.getHeight(),ColorSpace.RGB);
    ColorUtil.getTransform(original.getColor(),ColorSpace.RGB).transform(original,rgb);
    var yuv=Picture.create(size.getWidth(),size.getHeight(),ColorSpace.YUV420J);ColorUtil.getTransform(ColorSpace.RGB,ColorSpace.YUV420J).transform(rgb,yuv);
    var encoded=encoder.encodeFrame(yuv,ByteBuffer.allocate(encoder.estimateBufferSize(yuv)));
    long duration=Math.max(50,Math.min(1000,Math.round(frame.getDuration()*1000)));
    track.addFrame(Packet.createPacket(encoded.getData(),pts,1000,duration,frames++,encoded.isKeyFrame()?Packet.FrameType.KEY:Packet.FrameType.INTER,null));pts+=duration;
   }
   if(seen.size()!=ranges.size())throw new AssertionError("Each marked scene must have captured frames");
   mux.finish();System.out.println("F10 highlights: "+frames+" frames from "+decoded+" decoded source frames; "+pts/1000.0+" seconds; "+ranges.size()+" scenes in source order.");
  }
  if(Files.size(Path.of(a[1]))>6000000)throw new AssertionError("Highlight exceeds artifact limit");
 }
}
