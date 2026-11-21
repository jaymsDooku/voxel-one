import dev.jayms.net.city.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Owner-only, read-only private city snapshot. No collection, paths or simulation mutation. */
public final class RegionCityOwnerPrototype {
    private final CitySimulation simulation;private final CityHistory history;private final long revision;
    private final RegionSidecarsPrototype sidecars;private final byte[] encoded;
    private RegionCityOwnerPrototype(CitySimulation simulation,CityHistory history,long revision,RegionSidecarsPrototype sidecars,byte[] encoded){this.simulation=simulation;this.history=history;this.revision=revision;this.sidecars=sidecars;this.encoded=encoded;}
    private static void check(BooleanSupplier cancel){if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new CancellationException();}
    public static RegionCityOwnerPrototype capture(CitySimulation simulation,BooleanSupplier cancel)throws IOException {
        Objects.requireNonNull(simulation);Objects.requireNonNull(cancel);check(cancel);var history=simulation.history;
        if(history==null)throw new IOException("Missing owner city history");long revision=history.revision();var frame=simulation.frame();
        if(frame.config().city()&&(history.catalog().isEmpty()||history.latest()>frame.elapsed()))throw new IOException("City history requires initialized owner state");
        var citySink=new RegionHistoryPrototype.LimitedOutput(RegionSidecarsPrototype.MAX_CONTENT,cancel);var out=new DataOutputStream(citySink);out.writeInt(0x43495441);frame.write(out,17);out.flush();byte[] city=citySink.bytes.toByteArray();check(cancel);
        byte[] historyBytes=null;
        if(frame.config().city()){
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
            for(int offset=0;offset<city.length;offset+=65536){check(cancel);digest.update(city,offset,Math.min(65536,city.length-offset));}
            int remaining=RegionSidecarsPrototype.MAX_CONTENT-city.length;var historySink=new RegionHistoryPrototype.LimitedOutput(remaining,cancel);history.writeSnapshot(historySink,HexFormat.of().formatHex(digest.digest()),remaining);historyBytes=historySink.bytes.toByteArray();
        }
        if(simulation.history!=history||history.revision()!=revision)throw new IOException("City history changed while capturing");check(cancel);
        var sidecars=RegionSidecarsPrototype.capture(city,historyBytes,cancel);return new RegionCityOwnerPrototype(simulation,history,revision,sidecars,sidecars.encode(cancel));
    }
    boolean sameIdentity(CitySimulation current){return current==simulation&&current.history==history&&history.revision()==revision;}
    boolean sameState(RegionCityOwnerPrototype current){return current!=null&&simulation==current.simulation&&history==current.history&&revision==current.revision&&Arrays.equals(encoded,current.encoded);}
    RegionSidecarsPrototype sidecars(){return sidecars;}
}
