import dev.jayms.launcher.*;
import java.awt.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual UI integration for an independently promoted native catalog with web withheld. */
public final class UnavailableWebPlaytest {
    public static void main(String[] args) throws Exception {
        Path catalogFile=Path.of(args[0]),profile=Path.of(args[1]),image=Path.of(args[2]);
        Files.createDirectories(profile);Files.createDirectories(image.toAbsolutePath().getParent());
        Files.copy(catalogFile,profile.resolve("catalog.properties"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(profile.resolve("hub.properties"),"updates=false\n");
        System.setProperty("user.home",profile.toAbsolutePath().toString());
        HubCatalog catalog;
        try(var input=Files.newInputStream(catalogFile)){catalog=HubCatalog.read(input);}
        var web=catalog.entries().stream().filter(e->e.id().equals("space-invaders")).findFirst().orElseThrow();
        if(web.supported(Platform.current()))throw new AssertionError("Unpublished web target enabled");
        if(catalog.entries().stream().filter(e->e.adapter().equals("native")&&e.supported(Platform.current())).count()!=3)
            throw new AssertionError("Native library changed while web withheld");
        HubFrame[] frame={null};
        try {
            SwingUtilities.invokeAndWait(()->{try{frame[0]=new HubFrame(profile);frame[0].setVisible(true);frame[0].detail(web);}catch(Exception e){throw new RuntimeException(e);}});
            SwingUtilities.invokeAndWait(()->{
                boolean unavailable=false,disabled=false;
                for(Component c:HubPlaytest.descendants(frame[0])) {
                    if(c instanceof JLabel label && label.getText()!=null && label.getText().contains("Temporarily unavailable"))unavailable=true;
                    if(c instanceof JButton button && "Open in browser".equals(button.getText()))disabled=!button.isEnabled();
                }
                if(!unavailable||!disabled)throw new AssertionError("Web unavailability label/action incorrect");
            });
            new Robot().waitForIdle();Thread.sleep(400);
            ImageIO.write(new Robot().createScreenCapture(frame[0].getBounds()),"png",image.toFile());
            System.out.println("Playtest: actual catalog keeps three supported native entries; Space Invaders detail says Temporarily unavailable and browser action is disabled. No publication or native gameplay claim.");
        } finally {SwingUtilities.invokeAndWait(()->{if(frame[0]!=null)frame[0].dispose();});}
    }
}
