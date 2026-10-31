import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;

/** Displays the actual synthetic CLI result for a sanitized window-only capture. */
public final class RegionGenerationEvidence {
    public static void main(String[] args)throws Exception {
        String check=args.length==0?"RegionGenerationCheck":args[0];
        if(!java.util.Set.of("RegionGenerationCheck","RegionAuthorityCheck","RegionWorldLibraryCheck","RegionOfflineMetadataCheck","RegionOfflineImportCheck","RegionOfflineLegacyCheck","RegionSidecarsCheck","RegionOfflineBundleCheck","RegionServerMetadataCheck","RegionServerImportCheck").contains(check))throw new IllegalArgumentException("Unknown synthetic evidence check");
        boolean serverImport=check.equals("RegionServerImportCheck");boolean server=check.equals("RegionServerMetadataCheck")||serverImport;boolean bundle=check.equals("RegionOfflineBundleCheck");boolean sidecars=check.equals("RegionSidecarsCheck");boolean legacy=check.equals("RegionOfflineLegacyCheck");boolean offlineImport=check.equals("RegionOfflineImportCheck")||legacy||bundle;boolean metadata=check.equals("RegionOfflineMetadataCheck")||offlineImport||sidecars||server;boolean library=check.equals("RegionWorldLibraryCheck")||metadata;boolean authority=check.equals("RegionAuthorityCheck");String folder=authority?"target/structure-authority-evidence":"target/structure-generation-evidence";
        var arguments=new java.util.ArrayList<>(java.util.List.of("java","-Xmx512m","-cp",System.getProperty("java.class.path"),check));
        if(!library)arguments.add(folder);
        var command=new ProcessBuilder(arguments).redirectErrorStream(true).start();
        String output=new String(command.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int exit=command.waitFor();
        String text=(serverImport?"Voxel One: private server-world base-file import CLI check\n\n":server?"Voxel One: private server-world survival metadata CLI check\n\n":bundle?"Voxel One: private offline file-bundle migration CLI check\n\n":sidecars?"Voxel One: private city sidecar preservation CLI check\n\n":legacy?"Voxel One: private v3..7 offline base-file migration CLI check\n\n":offlineImport?"Voxel One: private v8/v9 offline base-file migration CLI check\n\n":metadata?"Voxel One: private offline metadata / generation CLI check\n\n":library?"Voxel One: private physical-world/library codec CLI check\n\n":authority?"Voxel One: actual StructureEngine / private authority CLI check\n\n":"Voxel One: private region-generation CLI check\n\n")
                +"Actual child command: java -Xmx512m -cp "+System.getProperty("java.class.path")+"\n"
                +check+(library?"":" "+folder)+"\n"
                +(serverImport?"Data: hand-built synthetic server-save schemas and private generations.\n\n":server?"Data: synthetic survival identifiers/inventories and private generations.\n\n":bundle?"Data: synthetic offline base/city/history files and private generations.\n\n":sidecars?"Data: generated synthetic city/history files and private generations.\n\n":legacy?"Data: hand-built old-format fixtures checked by LocalGame loader.\n\n":offlineImport?"Data: synthetic LocalGame saves and temporary private generations.\n\n":metadata?"Data: synthetic inventory, drops and temporary private generations.\n\n":library?"Data: synthetic in-memory models and world cells.\n\n":"Data: isolated synthetic directories; removed after the check.\n\n")
                +"Observed exit code: "+exit+"\n\n"+output+"\n"
                +(server?"No authentication data read; no multiplayer server started.\n":bundle?"This requires cooperative source locking; game migration is unfinished.\n":sidecars?"This preserves sidecar bytes; it is not full semantic city validation.\n":offlineImport?"This tests a private importer, not production migration or sidecars.\n":metadata?"This tests private offline metadata and files, not a live game save.\n":library?"This tests a detached codec, not file publication or a live game save.\n":"This tests private file publication, not a live game save.\n")
                +"Directory fsync, power-loss and full city/GPU/network qualification\n"
                +"remain pending. Production integration is unfinished.";
        SwingUtilities.invokeAndWait(()->{
            var frame=new JFrame(serverImport?"Voxel One server import CLI evidence":server?"Voxel One server metadata CLI evidence":bundle?"Voxel One offline bundle CLI evidence":sidecars?"Voxel One sidecar CLI evidence":legacy?"Voxel One old offline import CLI evidence":offlineImport?"Voxel One offline import CLI evidence":metadata?"Voxel One offline metadata CLI evidence":library?"Voxel One library CLI evidence":authority?"Voxel One authority CLI evidence":"Voxel One generation CLI evidence");var area=new JTextArea(text);
            area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);
            area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,15));area.setMargin(new Insets(22,22,22,22));
            area.setBackground(new Color(18,25,35));area.setForeground(new Color(220,235,245));
            frame.add(area);frame.setSize(960,500);frame.setLocation(80,80);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);frame.setVisible(true);
        });
    }
}
