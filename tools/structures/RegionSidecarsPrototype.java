import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Exact private world sidecar bytes, never reusable structure contents. Not a semantic city validator. */
public final class RegionSidecarsPrototype {
    static final int MAX_CONTENT=4*1024*1024,MAX_BYTES=MAX_CONTENT+20;
    private static final int MAGIC=0x56525343;
    private final byte[] city,history;
    private RegionSidecarsPrototype(byte[] city,byte[] history){this.city=city;this.history=history;}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static byte[] copy(byte[] source,BooleanSupplier cancel){var result=new byte[source.length];for(int offset=0;offset<source.length;offset+=65536){check(cancel);System.arraycopy(source,offset,result,offset,Math.min(65536,source.length-offset));}check(cancel);return result;}
    public byte[] city(){return city.clone();}
    public Optional<byte[]> history(){return history.length==0?Optional.empty():Optional.of(history.clone());}
    public static RegionSidecarsPrototype capture(byte[] city,byte[] history,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(city);Objects.requireNonNull(cancel);check(cancel);if(history!=null&&history.length==0)throw new IOException("Empty history sidecar");int size=history==null?0:history.length;
        if(city.length<4||city.length>MAX_CONTENT||size>MAX_CONTENT-city.length)throw new IOException("Sidecar content budget");
        var cityCopy=copy(city,cancel);var historyCopy=history==null?new byte[0]:copy(history,cancel);
        int magic=ByteBuffer.wrap(cityCopy).getInt();if(magic<0x43495431||magic>0x43495441)throw new IOException("Unknown city sidecar format");
        if(historyCopy.length!=0){
            if(historyCopy.length<82)throw new IOException("History framing");
            var in=new DataInputStream(new ByteArrayInputStream(historyCopy));
            if(in.readInt()!=0x48495331||in.readInt()!=1||in.readUnsignedShort()!=64)throw new IOException("History format/version/fingerprint");
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
            for(int offset=0;offset<cityCopy.length;offset+=65536){check(cancel);digest.update(cityCopy,offset,Math.min(65536,cityCopy.length-offset));}
            if(!Arrays.equals(in.readNBytes(64),HexFormat.of().formatHex(digest.digest()).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("History city fingerprint mismatch");
            var crc=new CRC32();for(int offset=0;offset<historyCopy.length-8;offset+=65536){check(cancel);crc.update(historyCopy,offset,Math.min(65536,historyCopy.length-8-offset));}
            if(ByteBuffer.wrap(historyCopy).getLong(historyCopy.length-8)!=crc.getValue())throw new IOException("History CRC");
        }
        check(cancel);return new RegionSidecarsPrototype(cityCopy,historyCopy);
    }
    public byte[] encode(BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(cancel);check(cancel);var sink=new RegionHistoryPrototype.LimitedOutput(MAX_BYTES,cancel);var crc=new CRC32();var out=new DataOutputStream(new CheckedOutputStream(sink,crc));
        out.writeInt(MAGIC);out.writeInt(1);out.writeInt(city.length);out.write(city);out.writeInt(history.length);out.write(history);out.flush();new DataOutputStream(sink).writeInt((int)crc.getValue());var result=sink.bytes.toByteArray();check(cancel);return result;
    }
    public static RegionSidecarsPrototype decode(byte[] bytes,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(bytes);Objects.requireNonNull(cancel);check(cancel);
        if(bytes.length<24||bytes.length>MAX_BYTES)throw new IOException("Sidecar byte budget");
        var crc=new CRC32();for(int offset=0;offset<bytes.length-4;offset+=65536){check(cancel);crc.update(bytes,offset,Math.min(65536,bytes.length-4-offset));}
        if((int)crc.getValue()!=ByteBuffer.wrap(bytes).getInt(bytes.length-4))throw new IOException("Sidecar CRC");
        var in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-4));if(in.readInt()!=MAGIC||in.readInt()!=1)throw new IOException("Sidecar envelope format/version");
        int size=in.readInt();if(size<4||size>MAX_CONTENT||size>in.available()-4)throw new IOException("City byte budget");var city=in.readNBytes(size);
        size=in.readInt();if(size<0||size>MAX_CONTENT-city.length||size!=in.available())throw new IOException("History byte budget");
        return capture(city,size==0?null:in.readNBytes(size),cancel);
    }
}
