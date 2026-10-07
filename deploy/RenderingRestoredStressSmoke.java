import dev.jayms.Main;
import dev.jayms.render.RenderPipeline;
import dev.jayms.window.Window;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;

/** Runs the preserved development workflow with the current renderer and a synthetic profile. */
public final class RenderingRestoredStressSmoke {
    public static void main(String[] args) throws Exception {
        var test=new PopulatedStressSmoke();
        test.root=Path.of(args[0]).toAbsolutePath();test.evidence=Path.of(args[1]);
        Files.createDirectories(test.root);Files.createDirectories(test.evidence);
        var game=new Main();CitySavesSmoke.set(game,"offlineSave",test.root.resolve("offline-city.dat"));
        CitySavesSmoke.set(game,"gameConfig",dev.jayms.net.city.GameConfig.cityGame());
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        try {
            game.run(new Main.FrameObserver(){
                public void started(Main value)throws Exception{
                    test.started(value);
                    glfwSetWindowSize(((Window)CitySavesSmoke.get(game,"window")).getHandle(),800,640);
                    var renderer=(RenderPipeline)CitySavesSmoke.get(game,"rendering");
                    renderer.settings.renderScale=.65f;renderer.resetHistory();
                }
                public void beforeFrame(Main value)throws Exception{test.beforeFrame(value);}
                public void afterFrame(Main value)throws Exception{test.afterFrame(value);}
            });
            test.require(test.step==29,"All preserved development workflow steps executed");
            Files.writeString(test.evidence.resolve("rendering-restored-stress-results.txt"),
                "Playtest: PASS. Current renderer, inherited assigned X11, synthetic city profile; 800x640 window, .65 render scale, GL3.3. Develop stress save, repeat action, populated districts, save/reload, original-city preservation and F6 regression.\n"+String.join("\n",test.checks)+"\n");
        } catch(Throwable error) {
            Files.writeString(test.root.resolve("assertion-failure.txt"),error.getClass().getName()+": "+error.getMessage()+"\n");throw error;
        }
    }
}
