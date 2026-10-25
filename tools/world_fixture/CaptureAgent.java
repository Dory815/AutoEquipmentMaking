import java.lang.instrument.Instrumentation;
import java.net.URLClassLoader;
import java.util.Arrays;

/** Loads Minecraft-aware snapshot code through the game's class loader. */
public final class CaptureAgent {
    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        var minecraft=Arrays.stream(instrumentation.getAllLoadedClasses())
            .filter(c->c.getName().equals("net.minecraft.client.Minecraft")).findFirst().orElseThrow();
        var helper=new URLClassLoader(new java.net.URL[]{CaptureAgent.class.getProtectionDomain().getCodeSource().getLocation()},minecraft.getClassLoader());
        helper.loadClass("CaptureWorker").getMethod("start",String.class).invoke(null,args);
    }
}
