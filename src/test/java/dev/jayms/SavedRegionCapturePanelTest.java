package dev.jayms;

import dev.jayms.ui.SavedRegionCapturePanel;
import dev.jayms.net.SavedRegionCaptureTest;
import dev.jayms.net.structure.StructurePackage;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.jayms.net.structure.StructurePackage.*;

class SavedRegionCapturePanelTest {
    @TempDir Path root;
    static final Position CAMERA=new Position(0,160,0);
    static SavedRegionCapturePanel.Status await(SavedRegionCapturePanel panel)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(panel.status().state()==SavedRegionCapturePanel.State.RUNNING||panel.status().state()==SavedRegionCapturePanel.State.CLOSING){assertTrue(System.nanoTime()<deadline);Thread.sleep(2);}return panel.status();
    }
    static String sha(Path file)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
    static void select(SavedRegionCapturePanel panel){panel.execute("first 0 160 0",CAMERA);panel.execute("second 31 161 1",CAMERA);panel.execute("anchor 16 160 0",CAMERA);}
    @Test void capturesToExistingLibraryFormatWithCanonicalBytesAndRepeat()throws Exception {
        var source=SavedRegionCaptureTest.fixture(root.resolve("source"));var original=Files.readAllBytes(source);var library=root.resolve("library");
        try(var panel=new SavedRegionCapturePanel(source,library,root.resolve("scratch"))) {
            panel.bindSnapshot(sha(source));select(panel);panel.execute("capture Named region",CAMERA);var status=await(panel);assertEquals(SavedRegionCapturePanel.State.SAVED,status.state());assertEquals(100,status.progress());panel.execute("cancel",CAMERA);assertEquals(SavedRegionCapturePanel.State.SAVED,panel.status().state());
            var actual=StructurePackage.load(library.resolve("Named region.vstr"));assertArrayEquals(SavedRegionCaptureTest.expected(source,"Named region",new Bounds(32,2,2,16,0,0),CAMERA).encode(),actual.encode());
            panel.execute("capture Repeat",CAMERA);assertEquals(SavedRegionCapturePanel.State.SAVED,await(panel).state());assertTrue(Files.isRegularFile(library.resolve("Repeat.vstr")));assertArrayEquals(original,Files.readAllBytes(source));
        }
    }
    @Test void commandInputAndInvalidSelectionStayBoundedBeforeFilesystemWork()throws Exception {
        var library=root.resolve("library");try(var panel=new SavedRegionCapturePanel(root.resolve("missing"),library,root.resolve("scratch"))) {
            panel.bindSnapshot("0".repeat(64));panel.execute("capture Missing",CAMERA);assertTrue(panel.status().message().startsWith("Choose"));
            panel.execute("first 1000001 0 0",CAMERA);assertTrue(panel.status().message().startsWith("Invalid"));select(panel);panel.execute("anchor 500 160 0",CAMERA);panel.execute("capture Invalid",CAMERA);assertTrue(panel.status().message().startsWith("Invalid"));assertFalse(Files.exists(library));
            panel.execute("anchor 0 160 0",CAMERA);panel.execute("capture ../escape",CAMERA);assertTrue(panel.status().message().startsWith("Name:"));assertFalse(Files.exists(library));
            panel.toggle();for(int i=0;i<5000;i++)panel.character('x');panel.backspace();panel.enter(CAMERA);assertTrue(panel.status().message().startsWith("Use"));panel.toggle();panel.character('x');panel.enter(CAMERA);assertFalse(Files.exists(library));
        }
    }
    @Test void malformedSourceAndFailedPublicationNeverClaimSaved()throws Exception {
        var source=root.resolve("bad.dat");Files.write(source,new byte[]{1,2,3});try(var panel=new SavedRegionCapturePanel(source,root.resolve("library"),root.resolve("scratch"))){panel.bindSnapshot(sha(source));select(panel);panel.execute("capture Bad",CAMERA);assertEquals(SavedRegionCapturePanel.State.FAILED,await(panel).state());assertFalse(Files.exists(root.resolve("library")));}
        source=SavedRegionCaptureTest.fixture(root.resolve("valid"));Path library=root.resolve("blocked");Files.writeString(library,"synthetic blocker");try(var panel=new SavedRegionCapturePanel(source,library,root.resolve("export-scratch"))){panel.bindSnapshot(sha(source));select(panel);panel.execute("capture Failed",CAMERA);assertEquals(SavedRegionCapturePanel.State.FAILED,await(panel).state());assertEquals("synthetic blocker",Files.readString(library));}
    }
    @Test void cancellationAndCloseHaveTerminalReceiptsAndPreserveTheSource()throws Exception {
        var source=SavedRegionCaptureTest.fixture(root.resolve("cancel"));var original=Files.readAllBytes(source);var library=root.resolve("library");
        var panel=new SavedRegionCapturePanel(source,library,root.resolve("scratch"));panel.bindSnapshot(sha(source));select(panel);panel.execute("first 0 0 0",CAMERA);panel.execute("second 1023 255 1023",CAMERA);panel.execute("anchor 0 0 0",CAMERA);panel.execute("capture Cancelled",CAMERA);panel.execute("cancel",CAMERA);assertEquals(SavedRegionCapturePanel.State.CANCELLED,await(panel).state());assertFalse(Files.exists(library.resolve("Cancelled.vstr")));panel.close();assertEquals(SavedRegionCapturePanel.State.CLOSED,panel.status().state());assertArrayEquals(original,Files.readAllBytes(source));
        var closing=new SavedRegionCapturePanel(source,library,root.resolve("closing"));closing.bindSnapshot(sha(source));select(closing);closing.execute("capture Closing",CAMERA);closing.close();await(closing);assertEquals(SavedRegionCapturePanel.State.CLOSED,closing.status().state());assertArrayEquals(original,Files.readAllBytes(source));
    }
    @Test void repeatedCancellationMessagesCannotReplaceTheWorkersTerminalReceipt()throws Exception {
        var source=SavedRegionCaptureTest.fixture(root.resolve("race"));var library=root.resolve("library");
        try(var panel=new SavedRegionCapturePanel(source,library,root.resolve("scratch"))) {
            panel.bindSnapshot(sha(source));select(panel);
            for(int run=0;run<8;run++) {
                panel.execute("capture Race "+run,CAMERA);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                while(panel.status().state()==SavedRegionCapturePanel.State.RUNNING){panel.execute("cancel",CAMERA);assertTrue(System.nanoTime()<end);Thread.sleep(1);}
                var result=panel.status();assertTrue(result.state()==SavedRegionCapturePanel.State.CANCELLED||result.state()==SavedRegionCapturePanel.State.SAVED);
                assertEquals(result.state()==SavedRegionCapturePanel.State.SAVED,Files.exists(library.resolve("Race "+run+".vstr")));
            }
        }
    }
    @Test void existingLibraryAliasCannotOverwriteTheSavedWorld()throws Exception {
        var source=SavedRegionCaptureTest.fixture(root.resolve("alias-source"));var bytes=Files.readAllBytes(source);var library=Files.createDirectories(root.resolve("library"));var alias=library.resolve("Aliased.vstr");Files.createLink(alias,source);
        try(var panel=new SavedRegionCapturePanel(source,library,root.resolve("scratch"))){panel.bindSnapshot(sha(source));select(panel);panel.execute("capture Aliased",CAMERA);assertEquals(SavedRegionCapturePanel.State.FAILED,await(panel).state());assertArrayEquals(bytes,Files.readAllBytes(source));assertArrayEquals(bytes,Files.readAllBytes(alias));}
    }
    @Test void viewedIdentityIsRequiredAndChangedValidSourceRejects()throws Exception {
        var source=SavedRegionCaptureTest.fixture(root.resolve("identity"));var original=Files.readAllBytes(source);var library=root.resolve("library");
        try(var panel=new SavedRegionCapturePanel(source,library,root.resolve("scratch"))) {
            select(panel);panel.execute("capture Unbound",CAMERA);assertTrue(panel.status().message().startsWith("Wait for saved"));assertFalse(Files.exists(library));
            panel.bindSnapshot(sha(source));assertThrows(IllegalArgumentException.class,()->panel.bindSnapshot("0".repeat(64)));
            var changed=original.clone();java.nio.ByteBuffer.wrap(changed).putLong(4,43);Files.write(source,changed);
            panel.execute("capture Changed",CAMERA);assertEquals(SavedRegionCapturePanel.State.FAILED,await(panel).state());assertFalse(Files.exists(library));assertArrayEquals(changed,Files.readAllBytes(source));
        }finally{Files.write(source,original);}
    }
    @Test void keyboardUsesSelectedCameraCellsAndForeignOwnersReject()throws Exception {
        var source=SavedRegionCaptureTest.fixture(root.resolve("keyboard"));try(var panel=new SavedRegionCapturePanel(source,root.resolve("library"),root.resolve("scratch"))) {
            panel.bindSnapshot(sha(source));panel.toggle();for(String command:new String[]{"first","second","capture Keyboard"}){for(char c:command.toCharArray())panel.character(c);panel.enter(new Position(16,160,0));}assertEquals(SavedRegionCapturePanel.State.SAVED,await(panel).state());assertEquals(new Bounds(1,1,1,0,0,0),StructurePackage.load(root.resolve("library/Keyboard.vstr")).bounds);
            var executor=Executors.newSingleThreadExecutor();try{executor.submit(()->{assertThrows(IllegalStateException.class,panel::close);assertThrows(IllegalStateException.class,panel::status);assertThrows(IllegalStateException.class,panel::toggle);return null;}).get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
        }
    }
}
