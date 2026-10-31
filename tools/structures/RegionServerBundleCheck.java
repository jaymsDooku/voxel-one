/** Runs the full shared file/source-fencing suite using synthetic server bytes only. */
public final class RegionServerBundleCheck {
    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("No paths accepted");
        RegionOfflineBundleCheck.main(new String[]{"server"});
    }
}
