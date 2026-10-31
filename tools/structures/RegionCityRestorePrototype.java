import dev.jayms.net.city.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Strict detached city/history decode. Never opens paths, resets history or installs live state. */
public final class RegionCityRestorePrototype {
    public record Restored(CityFrame frame,Optional<CityHistory> history){}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    private static InputStream input(byte[] bytes,BooleanSupplier cancel){return new FilterInputStream(new ByteArrayInputStream(bytes)){
        @Override public int read()throws IOException{check(cancel);return super.read();}
        @Override public int read(byte[] data,int offset,int length)throws IOException{check(cancel);return in.read(data,offset,length);}
    };}
    public static Restored decode(RegionSidecarsPrototype sidecars,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(sidecars);Objects.requireNonNull(cancel);check(cancel);byte[] city=sidecars.city();var in=new DataInputStream(input(city,cancel));
        int version=in.readInt()-0x43495430;if(version<1||version>17)throw new IOException("City restore format");CityFrame frame;
        try{frame=CityFrame.read(in,version);}catch(IllegalArgumentException invalid){throw new IOException("Invalid city restore state",invalid);}
        if(in.read()!=-1)throw new IOException("Trailing city restore bytes");check(cancel);
        var savedHistory=sidecars.history();if(savedHistory.isEmpty())return new Restored(frame,Optional.empty());
        if(!frame.config().city())throw new IOException("Sandbox must not discard city history");
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        for(int offset=0;offset<city.length;offset+=65536){check(cancel);digest.update(city,offset,Math.min(65536,city.length-offset));}
        byte[] history=savedHistory.orElseThrow();var decoded=CityHistory.readSnapshot(input(history,cancel),HexFormat.of().formatHex(digest.digest()),frame.config(),frame.elapsed(),history.length);check(cancel);
        return new Restored(frame,Optional.of(decoded));
    }
}
