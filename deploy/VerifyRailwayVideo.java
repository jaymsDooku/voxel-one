import org.jcodec.api.FrameGrab;
import org.jcodec.common.Codec;
import org.jcodec.common.io.NIOUtils;
import java.nio.file.*;
import java.util.Arrays;

/** Decode the captured F10 clip independently; print only public media metadata. */
public final class VerifyRailwayVideo {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[0]);
        if(Files.size(file)>6_000_000)throw new AssertionError("Clip exceeds artifact limit");
        try(var channel=NIOUtils.readableChannel(file.toFile())) {
            var grab=FrameGrab.createFrameGrab(channel);var meta=grab.getVideoTrack().getMeta();
            if(meta.getCodec()!=Codec.H264 || meta.getTotalDuration()<=0)throw new AssertionError("Invalid MP4 metadata");
            int decoded=0,changes=0,previous=0,width=0,height=0;
            for(var frame=grab.getNativeFrame();frame!=null;frame=grab.getNativeFrame()) {
                width=frame.getCroppedWidth();height=frame.getCroppedHeight();int hash=Arrays.hashCode(frame.getPlaneData(0));
                if(decoded>0&&hash!=previous)changes++;previous=hash;decoded++;
            }
            if(decoded!=meta.getTotalFrames()||decoded<2||changes==0||width!=960||height!=540)throw new AssertionError("Clip cannot be fully decoded or has no motion: decoded="+decoded+", total="+meta.getTotalFrames()+", changes="+changes+", dimensions="+width+"x"+height);
            System.out.println("{\"status\":\"passed\",\"codec\":\"H264\",\"width\":"+width+",\"height\":"+height+",\"frames\":"+decoded+",\"changedFrames\":"+changes+",\"durationSeconds\":"+meta.getTotalDuration()+",\"bytes\":"+Files.size(file)+"}");
        }
    }
}
