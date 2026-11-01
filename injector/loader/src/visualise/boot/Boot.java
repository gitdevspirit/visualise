package visualise.boot;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Bootstrap-side glue between the native agent and the engine. Deliberately tiny and ASM-free: everything
 * heavy lives in the engine jar, loaded through a private classloader.
 */
public final class Boot {
    private Boot() {}

    private static volatile Engine engine;
    private static volatile boolean broken;

    /** Registered by the native agent (JNI DefineClass). */
    public static native Class<?> define0(ClassLoader loader, String binaryName, byte[] bytes);

    /** Called by the agent once the VM is up. options looks like "dir=/path,..." */
    public static void init(String options) {
        try {
            String dir = null;
            for (String part : options.split(",")) {
                if (part.startsWith("dir=")) dir = part.substring(4);
            }
            if (dir == null) throw new IllegalStateException("no dir= option");

            List<URL> urls = new ArrayList<URL>();
            urls.add(new File(dir, "visualise-engine.jar").toURI().toURL());
            File[] libs = new File(dir, "lib").listFiles();
            if (libs != null) for (File f : libs) if (f.getName().endsWith(".jar")) urls.add(f.toURI().toURL());

            // parent == null: bootstrap only, so the engine sees Boot/Hooks/Engine but not the game's own ASM.
            URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), null);
            Class<?> impl = Class.forName("visualise.engine.EngineImpl", true, loader);
            engine = (Engine) impl.getConstructor(String.class).newInstance(dir);
            System.out.println("[visualise] engine ready (" + dir + ")");
        } catch (Throwable t) {
            broken = true;
            System.err.println("[visualise] init failed: " + t);
            t.printStackTrace();
        }
    }

    /** Called by the agent for every class-file load. */
    public static byte[] transform(ClassLoader loader, String name, byte[] bytes) {
        Engine e = engine;
        if (e == null || broken) return null;
        try {
            return e.transform(loader, name, bytes);
        } catch (Throwable t) {
            log(t);
            return null;
        }
    }

    private static boolean started;

    /** First game hook calls this once; defines the payload in the game's loader and runs it. */
    static synchronized void startPayload() {
        if (started || engine == null) return;
        started = true;
        try {
            engine.startPayload();
        } catch (Throwable t) {
            log(t);
        }
    }

    static void log(Throwable t) {
        System.err.println("[visualise] " + t);
        t.printStackTrace();
    }
}
