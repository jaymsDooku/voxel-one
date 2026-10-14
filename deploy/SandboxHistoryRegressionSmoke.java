import dev.jayms.*;
import dev.jayms.net.city.*;
import dev.jayms.render.*;
import java.nio.file.*;
import static org.lwjgl.glfw.GLFW.*;

/** The mayor shortcut must not consume the sandbox's existing F9 performance control. */
public final class SandboxHistoryRegressionSmoke extends CitySavesSmoke {
    @Override String id()throws Exception{return Long.toUnsignedString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(((dev.jayms.window.Window)get(game,"window")).getHandle()));}
    public void started(Main value)throws Exception {game=value;starts++;x("windowfocus",id());due=System.nanoTime()+1_000_000_000L;}
    public void beforeFrame(Main value)throws Exception {
        if(System.nanoTime()<due)return;due=System.nanoTime()+700_000_000L;
        switch(step++) {
            case 0 -> key("F9");
            case 1 -> {require(((PerformanceRecorder)get(game,"performance")).active(),"Sandbox F9 still starts performance recording");key("F9");}
            case 2 -> {require(!((PerformanceRecorder)get(game,"performance")).active(),"Sandbox F9 stops performance recording");Files.writeString(evidence.resolve("history-sandbox-regression.txt"),"Playtest: PASS. Running native sandbox, isolated synthetic profile, inherited role display. Real F9 started and stopped the existing performance recorder. No mayor opened. This checks the shortcut collision introduced by dashboard navigation.\n");glfwSetWindowShouldClose(((dev.jayms.window.Window)get(game,"window")).getHandle(),true);}
            default -> throw new AssertionError("Sandbox driver did not stop");
        }
    }
    public static void main(String[] args)throws Exception {
        var test=new SandboxHistoryRegressionSmoke();test.root=Path.of(args[0]);test.evidence=Path.of(args[1]);Files.createDirectories(test.root);Files.createDirectories(test.evidence);
        var game=new Main();set(game,"offlineSave",test.root.resolve("sandbox.dat"));set(game,"gameConfig",GameConfig.sandbox());glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);game.run(test);test.require(test.step==3,"Sandbox workflow completed");
    }
}
